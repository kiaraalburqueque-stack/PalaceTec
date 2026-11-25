package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.entity.Game;
import java.math.BigDecimal;

public record GameRulesResponse(
        Long id,
        String slug,
        String title,
        String category,
        String rtp,
        @JsonProperty("house_edge") String houseEdge,
        @JsonProperty("min_bet") String minBet,
        @JsonProperty("max_bet") String maxBet,
        String rules,
        @JsonProperty("payout_table") JsonNode payoutTable,
        @JsonProperty("play_room_id") Long playRoomId,
        @JsonProperty("play_path") String playPath) {

    private static final BigDecimal WHOLE = BigDecimal.valueOf(100);

    public static GameRulesResponse from(Game game, Long playRoomId) {
        return new GameRulesResponse(game.getId(), game.getSlug(), game.getTitle(), game.getCategory(),
                Formats.money(game.getRtp()), Formats.money(WHOLE.subtract(game.getRtp())),
                Formats.money(game.getMinBet()), Formats.money(game.getMaxBet()), game.getRules(),
                game.getPayoutTable(), playRoomId, game.getPlayPath().isEmpty() ? null : game.getPlayPath());
    }
}
