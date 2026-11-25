package com.tecsup.palacetec.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** HU54 de Django: detalle de una apuesta propia. */
class BetDetailTest extends ApiTest {

    private static final String NO_BET = "{\"detail\": \"No Bet matches the given query.\"}";

    private Player player;
    private long room;

    @BeforeEach
    void table() {
        player = newPlayer();
        room = newRoom(newGame("detalle"), 10, 500);
    }

    private ResultActions read(Player who, Object bet) throws Exception {
        return mvc.perform(get("/api/v1/users/bets/" + bet).with(who.bearer()));
    }

    @Test
    void theOwnerSeesTheSettledBetWithItsRound() throws Exception {
        long round = newSettledRound(room, 7);
        jdbc.update("update game_rounds set opened_at = '2026-11-20T15:00:00Z', closes_at = '2026-11-20T15:00:15Z', "
                + "settled_at = '2026-11-20T15:00:16.25Z' where id = ?", round);
        long bet = newBet(player, round, "110", "220", "2026-11-20T15:00:05.5Z");
        jdbc.update("update user_bets set chips = '[10, 50, 50]', reference = 'b0a90241-aaf8-4521-89f4-d566b84d48fa'");
        read(player, bet).andExpect(status().isOk()).andExpect(content().json("""
                {"id": %1$d, "round_id": %2$d, "kind": "COLOR", "selection": "rojo", "chips": [10, 50, 50],
                 "amount": "110.00", "payout": "220.00", "reference": "b0a90241-aaf8-4521-89f4-d566b84d48fa",
                 "created_at": "2026-11-20T10:00:05.500000-05:00", "room_id": %3$d, "game": "Juego detalle",
                 "status": "WON", "net": "110.00", "room_name": "spring-test-sala", "chip_count": 3,
                 "round": {"id": %2$d, "status": "SETTLED", "opened_at": "2026-11-20T15:00:00+00:00",
                           "settled_at": "2026-11-20T15:00:16.250000+00:00", "winning_number": 7,
                           "winning_color": "rojo"}}""".formatted(bet, round, room), true));
    }

    @Test
    void aPendingBetHasNoResultYet() throws Exception {
        jdbc.update("update rooms set game_id = null where id = ?", room);
        long bet = newBet(player, newOpenRound(room, 60), "10", null, "now");
        read(player, bet).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16)).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.game").value("Ruleta Europea"))
                .andExpect(jsonPath("$.payout").value((Object) null)).andExpect(jsonPath("$.net").value((Object) null))
                .andExpect(jsonPath("$.round.status").value("OPEN"))
                .andExpect(jsonPath("$.round.settled_at").value((Object) null))
                .andExpect(jsonPath("$.round.winning_number").value((Object) null))
                .andExpect(jsonPath("$.round.winning_color").value((Object) null));
    }

    @ParameterizedTest
    @CsvSource({"0, verde", "1, rojo", "2, negro"})
    void showsTheWinningColor(int number, String color) throws Exception {
        long bet = newBet(player, newSettledRound(room, number), "10", "0", "now");
        read(player, bet).andExpect(jsonPath("$.round.winning_number").value(number))
                .andExpect(jsonPath("$.round.winning_color").value(color))
                .andExpect(jsonPath("$.status").value("LOST")).andExpect(jsonPath("$.net").value("-10.00"));
    }

    @Test
    void otherPlayersCannotSeeIt() throws Exception {
        long bet = newBet(player, newSettledRound(room, 7), "10", "0", "now");
        read(newPlayer(), bet).andExpect(status().isNotFound()).andExpect(content().json(NO_BET, true));
        mvc.perform(get("/api/v1/users/bets/" + bet)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "999999999", "99999999999999999999999"})
    void unknownBetsAreNotFound(String id) throws Exception {
        read(player, id).andExpect(status().isNotFound()).andExpect(content().json(NO_BET, true));
        read(player, "abc").andExpect(status().isNotFound());
    }
}
