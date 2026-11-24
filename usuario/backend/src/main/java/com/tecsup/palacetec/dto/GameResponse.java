package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameResponse(
        Long id,
        String slug,
        String title,
        String category,
        @JsonProperty("category_slug") String categorySlug,
        String rtp,
        @JsonProperty("cover_url") String coverUrl,
        @JsonProperty("min_bet") String minBet,
        @JsonProperty("max_bet") String maxBet,
        @JsonProperty("rooms_url") String roomsUrl,
        @JsonProperty("play_path") String playPath,
        @JsonProperty("is_favorite") boolean favorite) {
}
