package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** casino/tests.py de Django: blackjack. */
class BlackjackTest extends ApiTest {

    private static final String PATH = "/api/v1/casino/blackjack";
    private static final String NO_HAND = "{\"detail\": \"No BlackjackHand matches the given query.\"}";

    private Player player;

    @BeforeEach
    void player() {
        player = newPlayer();
        dailyLimit(player, 1000);
    }

    private ResultActions send(Player who, String path, String body) throws Exception {
        return mvc.perform(post(path).with(who.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions start(String amount, String reference) throws Exception {
        return send(player, PATH, mapper.writeValueAsString(Map.of("amount", amount, "reference", reference)));
    }

    private ResultActions start(String amount) throws Exception {
        return start(amount, UUID.randomUUID().toString());
    }

    private JsonNode started(String amount) throws Exception {
        return json(start(amount).andExpect(status().isCreated()).andReturn()).get("hand");
    }

    private ResultActions act(Object hand, String action, Object revision) throws Exception {
        return send(player, PATH + "/" + hand + "/action",
                mapper.writeValueAsString(Map.of("action", action, "revision", revision)));
    }

    private JsonNode acted(JsonNode hand, String action) throws Exception {
        return json(act(hand.get("id"), action, hand.get("revision").asInt()).andExpect(status().isOk()).andReturn())
                .get("hand");
    }

    private String balance() {
        return jdbc.queryForObject("select balance::text from wallets where user_id = ?", String.class, player.id());
    }

    private String spent() {
        return jdbc.queryForObject("select coalesce(sum(amount), 0)::text from responsible_dailyspending", String.class);
    }

    private void assertNothingHappened() {
        assertThat(count("casino_play")).isZero();
        assertThat(count("casino_blackjackhand")).isZero();
        assertThat(count("wallet_transactions")).isZero();
        assertThat(spent()).isEqualTo("0");
    }

    @Test
    void aNaturalPaysThreeToTwoOnlyOnce() throws Exception {
        deck.deal("A_S K_S", "9_H 8_H");
        String reference = UUID.randomUUID().toString();
        JsonNode first = json(start("10.01", reference).andExpect(status().isCreated()).andReturn());
        assertThat(first).isEqualTo(mapper.readTree("""
                {"hand": {"id": %d, "amount": "10.01", "player": ["A_S", "K_S"], "dealer": ["9_H", "8_H"],
                 "dealer_hidden": false, "player_score": 21, "dealer_score": 17, "active": false, "revision": 0,
                 "outcome": "BLACKJACK", "payout": "25.03"}}""".formatted(first.at("/hand/id").asLong())));
        assertThat(json(start("10.01", reference).andExpect(status().isOk()).andReturn()))
                .isEqualTo(first);
        assertThat(balance()).isEqualTo("1015.02");
        assertThat(jdbc.queryForList("select type || ' ' || amount || ' ' || balance_after from wallet_transactions "
                + "order by id", String.class)).containsExactly("BET 10.01 989.99", "WIN 25.03 1015.02");
        assertThat(jdbc.queryForObject("select bonus_balance::text from wallets where user_id = ?", String.class,
                player.id())).isEqualTo("989.99");
        Map<String, Object> play = jdbc.queryForMap("select * from casino_play");
        assertThat(play).containsEntry("kind", "BLACKJACK").containsEntry("description", "Blackjack clásico");
        assertThat(play.get("settled_at")).isNotNull();
        assertThat(play.get("session_id")).isNotNull();
        assertThat(jdbc.queryForList("select reference from wallet_transactions order by id", String.class))
                .containsExactly("casino:bet:" + play.get("id"), "casino:win:" + play.get("id"));
    }

    @Test
    void theHoleCardAndTheDeckStayHiddenAndRetriesAreSafe() throws Exception {
        deck.deal("9_S 7_S", "10_H 8_H", "K_C");
        JsonNode hand = started("10");
        assertThat(hand).hasSize(11);
        assertThat(hand.get("dealer_hidden").asBoolean()).isTrue();
        assertThat(hand.get("dealer").toString()).isEqualTo("[\"10_H\"]");
        assertThat(hand.get("dealer_score").asInt()).isEqualTo(10);
        assertThat(hand.get("player_score").asInt()).isEqualTo(16);
        assertThat(hand.get("outcome").asText()).isEmpty();
        assertThat(hand.get("payout").isNull()).isTrue();
        start("10").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"detail\": \"Termina tu mano actual antes de comenzar otra.\"}", true));

        JsonNode bust = json(act(hand.get("id"), "hit", 0).andExpect(status().isOk()).andReturn());
        assertThat(bust.at("/hand/outcome").asText()).isEqualTo("LOST");
        assertThat(bust.at("/hand/player_score").asInt()).isEqualTo(26);
        assertThat(bust.at("/hand/dealer").toString()).isEqualTo("[\"10_H\",\"8_H\"]");
        assertThat(bust.at("/hand/revision").asInt()).isEqualTo(1);
        assertThat(bust.at("/hand/payout").asText()).isEqualTo("0.00");
        assertThat(json(act(hand.get("id"), "hit", 0).andExpect(status().isOk()).andReturn())).isEqualTo(bust);
        assertThat(json(act(hand.get("id"), "stand", 1).andExpect(status().isOk()).andReturn())).isEqualTo(bust);
        assertThat(spent()).isEqualTo("10.00");
        assertThat(balance()).isEqualTo("990.00");
        assertThat(count("wallet_transactions")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"10_S 8_S, 10_H 8_H, '', PUSH, 10.00, 2", "9_S 8_S, A_H 6_H, '', PUSH, 10.00, 2",
            "10_S 9_S, 10_H 8_H, '', WON, 20.00, 2", "10_S 8_S, 10_H 6_H, K_C, WON, 20.00, 3",
            "10_S 8_S, 10_H 6_H, 5_C, LOST, 0.00, 3", "10_S 8_S, 2_H 3_H, 4_C, PUSH, 10.00, 5"})
    void standingLetsTheDealerPlay(String mine, String dealer, String next, String outcome, String payout, int cards)
            throws Exception {
        if (next.isEmpty()) {
            deck.deal(mine, dealer);
        } else {
            deck.deal(mine, dealer, next, "5_D", "4_D");
        }
        JsonNode hand = acted(started("10"), "stand");
        assertThat(hand.get("outcome").asText()).isEqualTo(outcome);
        assertThat(hand.get("payout").asText()).isEqualTo(payout);
        assertThat(hand.get("dealer")).hasSize(cards);
        assertThat(hand.get("dealer_hidden").asBoolean()).isFalse();
        assertThat(hand.get("active").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"9_S 8_S, A_H K_H, LOST, 0.00", "A_S K_S, A_H K_H, PUSH, 10.00"})
    void aDealerNaturalEndsTheHand(String mine, String dealer, String outcome, String payout) throws Exception {
        deck.deal(mine, dealer);
        JsonNode hand = started("10");
        assertThat(hand.get("active").asBoolean()).isFalse();
        assertThat(hand.get("outcome").asText()).isEqualTo(outcome);
        assertThat(hand.get("payout").asText()).isEqualTo(payout);
    }

    @Test
    void hittingToTwentyOneEndsTheHand() throws Exception {
        deck.deal("5_S 6_S", "10_H 7_H", "2_C", "8_D");
        JsonNode hand = acted(started("10"), "hit");
        assertThat(hand.get("active").asBoolean()).isTrue();
        assertThat(hand.get("player_score").asInt()).isEqualTo(13);
        assertThat(hand.get("revision").asInt()).isEqualTo(1);
        hand = acted(hand, "hit");
        assertThat(hand.get("outcome").asText()).isEqualTo("WON");
        assertThat(hand.get("revision").asInt()).isEqualTo(2);
        assertThat(balance()).isEqualTo("1010.00");
    }

    @Test
    void aReferenceBelongsToOneBet() throws Exception {
        String reference = UUID.randomUUID().toString();
        start("10", reference).andExpect(status().isCreated());
        start("11", reference).andExpect(status().isConflict()).andExpect(content().json(
                "{\"code\": \"reference_conflict\", \"detail\": \"La referencia ya corresponde a otra jugada.\"}", true));
        jdbc.update("delete from casino_blackjackhand");
        jdbc.update("update casino_play set kind = 'SPORTS'");
        start("10", reference).andExpect(status().isConflict());
        assertThat(count("casino_play")).isEqualTo(1);
    }

    @Test
    void theLatestHandIsPrivate() throws Exception {
        mvc.perform(get(PATH).with(player.bearer())).andExpect(status().isOk())
                .andExpect(content().json("{\"hand\": null}", true));
        deck.deal("A_S K_S", "9_H 8_H");
        started("10");
        deck.deal("9_S 7_S", "10_H 8_H");
        JsonNode hand = started("20");
        assertThat(json(mvc.perform(get(PATH).with(player.bearer())).andReturn()).get("hand")).isEqualTo(hand);

        Player other = newPlayer();
        mvc.perform(get(PATH).with(other.bearer())).andExpect(content().json("{\"hand\": null}", true));
        send(other, PATH + "/" + hand.get("id") + "/action", "{\"action\": \"stand\", \"revision\": 0}")
                .andExpect(status().isNotFound()).andExpect(content().json(NO_HAND, true));
    }

    @Test
    void limitsBalanceAndExclusionApplyAndNothingIsCharged() throws Exception {
        dailyLimit(player, 5);
        start("10").andExpect(status().isBadRequest()).andExpect(content().json("{\"code\": \"daily_limit_exceeded\", "
                + "\"detail\": \"La apuesta supera tu limite diario disponible.\"}", true));
        jdbc.update("delete from responsible_dailylimit");
        start("10").andExpect(jsonPath("$.code").value("daily_limit_required"));
        dailyLimit(player, 1000);
        jdbc.update("update wallets set balance = 9, bonus_balance = 9 where user_id = ?", player.id());
        start("10").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"code\": \"insufficient_balance\", \"detail\": \"Saldo insuficiente.\"}", true));
        assertNothingHappened();
        jdbc.update("update users set self_excluded_until = now() + interval '1 day' where id = ?", player.id());
        start("10").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("access_not_allowed"));
        assertNothingHappened();
    }

    /** Django rechaza también el reintento; aquí, como en la ruleta, devuelve la mano ya pagada. */
    @Test
    void aPauseBlocksNewHandsButNotRetriesNorTheCurrentHand() throws Exception {
        deck.deal("9_S 7_S", "10_H 8_H", "2_C");
        String reference = UUID.randomUUID().toString();
        JsonNode hand = json(start("10", reference).andExpect(status().isCreated()).andReturn());
        jdbc.update("update users set pause_until = now() + interval '5 minutes' where id = ?", player.id());
        assertThat(json(start("10", reference).andExpect(status().isOk()).andReturn())).isEqualTo(hand);
        act(hand.at("/hand/id"), "stand", 0).andExpect(status().isOk()).andExpect(jsonPath("$.hand.outcome").value("LOST"));
        start("10").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"code\": \"pause_active\", \"detail\": \"Tu pausa de juego sigue activa.\"}", true));
        assertThat(count("casino_play")).isEqualTo(1);
    }

    @Test
    void fourTriplingLossesCreateOneRiskAlert() throws Exception {
        deck.deal("9_S 8_S", "A_H K_H");
        for (String amount : List.of("10", "30", "90", "270")) {
            assertThat(count("responsible_riskincident")).isZero();
            started(amount);
        }
        Map<String, Object> incident = jdbc.queryForMap("select *, evidence::text as rows from responsible_riskincident");
        List<Long> plays = jdbc.queryForList("select id from casino_play order by id", Long.class);
        assertThat(incident).containsEntry("user_id", player.id()).containsEntry("play_id", plays.getLast())
                .containsEntry("round_id", null).containsEntry("kind", "LOSS_ESCALATION")
                .containsEntry("pause_started_at", null);
        assertThat(mapper.readTree(incident.get("rows").toString())).isEqualTo(mapper.readTree("""
                [{"play_id": %d, "amount": "10.00", "paid": "0.00"}, {"play_id": %d, "amount": "30.00", "paid": "0.00"},
                 {"play_id": %d, "amount": "90.00", "paid": "0.00"}, {"play_id": %d, "amount": "270.00", "paid": "0.00"}]"""
                .formatted(plays.toArray())));
        mvc.perform(get("/api/v1/responsible-gaming/risk-alerts").with(player.bearer()))
                .andExpect(jsonPath("$.results[0].incident_id").value(incident.get("id")));
    }

    @ParameterizedTest
    @CsvSource({"10 20 30 40, LOST", "10 30 90 269, LOST", "30 90 270, LOST", "10 30 90 270, PUSH"})
    void otherPatternsDoNotAlert(String amounts, String last) throws Exception {
        String[] values = amounts.split(" ");
        for (int index = 0; index < values.length; index++) {
            boolean push = index == values.length - 1 && last.equals("PUSH");
            deck.deal(push ? "A_S K_S" : "9_S 8_S", "A_H K_H");
            started(values[index]);
        }
        assertThat(count("responsible_riskincident")).isZero();
    }

    @Test
    void handsAppearInTheHistoryTheStatisticsAndTheSession() throws Exception {
        deck.deal("A_S K_S", "9_H 8_H");
        started("10");
        mvc.perform(get("/api/v1/users/bets-history").with(player.bearer()))
                .andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.results[0].game").value("Blackjack"))
                .andExpect(jsonPath("$.results[0].net").value("15.00"));
        mvc.perform(get("/api/v1/stats/summary").with(player.bearer())).andExpect(jsonPath("$.net").value("15.00"));
        mvc.perform(get("/api/v1/responsible-gaming/session-summary").with(player.bearer()))
                .andExpect(jsonPath("$.total_paid").value("25.00"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ''             | A valid number is required.
            x              | A valid number is required.
            NaN            | A valid number is required.
            Infinity       | A valid number is required.
            9.99           | Ensure this value is greater than or equal to 10.
            -5             | Ensure this value is greater than or equal to 10.
            500.01         | Ensure this value is less than or equal to 500.
            1e3            | Ensure this value is less than or equal to 500.
            10.001         | Ensure that there are no more than 2 decimal places.
            0.0001         | Ensure that there are no more than 2 decimal places.
            12345678901.00 | Ensure that there are no more than 12 digits in total.
            12345678901    | Ensure that there are no more than 10 digits before the decimal point.
            """)
    void theAmountIsValidated(String amount, String message) throws Exception {
        start(amount).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of("amount", List.of(message))), true));
        assertNothingHappened();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {} | {"amount": ["This field is required."], "reference": ["This field is required."]}
            {"amount": null, "reference": null} | {"amount": ["This field may not be null."], "reference": ["This field may not be null."]}
            {"amount": true, "reference": "x"} | {"amount": ["A valid number is required."], "reference": ["Must be a valid UUID."]}
            {"amount": [10], "reference": 5} | {"amount": ["A valid number is required."]}
            [1] | {"non_field_errors": ["Invalid data. Expected a dictionary, but got list."]}
            """)
    void theBetIsValidated(String body, String expected) throws Exception {
        send(player, PATH, body).andExpect(status().isBadRequest()).andExpect(content().json(expected, true));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {} | {"action": ["This field is required."], "revision": ["This field is required."]}
            {"action": "x", "revision": -1} | {"action": ["\\"x\\" is not a valid choice."], "revision": ["Ensure this value is greater than or equal to 0."]}
            {"action": "", "revision": "1.5"} | {"action": ["\\"\\" is not a valid choice."], "revision": ["A valid integer is required."]}
            {"action": null, "revision": null} | {"action": ["This field may not be null."], "revision": ["This field may not be null."]}
            """)
    void theActionIsValidatedBeforeLookingForTheHand(String body, String expected) throws Exception {
        send(player, PATH + "/999999999/action", body).andExpect(status().isBadRequest())
                .andExpect(content().json(expected, true));
    }

    @Test
    void unknownHandsAndOtherRequestsAreRejected() throws Exception {
        for (String id : List.of("0", "999999999", "99999999999999999999999")) {
            act(id, "hit", 0).andExpect(status().isNotFound()).andExpect(content().json(NO_HAND, true));
        }
        act("abc", "hit", 0).andExpect(status().isNotFound());
        mvc.perform(get(PATH + "/1/action").with(player.bearer())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        browser.withoutCsrf().create(PATH, Map.of("amount", 10, "reference", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        assertNothingHappened();
    }

    @Test
    void simultaneousRetriesChargeOnce() throws Exception {
        String reference = UUID.randomUUID().toString();
        Callable<Integer> retry = () -> start("10", reference).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, retry)).stream().map(future -> future.resultNow()))
                    .containsExactlyInAnyOrder(201, 200, 200, 200);
        }
        assertThat(count("casino_play")).isEqualTo(1);
        assertThat(spent()).isEqualTo("10.00");
    }

    @Test
    void simultaneousActionsPayOnce() throws Exception {
        deck.deal("10_S 9_S", "10_H 8_H");
        JsonNode hand = started("10");
        Callable<Integer> stand = () -> act(hand.get("id"), "stand", 0).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, stand)).stream().map(future -> future.resultNow()))
                    .containsOnly(200);
        }
        assertThat(balance()).isEqualTo("1010.00");
        assertThat(jdbc.queryForObject("select revision from casino_blackjackhand", Integer.class)).isEqualTo(1);
    }
}
