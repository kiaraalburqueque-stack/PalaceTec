package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** hand es null si el jugador todavía no jugó. */
public record HandResponse(Hand hand) {

    /** Mientras la mano está activa, dealer solo tiene la carta descubierta. */
    public record Hand(
            Long id,
            String amount,
            List<String> player,
            List<String> dealer,
            @JsonProperty("dealer_hidden") boolean dealerHidden,
            @JsonProperty("player_score") int playerScore,
            @JsonProperty("dealer_score") int dealerScore,
            boolean active,
            int revision,
            String outcome,
            String payout) {
    }
}
