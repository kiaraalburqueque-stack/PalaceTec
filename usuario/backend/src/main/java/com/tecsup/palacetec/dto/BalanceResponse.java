package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BalanceResponse(
        String balance,
        @JsonProperty("low_balance_threshold") String lowBalanceThreshold,
        @JsonProperty("is_low_balance") boolean lowBalance) {

    static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("10.00");

    public static BalanceResponse from(BigDecimal balance) {
        return new BalanceResponse(Formats.money(balance), Formats.money(LOW_BALANCE_THRESHOLD),
                balance.compareTo(LOW_BALANCE_THRESHOLD) < 0);
    }
}
