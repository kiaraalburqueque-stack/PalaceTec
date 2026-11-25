package com.tecsup.palacetec.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Mismos mensajes que los serializadores de Django REST Framework 3.18. */
class RequestFieldsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static RequestFields fields(String json) throws Exception {
        return RequestFields.of(MAPPER.readTree(json));
    }

    private static Object errors(RequestFields fields) {
        return catchThrowableOfType(InvalidFieldsException.class, fields::throwIfInvalid).getBody();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}                 | This field is required.
            {"a": null}        | This field may not be null.
            {"a": ""}          | This field may not be blank.
            {"a": true}        | Not a valid string.
            {"a": []}          | Not a valid string.
            {"a": "12345"}     | Ensure this field has no more than 4 characters.
            """)
    void reportsTheDjangoMessage(String json, String message) throws Exception {
        RequestFields fields = fields(json);
        assertThat(fields.text("a", TextRule.required().max(4))).isNull();
        assertThat(errors(fields)).isEqualTo(Map.of("a", List.of(message)));
    }

    @Test
    void rejectsCharactersThatPostgresCannotStore() {
        var body = MAPPER.createObjectNode().put("nul", "a" + Character.toString(0)).put("surrogate",
                "a" + (char) 0xD800);
        RequestFields fields = RequestFields.of(body);
        fields.text("nul", TextRule.required());
        fields.text("surrogate", TextRule.required());
        assertThat(errors(fields)).isEqualTo(Map.of("nul", List.of("Null characters are not allowed."),
                "surrogate", List.of("Surrogate characters are not allowed: U+D800.")));
    }

    @Test
    void reportsEveryProblemOfEveryField() throws Exception {
        RequestFields fields = fields("{\"email\": \"" + "x".repeat(255) + "\"}");
        fields.text("email", TextRule.required().max(254).email());
        fields.text("password", TextRule.required());
        assertThat(errors(fields)).isEqualTo(Map.of(
                "email", List.of("Ensure this field has no more than 254 characters.", "Enter a valid email address."),
                "password", List.of("This field is required.")));
    }

    @Test
    void aWrongTypeUsesTheMessageOfTheField() throws Exception {
        RequestFields fields = fields("{\"email\": true, \"code\": []}");
        fields.text("email", TextRule.required().email());
        fields.text("code", TextRule.required().pattern("[a-z]+"));
        assertThat(errors(fields)).isEqualTo(Map.of("email", List.of("Enter a valid email address."),
                "code", List.of("This value does not match the required pattern.")));
    }

    @Test
    void trimsUnlessTheSpacesMatterAndAcceptsNumbers() {
        String noBreakSpace = Character.toString(0xA0);
        RequestFields fields = RequestFields.of(MAPPER.createObjectNode().put("a", noBreakSpace + " x ").put("n", 15)
                .put("blank", noBreakSpace + " "));
        assertThat(fields.text("a", TextRule.required())).isEqualTo("x");
        assertThat(fields.text("a", TextRule.required().keepSpaces())).isEqualTo(noBreakSpace + " x ");
        assertThat(fields.text("n", TextRule.required())).isEqualTo("15");
        assertThat(fields.text("blank", TextRule.optional().allowBlank())).isEmpty();
        assertThat(fields.text("missing", TextRule.optional())).isNull();
        fields.throwIfInvalid();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            [1]   | Invalid data. Expected a dictionary, but got list.
            "x"   | Invalid data. Expected a dictionary, but got str.
            5     | Invalid data. Expected a dictionary, but got int.
            5.5   | Invalid data. Expected a dictionary, but got float.
            false | Invalid data. Expected a dictionary, but got bool.
            null  | No data provided
            """)
    void rejectsABodyThatIsNotAnObject(String json, String message) {
        InvalidFieldsException error = catchThrowableOfType(InvalidFieldsException.class, () -> fields(json));
        assertThat(error.getBody()).isEqualTo(Map.of("non_field_errors", List.of(message)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "1", "1.0", "\"YES\"", "\"on\"", "\"t\"", "\"1\""})
    void readsTrue(String json) throws Exception {
        assertThat(fields("{\"a\": " + json + "}").flag("a")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "0", "0.0", "\"No\"", "\"off\"", "\"f\"", "\"0\""})
    void readsFalse(String json) throws Exception {
        assertThat(fields("{\"a\": " + json + "}").flag("a")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            "talvez" | Must be a valid boolean.
            2        | Must be a valid boolean.
            []       | Must be a valid boolean.
            ""       | Must be a valid boolean.
            null     | This field may not be null.
            """)
    void rejectsOtherBooleans(String json, String message) throws Exception {
        RequestFields fields = fields("{\"a\": " + json + "}");
        assertThat(fields.flag("a")).isNull();
        assertThat(errors(fields)).isEqualTo(Map.of("a", List.of(message)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.pe", "A.b+c@Sub.Example.com", "\"con!comillas\"@b.pe", "a@localhost",
            "a@[127.0.0.1]", "a@[::1]", "a@ñandú.pe", "a@xn--p1ai.xn--p1ai"})
    void acceptsTheEmailsThatDjangoAccepts(String email) {
        assertThat(Emails.isValid(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "a@", "@b.pe", "a@b", "a@b.p1", "a b@c.pe", "a@-b.pe", "a..b@c.pe", "ñ@b.pe",
            "a@[999.0.0.1]", "a@[1.2.3]", "a@[::zz]", "a@b.pe\n"})
    void rejectsTheEmailsThatDjangoRejects(String email) {
        assertThat(Emails.isValid(email)).isFalse();
    }
}
