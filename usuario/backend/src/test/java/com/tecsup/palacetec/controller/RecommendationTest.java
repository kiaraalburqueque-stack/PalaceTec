package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** HU70 de Django. Usa los juegos sembrados por las migraciones y otros tres de ruleta. */
class RecommendationTest extends ApiTest {

    private Player player;
    private Player other;
    private long europeanRoom;
    private final long[] extra = new long[3];

    @BeforeEach
    void catalog() {
        player = newPlayer();
        other = newPlayer();
        europeanRoom = jdbc.queryForObject("select min(id) from rooms where is_active and game_id = ?", Long.class,
                seededGame("ruleta-europea"));
        for (int index = 0; index < extra.length; index++) {
            extra[index] = newGame("extra-" + index);
            newRoom(extra[index], 10, 500);
        }
    }

    private ResultActions read() throws Exception {
        return mvc.perform(get("/api/v1/games/recommendations").with(player.bearer())).andExpect(status().isOk());
    }

    private JsonNode body() throws Exception {
        return json(read().andReturn());
    }

    private void plays(Player who, String kind, int times) {
        for (int time = 0; time < times; time++) {
            newPlay(who, kind, "10", null, "now");
        }
    }

    private void bet(Player who, long room) {
        newBet(who, newSettledRound(room, 2), "10", "0", "now");
    }

    private static List<String> slugs(JsonNode body) {
        return StreamSupport.stream(body.get("results").spliterator(), false)
                .map(item -> item.get("slug").asText()).toList();
    }

    private void expectBlocked(String reason) throws Exception {
        read().andExpect(content().json(
                "{\"results\": [], \"blocked\": \"%s\", \"personalized\": false}".formatted(reason), true));
    }

    @Test
    void aNewPlayerGetsTheMostPopularGames() throws Exception {
        plays(other, "SPORTS", 3);
        bet(other, europeanRoom);
        JsonNode body = body();
        assertThat(body).hasSize(3);
        assertThat(body.get("blocked").isNull()).isTrue();
        assertThat(body.get("personalized").asBoolean()).isFalse();
        assertThat(slugs(body)).hasSize(4).startsWith("deportes-virtuales", "ruleta-europea");
        JsonNode first = body.get("results").get(0);
        assertThat(first).hasSize(16);
        assertThat(first.get("reason").asText()).isEqualTo("Popular en PalaceTec");
        assertThat(first.get("reason_code").asText()).isEqualTo("popular");
        assertThat(first.get("score").toString()).isEqualTo("1.0");
        assertThat(first.get("play_room_id").isNull()).isTrue();
        JsonNode second = body.get("results").get(1);
        assertThat(second.get("score").toString()).isEqualTo("0.333");
        assertThat(second.get("play_room_id").asLong()).isEqualTo(europeanRoom);
        // Sin actividad el puntaje es un entero, como en Python.
        assertThat(body.get("results").get(2).get("score").toString()).isEqualTo("0");
    }

    @Test
    void theMostPlayedCategoryComesFirst() throws Exception {
        plays(player, "BLACKJACK", 4);
        bet(player, europeanRoom);
        plays(other, "SPORTS", 10);
        JsonNode body = body();
        assertThat(body.get("personalized").asBoolean()).isTrue();
        JsonNode first = body.get("results").get(0);
        assertThat(first.get("slug").asText()).isEqualTo("blackjack");
        assertThat(first.get("reason").asText()).isEqualTo("Porque jugaste Blackjack 21");
        assertThat(first.get("reason_code").asText()).isEqualTo("category");
        assertThat(first.get("score").toString()).isEqualTo("2.8"); // 3 x 4/5 + 4/10
        assertThat(slugs(body)).contains("deportes-virtuales");
        assertThat(slugs(body).indexOf("ruleta-europea")).isLessThan(slugs(body).indexOf("spring-test-extra-0"));
    }

    @Test
    void favoritesAddPoints() throws Exception {
        jdbc.update("insert into favorite_games (user_id, game_id, created_at) values (?, ?, now())", player.id(),
                extra[2]);
        JsonNode body = body();
        assertThat(body.get("personalized").asBoolean()).isTrue();
        JsonNode first = body.get("results").get(0);
        assertThat(first.get("slug").asText()).isEqualTo("spring-test-extra-2");
        assertThat(first.get("reason").asText()).isEqualTo("Está en tus favoritos");
        assertThat(first.get("reason_code").asText()).isEqualTo("favorite");
        assertThat(first.get("is_favorite").asBoolean()).isTrue();
        assertThat(first.get("score").toString()).isEqualTo("2");
    }

    @Test
    void severalBetsInARoundAreOneRound() throws Exception {
        long round = newSettledRound(europeanRoom, 2);
        for (int bet = 0; bet < 5; bet++) {
            newBet(player, round, "10", "0", "now");
        }
        plays(player, "BLACKJACK", 2);
        JsonNode first = body().get("results").get(0);
        assertThat(first.get("slug").asText()).isEqualTo("blackjack");
        assertThat(first.get("score").toString()).isEqualTo("3.0"); // 3 x 2/3 + 2/2
    }

    @Test
    void roomsWithoutGameCountAsEuropeanRoulette() throws Exception {
        long room = newRoom(extra[0], 10, 500);
        jdbc.update("update rooms set game_id = null where id = ?", room);
        bet(other, room);
        assertThat(slugs(body())).startsWith("ruleta-europea");
    }

    @Test
    void retiredGamesAndOldActivityAreIgnored() throws Exception {
        jdbc.update("update roulette_game set is_published = false where id = ?", extra[1]);
        long hiddenRoom = jdbc.queryForObject("select id from rooms where game_id = ?", Long.class, extra[1]);
        bet(player, hiddenRoom);
        jdbc.update("insert into favorite_games (user_id, game_id, created_at) values (?, ?, now())", player.id(),
                extra[1]);
        newPlay(player, "SPORTS", "10", null, "2020-01-01");
        JsonNode body = body();
        assertThat(slugs(body)).doesNotContain("spring-test-extra-1").hasSize(4);
        assertThat(body.get("personalized").asBoolean()).isFalse();
    }

    @Test
    void aPausedPlayerGetsNoRecommendations() throws Exception {
        plays(player, "SPORTS", 1);
        jdbc.update("update users set pause_until = now() + interval '5 minutes' where id = ?", player.id());
        expectBlocked("pause_active");
        jdbc.update("update users set pause_until = now() - interval '1 second' where id = ?", player.id());
        assertThat(body().get("results")).hasSize(4);
    }

    @Test
    void theDailyLimitStopsRecommendations() throws Exception {
        dailyLimit(player, 50);
        jdbc.update("insert into responsible_dailyspending (user_id, date, amount) "
                + "values (?, (now() at time zone 'America/Lima')::date, 40)", player.id());
        assertThat(body().get("results")).hasSize(4);
        jdbc.update("update responsible_dailyspending set amount = 50");
        expectBlocked("daily_limit_reached");
        // Un aumento que ya cumplió sus 24 horas cuenta aunque no se haya guardado.
        jdbc.update("update responsible_dailylimit set pending_amount = 80, effective_at = now() - interval '1 second'");
        assertThat(body().get("results")).hasSize(4);
        jdbc.update("update responsible_dailylimit set effective_at = now() + interval '1 hour'");
        expectBlocked("daily_limit_reached");
        jdbc.update("delete from responsible_dailyspending");
        jdbc.update("update responsible_dailylimit set amount = 0, pending_amount = null, effective_at = null");
        expectBlocked("daily_limit_reached");
    }
}
