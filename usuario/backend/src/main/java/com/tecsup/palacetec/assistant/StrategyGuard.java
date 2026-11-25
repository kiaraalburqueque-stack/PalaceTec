package com.tecsup.palacetec.assistant;

import com.tecsup.palacetec.dto.PlainText;
import java.util.List;

/**
 * Impide dar estrategias para ganar o animar a apostar más. A diferencia de Django, los signos de puntuación no separan
 * una palabra del filtro: "¿algún truco?" se detecta igual que "dame un truco".
 */
final class StrategyGuard {

    /** Frases ya normalizadas: minúsculas y sin tildes. */
    private static final List<String> PATTERNS = List.of(
            "estrategia", "truco", "trucos", "martingala", "fibonacci", "sistema para ganar",
            "metodo para ganar", "como gano", "como ganar", "como puedo ganar", "ganar siempre",
            "ganar seguro", "asegurar ganancia", "asegurar el premio", "nunca perder", "no perder",
            "recuperar lo perdido", "recuperar mis perdidas", "recuperar perdidas", "recuperar lo que perdi",
            "apostar mas", "subir la apuesta", "duplicar la apuesta", "doblar la apuesta",
            "que numero va a salir", "que numero saldra", "numero ganador", "predecir", "prediccion",
            "hackear", "hack", "patron de la ruleta");
    /** Desde aquí basta con que la frase aparezca dentro de otra palabra. */
    private static final int LONG_PATTERN = 7;

    private StrategyGuard() {
    }

    static boolean asksForStrategy(String text) {
        String plain = PlainText.normalize(text);
        String words = PlainText.words(plain);
        return PATTERNS.stream().anyMatch(pattern -> words.contains(" " + pattern + " ")
                || pattern.length() >= LONG_PATTERN && (plain.contains(pattern) || words.contains(pattern)));
    }
}
