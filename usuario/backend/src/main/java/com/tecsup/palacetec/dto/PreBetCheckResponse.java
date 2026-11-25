package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreBetCheckResponse(String balance, @JsonProperty("can_bet") boolean canBet) {
}
