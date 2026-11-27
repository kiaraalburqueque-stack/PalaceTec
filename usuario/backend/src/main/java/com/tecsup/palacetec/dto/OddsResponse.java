package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OddsResponse(
        String bet,
        String selection,
        String label,
        @JsonProperty("winning_numbers") int winningNumbers,
        @JsonProperty("total_numbers") int totalNumbers,
        String probability,
        @JsonProperty("probability_pct") String probabilityPct,
        String payout,
        @JsonProperty("total_return") int totalReturn,
        @JsonProperty("house_edge") String houseEdge,
        @JsonProperty("house_edge_pct") String houseEdgePct) {
}
