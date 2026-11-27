package com.tecsup.palacetec.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tecsup.palacetec.dto.OddsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** HU69 de Django. */
class OddsTest {

    @Test
    void aStraightUpIsOneInThirtySeven() {
        assertThat(Odds.compute("straight", "17")).isEqualTo(new OddsResponse("straight", "17", "Pleno al 17", 1, 37,
                "1/37", "2.70", "35:1", 36, "1/37", "2.70"));
    }

    @ParameterizedTest
    @CsvSource({"color, rojo, Color rojo, 18/37, 48.65, 1:1", "color, NEGRO, Color negro, 18/37, 48.65, 1:1",
            "parity, impar, Paridad impar, 18/37, 48.65, 1:1", "paridad, par, Paridad par, 18/37, 48.65, 1:1",
            "dozen, 2, Docena 2 (13-24), 12/37, 32.43, 2:1", "docena, 3, Docena 3 (25-36), 12/37, 32.43, 2:1",
            "column, 3, Columna 3, 12/37, 32.43, 2:1", "columna, 1, Columna 1, 12/37, 32.43, 2:1",
            "pleno, 0, Pleno al 0, 1/37, 2.70, 35:1", "numero, 36, Pleno al 36, 1/37, 2.70, 35:1"})
    void computesEveryBet(String bet, String selection, String label, String probability, String percent,
                          String payout) {
        OddsResponse odds = Odds.compute(bet, selection);
        assertThat(odds.label()).isEqualTo(label);
        assertThat(odds.probability()).isEqualTo(probability);
        assertThat(odds.probabilityPct()).isEqualTo(percent);
        assertThat(odds.payout()).isEqualTo(payout);
        assertThat(odds.houseEdge()).isEqualTo("1/37");
        assertThat(odds.houseEdgePct()).isEqualTo("2.70");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            straight | 37    | Selección no válida para pleno: usa 0, 1, 2….
            straight | 017   | Selección no válida para pleno: usa 0, 1, 2….
            color    | verde | Selección no válida para color: usa rojo, negro.
            columna  | 4     | Selección no válida para columna: usa 1, 2, 3.
            split    | 5     | Tipo de apuesta no válido. Usa straight, color, parity, dozen o column.
            """)
    void rejectsInvalidBets(String bet, String selection, String message) {
        assertThatThrownBy(() -> Odds.compute(bet, selection)).hasMessage(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ¿Qué probabilidad tiene un pleno al 17?                | STRAIGHT | 17
            probabilidad al 0                                      | STRAIGHT | 0
            ¿Qué chance tiene el número el 5?                      | STRAIGHT | 5
            ¿Probabilidad de que salga 08?                         | STRAIGHT | 8
            probabilidad del ROJO                                  | COLOR    | rojo
            ¿Cuál es la ventaja de la casa en el negro?            | COLOR    | negro
            ¿Qué posibilidad hay de acertar la segunda docena?     | DOZEN    | 2
            chance de la docena 25-36                              | DOZEN    | 3
            probabilidad de 13-24                                  | DOZEN    | 2
            probabilidad de una docena                             | DOZEN    | 1
            qué posibilidad tengo en 3 giros de la primera docena  | DOZEN    | 3
            probabilidad de la tercera columna                     | COLUMN   | 3
            probabilidad de la 2da columna                         | COLUMN   | 2
            probabilidad de impar                                  | PARITY   | impar
            ¿Probabilidad de PAR?                                  | PARITY   | par
            """)
    void detectsTheBetOfAQuestion(String message, Odds.Kind kind, String selection) {
        assertThat(Odds.parse(message)).isEqualTo(new Odds.Question(kind, selection));
    }

    @ParameterizedTest
    @ValueSource(strings = {"¿Cuánto paga el pleno al 17?", "probabilidad del 40", "¿probabilidad de un pleno al 017?",
            "probabilidad de ganar en la ruleta", "hola"})
    void otherQuestionsAreNotAboutABet(String message) {
        assertThat(Odds.parse(message)).isNull();
    }

    /** En Django se responden como la primera. */
    @ParameterizedTest
    @ValueSource(strings = {"probabilidad de la docena 4", "¿probabilidad de la cuarta docena?",
            "probabilidad de la 5ta columna", "probabilidad de la columna 0", "chance de la docena 13 a 24"})
    void missingDozensAndColumnsAreNotTheFirst(String message) {
        assertThat(Odds.parse(message)).isNull();
    }

    @Test
    void theTextComesFromTheComputedOdds() {
        assertThat(Odds.text(Odds.compute("color", "rojo"))).isEqualTo("Color rojo: gana con 18 de 37 números, una "
                + "probabilidad de 18/37 (48.65 %). Paga 1 a 1 (devuelve 2 veces lo apostado). La ventaja de la casa es "
                + "2.70 %: a largo plazo se pierde en promedio ese porcentaje de lo apostado. Cada giro es "
                + "independiente.");
    }
}
