package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BalanceHistoryResponse(
        int period,
        @JsonProperty("opening_balance") String openingBalance,
        @JsonProperty("closing_balance") String closingBalance,
        String timezone,
        List<Point> points) {

    /** Saldo al cierre de un día de Lima. */
    public record Point(String date, String balance, int movements) {
    }
}
