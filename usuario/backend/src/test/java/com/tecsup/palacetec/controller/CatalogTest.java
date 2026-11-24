package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Usa juegos y salas propios (spring-test-...) además de los que siembran las migraciones de Django. */
class CatalogTest extends ApiTest {

    private JsonNode games(Player player, String query) throws Exception {
        return json(mvc.perform(get("/api/v1/games" + query).with(player.bearer()))
                .andExpect(status().isOk()).andReturn());
    }

    private static JsonNode byId(JsonNode list, long id) {
        return StreamSupport.stream(list.spliterator(), false)
                .filter(item -> item.get("id").asLong() == id).findFirst().orElse(null);
    }

    @Test
    void gameLimitsComeFromItsActiveRooms() throws Exception {
        Player player = newPlayer();
        long game = newGame("ruleta");
        newRoom(game, 20, 300);
        newRoom(game, 10, 900);
        long inactive = newRoom(game, 1, 5000);
        jdbc.update("update rooms set is_active = false where id = ?", inactive);
        jdbc.update("update roulette_game set cover = 'games/covers/mi foto.png' where id = ?", game);
        jdbc.update("insert into favorite_games (user_id, game_id, created_at) values (?, ?, now())", player.id(), game);

        JsonNode item = byId(games(player, ""), game);
        assertThat(item.get("slug").asText()).isEqualTo("spring-test-ruleta");
        assertThat(item.get("category").asText()).isEqualTo("RULETA");
        assertThat(item.get("category_slug").asText()).isEqualTo("ruleta");
        assertThat(item.get("rtp").asText()).isEqualTo("97.30");
        assertThat(item.get("min_bet").asText()).isEqualTo("10.00");
        assertThat(item.get("max_bet").asText()).isEqualTo("900.00");
        assertThat(item.get("rooms_url").asText()).isEqualTo("/api/v1/rooms?game=" + game);
        assertThat(item.get("cover_url").asText())
                .isEqualTo("http://localhost:8000/media/games/covers/mi%20foto.png");
        assertThat(item.get("play_path").isNull()).isTrue();
        assertThat(item.get("is_favorite").asBoolean()).isTrue();
        assertThat(item).hasSize(12);
        assertThat(byId(games(newPlayer(), ""), game).get("is_favorite").asBoolean()).isFalse();
    }

    @Test
    void gamesWithoutRoomsNeedTheirOwnScreen() throws Exception {
        Player player = newPlayer();
        long withoutRooms = newGame("vacio");
        long withScreen = newGame("pantalla");
        jdbc.update("update roulette_game set play_kind = 'SPORTS', play_path = '/deportes', category = 'DEPORTES' "
                + "where id = ?", withScreen);
        JsonNode list = games(player, "");
        assertThat(byId(list, withoutRooms)).isNull();
        JsonNode item = byId(list, withScreen);
        assertThat(item.get("rooms_url").isNull()).isTrue();
        assertThat(item.get("play_path").asText()).isEqualTo("/deportes");
        assertThat(item.get("min_bet").asText()).isEqualTo("10.00");
        assertThat(item.get("max_bet").asText()).isEqualTo("500.00");
        assertThat(item.get("cover_url").isNull()).isTrue();
    }

    @Test
    void hidesUnpublishedAndDeletedGamesAndTheirRooms() throws Exception {
        Player player = newPlayer();
        long unpublished = newGame("oculto");
        long hiddenRoom = newRoom(unpublished, 10, 500);
        jdbc.update("update roulette_game set is_published = false where id = ?", unpublished);
        long deleted = newGame("borrado");
        long deletedRoom = newRoom(deleted, 10, 500);
        jdbc.update("update roulette_game set deleted_at = now() where id = ?", deleted);

        JsonNode list = games(player, "");
        assertThat(byId(list, unpublished)).isNull();
        assertThat(byId(list, deleted)).isNull();
        JsonNode rooms = json(mvc.perform(get("/api/v1/rooms").with(player.bearer())).andReturn());
        assertThat(byId(rooms, hiddenRoom)).isNull();
        assertThat(byId(rooms, deletedRoom)).isNull();
        mvc.perform(get("/api/v1/rooms/" + hiddenRoom + "/state").with(player.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersByCategoryAndAccentInsensitiveSearch() throws Exception {
        Player player = newPlayer();
        long game = newGame("acento");
        newRoom(game, 10, 500);
        jdbc.update("update roulette_game set title = 'Pequeña   Ruleta' where id = ?", game);
        assertThat(byId(games(player, "?search=PEQUENA ruleta"), game)).isNotNull();
        assertThat(byId(games(player, "?category=Ruleta&search=peque"), game)).isNotNull();
        assertThat(byId(games(player, "?category=cartas"), game)).isNull();
        assertThat(byId(games(player, "?search=poker"), game)).isNull();
        mvc.perform(get("/api/v1/games").param("category", "bingo").with(player.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.category[0]").isNotEmpty());
        mvc.perform(get("/api/v1/games").param("search", "x".repeat(81)).with(player.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.search[0]").isNotEmpty());
    }

    @Test
    void roomsListLimitsAndDistinctConnectedPlayers() throws Exception {
        Player player = newPlayer();
        Player other = newPlayer();
        long game = newGame("salas");
        long room = newRoom(game, 50, 2000);
        connect(room, player, "a", "1 minute");
        connect(room, player, "b", "1 minute");
        connect(room, other, "c", "1 minute");
        connect(room, newPlayer(), "vencida", "-1 minute");

        mvc.perform(get("/api/v1/rooms").param("game", String.valueOf(game)).with(player.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(room)).andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].min_bet").value("50.00")).andExpect(jsonPath("$[0].max_bet").value("2000.00"))
                .andExpect(jsonPath("$[0].players").value(2)).andExpect(jsonPath("$[0].length()").value(5));
        for (String invalid : new String[]{"0", "abc", ""}) {
            mvc.perform(get("/api/v1/rooms").param("game", invalid).with(player.bearer()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0]").isNotEmpty());
        }
    }

    @Test
    void stateShowsTheOpenRoundAndTheLastTenResults() throws Exception {
        Player player = newPlayer();
        long room = newRoom(newGame("estado"), 10, 500);
        mvc.perform(get("/api/v1/rooms/" + room + "/state").with(player.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(nullValue())).andExpect(jsonPath("$.recent_results").isEmpty());

        long lastSettled = 0;
        for (int number = 0; number <= 11; number++) {
            lastSettled = newSettledRound(room, number);
        }
        long open = newOpenRound(room, 12);
        JsonNode state = json(mvc.perform(get("/api/v1/rooms/" + room + "/state").with(player.bearer()))
                .andExpect(status().isOk()).andReturn());
        assertThat(state).hasSize(8);
        assertThat(state.get("room_id").asLong()).isEqualTo(room);
        assertThat(state.get("server_time").asText()).endsWith("+00:00");
        assertThat(state.get("min_bet").asText()).isEqualTo("10.00");
        assertThat(state.get("chip_values").toString()).isEqualTo("[10,50,100,500]");
        JsonNode round = state.get("round");
        assertThat(round.get("id").asLong()).isEqualTo(open);
        assertThat(round.get("status").asText()).isEqualTo("OPEN");
        assertThat(round.get("remaining_seconds").asInt()).isBetween(10, 12);
        assertThat(round.get("winning_number").isNull()).isTrue();
        assertThat(round.get("closes_at").asText()).endsWith("+00:00");
        JsonNode results = state.get("recent_results");
        assertThat(results).hasSize(10);
        assertThat(results.get(0).get("round_id").asLong()).isEqualTo(lastSettled);
        assertThat(results.get(0).get("number").asInt()).isEqualTo(11);
        assertThat(results.get(0).get("color").asText()).isEqualTo("negro");
        assertThat(results.get(9).get("number").asInt()).isEqualTo(2);
        assertThat(state.get("numbers")).hasSize(37);
        assertThat(state.get("numbers").get(0).toString()).isEqualTo("{\"number\":0,\"color\":\"verde\"}");
        assertThat(StreamSupport.stream(state.get("numbers").spliterator(), false)
                .filter(item -> item.get("color").asText().equals("rojo"))).hasSize(18);
    }

    @Test
    void stateNeverChangesTheRound() throws Exception {
        Player player = newPlayer();
        long room = newRoom(newGame("intacta"), 10, 500);
        long round = newOpenRound(room, -5);
        mvc.perform(get("/api/v1/rooms/" + room + "/state").with(player.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.round.status").value("OPEN"))
                .andExpect(jsonPath("$.round.remaining_seconds").value(0));
        assertThat(jdbc.queryForObject("select status from game_rounds where id = ?", String.class, round))
                .isEqualTo("OPEN");
        assertThat(count("game_rounds")).isEqualTo(1);
        mvc.perform(get("/api/v1/rooms/999999999/state").with(player.bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/rooms/abc/state").with(player.bearer())).andExpect(status().isNotFound());
    }

    private void connect(long room, Player player, String channel, String expiresIn) {
        jdbc.update("insert into room_connections (room_id, user_id, channel_name, expires_at) "
                + "values (?, ?, ?, now() + cast(? as interval))", room, player.id(), channel, expiresIn);
    }
}
