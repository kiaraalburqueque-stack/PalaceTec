package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.Bet;
import java.util.List;
import java.util.UUID;

public record BetResponse(
        Long id,
        @JsonProperty("round_id") Long roundId,
        String kind,
        String selection,
        List<Integer> chips,
        String amount,
        String payout,
        UUID reference,
        @JsonProperty("created_at") String createdAt) {

    public static BetResponse from(Bet bet) {
        return new BetResponse(bet.getId(), bet.getRound().getId(), bet.getKind().name(), bet.getSelection(),
                bet.getChips(), Formats.money(bet.getAmount()), Formats.money(bet.getPayout()), bet.getReference(),
                Formats.lima(bet.getCreatedAt()));
    }
}
