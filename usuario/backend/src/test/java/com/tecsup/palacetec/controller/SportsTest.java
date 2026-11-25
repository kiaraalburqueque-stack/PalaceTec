package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.dto.Formats;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** casino/tests.py de Django: deportes simulados. */
class SportsTest extends ApiTest {

    private static final String EVENTS = "/api/v1/casino/sports/events";
    private static final String TICKETS = "/api/v1/casino/sports/tickets";
    private static final String CONFLICT =
            "{\"code\": \"reference_conflict\", \"detail\": \"La referencia ya corresponde a otro boleto.\"}";

    private Player player;
    private long event;

    @BeforeEach
    void market() {
        player = newPlayer();
        dailyLimit(player, 1000);
        event = newEvent("2 minutes");
    }

    private long newEvent(String startsIn) {
        return jdbc.queryForObject("insert into casino_sportevent (home, away, starts_at, ends_at, home_odds, "
                + "draw_odds, away_odds) values ('Cóndores', 'Halcones', now() + cast(? as interval), "
                + "now() + cast(? as interval) + interval '1 minute', 1.90, 3.20, 2.40) returning id", Long.class,
                startsIn, startsIn);
    }

    private Map<String, Object> bet() {
        return new HashMap<>(Map.of("event_id", event, "selection", "HOME", "amount", "10.00", "quoted_odds", "1.90",
                "reference", UUID.randomUUID().toString()));
    }

    private ResultActions place(Map<String, Object> body) throws Exception {
        return send(mapper.writeValueAsString(body));
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post(TICKETS).with(player.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions place(Map<String, Object> body, String field, Object value) throws Exception {
        Map<String, Object> changed = new HashMap<>(body);
        changed.put(field, value);
        return place(changed);
    }

    private void expectDetail(ResultActions result, String detail) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().json("{\"detail\": \"" + detail + "\"}", true));
    }

    private String balance() {
        return jdbc.queryForObject("select balance::text from wallets where user_id = ?", String.class, player.id());
    }

    private void assertNothingHappened() {
        assertThat(count("casino_play")).isZero();
        assertThat(count("casino_sportticket")).isZero();
        assertThat(count("wallet_transactions")).isZero();
        assertThat(count("responsible_dailyspending")).isZero();
    }

    @Test
    void aTicketKeepsItsQuoteAndIsChargedOnce() throws Exception {
        Map<String, Object> body = bet();
        JsonNode ticket = json(place(body).andExpect(status().isCreated()).andReturn());
        Map<String, Object> play = jdbc.queryForMap("select * from casino_play");
        assertThat(ticket).isEqualTo(mapper.readTree("""
                {"id": %d, "event_id": %d, "description": "Cóndores vs Halcones · HOME", "selection": "HOME",
                 "odds": "1.90", "amount": "10.00", "payout": null, "created_at": "%s", "settled_at": null}"""
                .formatted(ticket.get("id").asLong(), event,
                        Formats.utc(((Timestamp) play.get("created_at")).toInstant()))));
        assertThat(play).containsEntry("kind", "SPORTS").containsEntry("payout", null);
        assertThat(play.get("session_id")).isNotNull();
        assertThat(jdbc.queryForMap("select type, reference, balance_after::text as after from wallet_transactions"))
                .containsEntry("type", "BET").containsEntry("reference", "casino:bet:" + play.get("id"))
                .containsEntry("after", "990.00");

        jdbc.update("update casino_sportevent set home_odds = 5");
        assertThat(json(place(body).andExpect(status().isOk()).andReturn())).isEqualTo(ticket);
        assertThat(json(place(body, "quoted_odds", 1.9).andExpect(status().isOk()).andReturn())).isEqualTo(ticket);
        // El reintento responde aunque el encuentro ya haya empezado.
        jdbc.update("update casino_sportevent set starts_at = now() - interval '1 second'");
        place(body).andExpect(status().isOk());
        assertThat(balance()).isEqualTo("990.00");
        assertThat(count("casino_play")).isEqualTo(1);
    }

    @Test
    void aReferenceBelongsToOneTicket() throws Exception {
        Map<String, Object> body = bet();
        place(body).andExpect(status().isCreated());
        long other = newEvent("5 minutes");
        for (Map.Entry<String, Object> change : Map.<String, Object>of("selection", "AWAY", "amount", "11",
                "quoted_odds", "2.40", "event_id", other).entrySet()) {
            place(body, change.getKey(), change.getValue()).andExpect(status().isConflict())
                    .andExpect(content().json(CONFLICT, true));
        }
        mvc.perform(post("/api/v1/casino/blackjack").with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("amount", 10, "reference", body.get("reference")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La referencia ya corresponde a otra jugada."));
        assertThat(count("casino_play")).isEqualTo(1);
    }

    @Test
    void theReferenceOfAHandIsNotATicket() throws Exception {
        deck.deal("A_S K_S", "9_H 8_H");
        Map<String, Object> body = bet();
        mvc.perform(post("/api/v1/casino/blackjack").with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("amount", 10, "reference", body.get("reference")))))
                .andExpect(status().isCreated());
        place(body).andExpect(status().isConflict()).andExpect(content().json(CONFLICT, true));
    }

    @Test
    void theQuoteMustBeTheCurrentOneAndTheMarketOpen() throws Exception {
        expectDetail(place(bet(), "quoted_odds", "9.99"), "La cuota cambio. Consulta y confirma nuevamente.");
        expectDetail(place(bet(), "selection", "DRAW"), "La cuota cambio. Consulta y confirma nuevamente.");
        jdbc.update("update casino_sportevent set starts_at = now() - interval '1 second'");
        expectDetail(place(bet()), "El mercado esta cerrado. No se aceptan apuestas en vivo.");
        jdbc.update("update casino_sportevent set starts_at = now() + interval '1 minute', settled_at = now(), "
                + "home_score = 0, away_score = 0");
        expectDetail(place(bet()), "El mercado esta cerrado. No se aceptan apuestas en vivo.");
        assertNothingHappened();
    }

    @Test
    void limitsBalanceAndExclusionApplyAndNothingIsCharged() throws Exception {
        dailyLimit(player, 5);
        place(bet()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("daily_limit_exceeded"));
        dailyLimit(player, 1000);
        jdbc.update("update wallets set balance = 0, bonus_balance = 0 where user_id = ?", player.id());
        place(bet()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("insufficient_balance"));
        jdbc.update("update users set self_excluded_until = now() + interval '1 day' where id = ?", player.id());
        place(bet()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("access_not_allowed"));
        assertNothingHappened();
    }

    /** Django rechaza también el reintento; aquí, como en la ruleta, devuelve el boleto ya pagado. */
    @Test
    void aPauseBlocksNewTicketsButNotRetries() throws Exception {
        Map<String, Object> body = bet();
        place(body).andExpect(status().isCreated());
        jdbc.update("update users set pause_until = now() + interval '5 minutes' where id = ?", player.id());
        place(body).andExpect(status().isOk());
        place(bet()).andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"code\": \"pause_active\", \"detail\": \"Tu pausa de juego sigue activa.\"}", true));
        assertThat(count("casino_play")).isEqualTo(1);
    }

    @Test
    void ticketsAreTheLastFiftyOfThePlayer() throws Exception {
        mvc.perform(get(TICKETS).with(player.bearer())).andExpect(status().isOk())
                .andExpect(content().json("[]", true));
        long first = json(place(bet()).andReturn()).get("id").asLong();
        long second = json(place(bet()).andReturn()).get("id").asLong();
        jdbc.update("update casino_play set payout = 19, settled_at = '2026-11-20T15:00:00.5Z' where id = "
                + "(select play_id from casino_sportticket where id = ?)", first);
        JsonNode list = json(mvc.perform(get(TICKETS).with(player.bearer())).andReturn());
        assertThat(list.findValuesAsText("id")).containsExactly(String.valueOf(second), String.valueOf(first));
        assertThat(list.get(1).get("payout").asText()).isEqualTo("19.00");
        assertThat(list.get(1).get("settled_at").asText()).isEqualTo("2026-11-20T15:00:00.500000+00:00");
        mvc.perform(get(TICKETS).with(newPlayer().bearer())).andExpect(content().json("[]", true));
        for (int index = 0; index < 50; index++) {
            place(bet()).andExpect(status().isCreated());
        }
        mvc.perform(get(TICKETS).with(player.bearer())).andExpect(jsonPath("$.length()").value(50));
    }

    @Test
    void eventsShowTheirPhaseAndTheScoreOnlyWhenSettled() throws Exception {
        jdbc.update("update casino_sportevent set starts_at = '2099-01-01T00:00:00Z', ends_at = '2099-01-01T00:01:00Z'");
        long live = newEvent("-10 seconds");
        long settled = newEvent("-10 minutes");
        jdbc.update("update casino_sportevent set settled_at = now(), home_score = 3, away_score = 0 where id = ?", settled);
        JsonNode body = json(mvc.perform(get(EVENTS).with(player.bearer())).andExpect(status().isOk()).andReturn());
        assertThat(body).hasSize(3);
        assertThat(body.get("simulation").asBoolean()).isTrue();
        assertThat(body.get("server_time").asText()).endsWith("+00:00");
        JsonNode events = body.get("events");
        assertThat(events.get(0)).isEqualTo(mapper.readTree("""
                {"id": %d, "home": "Cóndores", "away": "Halcones", "starts_at": "2099-01-01T00:00:00+00:00",
                 "ends_at": "2099-01-01T00:01:00+00:00", "phase": "OPEN",
                 "odds": {"HOME": "1.90", "DRAW": "3.20", "AWAY": "2.40"}, "score": null}""".formatted(event)));
        assertThat(events.get(1).get("id").asLong()).isEqualTo(live);
        assertThat(events.get(1).get("phase").asText()).isEqualTo("LIVE");
        assertThat(events.get(1).get("score").isNull()).isTrue();
        assertThat(events.get(2).get("phase").asText()).isEqualTo("SETTLED");
        assertThat(events.get(2).get("score").toString()).isEqualTo("[3,0]");
        for (int index = 0; index < 12; index++) {
            newEvent("1 minute");
        }
        mvc.perform(get(EVENTS).with(player.bearer())).andExpect(jsonPath("$.events.length()").value(12));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {} | {"amount": ["@"], "reference": ["@"], "event_id": ["@"], "selection": ["@"], "quoted_odds": ["@"]}
            {"amount": 10, "reference": 1, "event_id": 0, "selection": "X", "quoted_odds": 1} | {"event_id": ["Ensure this value is greater than or equal to 1."], "selection": ["\\"X\\" is not a valid choice."], "quoted_odds": ["Ensure this value is greater than or equal to 1.01."]}
            {"amount": 10, "reference": 1, "event_id": "x", "selection": null, "quoted_odds": "1.905"} | {"event_id": ["A valid integer is required."], "selection": ["This field may not be null."], "quoted_odds": ["Ensure that there are no more than 2 decimal places."]}
            {"amount": 10, "reference": 1, "event_id": 1, "selection": "HOME", "quoted_odds": "1000.00"} | {"quoted_odds": ["Ensure that there are no more than 5 digits in total."]}
            {"amount": 10, "reference": 1, "event_id": 1, "selection": "HOME", "quoted_odds": "1000"} | {"quoted_odds": ["Ensure that there are no more than 3 digits before the decimal point."]}
            """)
    void theBetIsValidated(String body, String expected) throws Exception {
        send(body).andExpect(status().isBadRequest())
                .andExpect(content().json(expected.replace("@", "This field is required."), true));
        assertNothingHappened();
    }

    @Test
    void unknownEventsAndOtherRequestsAreRejected() throws Exception {
        for (Object id : List.of(999999999, "1000000000000000000000000000000")) {
            place(bet(), "event_id", id).andExpect(status().isNotFound())
                    .andExpect(content().json("{\"detail\": \"No SportEvent matches the given query.\"}", true));
        }
        mvc.perform(post(EVENTS).with(player.bearer())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(EVENTS)).andExpect(status().isUnauthorized());
        mvc.perform(get(TICKETS)).andExpect(status().isUnauthorized());
        mvc.perform(post(TICKETS)).andExpect(status().isUnauthorized());
    }

    @Test
    void simultaneousRetriesChargeOnce() throws Exception {
        Map<String, Object> body = bet();
        Callable<Integer> retry = () -> place(body).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, retry)).stream().map(future -> future.resultNow()))
                    .containsExactlyInAnyOrder(201, 200, 200, 200);
        }
        assertThat(balance()).isEqualTo("990.00");
    }

    @Test
    void simultaneousTicketsShareTheDailyLimit() throws Exception {
        dailyLimit(player, 20);
        List<Callable<Integer>> tickets = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Map<String, Object> body = bet();
            body.put("event_id", newEvent("5 minutes"));
            tickets.add(() -> place(body).andReturn().getResponse().getStatus());
        }
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(tickets).stream().map(future -> future.resultNow()))
                    .containsExactlyInAnyOrder(201, 201, 400, 400);
        }
        assertThat(balance()).isEqualTo("980.00");
        assertThat(jdbc.queryForObject("select sum(amount)::text from responsible_dailyspending", String.class))
                .isEqualTo("20.00");
        assertThat(Collections.max(List.of(count("casino_play"), count("casino_sportticket")))).isEqualTo(2);
    }
}
