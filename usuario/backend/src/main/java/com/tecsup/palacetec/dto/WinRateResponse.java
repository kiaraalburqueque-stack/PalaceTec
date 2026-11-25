package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.repository.OutcomeCounts;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Como en Django, cuenta apuestas: dos apuestas en la misma ronda son dos. */
public record WinRateResponse(
        long won,
        long lost,
        long push,
        long total,
        @JsonProperty("win_rate") double winRate,
        @JsonProperty("lost_rate") double lostRate,
        String scope,
        String timezone) {

    public static WinRateResponse of(OutcomeCounts roulette, OutcomeCounts casino) {
        long won = roulette.getWon() + casino.getWon();
        long lost = roulette.getLost() + casino.getLost();
        long push = roulette.getPush() + casino.getPush();
        long total = won + lost + push;
        return new WinRateResponse(won, lost, push, total, rate(won, total), rate(lost, total), "settled_bets",
                "America/Lima");
    }

    /** round(cantidad * 100 / total, 2) de Python, que redondea el valor exacto del float. */
    private static double rate(long count, long total) {
        return total == 0 ? 0 : new BigDecimal(count * 100 / (double) total).setScale(2, RoundingMode.HALF_EVEN)
                .doubleValue();
    }
}
