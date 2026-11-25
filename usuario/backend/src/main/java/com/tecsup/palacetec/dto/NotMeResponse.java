package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotMeResponse(String detail, @JsonProperty("locked_until") String lockedUntil) {
}
