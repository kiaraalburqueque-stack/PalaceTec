package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.entity.Play;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Apuesta de ruleta (source=roulette) o jugada de blackjack o deportes (source=casino). */
public record HistoryItemResponse(
        Long id,
        @JsonProperty("round_id") Long roundId,
        String kind,
        String selection,
        List<Integer> chips,
        String amount,
        String payout,
        UUID reference,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("room_id") Long roomId,
        String game,
        String status,
        String net,
        String source) {

    public static HistoryItemResponse from(Bet bet) {
        Game game = bet.getRound().getRoom().getGame();
        return new HistoryItemResponse(bet.getId(), bet.getRound().getId(), bet.getKind().name(), bet.getSelection(),
                bet.getChips(), Formats.money(bet.getAmount()), Formats.money(bet.getPayout()), bet.getReference(),
                Formats.lima(bet.getCreatedAt()), bet.getRound().getRoom().getId(),
                game == null ? "Ruleta Europea" : game.getTitle(), rouletteStatus(bet.getPayout()),
                net(bet.getAmount(), bet.getPayout()), "roulette");
    }

    public static HistoryItemResponse from(Play play) {
        return new HistoryItemResponse(play.getId(), null, play.getKind(), play.getDescription(), List.of(),
                Formats.money(play.getAmount()), Formats.money(play.getPayout()), play.getReference(),
                Formats.zulu(play.getCreatedAt()), null,
                play.getKind().equals("BLACKJACK") ? "Blackjack" : "Deportes simulados",
                casinoStatus(play.getAmount(), play.getPayout()), net(play.getAmount(), play.getPayout()), "casino");
    }

    private static String rouletteStatus(BigDecimal payout) {
        if (payout == null) {
            return "PENDING";
        }
        return payout.signum() > 0 ? "WON" : "LOST";
    }

    private static String casinoStatus(BigDecimal amount, BigDecimal payout) {
        if (payout == null) {
            return "PENDING";
        }
        int result = payout.compareTo(amount);
        if (result == 0) {
            return "PUSH";
        }
        return result > 0 ? "WON" : "LOST";
    }

    private static String net(BigDecimal amount, BigDecimal payout) {
        return payout == null ? null : Formats.money(payout.subtract(amount));
    }
}
