package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Lee un cuerpo JSON con las reglas y los mensajes de los serializadores de Django REST Framework: distingue un campo
 * ausente de uno nulo o vacío y reúne los errores de todos los campos, cosas que un DTO con Bean Validation no permite.
 */
public final class RequestFields {

    private static final String NON_FIELD = "non_field_errors";
    private static final Set<String> TRUE_VALUES = Set.of("t", "y", "yes", "true", "on", "1");
    private static final Set<String> FALSE_VALUES = Set.of("f", "n", "no", "false", "off", "0");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+(_[0-9]+)*");
    private static final Pattern ZERO_DECIMALS = Pattern.compile("\\.0*\\s*$");
    private static final Pattern UUID_HEX = Pattern.compile("[0-9a-fA-F]{32}");
    private static final int MAX_INTEGER_TEXT = 1000;
    private static final int UUID_BITS = 128;
    /** Desde aquí Python escribe un float con exponente, que int() no acepta. */
    private static final double FLOAT_EXPONENT = 1e16;
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

    /** Parámetros de la consulta: como en QueryDict, de uno repetido vale el último. */
    public static RequestFields ofQuery(HttpServletRequest request) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        request.getParameterMap().forEach((name, values) -> node.put(name, values[values.length - 1]));
        return of(node);
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
            error(field, typeProblem(rule));
            return null;
        }
        String value = rule.trim ? strip(node.asText()) : node.asText();
        return isValid(field, value, rule) ? value : null;
    }

    /** EmailField y RegexField usan su propio mensaje también para un valor que no es texto. */
    private String typeProblem(TextRule rule) {
        if (rule.email) {
            return messages.email();
        }
        return rule.checks.isEmpty() ? messages.notText() : TextRule.PATTERN_MESSAGE;
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

    /** IntegerField con mínimo. Lo que no cabe en un bigint queda en Long.MAX_VALUE: ninguna fila tiene ese id. */
    public Long integer(String field, boolean required, long min) {
        JsonNode node = value(field, required);
        if (node == null) {
            return null;
        }
        if (node.isTextual() && node.textValue().length() > MAX_INTEGER_TEXT) {
            error(field, "String value too large.");
            return null;
        }
        String text = integerText(node);
        if (!INTEGER.matcher(text).matches()) {
            error(field, "A valid integer is required.");
            return null;
        }
        BigInteger number = new BigInteger(text.replace("_", ""));
        if (number.compareTo(BigInteger.valueOf(min)) < 0) {
            error(field, "Ensure this value is greater than or equal to " + min + ".");
            return null;
        }
        return number.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    /** Texto que Python pasaría a int(), ya sin ".0" final; vacío si el valor no puede ser un entero. */
    private static String integerText(JsonNode node) {
        if (node.isTextual()) {
            return strip(ZERO_DECIMALS.matcher(node.textValue()).replaceFirst(""));
        }
        if (node.isIntegralNumber()) {
            return node.asText();
        }
        boolean whole = node.isNumber() && node.doubleValue() == Math.rint(node.doubleValue())
                && Math.abs(node.doubleValue()) < FLOAT_EXPONENT;
        return whole ? String.valueOf(node.longValue()) : "";
    }

    /** UUIDField: acepta las formas de uuid.UUID de Python y el entero de 128 bits. */
    public UUID uuid(String field) {
        JsonNode node = value(field, true);
        if (node == null) {
            return null;
        }
        BigInteger number = null;
        if (node.isTextual()) {
            String hex = node.textValue().replace("urn:", "").replace("uuid:", "")
                    .replaceAll("^[{}]+|[{}]+$", "").replace("-", "");
            number = UUID_HEX.matcher(hex).matches() ? new BigInteger(hex, 16) : null;
        } else if (node.isIntegralNumber()) {
            number = node.bigIntegerValue();
        } else if (node.isBoolean()) {
            number = node.booleanValue() ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (number == null || number.signum() < 0 || number.bitLength() > UUID_BITS) {
            error(field, "Must be a valid UUID.");
            return null;
        }
        return new UUID(number.shiftRight(Long.SIZE).longValue(), number.longValue());
    }

    /** DateField opcional de una consulta, donde un texto vacío equivale a no enviarlo. */
    public LocalDate date(String field) {
        String value = body.path(field).asText();
        if (value.isEmpty()) {
            return null;
        }
        LocalDate date = DateRange.parseDate(value);
        if (date == null) {
            error(field, "Date has wrong format. Use one of these formats instead: YYYY-MM-DD.");
        }
        return date;
    }

    /** ChoiceField opcional de una consulta, donde un texto vacío equivale a no enviarlo. */
    public String choiceOr(String field, String defaultValue, String... choices) {
        String value = body.path(field).asText();
        if (value.isEmpty()) {
            return defaultValue;
        }
        if (!List.of(choices).contains(value)) {
            error(field, "\"" + value + "\" is not a valid choice.");
            return null;
        }
        return value;
    }

    /** ChoiceField obligatorio. */
    public String choice(String field, String message, String... choices) {
        JsonNode node = value(field, true);
        if (node == null) {
            return null;
        }
        if (!node.isTextual() || !List.of(choices).contains(node.textValue())) {
            error(field, message);
            return null;
        }
        return node.textValue();
    }

    /** Valor presente y no nulo; si falta o es null, anota el error que corresponda. */
    private JsonNode value(String field, boolean required) {
        if (!body.has(field)) {
            if (required) {
                error(field, messages.required());
            }
            return null;
        }
        if (body.get(field).isNull()) {
            error(field, messages.nullValue());
            return null;
        }
        return body.get(field);
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
