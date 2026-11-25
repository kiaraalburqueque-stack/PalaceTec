package com.tecsup.palacetec.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrategyGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"Dame una estrategia para ganar en la ruleta", "¿Cómo recupero lo perdido? quiero recuperar "
            + "lo perdido", "¿qué número va a salir?", "¿Sirve la martingala?", "¿Debo apostar más para ganar?",
            "dame un truco", "ESTRATEGIAS", "quiero duplicar   la apuesta"})
    void blocksStrategies(String message) {
        assertThat(StrategyGuard.asksForStrategy(message)).isTrue();
    }

    /** En Django los signos pegados dejan pasar las palabras cortas. */
    @ParameterizedTest
    @ValueSource(strings = {"¿Tienes algún truco?", "truco, por favor", "¿algún hack?", "trucos!", "«hack»",
            "¿cómo... gano?", "apostar, más"})
    void punctuationDoesNotHideAWord(String message) {
        assertThat(StrategyGuard.asksForStrategy(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"¿Cuánto paga una docena?", "hola", "¿Qué es un pleno?", "shackleton", "estructura"})
    void allowsOtherQuestions(String message) {
        assertThat(StrategyGuard.asksForStrategy(message)).isFalse();
    }
}
