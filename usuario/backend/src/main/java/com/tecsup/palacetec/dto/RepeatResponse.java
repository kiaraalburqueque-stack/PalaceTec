package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record RepeatResponse(
        @JsonProperty("source_round_id") Long sourceRoundId,
        @JsonProperty("round_id") Long roundId,
        UUID reference,
        List<BetResponse> bets) {
}
