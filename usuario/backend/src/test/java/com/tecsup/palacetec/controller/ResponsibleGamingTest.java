package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import com.tecsup.palacetec.dto.Formats;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU27, HU28 y HU29 de Django: autoexclusión, resumen de la sesión y alertas de riesgo. */
class ResponsibleGamingTest extends ApiTest {

    private static final String BASE = "/api/v1/responsible-gaming/";
    private static final String NO_INCIDENT = "{\"detail\": \"No RiskIncident matches the given query.\"}";

    private Player player;
    private long room;

    @BeforeEach
    void table() {
        player = newPlayer();
        room = newRoom(newGame("responsable"), 10, 500);
    }

    private ResultActions read(Player who, String path) throws Exception {
        return mvc.perform(get(BASE + path).with(who.bearer()));
    }

    private ResultActions send(String path, String body) throws Exception {
        return mvc.perform(post(BASE + path).with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions pause(Object incident) throws Exception {
        return send("pause", "{\"incident_id\": " + incident + "}");
    }

    private long incident(Player who) {
        return jdbc.queryForObject("insert into responsible_riskincident (user_id, round_id, kind, evidence, created_at) "
                + "values (?, ?, 'LOSS_ESCALATION', '[]', now()) returning id", Long.class, who.id(),
                newSettledRound(room, 2));
    }

    private Instant instant(String column) {
        Timestamp value = jdbc.queryForObject("select " + column + " from users where id = ?", Timestamp.class,
                player.id());
        return value == null ? null : value.toInstant();
    }

    private long openSessions(Player who) {
        return jdbc.queryForObject("select count(*) from accounts_authsession where user_id = ? "
                + "and revoked_at is null", Long.class, who.id());
    }

    private ResultActions bet() throws Exception {
        return mvc.perform(post("/api/v1/rooms/" + room + "/bets").with(player.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("round_id",
                        newOpenRoundOnce(), "kind", "COLOR", "selection", "rojo", "chips", List.of(10), "reference",
                        UUID.randomUUID().toString()))));
    }

    private long newOpenRoundOnce() {
        List<Long> open = jdbc.queryForList("select id from game_rounds where room_id = ? and status = 'OPEN'",
                Long.class, room);
        return open.isEmpty() ? newOpenRound(room, 60) : open.getFirst();
    }

    // HU27

    @ParameterizedTest
    @CsvSource({"7, 7", "30, 30", "90, 90", "'\"30\"', 30"})
    void selfExclusionClosesEverySessionAndBlocksTheAccess(String value, int days) throws Exception {
        Player other = newPlayer();
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        Instant before = Instant.now();
        JsonNode body = json(browser.create(BASE + "self-exclude", "{\"days\": " + value + "}")
                .andExpect(status().isOk()).andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0)).andReturn());

        assertThat(body).hasSize(2);
        assertThat(body.get("detail").asText())
                .isEqualTo("Autoexclusion confirmada. No se puede cancelar anticipadamente.");
        assertThat(body.get("self_excluded_until").asText()).endsWith("+00:00")
                .isEqualTo(Formats.utc(instant("self_excluded_until")));
        assertThat(OffsetDateTime.parse(body.get("self_excluded_until").asText()).toInstant()).isBetween(
                before.plus(days, ChronoUnit.DAYS).minusSeconds(1), Instant.now().plus(days, ChronoUnit.DAYS));
        assertThat(jdbc.queryForMap("select user_id, days, excluded_until from responsible_selfexclusion"))
                .containsEntry("user_id", player.id()).containsEntry("days", days)
                .containsEntry("excluded_until", Timestamp.from(instant("self_excluded_until")));
        assertThat(jdbc.queryForObject("select auth_version from users where id = ?", Integer.class, player.id()))
                .isEqualTo(1);
        assertThat(openSessions(player)).isZero();
        assertThat(openSessions(other)).isEqualTo(1);

        mvc.perform(get("/api/v1/auth/me").with(player.bearer())).andExpect(status().isForbidden());
        browser().login(player.email(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_not_allowed"));
        bet().andExpect(status().isForbidden());
        send("self-exclude", "{\"days\": 7}").andExpect(status().isForbidden());
        assertThat(count("responsible_selfexclusion")).isEqualTo(1);
    }

    @Test
    void thePlayerCanLoginWhenThePeriodEnds() throws Exception {
        send("self-exclude", "{\"days\": 7}").andExpect(status().isOk());
        jdbc.update("update users set self_excluded_until = now() - interval '1 second'");
        browser().login(player.email(), PASSWORD).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}                           | This field is required.
            {"days": null}               | This field may not be null.
            {"days": ""}                 | "" is not a valid choice.
            {"days": 1}                  | "1" is not a valid choice.
            {"days": " 7"}               | " 7" is not a valid choice.
            {"days": true}               | "True" is not a valid choice.
            {"days": 7.0}                | "7.0" is not a valid choice.
            {"days": [7]}                | "[7]" is not a valid choice.
            {"days": [true, null, 1.5, "q"]}   | "[True, None, 1.5, 'q']" is not a valid choice.
            {"days": {"a": "b", "c": null}}    | "{'a': 'b', 'c': None}" is not a valid choice.
            """)
    void onlyTheOfferedPeriodsAreAccepted(String body, String message) throws Exception {
        send("self-exclude", body).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of("days", List.of(message))), true));
        assertThat(instant("self_excluded_until")).isNull();
        assertThat(openSessions(player)).isEqualTo(1);
    }

    @Test
    void selfExclusionCannotBeReadOrCancelled() throws Exception {
        read(player, "self-exclude").andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("Method \"GET\" not allowed."));
        mvc.perform(delete(BASE + "self-exclude").with(player.bearer())).andExpect(status().isMethodNotAllowed());
    }

    // HU28

    @Test
    void theSummaryOnlyCountsTheCurrentSession() throws Exception {
        JsonNode empty = json(read(player, "session-summary").andExpect(status().isOk()).andReturn());
        assertThat(empty).hasSize(9);
        assertThat(empty.get("total_wagered").asText()).isEqualTo("0.00");
        assertThat(empty.get("pending_wagered").asText()).isEqualTo("0.00");
        assertThat(empty.get("duration_seconds").asLong()).isBetween(0L, 5L);

        Long session = jdbc.queryForObject("select id from accounts_authsession where user_id = ?", Long.class,
                player.id());
        jdbc.update("update accounts_authsession set started_at = now() - interval '1 hour' where id = ?", session);
        newBet(player, newSettledRound(room, 1), "10", "20", "now");
        newBet(player, newSettledRound(room, 2), "50", "0", "now");
        newBet(player, newOpenRound(room, 60), "30", null, "now");
        newPlay(player, "BLACKJACK", "40", "100", "now");
        newPlay(player, "SPORTS", "15", null, "now");
        jdbc.update("update user_bets set session_id = ?", session);
        jdbc.update("update casino_play set session_id = ?", session);
        newBet(player, newSettledRound(room, 3), "500", "1000", "now"); // de otra sesión
        newPlay(player, "SPORTS", "500", null, "now");

        read(player, "session-summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.total_wagered").value("100.00"))
                .andExpect(jsonPath("$.total_paid").value("120.00")).andExpect(jsonPath("$.net").value("20.00"))
                .andExpect(jsonPath("$.pending_wagered").value("45.00"))
                .andExpect(jsonPath("$.pending_count").value(2))
                .andExpect(jsonPath("$.reality_check_interval_seconds").value(3600))
                .andExpect(jsonPath("$.scope").value("settled_session_bets"))
                .andExpect(jsonPath("$.session_started_at").value(Formats.utc(jdbc.queryForObject(
                        "select started_at from accounts_authsession where id = ?", Timestamp.class, session)
                        .toInstant())))
                .andExpect(jsonPath("$.duration_seconds").value(
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.greaterThanOrEqualTo(3600),
                                org.hamcrest.Matchers.lessThan(3610))));

        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        browser.read(BASE + "session-summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.total_wagered").value("0.00")).andExpect(jsonPath("$.pending_count").value(0));
    }

    @Test
    void betsPlacedHereBelongToTheSession() throws Exception {
        dailyLimit(player, 1000);
        bet().andExpect(status().isCreated());
        read(player, "session-summary").andExpect(jsonPath("$.pending_wagered").value("10.00"))
                .andExpect(jsonPath("$.pending_count").value(1));
    }

    // HU29

    @Test
    void alertsAreThePlayersLastTwentyNewestFirst() throws Exception {
        read(player, "risk-alerts").andExpect(status().isOk())
                .andExpect(content().json("{\"pause_until\": null, \"results\": []}", true));
        long last = 0;
        for (int index = 0; index < 22; index++) {
            last = incident(player);
        }
        incident(newPlayer());
        jdbc.update("update responsible_riskincident set created_at = '2026-11-20T15:00:00.5Z', "
                + "pause_started_at = '2026-11-20T15:01:00Z' where id = ?", last);
        jdbc.update("update users set pause_until = '2026-11-20T15:11:00Z' where id = ?", player.id());
        JsonNode body = json(read(player, "risk-alerts").andExpect(status().isOk()).andReturn());
        assertThat(body.get("pause_until").asText()).isEqualTo("2026-11-20T15:11:00+00:00");
        assertThat(body.get("results")).hasSize(20);
        assertThat(body.get("results").get(0)).isEqualTo(mapper.readTree("""
                {"incident_id": %d, "created_at": "2026-11-20T15:00:00.500000+00:00",
                 "pause_started_at": "2026-11-20T15:01:00+00:00", "suggested_pause_seconds": 600}""".formatted(last)));
        assertThat(body.get("results").get(1).get("pause_started_at").isNull()).isTrue();
    }

    @Test
    void acceptingThePauseBlocksBetsForTenMinutesOnlyOnce() throws Exception {
        dailyLimit(player, 1000);
        long incident = incident(player);
        Instant before = Instant.now();
        JsonNode first = json(pause(incident).andExpect(status().isOk()).andReturn());
        assertThat(first).hasSize(1);
        Instant until = instant("pause_until");
        assertThat(first.get("pause_until").asText()).isEqualTo(Formats.utc(until));
        assertThat(until).isBetween(before.plusSeconds(599), Instant.now().plusSeconds(600));
        assertThat(jdbc.queryForObject("select pause_started_at from responsible_riskincident", Timestamp.class))
                .isNotNull();
        bet().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("pause_active"));

        assertThat(json(pause("\"" + incident + "\"").andExpect(status().isOk()).andReturn())).isEqualTo(first);
        assertThat(instant("pause_until")).isEqualTo(until);

        jdbc.update("update users set pause_until = null where id = ?", player.id());
        pause(incident).andExpect(status().isOk()).andExpect(content().json("{\"pause_until\": null}", true));
        bet().andExpect(status().isCreated());
    }

    @Test
    void aLongerPauseIsKept() throws Exception {
        jdbc.update("update users set pause_until = now() + interval '1 hour' where id = ?", player.id());
        Instant until = instant("pause_until");
        pause(incident(player)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pause_until").value(Formats.utc(until)));
        assertThat(instant("pause_until")).isEqualTo(until);
    }

    @Test
    void theAlertMustBelongToThePlayer() throws Exception {
        long foreign = incident(newPlayer());
        for (Object id : List.of(foreign, 999999999, "1000000000000000000000000000000")) {
            pause(id).andExpect(status().isNotFound()).andExpect(content().json(NO_INCIDENT, true));
        }
        assertThat(instant("pause_until")).isNull();
        assertThat(jdbc.queryForObject("select pause_started_at from responsible_riskincident", Timestamp.class))
                .isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}                     | This field is required.
            {"incident_id": null}  | This field may not be null.
            {"incident_id": ""}    | A valid integer is required.
            {"incident_id": "x"}   | A valid integer is required.
            {"incident_id": 0}     | Ensure this value is greater than or equal to 1.
            """)
    void theAlertIdIsValidated(String body, String message) throws Exception {
        send("pause", body).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of("incident_id", List.of(message))), true));
    }

    @Test
    void simultaneousPausesStartOnlyOne() throws Exception {
        long incident = incident(player);
        Callable<String> click = () -> pause(incident).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, click)).stream().map(future -> future.resultNow())
                    .distinct()).hasSize(1);
        }
    }

    // Comunes

    @ParameterizedTest
    @ValueSource(strings = {"self-exclude", "pause"})
    void writesRequireASessionAndCsrf(String path) throws Exception {
        mvc.perform(post(BASE + path)).andExpect(status().isUnauthorized());
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        browser.withoutCsrf().create(BASE + path, Map.of("days", 7, "incident_id", incident(player)))
                .andExpect(status().isForbidden());
        assertThat(instant("self_excluded_until")).isNull();
        assertThat(instant("pause_until")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"session-summary", "risk-alerts"})
    void readsRequireASession(String path) throws Exception {
        mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
    }

    /** Es del administrador: sigue solo en Django. */
    @Test
    void excludedPeopleIsNotServedHere() throws Exception {
        send("excluded-people", "{\"dni\": \"12345678\", \"source\": \"x\"}").andExpect(status().isNotFound());
    }
}
