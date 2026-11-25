package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.config.TimeConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** HU40, HU51, HU52, HU53 y HU67 de Django. */
class StatsTest extends ApiTest {

    private Player player;
    private long game;
    private long room;

    @BeforeEach
    void table() {
        player = newPlayer();
        game = newGame("estadisticas");
        room = newRoom(game, 10, 500);
    }

    private ResultActions read(String path) throws Exception {
        return mvc.perform(get("/api/v1/stats/" + path).with(player.bearer()));
    }

    private JsonNode body(String path) throws Exception {
        return json(read(path).andExpect(status().isOk()).andReturn());
    }

    private void expect(String path, String expected) throws Exception {
        read(path).andExpect(status().isOk()).andExpect(content().json(expected, true));
    }

    /** Apuesta en su propia ronda, ya liquidada. */
    private long bet(Player who, String amount, String payout, String createdAt) {
        return newBet(who, newSettledRound(room, 5), amount, payout, createdAt);
    }

    private long bet(String amount, String payout) {
        return bet(player, amount, payout, "now");
    }

    private void movement(Player who, String balance, String createdAt) {
        jdbc.update("insert into wallet_transactions (wallet_id, type, amount, balance_after, created_at) "
                + "select id, 'WIN', 10, cast(? as numeric), cast(? as timestamptz) from wallets where user_id = ?",
                balance, createdAt, who.id());
    }

    private static LocalDate today() {
        return LocalDate.now(TimeConfig.LIMA);
    }

    private void movement(String balance, int daysAgo, String time) {
        movement(player, balance, today().minusDays(daysAgo) + "T" + time + "-05:00");
    }

    // HU40

    @Test
    void summaryAddsSettledRouletteAndCasino() throws Exception {
        String empty = "{\"total_wagered\": \"0.00\", \"total_paid\": \"0.00\", \"net\": \"0.00\", "
                + "\"scope\": \"settled_bets\", \"timezone\": \"America/Lima\"}";
        expect("summary", empty);
        newBet(player, newOpenRound(room, 60), "100", null, "now");
        newPlay(player, "SPORTS", "40", null, "now");
        bet(newPlayer(), "500", "0", "now");
        expect("summary", empty);
        bet("10", "20");
        bet("50", "0");
        newPlay(player, "BLACKJACK", "30", "75", "now");
        expect("summary", "{\"total_wagered\": \"90.00\", \"total_paid\": \"95.00\", \"net\": \"5.00\", "
                + "\"scope\": \"settled_bets\", \"timezone\": \"America/Lima\"}");
    }

    @Test
    void summaryIgnoresPaidBetsOfRoundsNotSettledYet() throws Exception {
        newBet(player, newOpenRound(room, 60), "10", "20", "now");
        read("summary").andExpect(jsonPath("$.total_wagered").value("0.00"));
    }

    @Test
    void datesAreWholeDaysInLima() throws Exception {
        bet(player, "10", "0", "2026-11-20T04:59:59Z"); // 19 de noviembre en Lima
        bet(player, "20", "40", "2026-11-20T05:00:00Z");
        newPlay(player, "SPORTS", "30", "0", "2026-11-21T04:59:59Z"); // 20, último segundo
        bet(player, "40", "0", "2026-11-21T05:00:00Z");
        String day = "?startDate=2026-11-20&endDate=2026-11-20";
        read("summary" + day).andExpect(jsonPath("$.total_wagered").value("50.00"));
        read("win-rate" + day).andExpect(jsonPath("$.won").value(1)).andExpect(jsonPath("$.lost").value(1));
        read("top-games" + day).andExpect(jsonPath("$.total_rounds").value(2));
        read("summary?startDate=&endDate=").andExpect(jsonPath("$.total_wagered").value("100.00"));
        // Estas dos no filtran por fecha, ni la validan.
        read("biggest-win?startDate=x").andExpect(jsonPath("$.record.net").value("20.00"));
        read("balance-history?startDate=x").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "win-rate", "top-games"})
    void datesAreValidated(String path) throws Exception {
        String badDate = "[\"Date has wrong format. Use one of these formats instead: YYYY-MM-DD.\"]";
        read(path + "?startDate=x&endDate=2026-02-30").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"startDate\": " + badDate + ", \"endDate\": " + badDate + "}", true));
        read(path + "?startDate=2026-11-10&endDate=2026-11-01").andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"endDate\": [\"La fecha final debe ser igual o posterior al inicio.\"]}", true));
        read(path + "?endDate=9999-12-31").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"endDate\": [\"La fecha final excede el rango permitido.\"]}", true));
    }

    // HU51

    @Test
    void winRateCountsEachBetWithResult() throws Exception {
        expect("win-rate", "{\"won\": 0, \"lost\": 0, \"push\": 0, \"total\": 0, \"win_rate\": 0.0, "
                + "\"lost_rate\": 0.0, \"scope\": \"settled_bets\", \"timezone\": \"America/Lima\"}");
        bet("10", "360");
        bet("10", "0");
        bet("20", "0");
        bet("50", "100");
        newBet(player, newOpenRound(room, 60), "10", null, "now");
        bet(newPlayer(), "10", "360", "now");
        expect("win-rate", "{\"won\": 2, \"lost\": 2, \"push\": 0, \"total\": 4, \"win_rate\": 50.0, "
                + "\"lost_rate\": 50.0, \"scope\": \"settled_bets\", \"timezone\": \"America/Lima\"}");
        newPlay(player, "BLACKJACK", "10", "10", "now");
        newPlay(player, "SPORTS", "10", "19", "now");
        newPlay(player, "SPORTS", "10", null, "now");
        JsonNode body = body("win-rate");
        assertThat(body.get("push").asInt()).isEqualTo(1);
        assertThat(body.get("total").asInt()).isEqualTo(6);
        assertThat(body.get("win_rate").toString()).isEqualTo("50.0");
        assertThat(body.get("lost_rate").toString()).isEqualTo("33.33");
    }

    /** Como en Django, dos apuestas en la misma ronda cuentan dos veces. */
    @Test
    void betsOfTheSameRoundAreCountedSeparately() throws Exception {
        long round = newSettledRound(room, 5);
        newBet(player, round, "500", "1000", "now");
        newBet(player, round, "500", "0", "now");
        read("win-rate").andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.win_rate").value(50.0));
        read("biggest-win").andExpect(jsonPath("$.record.net").value("500.00"));
        read("top-games").andExpect(jsonPath("$.total_rounds").value(1));
    }

    @ParameterizedTest
    @CsvSource({"1, 2, 33.33, 66.67", "2, 1, 66.67, 33.33", "1, 7, 12.5, 87.5", "1, 799, 0.12, 99.88",
            "3, 797, 0.38, 99.62"})
    void ratesAreRoundedLikePython(int won, int lost, double winRate, double lostRate) throws Exception {
        long round = newSettledRound(room, 5);
        jdbc.update("insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, "
                + "created_at) select ?, ?, 'COLOR', 'rojo', '[10]', 10, case when n <= ? then 20 else 0 end, "
                + "gen_random_uuid(), now() from generate_series(1, ?) n", player.id(), round, won, won + lost);
        read("win-rate").andExpect(jsonPath("$.win_rate").value(winRate))
                .andExpect(jsonPath("$.lost_rate").value(lostRate));
    }

    // HU52

    @Test
    void biggestWinIsEmptyWithoutWins() throws Exception {
        bet("10", "0");
        newPlay(player, "BLACKJACK", "10", "10", "now");
        expect("biggest-win", "{\"record\": null}");
    }

    @Test
    void biggestWinIsTheLargestNetProfit() throws Exception {
        bet(player, "500", "600", "2026-11-20T10:00:00Z");
        bet(newPlayer(), "10", "5000", "now");
        long best = bet(player, "10", "360", "2026-11-20T15:00:00.5Z");
        long round = jdbc.queryForObject("select round_id from user_bets where id = ?", Long.class, best);
        newPlay(player, "SPORTS", "10", "24", "now");
        expect("biggest-win", """
                {"record": {"net": "350.00", "payout": "360.00", "amount": "10.00", "source": "roulette",
                 "game": "Juego estadisticas", "round_id": %d, "bet_id": %d,
                 "created_at": "2026-11-20T15:00:00.500000+00:00"}}""".formatted(round, best));
        long play = newPlay(player, "SPORTS", "300", "960", "2026-11-21T15:00:00Z");
        expect("biggest-win", """
                {"record": {"net": "660.00", "payout": "960.00", "amount": "300.00", "source": "casino",
                 "game": "Deportes simulados", "round_id": null, "bet_id": %d,
                 "created_at": "2026-11-21T15:00:00+00:00"}}""".formatted(play));
    }

    @ParameterizedTest
    @CsvSource({"10, 11, roulette", "11, 10, casino", "10, 10, roulette"})
    void tiesAreWonByTheOldest(int betHour, int playHour, String source) throws Exception {
        bet(player, "10", "20", "2026-11-20T" + (betHour + 2) + ":00:00Z");
        bet(player, "10", "20", "2026-11-20T" + betHour + ":00:00Z");
        newPlay(player, "BLACKJACK", "10", "20", "2026-11-20T" + playHour + ":00:00Z");
        read("biggest-win").andExpect(jsonPath("$.record.source").value(source))
                .andExpect(jsonPath("$.record.created_at").value("2026-11-20T10:00:00+00:00"));
    }

    // HU53

    @Test
    void balanceHistoryCarriesTheBalanceOfDaysWithoutMovements() throws Exception {
        movement("1000", 10, "12:00:00");
        movement("950", 5, "00:00:00");
        movement("900", 5, "23:59:59.999999");
        movement("1250", 2, "12:00:00");
        movement("1200", 0, "00:00:00");
        movement(newPlayer(), "9999", "now");
        JsonNode body = body("balance-history?period=7");
        assertThat(body).hasSize(5);
        assertThat(body.get("period").asInt()).isEqualTo(7);
        assertThat(body.get("opening_balance").asText()).isEqualTo("1000.00");
        assertThat(body.get("closing_balance").asText()).isEqualTo("1200.00");
        assertThat(body.get("timezone").asText()).isEqualTo("America/Lima");
        JsonNode points = body.get("points");
        assertThat(points.findValuesAsText("balance")).containsExactly("1000.00", "900.00", "900.00", "900.00",
                "1250.00", "1250.00", "1200.00");
        assertThat(points.findValuesAsText("movements")).containsExactly("0", "2", "0", "0", "1", "0", "1");
        assertThat(points.get(0).get("date").asText()).isEqualTo(today().minusDays(6).toString());
        assertThat(points.get(6).get("date").asText()).isEqualTo(today().toString());
    }

    @Test
    void thirtyDaysStartWithTheBalanceOfTheDayBefore() throws Exception {
        movement("1000", 40, "12:00:00");
        movement("800", 20, "12:00:00");
        JsonNode body = body("balance-history?period=30");
        assertThat(body.get("points")).hasSize(30);
        assertThat(body.get("opening_balance").asText()).isEqualTo("1000.00");
        assertThat(body.get("points").get(0).get("balance").asText()).isEqualTo("1000.00");
        assertThat(body.get("closing_balance").asText()).isEqualTo("800.00");
    }

    @Test
    void withoutMovementsTheBalanceStartsAtZero() throws Exception {
        for (String path : List.of("balance-history", "balance-history?period=")) {
            JsonNode body = body(path);
            assertThat(body.get("opening_balance").asText()).isEqualTo("0.00");
            assertThat(body.get("points").findValuesAsText("balance")).hasSize(7).containsOnly("0.00");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"15", "07", "x"})
    void onlySevenOrThirtyDays(String period) throws Exception {
        read("balance-history?period=" + period).andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"period\": [\"\\\"" + period + "\\\" is not a valid choice.\"]}", true));
    }

    // HU67

    @Test
    void topGamesAreOrderedByRoundsAndThenByCredits() throws Exception {
        expect("top-games", "{\"results\": [], \"total_rounds\": 0, \"timezone\": \"America/Lima\"}");
        long round = newSettledRound(room, 5);
        newBet(player, round, "10", "0", "now");
        newBet(player, round, "100", null, "now");
        for (int play = 0; play < 3; play++) {
            newPlay(player, "BLACKJACK", "10", "0", "now");
        }
        newPlay(player, "SPORTS", "200", null, "now");
        newPlay(newPlayer(), "SPORTS", "200", null, "now");
        expect("top-games", """
                {"results": [
                  {"game_id": %d, "title": "Blackjack 21", "category": "CARTAS", "rounds": 3, "wagered": "30.00"},
                  {"game_id": %d, "title": "Deportes virtuales", "category": "DEPORTES", "rounds": 1, "wagered": "200.00"},
                  {"game_id": %d, "title": "Juego estadisticas", "category": "RULETA", "rounds": 1, "wagered": "110.00"}],
                 "total_rounds": 5, "timezone": "America/Lima"}"""
                .formatted(seededGame("blackjack"), seededGame("deportes-virtuales"), game));
    }

    @Test
    void topGamesAreFiveAndRetiredGamesStillCount() throws Exception {
        for (int index = 0; index < 5; index++) {
            long other = newGame("top-" + index);
            long otherRoom = newRoom(other, 10, 500);
            for (int round = 0; round <= index; round++) {
                newBet(player, newSettledRound(otherRoom, 5), "10", "0", "now");
            }
            jdbc.update("update roulette_game set is_published = false, deleted_at = now() where id = ?", other);
        }
        newPlay(player, "BLACKJACK", "10", "0", "now");
        JsonNode body = body("top-games");
        assertThat(body.get("results")).hasSize(5);
        assertThat(body.get("results").get(0).get("title").asText()).isEqualTo("Juego top-4");
        assertThat(body.get("total_rounds").asInt()).isEqualTo(16);
        // Con una ronda y los mismos créditos, primero el juego de menor id.
        assertThat(body.get("results").get(4).get("title").asText()).isEqualTo("Blackjack 21");
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "win-rate", "biggest-win", "balance-history", "top-games"})
    void requiresASession(String path) throws Exception {
        mvc.perform(get("/api/v1/stats/" + path)).andExpect(status().isUnauthorized());
    }
}
