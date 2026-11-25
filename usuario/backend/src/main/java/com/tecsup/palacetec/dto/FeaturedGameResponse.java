package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Juego destacado, con la sala a la que entra el jugador (null si tiene su propia pantalla). */
public record FeaturedGameResponse(@JsonUnwrapped GameResponse game, @JsonProperty("play_room_id") Long playRoomId) {
}
