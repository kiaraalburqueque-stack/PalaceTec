package com.tecsup.palacetec.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlackjackDeckTest {

    @Test
    void hasFiftyTwoDifferentCardsInAnotherOrderEachTime() {
        BlackjackDeck deck = new BlackjackDeck();
        List<String> first = deck.shuffled();
        assertThat(first).hasSize(52).doesNotHaveDuplicates().contains("A_S", "10_H", "K_C", "2_D");
        assertThat(first).allMatch(card -> card.matches("(A|[2-9]|10|J|Q|K)_[SHDC]"));
        assertThat(deck.shuffled()).containsExactlyInAnyOrderElementsOf(first).isNotEqualTo(first);
    }

    @ParameterizedTest
    @CsvSource({"A_S A_H 9_D, 21", "A_S K_S, 21", "A_H 6_H, 17", "A_S A_H, 12", "10_S 8_S 5_D, 23", "J_S Q_S K_S, 30",
            "A_S K_S Q_S, 21", "A_S A_H A_D A_C, 14", "2_S 3_S, 5"})
    void acesAreWorthElevenUnlessThatGoesOverTwentyOne(String cards, int score) {
        assertThat(BlackjackRules.score(List.of(cards.split(" ")))).isEqualTo(score);
    }
}
