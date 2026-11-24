package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.Room;

public record RoomResponse(
        Long id,
        String name,
        @JsonProperty("min_bet") String minBet,
        @JsonProperty("max_bet") String maxBet,
        long players) {

    public static RoomResponse from(Room room, long players) {
        return new RoomResponse(room.getId(), room.getName(), Formats.money(room.getMinBet()),
                Formats.money(room.getMaxBet()), players);
    }
}
