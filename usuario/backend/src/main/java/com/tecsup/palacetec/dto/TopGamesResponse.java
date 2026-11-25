package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TopGamesResponse(
        List<Item> results,
        @JsonProperty("total_rounds") long totalRounds,
        String timezone) {

    public record Item(@JsonProperty("game_id") long gameId, String title, String category, long rounds,
                       String wagered) {
    }
}
