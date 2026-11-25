package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.GameRound;
import com.tecsup.palacetec.entity.Room;

/** Una apuesta de ruleta con su ronda (HU54). */
public record BetDetailResponse(
        @JsonUnwrapped BetResponse bet,
        @JsonProperty("room_id") Long roomId,
        String game,
        String status,
        String net,
        @JsonProperty("room_name") String roomName,
        @JsonProperty("chip_count") int chipCount,
        Round round) {

    public record Round(
            Long id,
            String status,
            @JsonProperty("opened_at") String openedAt,
            @JsonProperty("settled_at") String settledAt,
            @JsonProperty("winning_number") Short winningNumber,
            @JsonProperty("winning_color") String winningColor) {
    }

    /** winningColor es null mientras la ronda no tenga número. */
    public static BetDetailResponse from(Bet bet, String winningColor) {
        GameRound round = bet.getRound();
        Room room = round.getRoom();
        return new BetDetailResponse(BetResponse.from(bet), room.getId(), room.getGameTitle(),
                BetStatus.ofRoulette(bet.getPayout()).name(),
                HistoryItemResponse.net(bet.getAmount(), bet.getPayout()), room.getName(), bet.getChips().size(),
                new Round(round.getId(), round.getStatus().name(), Formats.utc(round.getOpenedAt()),
                        Formats.utc(round.getSettledAt()), round.getWinningNumber(), winningColor));
    }
}
