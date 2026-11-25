package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record StatsSummaryResponse(@JsonUnwrapped PlayTotals totals, String scope, String timezone) {

    public StatsSummaryResponse(PlayTotals totals) {
        this(totals, "settled_bets", "America/Lima");
    }
}
