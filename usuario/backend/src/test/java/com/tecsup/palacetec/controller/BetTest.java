package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class BetTest extends ApiTest {

    private Player player;
    private long room;
    private long round;

    @BeforeEach
    void openTable() {
        player = newPlayer();
        room = newRoom(newGame("apuestas"), 10, 500);
        round = newOpenRound(room, 60);
        limit(player, 2000);
    }

    private void limit(Player target, int amount) {
        jdbc.update("insert into responsible_dailylimit (user_id, amount) values (?, ?) "
                + "on conflict (user_id) do update set amount = excluded.amount", target.id(), amount);
    }

    private Map<String, Object> bet(Integer... chips) {
        return new HashMap<>(Map.of("round_id", round, "kind", "COLOR", "selection", "rojo",
                "chips", List.of(chips), "reference", UUID.randomUUID().toString()));
    }

    private ResultActions place(Player target, long roomId, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/rooms/" + roomId + "/bets").with(target.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }

    private ResultActions place(Map<String, Object> body) throws Exception {
        return place(player, room, body);
    }

    private void assertNothingHappened() {
        assertThat(count("user_bets")).isZero();
        assertThat(count("wallet_transactions")).isZero();
        assertThat(count("responsible_dailyspending")).isZero();
        assertThat(wallet()).containsEntry("balance", "1000.00").containsEntry("bonus_balance", "1000.00");
    }

    private Map<String, Object> wallet() {
        return jdbc.queryForMap("select balance::text as balance, bonus_balance::text as bonus_balance "
                + "from wallets where user_id = ?", player.id());
    }

    @Test
    void acceptsABetDebitsBonusFirstAndLinksTheSession() throws Exception {
        jdbc.update("update wallets set bonus_balance = 100 where user_id = ?", player.id());
        Map<String, Object> body = bet(100, 50);
        long id = json(place(body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.round_id").value(round)).andExpect(jsonPath("$.kind").value("COLOR"))
                .andExpect(jsonPath("$.selection").value("rojo")).andExpect(jsonPath("$.chips[0]").value(100))
                .andExpect(jsonPath("$.chips[1]").value(50)).andExpect(jsonPath("$.amount").value("150.00"))
                .andExpect(jsonPath("$.payout").value(nullValue()))
                .andExpect(jsonPath("$.reference").value(body.get("reference")))
                .andExpect(jsonPath("$.created_at").value(endsWith("-05:00")))
                .andExpect(jsonPath("$.length()").value(9)).andReturn()).get("id").asLong();

        assertThat(wallet()).containsEntry("balance", "850.00").containsEntry("bonus_balance", "0.00");
        assertThat(jdbc.queryForMap("select type, amount::text as amount, balance_after::text as balance_after, "
                + "reference from wallet_transactions"))
                .containsEntry("type", "BET").containsEntry("amount", "150.00")
                .containsEntry("balance_after", "850.00").containsEntry("reference", "roulette:bet:" + id);
        assertThat(jdbc.queryForObject("select amount::text from responsible_dailyspending where user_id = ? "
                + "and date = (now() at time zone 'America/Lima')::date", String.class, player.id()))
                .isEqualTo("150.00");
        // Django guarda las fichas como JSON y usa la sesión en el resumen de la sesión.
        assertThat(jdbc.queryForMap("select b.chips::text as chips, b.payout, b.repeat_operation_id, "
                + "s.user_id from user_bets b join accounts_authsession s on s.id = b.session_id"))
                .containsEntry("chips", "[100, 50]").containsEntry("payout", null)
                .containsEntry("repeat_operation_id", null).containsEntry("user_id", player.id());
        assertThat(jdbc.queryForObject("select status from game_rounds where id = ?", String.class, round))
                .isEqualTo("OPEN");
    }

    @ParameterizedTest
    @CsvSource({"STRAIGHT_UP,0", "STRAIGHT_UP,36", "COLOR,negro", "PARITY,par", "PARITY,impar", "DOZEN,1", "COLUMN,3"})
    void acceptsEveryKind(String kind, String selection) throws Exception {
        Map<String, Object> body = bet(10);
        body.put("kind", kind);
        body.put("selection", selection);
        place(body).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({"STRAIGHT_UP,37", "STRAIGHT_UP,07", "STRAIGHT_UP,-1", "COLOR,verde", "COLOR,Rojo", "PARITY,rojo",
            "DOZEN,4", "COLUMN,0", "COLOR,demasiado"})
    void rejectsInvalidSelections(String kind, String selection) throws Exception {
        Map<String, Object> body = bet(10);
        body.put("kind", kind);
        body.put("selection", selection);
        place(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.selection[0]").isNotEmpty());
        assertNothingHappened();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            chips     | [20]
            chips     | [10, 10.5]
            chips     | [-10]
            chips     | [null]
            chips     | []
            chips     | null
            chips     | "10"
            kind      | "SPLIT"
            kind      | 0
            round_id  | 0
            round_id  | null
            selection | null
            reference | "no-es-uuid"
            reference | null
            """)
    void rejectsInvalidInput(String field, String value) throws Exception {
        Map<String, Object> body = bet(10);
        body.put(field, mapper.readTree(value));
        place(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$." + field + "[0]").isNotEmpty());
        assertNothingHappened();
    }

    @Test
    void limitsTheNumberOfChips() throws Exception {
        Integer[] chips = IntStream.range(0, 101).mapToObj(index -> 10).toArray(Integer[]::new);
        place(bet(chips)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.chips[0]").isNotEmpty());
        assertNothingHappened();
    }

    @Test
    void sameReferenceIsChargedOnceEvenAfterTheRoundCloses() throws Exception {
        Map<String, Object> body = bet(100);
        long id = json(place(body).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        place(body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        jdbc.update("update game_rounds set status = 'CLOSED' where id = ?", round);
        place(body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(wallet()).containsEntry("balance", "900.00");
        assertThat(count("wallet_transactions")).isEqualTo(1);

        body.put("selection", "negro");
        place(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("reference_conflict"));
        // La referencia es por jugador: otro puede usar la misma.
        jdbc.update("update game_rounds set status = 'OPEN' where id = ?", round);
        Player other = newPlayer();
        limit(other, 500);
        place(other, room, body).andExpect(status().isCreated());
    }

    @Test
    void rejectsClosedExpiredSettledOrForeignRounds() throws Exception {
        jdbc.update("update game_rounds set status = 'CLOSED' where id = ?", round);
        place(bet(10)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("bets_closed"));
        // Plazo vencido aunque Django todavía no la haya cerrado.
        jdbc.update("update game_rounds set status = 'OPEN', closes_at = now() - interval '1 second' where id = ?", round);
        place(bet(10)).andExpect(jsonPath("$.code").value("bets_closed"));
        jdbc.update("update game_rounds set status = 'SETTLED', winning_number = 7, settled_at = now() where id = ?", round);
        place(bet(10)).andExpect(jsonPath("$.code").value("bets_closed"));

        long otherRoom = newRoom(newGame("otra"), 10, 500);
        newOpenRound(otherRoom, 60);
        place(player, otherRoom, bet(10)).andExpect(jsonPath("$.code").value("bets_closed"));
        place(player, 999999999L, bet(10)).andExpect(status().isNotFound());
        jdbc.update("update rooms set is_active = false where id = ?", room);
        place(bet(10)).andExpect(status().isNotFound());
        assertNothingHappened();
    }

    @Test
    void respectsRoomMinimumAndAccumulatedMaximum() throws Exception {
        jdbc.update("update rooms set min_bet = 50, max_bet = 300 where id = ?", room);
        place(bet(10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").value("El monto debe respetar el minimo por apuesta y el maximo acumulado por ronda."));
        place(bet(50)).andExpect(status().isCreated());
        place(bet(100, 100, 50)).andExpect(status().isCreated());
        place(bet(50)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.amount").isNotEmpty());
        assertThat(wallet()).containsEntry("balance", "700.00");
    }

    @Test
    void requiresAndRespectsTheDailyLimit() throws Exception {
        jdbc.update("delete from responsible_dailylimit");
        place(bet(10)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("daily_limit_required"));
        limit(player, 100);
        place(bet(50)).andExpect(status().isCreated());
        place(bet(50, 10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("daily_limit_exceeded"));
        place(bet(50)).andExpect(status().isCreated());
        place(bet(10)).andExpect(jsonPath("$.code").value("daily_limit_exceeded"));
        limit(player, 0);
        place(bet(10)).andExpect(jsonPath("$.code").value("daily_limit_exceeded"));
        assertThat(wallet()).containsEntry("balance", "900.00");
    }

    @Test
    void pauseAndSelfExclusionBlockNewBets() throws Exception {
        jdbc.update("update users set pause_until = now() + interval '10 minutes' where id = ?", player.id());
        place(bet(10)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("pause_active"));
        jdbc.update("update users set pause_until = now() - interval '1 second', "
                + "self_excluded_until = now() + interval '7 days' where id = ?", player.id());
        place(bet(10)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("access_not_allowed"));
        assertNothingHappened();
        jdbc.update("update users set self_excluded_until = null where id = ?", player.id());
        place(bet(10)).andExpect(status().isCreated());
    }

    @Test
    void insufficientBalanceRevertsBetAndDailySpending() throws Exception {
        jdbc.update("update wallets set balance = 90, bonus_balance = 90 where user_id = ?", player.id());
        place(bet(100)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("insufficient_balance"))
                .andExpect(jsonPath("$.detail").value("Saldo insuficiente."));
        assertThat(count("user_bets")).isZero();
        assertThat(count("wallet_transactions")).isZero();
        assertThat(count("responsible_dailyspending")).isZero();
        assertThat(wallet()).containsEntry("balance", "90.00");
    }

    @Test
    void simultaneousRetriesOfOneBetChargeOnce() throws Exception {
        Map<String, Object> body = bet(100);
        Callable<Integer> attempt = () -> place(body).andReturn().getResponse().getStatus();
        assertThat(inParallel(attempt, attempt, attempt, attempt)).containsExactly(200, 200, 200, 201);
        assertThat(count("user_bets")).isEqualTo(1);
        assertThat(wallet()).containsEntry("balance", "900.00");
    }

    @Test
    void simultaneousBetsInTwoRoomsShareTheDailyLimitAndTheBalance() throws Exception {
        long secondRoom = newRoom(newGame("segunda"), 10, 500);
        Map<String, Object> second = bet(100);
        second.put("round_id", newOpenRound(secondRoom, 60));
        limit(player, 150);
        assertThat(inParallel(() -> place(bet(100)).andReturn().getResponse().getStatus(),
                () -> place(player, secondRoom, second).andReturn().getResponse().getStatus()))
                .containsExactly(201, 400);
        assertThat(wallet()).containsEntry("balance", "900.00");
        assertThat(jdbc.queryForObject("select amount::text from responsible_dailyspending", String.class))
                .isEqualTo("100.00");
    }

    @SafeVarargs
    private static List<Integer> inParallel(Callable<Integer>... attempts) throws Exception {
        try (var pool = Executors.newFixedThreadPool(attempts.length)) {
            return pool.invokeAll(List.of(attempts)).stream().map(future -> future.resultNow()).sorted().toList();
        }
    }
}
