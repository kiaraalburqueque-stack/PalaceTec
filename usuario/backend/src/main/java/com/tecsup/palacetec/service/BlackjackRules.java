package com.tecsup.palacetec.service;

import com.tecsup.palacetec.entity.BlackjackHand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Reglas del blackjack: la banca se planta en 17, incluido el 17 suave. */
final class BlackjackRules {

    static final int LIMIT = 21;
    private static final int DEALER_STANDS = 17;
    private static final int ACE = 11;
    private static final int FIGURE = 10;
    private static final Set<String> FIGURES = Set.of("J", "Q", "K");

    /** Resultado de una mano; multiplier es el pago total por crédito apostado. */
    enum Outcome {
        BLACKJACK("2.5"), WON("2"), PUSH("1"), LOST("0");

        private final BigDecimal multiplier;

        Outcome(String multiplier) {
            this.multiplier = new BigDecimal(multiplier);
        }

        BigDecimal multiplier() {
            return multiplier;
        }
    }

    private BlackjackRules() {
    }

    /** Cada As vale 11 mientras no se pase de 21; si no, 1. */
    static int score(List<String> cards) {
        int total = 0;
        int aces = 0;
        for (String card : cards) {
            String rank = card.substring(0, card.indexOf('_'));
            if (rank.equals("A")) {
                total += ACE;
                aces++;
            } else {
                total += FIGURES.contains(rank) ? FIGURE : Integer.parseInt(rank);
            }
        }
        while (total > LIMIT && aces > 0) {
            total -= ACE - 1;
            aces--;
        }
        return total;
    }

    /** Con 21 de entrada la mano termina sin que nadie pida carta. */
    static Outcome natural(BlackjackHand hand) {
        int player = score(hand.getPlayer());
        if (player == score(hand.getDealer())) {
            return Outcome.PUSH;
        }
        return player == LIMIT ? Outcome.BLACKJACK : Outcome.LOST;
    }

    /** La banca pide hasta plantarse, salvo que el jugador ya se haya pasado. */
    static Outcome playDealer(BlackjackHand hand) {
        int player = score(hand.getPlayer());
        while (player <= LIMIT && score(hand.getDealer()) < DEALER_STANDS) {
            hand.drawForDealer();
        }
        int dealer = score(hand.getDealer());
        if (player > LIMIT) {
            return Outcome.LOST;
        }
        if (dealer > LIMIT || player > dealer) {
            return Outcome.WON;
        }
        return player == dealer ? Outcome.PUSH : Outcome.LOST;
    }
}
