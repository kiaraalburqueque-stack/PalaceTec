package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.ResultActions;

class HistoryTest extends ApiTest {

    private static final String PATH = "/api/v1/users/bets-history";

    private Player player;
    private long room;
    private long round;

    @BeforeEach
    void table() {
        player = newPlayer();
        room = newRoom(newGame("historial"), 10, 500);
        round = newSettledRound(room, 7);
    }

    private void bet(Player owner, String createdAt, String payout) {
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                        + "created_at) values (?, ?, 'COLOR', 'rojo', '[100]', 100, cast(? as numeric), "
                        + "gen_random_uuid(), cast(? as timestamptz))", owner.id(), round, payout, createdAt);
    }

    private void play(Player owner, String kind, String createdAt, String payout) {
        jdbc.update("insert into casino_play (user_id, kind, amount, payout, reference, description, created_at, "
                        + "settled_at) values (?, ?, 50, cast(? as numeric), gen_random_uuid(), 'Jugada', "
                        + "cast(? as timestamptz), case when cast(? as numeric) is null then null else now() end)",
                owner.id(), kind, payout, createdAt, payout);
    }

    private ResultActions history(String query) throws Exception {
        return mvc.perform(get(PATH + query).with(player.bearer()));
    }

    @Test
    void mergesRouletteAndCasinoNewestFirst() throws Exception {
        bet(player, "2026-11-20T15:00:00Z", "200");
        bet(player, "2026-11-20T15:01:00.5Z", "0");
        bet(player, "2026-11-20T15:02:00Z", null);
        play(player, "BLACKJACK", "2026-11-20T15:00:30.25Z", "50");
        play(player, "SPORTS", "2026-11-20T14:00:00Z", "120");
        bet(newPlayer(), "2026-11-20T16:00:00Z", "0");

        JsonNode page = json(history("").andExpect(status().isOk()).andReturn());
        assertThat(page).hasSize(4);
        assertThat(page.get("count").asInt()).isEqualTo(5);
        assertThat(page.get("next").isNull()).isTrue();
        assertThat(page.get("previous").isNull()).isTrue();
        JsonNode results = page.get("results");
        assertThat(results.findValuesAsText("status")).containsExactly("PENDING", "LOST", "PUSH", "WON", "WON");
        assertThat(results.findValuesAsText("source"))
                .containsExactly("roulette", "roulette", "casino", "roulette", "casino");

        JsonNode pending = results.get(0);
        assertThat(pending).hasSize(14);
        assertThat(pending.get("payout").isNull()).isTrue();
        assertThat(pending.get("net").isNull()).isTrue();
        assertThat(pending.get("round_id").asLong()).isEqualTo(round);
        assertThat(pending.get("room_id").asLong()).isEqualTo(room);
        assertThat(pending.get("game").asText()).isEqualTo("Juego historial");
        assertThat(pending.get("chips").toString()).isEqualTo("[100]");
        assertThat(pending.get("created_at").asText()).isEqualTo("2026-11-20T10:02:00-05:00");
        assertThat(results.get(1).get("net").asText()).isEqualTo("-100.00");
        assertThat(results.get(1).get("created_at").asText()).isEqualTo("2026-11-20T10:01:00.500000-05:00");
        JsonNode blackjack = results.get(2);
        assertThat(blackjack).hasSize(14);
        assertThat(blackjack.get("game").asText()).isEqualTo("Blackjack");
        assertThat(blackjack.get("selection").asText()).isEqualTo("Jugada");
        assertThat(blackjack.get("round_id").isNull()).isTrue();
        assertThat(blackjack.get("chips").toString()).isEqualTo("[]");
        assertThat(blackjack.get("net").asText()).isEqualTo("0.00");
        assertThat(blackjack.get("created_at").asText()).isEqualTo("2026-11-20T15:00:30.250000Z");
        assertThat(results.get(3).get("net").asText()).isEqualTo("100.00");
        assertThat(results.get(4).get("game").asText()).isEqualTo("Deportes simulados");
        assertThat(results.get(4).get("net").asText()).isEqualTo("70.00");
    }

    @Test
    void roomsWithoutGameAreEuropeanRoulette() throws Exception {
        jdbc.update("update rooms set game_id = null where id = ?", room);
        bet(player, "2026-11-20T15:00:00Z", "0");
        history("").andExpect(jsonPath("$.results[0].game").value("Ruleta Europea"));
    }

    @Test
    void paginatesWithLinksThatKeepTheFilters() throws Exception {
        for (int minute = 10; minute < 15; minute++) {
            bet(player, "2026-11-20T15:" + minute + ":00Z", "0");
        }
        String filters = "&startDate=2026-11-20";
        history("?page_size=2&page=2" + filters).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5)).andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].created_at").value("2026-11-20T10:12:00-05:00"))
                .andExpect(jsonPath("$.next").value("http://localhost" + PATH + "?page=3&page_size=2&startDate=2026-11-20"))
                .andExpect(jsonPath("$.previous").value("http://localhost" + PATH + "?page_size=2&startDate=2026-11-20"));
        history("?page_size=2&page=last").andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.next").value(nullValue()));
        history("?page_size=abc").andExpect(jsonPath("$.results.length()").value(5));
        history("?page_size=1000").andExpect(jsonPath("$.results.length()").value(5));
        for (String invalid : new String[]{"0", "-1", "99", "abc"}) {
            history("?page=" + invalid).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").isNotEmpty());
        }
    }

    @Test
    void pageSizeIsCappedAtOneHundred() throws Exception {
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                + "created_at) select ?, ?, 'COLOR', 'rojo', '[10]', 10, 0, gen_random_uuid(), now() "
                + "from generate_series(1, 105)", player.id(), round);
        history("").andExpect(jsonPath("$.results.length()").value(20));
        history("?page_size=1000").andExpect(jsonPath("$.results.length()").value(100));
    }

    @Test
    void datesAreWholeDaysInLima() throws Exception {
        bet(player, "2026-11-20T04:59:59Z", "0"); // 19 de noviembre en Lima
        bet(player, "2026-11-20T05:00:00Z", "0"); // 20, primer segundo
        play(player, "BLACKJACK", "2026-11-21T04:59:59Z", "0"); // 20, último segundo
        bet(player, "2026-11-21T05:00:00Z", "0"); // 21
        history("?startDate=2026-11-20&endDate=2026-11-20").andExpect(jsonPath("$.count").value(2));
        history("?startDate=2026-11-20").andExpect(jsonPath("$.count").value(3));
        history("?endDate=2026-11-19").andExpect(jsonPath("$.count").value(1));
        history("?startDate=2026-12-01").andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"startDate=20-11-2026,startDate", "startDate=2026-02-30,startDate", "endDate=,endDate",
            "endDate=9999-12-31,endDate", "startDate=2026-11-21&endDate=2026-11-20,endDate"})
    void rejectsInvalidDates(String query, String field) throws Exception {
        history("?" + query).andExpect(status().isBadRequest()).andExpect(jsonPath("$." + field + "[0]").isNotEmpty());
    }

    @Test
    void ignoresAttemptsToReadAnotherPlayer() throws Exception {
        Player other = newPlayer();
        bet(other, "2026-11-20T15:00:00Z", "0");
        history("?user_id=" + other.id() + "&user=" + other.id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
