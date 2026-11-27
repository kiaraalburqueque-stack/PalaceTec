package com.tecsup.palacetec.assistant;

import com.tecsup.palacetec.dto.OddsResponse;
import com.tecsup.palacetec.dto.PlainText;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.entity.BetKind;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.service.RouletteTable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Probabilidad, pago y ventaja de la casa de cada apuesta de la ruleta europea (HU69), con las mismas reglas de pago del
 * juego: la IA nunca los calcula.
 */
public final class Odds {

    /** Apuesta reconocida en una pregunta. */
    record Question(Kind kind, String selection) {
    }

    enum Kind {
        STRAIGHT(BetKind.STRAIGHT_UP, "Pleno", IntStream.rangeClosed(0, RouletteTable.MAX_NUMBER)
                .mapToObj(String::valueOf).toList(), "pleno", "numero"),
        COLOR(BetKind.COLOR, "Color", List.of("rojo", "negro")),
        PARITY(BetKind.PARITY, "Paridad", List.of("par", "impar"), "paridad"),
        DOZEN(BetKind.DOZEN, "Docena", List.of("1", "2", "3"), "docena"),
        COLUMN(BetKind.COLUMN, "Columna", List.of("1", "2", "3"), "columna");

        private final BetKind bet;
        private final String label;
        private final List<String> selections;
        private final Set<String> aliases;

        Kind(BetKind bet, String label, List<String> selections, String... aliases) {
            this.bet = bet;
            this.label = label;
            this.selections = selections;
            this.aliases = Set.of(aliases);
        }

        private String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static Kind of(String name) {
            return Arrays.stream(values()).filter(kind -> kind.code().equals(name) || kind.aliases.contains(name))
                    .findFirst().orElseThrow(() -> ApiException.badRequest(null,
                            "Tipo de apuesta no válido. Usa straight, color, parity, dozen o column."));
        }
    }

    private static final int SHOWN_SELECTIONS = 3;
    private static final int NUMBERS = RouletteTable.MAX_NUMBER + 1;
    private static final List<String> DOZENS = List.of("1-12", "13-24", "25-36");
    private static final Map<String, String> ORDINALS = Map.of("primera", "1", "1ra", "1", "1", "1", "segunda", "2",
            "2da", "2", "2", "2", "tercera", "3", "3ra", "3", "3", "3");
    /** Con un número de estos se pregunta por una docena o columna que no existe. */
    private static final Set<String> OTHER_ORDINALS = Set.of("cuarta", "quinta", "sexta", "septima", "octava", "novena",
            "decima", "undecima", "duodecima");
    private static final List<String> QUESTION_WORDS = List.of("probab", "posibilidad", "chance", "odds",
            "que tan facil", "cuantas veces sale", "ventaja de la casa");
    private static final Pattern WORD = Pattern.compile("[a-z0-9\\-]+");
    private static final Pattern NAMED_NUMBER = Pattern.compile(
            "\\b(?:pleno|numero|al|el)\\s+(?:al\\s+|el\\s+)?(\\d{1,2})\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ANY_NUMBER = Pattern.compile("\\b(\\d{1,2})\\b", Pattern.UNICODE_CHARACTER_CLASS);

    private Odds() {
    }

    public static OddsResponse compute(String bet, String selection) {
        return compute(Kind.of(bet), selection);
    }

    static OddsResponse compute(Question question) {
        return compute(question.kind(), question.selection());
    }

    private static OddsResponse compute(Kind kind, String value) {
        String selection = RequestFields.strip(value).toLowerCase(Locale.ROOT);
        if (!kind.selections.contains(selection)) {
            String shown = String.join(", ", kind.selections.stream().limit(SHOWN_SELECTIONS).toList());
            throw ApiException.badRequest(null, "Selección no válida para " + kind.label.toLowerCase(Locale.ROOT)
                    + ": usa " + shown + (kind.selections.size() > SHOWN_SELECTIONS ? "…" : "") + ".");
        }
        int winners = (int) IntStream.range(0, NUMBERS)
                .filter(number -> RouletteTable.wins(kind.bet, selection, number)).count();
        int totalReturn = RouletteTable.totalReturn(kind.bet);
        int edge = NUMBERS - winners * totalReturn;
        int divisor = BigInteger.valueOf(edge).gcd(BigInteger.valueOf(NUMBERS)).intValue();
        return new OddsResponse(kind.code(), selection, describe(kind, selection), winners, NUMBERS,
                winners + "/" + NUMBERS, percent(winners), totalReturn - 1 + ":1", totalReturn,
                edge / divisor + "/" + NUMBERS / divisor, percent(edge));
    }

    private static String percent(int part) {
        return new BigDecimal(part / (double) NUMBERS * 100).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String describe(Kind kind, String selection) {
        return switch (kind) {
            case STRAIGHT -> "Pleno al " + selection;
            case DOZEN -> "Docena " + selection + " (" + DOZENS.get(Integer.parseInt(selection) - 1) + ")";
            default -> kind.label + " " + selection;
        };
    }

    static String text(OddsResponse odds) {
        return odds.label() + ": gana con " + odds.winningNumbers() + " de " + odds.totalNumbers()
                + " números, una probabilidad de " + odds.probability() + " (" + odds.probabilityPct() + " %). Paga "
                + odds.payout().replace(":", " a ") + " (devuelve " + odds.totalReturn()
                + " veces lo apostado). La ventaja de la casa es " + odds.houseEdgePct()
                + " %: a largo plazo se pierde en promedio ese porcentaje de lo apostado. Cada giro es independiente.";
    }

    /** Detecta preguntas de probabilidad sobre una apuesta concreta; null si no lo es. */
    static Question parse(String message) {
        String text = PlainText.normalize(message);
        if (QUESTION_WORDS.stream().noneMatch(text::contains)) {
            return null;
        }
        List<String> words = WORD.matcher(text).results().map(result -> result.group()).toList();
        String range = DOZENS.stream().filter(text::contains).findFirst().orElse(null);
        if (range != null) {
            return new Question(Kind.DOZEN, String.valueOf(DOZENS.indexOf(range) + 1));
        }
        if (words.contains("docena")) {
            return ordinal(Kind.DOZEN, words);
        }
        if (words.contains("columna")) {
            return ordinal(Kind.COLUMN, words);
        }
        if (words.contains("rojo") || words.contains("negro")) {
            return new Question(Kind.COLOR, words.contains("rojo") ? "rojo" : "negro");
        }
        if (words.contains("impar") || words.contains("par")) {
            return new Question(Kind.PARITY, words.contains("impar") ? "impar" : "par");
        }
        Matcher number = NAMED_NUMBER.matcher(text);
        if (!number.find()) {
            number = ANY_NUMBER.matcher(text);
            if (!number.find()) {
                return null;
            }
        }
        int value = Integer.parseInt(number.group(1));
        return value <= RouletteTable.MAX_NUMBER ? new Question(Kind.STRAIGHT, String.valueOf(value)) : null;
    }

    /** Sin número se entiende la primera. Django también responde la primera si se pide una que no existe. */
    private static Question ordinal(Kind kind, List<String> words) {
        String selection = words.stream().map(ORDINALS::get).filter(found -> found != null).findFirst().orElse(null);
        if (selection == null && words.stream().anyMatch(Odds::isOtherNumber)) {
            return null;
        }
        return new Question(kind, selection == null ? "1" : selection);
    }

    private static boolean isOtherNumber(String word) {
        return OTHER_ORDINALS.contains(word) || word.chars().anyMatch(Character::isDigit);
    }
}
