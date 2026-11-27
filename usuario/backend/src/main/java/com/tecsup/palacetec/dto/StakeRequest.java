package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

/** Apuesta de una mano de blackjack. */
public record StakeRequest(BigDecimal amount, UUID reference) {

    private static final BigDecimal MIN = BigDecimal.TEN;
    private static final BigDecimal MAX = BigDecimal.valueOf(500);

    public static StakeRequest from(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        StakeRequest request = read(fields);
        fields.throwIfInvalid();
        return request;
    }

    static StakeRequest read(RequestFields fields) {
        return new StakeRequest(fields.decimal("amount", 12, 2, MIN, MAX), fields.uuid("reference"));
    }
}
