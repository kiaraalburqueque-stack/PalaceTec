package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** GET de BetView de Django: las últimas apuestas propias en una sala. */
class BetListTest extends ApiTest {

    private static final String NO_ROOM = "{\"detail\": \"No Room matches the given query.\"}";

    private Player player;
    private long room;

    @BeforeEach
    void table() {
        player = newPlayer();
        room = newRoom(newGame("lista"), 10, 500);
    }

    private ResultActions read(Object roomId) throws Exception {
        return mvc.perform(get("/api/v1/rooms/" + roomId + "/bets").with(player.bearer()));
    }

    @Test
    void showsOnlyOwnBetsOfTheRoomNewestFirst() throws Exception {
        read(room).andExpect(status().isOk()).andExpect(content().json("[]", true));
        long settled = newSettledRound(room, 7);
        long first = newBet(player, settled, "10", "20", "2026-11-20T15:00:00.5Z");
        jdbc.update("update user_bets set reference = 'b0a90241-aaf8-4521-89f4-d566b84d48fa'");
        newBet(newPlayer(), settled, "10", "20", "now");
        newBet(player, newSettledRound(newRoom(newGame("otra"), 10, 500), 7), "10", "0", "now");
        long open = newOpenRound(room, 60);
        // El orden es por id, aunque la fecha diga otra cosa.
        long second = newBet(player, open, "50", null, "2020-01-01T00:00:00Z");
        JsonNode list = json(read(room).andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(second);
        assertThat(list.get(0).get("payout").isNull()).isTrue();
        assertThat(list.get(1)).isEqualTo(mapper.readTree("""
                {"id": %d, "round_id": %d, "kind": "COLOR", "selection": "rojo", "chips": [10], "amount": "10.00",
                 "payout": "20.00", "reference": "b0a90241-aaf8-4521-89f4-d566b84d48fa",
                 "created_at": "2026-11-20T10:00:00.500000-05:00"}""".formatted(first, settled)));
    }

    @Test
    void showsTheLastHundred() throws Exception {
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                + "created_at) select ?, ?, 'COLOR', 'rojo', '[10]', n, 0, gen_random_uuid(), now() "
                + "from generate_series(1, 105) n", player.id(), newSettledRound(room, 7));
        read(room).andExpect(jsonPath("$.length()").value(100)).andExpect(jsonPath("$[0].amount").value("105.00"))
                .andExpect(jsonPath("$[99].amount").value("6.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "999999999", "99999999999999999999999"})
    void unknownRoomsAreNotFound(String id) throws Exception {
        read(id).andExpect(status().isNotFound()).andExpect(content().json(NO_ROOM, true));
    }

    @Test
    void hiddenRoomsAndOtherPathsAreRejected() throws Exception {
        jdbc.update("update rooms set is_active = false where id = ?", room);
        read(room).andExpect(status().isNotFound()).andExpect(content().json(NO_ROOM, true));
        read("abc").andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/rooms/" + room + "/bets").with(player.bearer()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/rooms/" + room + "/bets")).andExpect(status().isUnauthorized());
    }
}
