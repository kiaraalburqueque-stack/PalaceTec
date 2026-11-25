package com.tecsup.palacetec.dto;

import java.math.BigDecimal;

/** Resultado de una apuesta para el jugador. */
public enum BetStatus {

    PENDING("Pendiente"), WON("Ganada"), LOST("Perdida"), PUSH("Empate");

    private final String label;

    BetStatus(String label) {
        this.label = label;
    }

    /** En la ruleta, cualquier pago es una victoria. */
    public static BetStatus ofRoulette(BigDecimal payout) {
        if (payout == null) {
            return PENDING;
        }
        return payout.signum() > 0 ? WON : LOST;
    }

    /** En el casino, recuperar lo apostado es un empate. */
    public static BetStatus ofCasino(BigDecimal amount, BigDecimal payout) {
        if (payout == null) {
            return PENDING;
        }
        int result = payout.compareTo(amount);
        if (result == 0) {
            return PUSH;
        }
        return result > 0 ? WON : LOST;
    }

    public String label() {
        return label;
    }
}
