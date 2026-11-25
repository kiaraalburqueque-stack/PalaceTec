package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.GameRound;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record RoomStateResponse(
        @JsonProperty("room_id") Long roomId,
        @JsonProperty("server_time") String serverTime,
        @JsonProperty("min_bet") String minBet,
        @JsonProperty("max_bet") String maxBet,
        Round round,
        @JsonProperty("chip_values") List<Integer> chipValues,
        @JsonProperty("recent_results") List<Result> recentResults,
        List<NumberColor> numbers) {

    public record Round(
            Long id,
            String status,
            @JsonProperty("opened_at") String openedAt,
            @JsonProperty("closes_at") String closesAt,
            @JsonProperty("remaining_seconds") long remainingSeconds,
            @JsonProperty("winning_number") Short winningNumber) {

        public static Round from(GameRound round, Instant now) {
            long remaining = 0;
            if (round.getStatus() == GameRound.Status.OPEN) {
                Duration left = Duration.between(now, round.getClosesAt());
                // Redondeo hacia arriba, sin bajar de cero.
                remaining = Math.max(0, left.plusSeconds(1).minusNanos(1).toSeconds());
            }
            return new Round(round.getId(), round.getStatus().name(), Formats.utc(round.getOpenedAt()),
                    Formats.utc(round.getClosesAt()), remaining, round.getWinningNumber());
        }
    }

    public record Result(@JsonProperty("round_id") Long roundId, int number, String color) {
    }

    public record NumberColor(int number, String color) {
    }
}
