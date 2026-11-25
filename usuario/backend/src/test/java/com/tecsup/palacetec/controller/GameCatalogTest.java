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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** HU60 a HU64 de Django: reglas, categorías, búsqueda, favoritos y destacados. */
class GameCatalogTest extends ApiTest {

    private static final String GAMES = "/api/v1/games";
    private static final String NO_GAME = "{\"detail\": \"No Game matches the given query.\"}";

    private Player player;

    @BeforeEach
    void player() {
        player = newPlayer();
    }

    private ResultActions read(String path) throws Exception {
        return mvc.perform(get(GAMES + path).with(player.bearer()));
    }

    private JsonNode body(String path) throws Exception {
        return json(read(path).andExpect(status().isOk()).andReturn());
    }

    private ResultActions toggle(Player who, Object game) throws Exception {
        return mvc.perform(post(GAMES + "/" + game + "/favorite").with(who.bearer()));
    }

    private void toggle(long game, boolean expected) throws Exception {
        toggle(player, game).andExpect(status().isOk()).andExpect(content().json(
                "{\"game_id\": %d, \"is_favorite\": %s}".formatted(game, expected), true));
    }

    private Map<String, Integer> counts(JsonNode body) {
        Map<String, Integer> counts = new HashMap<>();
        body.get("categories").forEach(item -> counts.put(item.get("slug").asText(), item.get("count").asInt()));
        return counts;
    }

    private long gameWithRoom(String name) {
        long game = newGame(name);
        newRoom(game, 10, 500);
        return game;
    }

    private List<String> slugs(JsonNode list) {
        return list.findValuesAsText("slug");
    }

    @Test
    void categoriesCountOnlyAvailableGamesIncludingEmptyCategories() throws Exception {
        JsonNode before = body("/categories");
        assertThat(before).hasSize(3);
        assertThat(before.get("categories").findValuesAsText("slug"))
                .containsExactly("ruleta", "cartas", "deportes", "otros");
        assertThat(before.get("categories").findValuesAsText("label"))
                .containsExactly("Ruleta", "Cartas", "Deportes virtuales", "Otros");
        gameWithRoom("uno");
        newGame("sin-salas");
        long hidden = gameWithRoom("oculto");
        jdbc.update("update roulette_game set is_published = false where id = ?", hidden);
        JsonNode after = body("/categories");
        assertThat(after.get("total").asInt()).isEqualTo(before.get("total").asInt() + 1);
        Map<String, Integer> expected = counts(before);
        expected.merge("ruleta", 1, Integer::sum);
        assertThat(counts(after)).isEqualTo(expected);
        assertThat(after.get("favorites").asInt()).isZero();
    }

    @Test
    void categoryCountsFollowTheSearchButNotTheCategory() throws Exception {
        long game = gameWithRoom("pequena");
        jdbc.update("update roulette_game set title = 'Ruleta Pequeña Mágica' where id = ?", game);
        toggle(game, true);
        for (String query : List.of("?search=PEQUENA  mag", "?search=ña mág&category=cartas")) {
            JsonNode found = body("/categories" + query);
            assertThat(found.get("total").asInt()).isEqualTo(1);
            assertThat(counts(found)).containsEntry("ruleta", 1).containsEntry("cartas", 0);
            assertThat(found.get("favorites").asInt()).isEqualTo(1);
        }
        assertThat(body("/categories?search=tragamonedas").get("total").asInt()).isZero();
        assertThat(body("/categories?category=&search=")).isEqualTo(body("/categories"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categories", "/favorites"})
    void filtersAreValidatedWithTheMessagesOfDjango(String path) throws Exception {
        read(path + "?category=bingo").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"category\": [\"Categoría no válida. Usa ruleta, cartas, deportes u otros.\"]}", true));
        read(path + "?category=" + "y".repeat(21) + "&search=" + "z".repeat(81)).andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"category": ["Ensure this field has no more than 20 characters."],
                         "search": ["Ensure this field has no more than 80 characters."]}""", true));
        read(path + "?category= RULETA ").andExpect(status().isOk());
    }

    @Test
    void favoritesAreListedFromNewestToOldestWithTheirFlag() throws Exception {
        long first = gameWithRoom("primero");
        long second = gameWithRoom("segundo");
        toggle(first, true);
        toggle(second, true);
        JsonNode list = body("/favorites");
        assertThat(slugs(list)).containsExactly("spring-test-segundo", "spring-test-primero");
        assertThat(list.get(0)).hasSize(12);
        assertThat(list.get(0).get("is_favorite").asBoolean()).isTrue();
        assertThat(slugs(body("/favorites?search=juego PRIMERO"))).containsExactly("spring-test-primero");
        assertThat(slugs(body("/favorites?category=cartas"))).hasSize(2);
        assertThat(body("/categories").get("favorites").asInt()).isEqualTo(2);
        toggle(second, false);
        assertThat(slugs(body("/favorites"))).containsExactly("spring-test-primero");
    }

    @Test
    void favoritesArePrivate() throws Exception {
        Player other = newPlayer();
        long game = gameWithRoom("ajeno");
        toggle(other, game).andExpect(status().isOk());
        assertThat(body("/favorites")).isEmpty();
        toggle(game, true);
        toggle(game, false);
        assertThat(jdbc.queryForList("select user_id from favorite_games", Long.class)).containsExactly(other.id());
    }

    @Test
    void anUnavailableGameCanBeRemovedFromFavoritesButNotAdded() throws Exception {
        long game = gameWithRoom("retirado");
        toggle(game, true);
        jdbc.update("update roulette_game set is_published = false where id = ?", game);
        assertThat(body("/favorites")).isEmpty();
        toggle(game, false);
        toggle(player, game).andExpect(status().isNotFound()).andExpect(content().json(NO_GAME, true));
        assertThat(count("favorite_games")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "999999999", "9223372036854775808", "99999999999999999999999"})
    void unknownGamesAreNotFound(String id) throws Exception {
        toggle(player, id).andExpect(status().isNotFound()).andExpect(content().json(NO_GAME, true));
        read("/" + id + "/rules").andExpect(status().isNotFound()).andExpect(content().json(NO_GAME, true));
    }

    @Test
    void otherPathsAndMethodsAreRejected() throws Exception {
        read("/abc/rules").andExpect(status().isNotFound());
        toggle(player, "-1").andExpect(status().isNotFound());
        read("/1/favorite").andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("Method \"GET\" not allowed."));
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        browser.withoutCsrf().create(GAMES + "/" + gameWithRoom("csrf") + "/favorite", Map.of())
                .andExpect(status().isForbidden());
        assertThat(count("favorite_games")).isZero();
    }

    @Test
    void simultaneousTogglesNeverDuplicateTheFavorite() throws Exception {
        long game = gameWithRoom("doble-clic");
        Callable<Integer> click = () -> toggle(player, game).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, click)).stream().map(future -> future.resultNow()))
                    .containsOnly(200);
        }
        assertThat(count("favorite_games")).isLessThanOrEqualTo(1);
    }

    @Test
    void featuredGamesAreAtMostFiveWithTheRoomToPlay() throws Exception {
        long room = 0;
        for (int index = 0; index < 6; index++) {
            long game = newGame("destacado-" + index);
            long first = newRoom(game, 10, 500);
            newRoom(game, 20, 900);
            room = index == 0 ? first : room;
            jdbc.update("update roulette_game set is_featured = true where id = ?", game);
        }
        JsonNode list = body("/featured");
        assertThat(slugs(list)).containsExactly("spring-test-destacado-0", "spring-test-destacado-1",
                "spring-test-destacado-2", "spring-test-destacado-3", "spring-test-destacado-4");
        assertThat(list.get(0)).hasSize(13);
        assertThat(list.get(0).get("play_room_id").asLong()).isEqualTo(room);
        assertThat(list.get(0).get("max_bet").asText()).isEqualTo("900.00");
        assertThat(list.get(0).get("is_favorite").asBoolean()).isFalse();
    }

    @Test
    void hiddenFeaturedGamesDisappearAndScreensHaveNoRoom() throws Exception {
        long hidden = gameWithRoom("escondido");
        long screen = newGame("pantalla");
        jdbc.update("update roulette_game set is_featured = true where id in (?, ?)", hidden, screen);
        jdbc.update("update roulette_game set is_published = false where id = ?", hidden);
        jdbc.update("update roulette_game set play_kind = 'X', play_path = '/pantalla' where id = ?", screen);
        JsonNode list = body("/featured");
        assertThat(slugs(list)).containsExactly("spring-test-pantalla");
        assertThat(list.get(0).get("play_room_id").isNull()).isTrue();
        assertThat(list.get(0).get("play_path").asText()).isEqualTo("/pantalla");
    }

    @Test
    void rulesShowThePayoutTableAndTheHouseEdge() throws Exception {
        JsonNode rules = body("/" + seededGame("ruleta-europea") + "/rules");
        assertThat(rules).hasSize(12);
        assertThat(rules.get("slug").asText()).isEqualTo("ruleta-europea");
        assertThat(rules.get("category").asText()).isEqualTo("RULETA");
        assertThat(rules.get("rtp").asText()).isEqualTo("97.30");
        assertThat(rules.get("house_edge").asText()).isEqualTo("2.70");
        assertThat(rules.get("rules").asText()).contains("37 casillas");
        JsonNode straight = rules.get("payout_table").get(0);
        assertThat(straight.get("bet").asText()).isEqualTo("Pleno");
        assertThat(straight.get("pays").asText()).isEqualTo("35 a 1");
        assertThat(rules.get("play_room_id").isNumber()).isTrue();
        assertThat(rules.get("play_path").isNull()).isTrue();
    }

    @Test
    void rulesUseTheLimitsOfTheGameAndPointToItsScreen() throws Exception {
        long game = newGame("reglas");
        newRoom(game, 50, 900);
        jdbc.update("update rooms set is_active = false where game_id = ?", game);
        jdbc.update("update roulette_game set play_path = '/x', rtp = 88.5, min_bet = 5 where id = ?", game);
        read("/" + game + "/rules").andExpect(status().isOk()).andExpect(content().json("""
                {"id": %d, "slug": "spring-test-reglas", "title": "Juego reglas", "category": "RULETA", "rtp": "88.50",
                 "house_edge": "11.50", "min_bet": "5.00", "max_bet": "500.00", "rules": "", "payout_table": [],
                 "play_room_id": null, "play_path": "/x"}""".formatted(game), true));
    }

    @ParameterizedTest
    @CsvSource({"is_published = false", "deleted_at = now()"})
    void hiddenGamesHaveNoRules(String change) throws Exception {
        long game = gameWithRoom("sin-reglas");
        jdbc.update("update roulette_game set " + change + " where id = ?", game);
        read("/" + game + "/rules").andExpect(status().isNotFound()).andExpect(content().json(NO_GAME, true));
    }

    @Test
    void everyRouteRequiresASession() throws Exception {
        for (String path : List.of("/categories", "/favorites", "/featured", "/recommendations", "/1/rules")) {
            mvc.perform(get(GAMES + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post(GAMES + "/1/favorite")).andExpect(status().isUnauthorized());
    }
}
