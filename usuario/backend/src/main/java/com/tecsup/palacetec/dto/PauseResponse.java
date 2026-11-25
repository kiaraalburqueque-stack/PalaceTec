package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PauseResponse(@JsonProperty("pause_until") String pauseUntil) {
}
