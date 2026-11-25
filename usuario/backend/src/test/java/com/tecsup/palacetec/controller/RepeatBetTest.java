package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
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

/** HU35 de Django: repetir las apuestas de la ronda anterior. */
class RepeatBetTest extends ApiTest {

    private static final String NO_ROOM = "{\"detail\": \"No Room matches the given query.\"}";
    private static final String CONFLICT = "{\"code\": \"reference_conflict\", "
            + "\"detail\": \"Esta referencia corresponde a otra repeticion.\"}";

    private Player player;
    private long room;
    private long source;
    private long target;
    private final String reference = UUID.randomUUID().toString();

    @BeforeEach
    void previousRoundWithTwoBets() {
        player = newPlayer();
        room = newRoom(newGame("repetir"), 10, 500);
        source = newSettledRound(room, 0);
        dozen("1", 10);
        dozen("2", 50);
        target = newOpenRound(room, 60);
        dailyLimit(player, 1000);
    }

    private void dozen(String selection, int chip) {
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                + "created_at) values (?, ?, 'DOZEN', ?, cast(? as jsonb), ?, 0, gen_random_uuid(), now())",
                player.id(), source, selection, "[" + chip + "]", chip);
    }

    private ResultActions previous(Object roomId) throws Exception {
        return mvc.perform(get("/api/v1/rooms/" + roomId + "/bets/previous").with(player.bearer()));
    }

    private ResultActions send(Object roomId, String body) throws Exception {
        return mvc.perform(post("/api/v1/rooms/" + roomId + "/bets/repeat").with(player.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions repeat(Map<String, Object> changes) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("round_id", target, "reference", reference));
        body.putAll(changes);
        return send(room, mapper.writeValueAsString(body));
    }

    private ResultActions repeat() throws Exception {
        return repeat(Map.of());
    }

    private void expectError(ResultActions result, String code, String detail) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().json("{\"code\": \"%s\", \"detail\": \"%s\"}".formatted(code, detail), true));
        assertNothingHappened();
    }

    private void assertNothingHappened() {
        assertThat(count("roulette_repeatoperation")).isZero();
        assertThat(count("user_bets")).isEqualTo(2);
        assertThat(count("wallet_transactions")).isZero();
        assertThat(count("responsible_dailyspending")).isZero();
    }

    private String balance() {
        return jdbc.queryForObject("select balance::text from wallets where user_id = ?", String.class, player.id());
    }

    @Test
    void previousShowsTheLastSettledRoundOfTheRoomWithOwnBets() throws Exception {
        newBet(newPlayer(), newSettledRound(room, 5), "10", "0", "now");
        newBet(player, newSettledRound(newRoom(newGame("otra"), 10, 500), 5), "10", "0", "now");
        JsonNode body = json(previous(room).andExpect(status().isOk()).andReturn());
        assertThat(body).hasSize(2);
        assertThat(body.get("source_round_id").asLong()).isEqualTo(source);
        assertThat(body.get("bets").findValuesAsText("selection")).containsExactly("1", "2");
        assertThat(body.get("bets").get(0)).hasSize(9);
        assertThat(body.get("bets").get(1).get("payout").asText()).isEqualTo("0.00");
        jdbc.update("delete from user_bets where user_id = ?", player.id());
        previous(room).andExpect(status().isOk())
                .andExpect(content().json("{\"source_round_id\": null, \"bets\": []}", true));
    }

    @Test
    void repeatCopiesEveryBetOnce() throws Exception {
        JsonNode body = json(repeat(Map.of("source_round_id", source)).andExpect(status().isCreated()).andReturn());
        assertThat(body).hasSize(4);
        assertThat(body.get("source_round_id").asLong()).isEqualTo(source);
        assertThat(body.get("round_id").asLong()).isEqualTo(target);
        assertThat(body.get("reference").asText()).isEqualTo(reference);
        JsonNode placed = body.get("bets");
        assertThat(placed.findValuesAsText("selection")).containsExactly("1", "2");
        assertThat(placed.findValuesAsText("amount")).containsExactly("10.00", "50.00");
        assertThat(placed.get(1).get("chips").toString()).isEqualTo("[50]");
        assertThat(placed.get(0).get("payout").isNull()).isTrue();
        assertThat(balance()).isEqualTo("940.00");
        assertThat(jdbc.queryForObject("select count(*) from user_bets b join roulette_repeatoperation o "
                + "on o.id = b.repeat_operation_id where b.session_id is not null and b.round_id = ?", Long.class,
                target)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(amount)::text from responsible_dailyspending", String.class))
                .isEqualTo("60.00");

        JsonNode again = json(repeat().andExpect(status().isOk()).andReturn());
        assertThat(again).isEqualTo(body);
        repeat(Map.of("source_round_id", source)).andExpect(status().isOk());
        assertThat(balance()).isEqualTo("940.00");
        assertThat(count("user_bets")).isEqualTo(4);
    }

    @Test
    void theSameReferenceCannotRepeatSomethingElse() throws Exception {
        repeat().andExpect(status().isCreated());
        repeat(Map.of("source_round_id", 999)).andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT, true));
        repeat(Map.of("round_id", target + 1)).andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT, true));
        long otherRoom = newRoom(newGame("otra-sala"), 10, 500);
        send(otherRoom, mapper.writeValueAsString(Map.of("round_id", target, "reference", reference)))
                .andExpect(status().isConflict()).andExpect(content().json(CONFLICT, true));
        assertThat(count("roulette_repeatoperation")).isEqualTo(1);
    }

    @Test
    void theReplayStillAnswersAfterTheRoundCloses() throws Exception {
        repeat().andExpect(status().isCreated());
        jdbc.update("update game_rounds set status = 'CLOSED' where id = ?", target);
        repeat().andExpect(status().isOk()).andExpect(jsonPath("$.bets", hasSize(2)));
    }

    @Test
    void aFailureRevertsTheWholeRepeat() throws Exception {
        dailyLimit(player, 30);
        expectError(repeat(), "daily_limit_exceeded", "La apuesta supera tu limite diario disponible.");
        dailyLimit(player, 1000);
        jdbc.update("update wallets set balance = 55, bonus_balance = 55 where user_id = ?", player.id());
        expectError(repeat(), "insufficient_balance", "Saldo insuficiente.");
        assertThat(balance()).isEqualTo("55.00");
        jdbc.update("update rooms set min_bet = 20 where id = ?", room);
        repeat().andExpect(status().isBadRequest()).andExpect(content().json("{\"amount\": \"El monto debe respetar el "
                + "minimo por apuesta y el maximo acumulado por ronda.\"}", true));
        assertNothingHappened();
    }

    @Test
    void pauseAndDailyLimitAreRespected() throws Exception {
        jdbc.update("update users set pause_until = now() + interval '5 minutes' where id = ?", player.id());
        expectError(repeat(), "pause_active", "Tu pausa de juego todavia esta activa.");
        jdbc.update("update users set pause_until = null where id = ?", player.id());
        jdbc.update("delete from responsible_dailylimit");
        expectError(repeat(), "daily_limit_required", "Define tu limite diario antes de apostar.");
    }

    @Test
    void theTargetMustBeAnOpenRoundOfTheRoom() throws Exception {
        expectError(repeat(Map.of("round_id", 999999999)), "bets_closed", "Ronda no disponible.");
        long foreign = newOpenRound(newRoom(newGame("ajena"), 10, 500), 60);
        expectError(repeat(Map.of("round_id", foreign)), "bets_closed", "Ronda no disponible.");
        jdbc.update("update game_rounds set closes_at = now() - interval '1 second' where id = ?", target);
        expectError(repeat(), "bets_closed", "No va mas: la ronda no acepta apuestas.");
    }

    @Test
    void theSourceMustBeThePreviousRound() throws Exception {
        String expected = "{\"source_round_id\": \"No existe esa ronda anterior para repetir.\"}";
        repeat(Map.of("source_round_id", 999)).andExpect(status().isBadRequest())
                .andExpect(content().json(expected, true));
        jdbc.update("delete from user_bets");
        repeat().andExpect(status().isBadRequest()).andExpect(content().json(expected, true));
    }

    @Test
    void aBetThatAlreadyUsesTheDerivedReferenceIsAConflict() throws Exception {
        repeat().andExpect(status().isCreated());
        // Como si las apuestas se hubieran hecho a mano con las referencias que derivará la repetición.
        jdbc.update("update user_bets set repeat_operation_id = null");
        jdbc.update("delete from roulette_repeatoperation");
        repeat().andExpect(status().isConflict()).andExpect(content().json("{\"code\": \"reference_conflict\", "
                + "\"detail\": \"La referencia de repeticion ya fue utilizada.\"}", true));
        assertThat(count("roulette_repeatoperation")).isZero();
        assertThat(balance()).isEqualTo("940.00");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {} | {"round_id": ["This field is required."], "reference": ["This field is required."]}
            {"round_id": null, "reference": null, "source_round_id": null} | {"round_id": ["This field may not be null."], "source_round_id": ["This field may not be null."], "reference": ["This field may not be null."]}
            {"round_id": "", "reference": ""} | {"round_id": ["A valid integer is required."], "reference": ["Must be a valid UUID."]}
            {"round_id": 0, "reference": 5, "source_round_id": 0} | {"round_id": ["Ensure this value is greater than or equal to 1."], "source_round_id": ["Ensure this value is greater than or equal to 1."]}
            {"round_id": true, "reference": true} | {"round_id": ["A valid integer is required."]}
            {"round_id": 1.5, "reference": [1]} | {"round_id": ["A valid integer is required."], "reference": ["Must be a valid UUID."]}
            [1] | {"non_field_errors": ["Invalid data. Expected a dictionary, but got list."]}
            """)
    void theRequestIsValidated(String body, String expected) throws Exception {
        send(room, body).andExpect(status().isBadRequest()).andExpect(content().json(expected, true));
        assertNothingHappened();
    }

    @Test
    void roomsMustBeAvailableBeforeLookingAtTheRequest() throws Exception {
        for (String id : List.of("0", "999999999", "99999999999999999999999")) {
            previous(id).andExpect(status().isNotFound()).andExpect(content().json(NO_ROOM, true));
            send(id, "{}").andExpect(status().isNotFound()).andExpect(content().json(NO_ROOM, true));
        }
        jdbc.update("update rooms set is_active = false where id = ?", room);
        previous(room).andExpect(status().isNotFound()).andExpect(content().json(NO_ROOM, true));
        repeat().andExpect(status().isNotFound()).andExpect(content().json(NO_ROOM, true));
        previous("abc").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/rooms/1/bets/previous")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/rooms/1/bets/repeat")).andExpect(status().isUnauthorized());
    }

    @Test
    void selfExcludedPlayersCannotRepeat() throws Exception {
        jdbc.update("update users set self_excluded_until = now() + interval '1 day' where id = ?", player.id());
        repeat().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("access_not_allowed"));
        assertNothingHappened();
    }

    @Test
    void simultaneousRetriesRepeatOnce() throws Exception {
        Callable<Integer> retry = () -> repeat().andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, retry)).stream().map(future -> future.resultNow()))
                    .containsExactlyInAnyOrder(201, 200, 200, 200);
        }
        assertThat(count("roulette_repeatoperation")).isEqualTo(1);
        assertThat(count("user_bets")).isEqualTo(4);
        assertThat(balance()).isEqualTo("940.00");
    }
}
