package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SelfExclusionResponse(@JsonProperty("self_excluded_until") String selfExcludedUntil, String detail) {
}
