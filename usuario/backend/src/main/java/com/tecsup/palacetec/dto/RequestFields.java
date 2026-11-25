package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Lee un cuerpo JSON con las reglas y los mensajes de los serializadores de Django REST Framework: distingue un campo
 * ausente de uno nulo o vacío y reúne los errores de todos los campos, cosas que un DTO con Bean Validation no permite.
 */
public final class RequestFields {

    private static final String NON_FIELD = "non_field_errors";
    private static final Set<String> TRUE_VALUES = Set.of("t", "y", "yes", "true", "on", "1");
    private static final Set<String> FALSE_VALUES = Set.of("f", "n", "no", "false", "off", "0");
    private static final char NULL_CHARACTER = 0;
    private static final char NEXT_LINE = 0x85;

    private final JsonNode body;
    private final FieldMessages messages;
    private final Map<String, List<String>> errors = new LinkedHashMap<>();

    private RequestFields(JsonNode body, FieldMessages messages) {
        this.body = body;
        this.messages = messages;
    }

    public static RequestFields of(JsonNode body) {
        return of(body, FieldMessages.DJANGO);
    }

    /** Un cuerpo vacío equivale a {}. */
    public static RequestFields of(JsonNode body, FieldMessages messages) {
        JsonNode node = body == null || body.isMissingNode() ? JsonNodeFactory.instance.objectNode() : body;
        if (node.isNull()) {
            throw InvalidFieldsException.field(NON_FIELD, "No data provided");
        }
        if (!node.isObject()) {
            throw InvalidFieldsException.field(NON_FIELD,
                    "Invalid data. Expected a dictionary, but got " + pythonType(node) + ".");
        }
        return new RequestFields(node, messages);
    }

    public boolean has(String field) {
        return body.has(field);
    }

    /** Valor válido del campo; null si no vino o si tiene errores (que quedan anotados). */
    public String text(String field, TextRule rule) {
        if (!body.has(field)) {
            if (rule.required) {
                error(field, messages.required());
            }
            return null;
        }
        JsonNode node = body.get(field);
        if (node.isTextual() && (rule.trim ? strip(node.textValue()) : node.textValue()).isEmpty()) {
            if (rule.allowBlank) {
                return "";
            }
            error(field, messages.blank());
            return null;
        }
        if (node.isNull()) {
            error(field, messages.nullValue());
            return null;
        }
        // Como CharField, un número se acepta y se convierte en texto.
        if (!node.isTextual() && !node.isNumber()) {
            error(field, messages.notText());
            return null;
        }
        String value = rule.trim ? strip(node.asText()) : node.asText();
        return isValid(field, value, rule) ? value : null;
    }

    private boolean isValid(String field, String value, TextRule rule) {
        List<String> problems = new ArrayList<>();
        if (value.codePointCount(0, value.length()) > rule.maxLength) {
            problems.add(messages.maxLength().formatted(rule.maxLength));
        }
        if (value.indexOf(NULL_CHARACTER) >= 0) {
            problems.add("Null characters are not allowed.");
        }
        value.codePoints().filter(point -> point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)
                .findFirst()
                .ifPresent(point -> problems.add("Surrogate characters are not allowed: U+%X.".formatted(point)));
        if (rule.email && !Emails.isValid(value)) {
            problems.add(messages.email());
        }
        rule.checks.stream().filter(check -> !check.accepts().test(value)).map(TextRule.Check::message)
                .forEach(problems::add);
        problems.forEach(problem -> error(field, problem));
        return problems.isEmpty();
    }

    /** BooleanField de un serializador parcial: null si el campo no vino. */
    public Boolean flag(String field) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            error(field, messages.nullValue());
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber() && (node.doubleValue() == 0 || node.doubleValue() == 1)) {
            return node.doubleValue() == 1;
        }
        String text = node.isTextual() ? node.textValue().toLowerCase(Locale.ROOT) : "";
        if (TRUE_VALUES.contains(text)) {
            return true;
        }
        if (FALSE_VALUES.contains(text)) {
            return false;
        }
        error(field, "Must be a valid boolean.");
        return null;
    }

    public void error(String field, String message) {
        errors.computeIfAbsent(field, key -> new ArrayList<>()).add(message);
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(HttpStatus.BAD_REQUEST, errors);
        }
    }

    /** str.strip() de Python, que también quita espacios que String.strip() conserva (por ejemplo U+00A0). */
    public static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isPythonSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isPythonSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isPythonSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character) || character == NEXT_LINE;
    }

    private static String pythonType(JsonNode node) {
        return switch (node.getNodeType()) {
            case ARRAY -> "list";
            case STRING -> "str";
            case BOOLEAN -> "bool";
            default -> node.isIntegralNumber() ? "int" : "float";
        };
    }
}
