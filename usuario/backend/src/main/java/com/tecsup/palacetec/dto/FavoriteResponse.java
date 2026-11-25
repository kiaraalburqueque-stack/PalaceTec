package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FavoriteResponse(@JsonProperty("game_id") long gameId, @JsonProperty("is_favorite") boolean favorite) {
}
