package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.repository.SettledTotals;
import java.math.BigDecimal;

/** Lo apostado, lo pagado y la diferencia. */
public record PlayTotals(
        @JsonProperty("total_wagered") String wagered,
        @JsonProperty("total_paid") String paid,
        String net) {

    public static PlayTotals sum(SettledTotals roulette, SettledTotals casino) {
        BigDecimal wagered = roulette.getWagered().add(casino.getWagered());
        BigDecimal paid = roulette.getPaid().add(casino.getPaid());
        return new PlayTotals(Formats.money(wagered), Formats.money(paid), Formats.money(paid.subtract(wagered)));
    }
}
