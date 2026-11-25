package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.entity.SportSelection;
import java.math.BigDecimal;
import java.util.List;

/** quotedOdds es la cuota que vio el jugador: si cambió, se le pide confirmar. */
public record SportsBetRequest(StakeRequest stake, Long eventId, SportSelection selection, BigDecimal quotedOdds) {

    private static final BigDecimal MIN_ODDS = new BigDecimal("1.01");

    public static SportsBetRequest from(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        StakeRequest stake = StakeRequest.read(fields);
        Long event = fields.integer("event_id", true, 1);
        SportSelection selection = fields.choice("selection", List.of(SportSelection.values()));
        BigDecimal odds = fields.decimal("quoted_odds", 5, 2, MIN_ODDS, null);
        fields.throwIfInvalid();
        return new SportsBetRequest(stake, event, selection, odds);
    }
}
