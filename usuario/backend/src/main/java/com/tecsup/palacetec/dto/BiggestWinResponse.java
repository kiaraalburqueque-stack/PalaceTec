package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.Play;

/** record es null si el jugador todavía no ganó. */
public record BiggestWinResponse(@JsonProperty("record") Win win) {

    public record Win(
            String net,
            String payout,
            String amount,
            String source,
            String game,
            @JsonProperty("round_id") Long roundId,
            @JsonProperty("bet_id") Long betId,
            @JsonProperty("created_at") String createdAt) {

        public static Win from(Bet bet) {
            return new Win(Formats.money(bet.getPayout().subtract(bet.getAmount())), Formats.money(bet.getPayout()),
                    Formats.money(bet.getAmount()), "roulette", bet.getRound().getRoom().getGameTitle(),
                    bet.getRound().getId(), bet.getId(), Formats.utc(bet.getCreatedAt()));
        }

        public static Win from(Play play) {
            return new Win(Formats.money(play.getPayout().subtract(play.getAmount())), Formats.money(play.getPayout()),
                    Formats.money(play.getAmount()), "casino", play.getKindLabel(), null, play.getId(),
                    Formats.utc(play.getCreatedAt()));
        }
    }
}
