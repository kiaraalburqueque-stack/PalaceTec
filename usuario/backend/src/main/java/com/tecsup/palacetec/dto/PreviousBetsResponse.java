package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PreviousBetsResponse(@JsonProperty("source_round_id") Long sourceRoundId, List<BetResponse> bets) {
}
