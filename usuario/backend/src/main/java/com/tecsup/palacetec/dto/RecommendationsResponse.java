package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/** Sección "Para ti". blocked dice por qué no se recomienda jugar, o es null. */
public record RecommendationsResponse(List<Item> results, String blocked, boolean personalized) {

    public static RecommendationsResponse empty(String blocked) {
        return new RecommendationsResponse(List.of(), blocked, false);
    }

    public record Item(
            @JsonUnwrapped GameResponse game,
            @JsonProperty("play_room_id") Long playRoomId,
            String reason,
            @JsonProperty("reason_code") String reasonCode,
            Number score) {
    }
}
