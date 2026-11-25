package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU57 y HU58 de Django. */
class NotificationTest extends ApiTest {

    private static final String LIST = "/api/v1/notifications";
    private static final String READ_ALL = "/api/v1/notifications/read-all";
    private static final String PREFERENCES = "/api/v1/users/notification-preferences";

    private long notification(Player player, String title, int minutesAgo) {
        return jdbc.queryForObject("""
                insert into notifications (user_id, category, title, message, created_at)
                values (?, 'GAME', ?, 'Mensaje', now() - make_interval(mins => ?)) returning id""",
                Long.class, player.id(), title, minutesAgo);
    }

    private ResultActions read(Player player, long id) throws Exception {
        return mvc.perform(post(LIST + "/" + id + "/read").with(player.bearer()));
    }

    private ResultActions update(Player player, String body) throws Exception {
        return mvc.perform(put(PREFERENCES).with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void listsOnlyTheOwnOnesFromNewestToOldest() throws Exception {
        Player other = newPlayer();
        notification(other, "Ajeno", 0);
        Player player = newPlayer();
        notification(player, "Antiguo", 10);
        long sameTime = notification(player, "Primero", 5);
        long last = notification(player, "Segundo", 5);
        jdbc.update("update notifications set created_at = '2026-11-20 10:00:00.5-05' where id in (?, ?)", sameTime,
                last);
        jdbc.update("update notifications set created_at = '2026-11-19 10:00:00-05', read_at = now(), "
                + "category = 'SECURITY' where title = 'Antiguo'");
        mvc.perform(get(LIST).with(player.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$.unread_count").value(2))
                .andExpect(jsonPath("$.results[*].title", contains("Segundo", "Primero", "Antiguo")))
                .andExpect(jsonPath("$.results[0].length()").value(6))
                .andExpect(jsonPath("$.results[0].id").value(last))
                .andExpect(jsonPath("$.results[0].category").value("GAME"))
                .andExpect(jsonPath("$.results[0].message").value("Mensaje"))
                .andExpect(jsonPath("$.results[0].is_read").value(false))
                .andExpect(jsonPath("$.results[0].created_at").value("2026-11-20T15:00:00.500000+00:00"))
                .andExpect(jsonPath("$.results[2].category").value("SECURITY"))
                .andExpect(jsonPath("$.results[2].is_read").value(true))
                .andExpect(jsonPath("$.results[2].created_at").value("2026-11-19T15:00:00+00:00"));
    }

    @Test
    void listsTheLatestThirtyAndCountsAll() throws Exception {
        Player player = newPlayer();
        for (int i = 0; i < 32; i++) {
            notification(player, "Aviso " + i, i);
        }
        mvc.perform(get(LIST).with(player.bearer())).andExpect(jsonPath("$.unread_count").value(32))
                .andExpect(jsonPath("$.results", hasSize(30)))
                .andExpect(jsonPath("$.results[29].title").value("Aviso 29"));
        mvc.perform(get(LIST).with(newPlayer().bearer())).andExpect(content().json(
                "{\"unread_count\": 0, \"results\": []}", true));
    }

    @Test
    void readingLowersTheCounterAndKeepsTheFirstDate() throws Exception {
        Player player = newPlayer();
        notification(player, "Otro", 1);
        long id = notification(player, "Aviso", 0);
        read(player, id).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.title").value("Aviso"))
                .andExpect(jsonPath("$.is_read").value(true)).andExpect(jsonPath("$.unread_count").value(1));
        jdbc.update("update notifications set read_at = now() - interval '1 day' where id = ?", id);
        read(player, id).andExpect(status().isOk()).andExpect(jsonPath("$.unread_count").value(1));
        assertThat(jdbc.queryForObject("select read_at < now() - interval '23 hours' from notifications where id = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void foreignOrMissingNotificationsAreNotFound() throws Exception {
        long foreign = notification(newPlayer(), "Ajeno", 0);
        Player player = newPlayer();
        for (long id : new long[] {foreign, 999_999_999L}) {
            read(player, id).andExpect(status().isNotFound()).andExpect(content().json(
                    "{\"detail\": \"No Notification matches the given query.\"}", true));
        }
        mvc.perform(post(LIST + "/abc/read").with(player.bearer())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from notifications where read_at is null", Long.class))
                .isEqualTo(1);
    }

    @Test
    void readAllOnlyTouchesTheOwnUnreadOnes() throws Exception {
        notification(newPlayer(), "Ajeno", 0);
        Player player = newPlayer();
        notification(player, "Uno", 0);
        notification(player, "Dos", 0);
        read(player, notification(player, "Leído", 0));
        mvc.perform(post(READ_ALL).with(player.bearer())).andExpect(status().isOk())
                .andExpect(content().json("{\"updated\": 2, \"unread_count\": 0}", true));
        mvc.perform(post(READ_ALL).with(player.bearer()))
                .andExpect(content().json("{\"updated\": 0, \"unread_count\": 0}", true));
        assertThat(jdbc.queryForObject("select count(*) from notifications where read_at is null", Long.class))
                .isEqualTo(1);
    }

    @Test
    void preferencesStartActiveAndChangeOneByOne() throws Exception {
        Player player = newPlayer();
        mvc.perform(get(PREFERENCES).with(player.bearer())).andExpect(status().isOk()).andExpect(content().json(
                "{\"promotions\": true, \"security\": true, \"game_alerts\": true}", true));
        update(player, "{\"promotions\": false}").andExpect(status().isOk()).andExpect(content().json(
                "{\"promotions\": false, \"security\": true, \"game_alerts\": true}", true));
        update(player, "{\"game_alerts\": \"off\", \"otro\": 1}").andExpect(content().json(
                "{\"promotions\": false, \"security\": true, \"game_alerts\": false}", true));
        update(player, "").andExpect(status().isOk()).andExpect(jsonPath("$.security").value(true));
        mvc.perform(get(PREFERENCES).with(newPlayer().bearer())).andExpect(jsonPath("$.promotions").value(true));
        assertThat(count("notification_settings")).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"promotions": "talvez"} | {"promotions": ["Must be a valid boolean."]}
            {"security": null}       | {"security": ["This field may not be null."]}
            []                       | {"non_field_errors": ["Invalid data. Expected a dictionary, but got list."]}
            """)
    void invalidPreferencesChangeNothing(String body, String errors) throws Exception {
        Player player = newPlayer();
        update(player, body).andExpect(status().isBadRequest()).andExpect(content().json(errors, true));
        mvc.perform(get(PREFERENCES).with(player.bearer())).andExpect(content().json(
                "{\"promotions\": true, \"security\": true, \"game_alerts\": true}", true));
    }

    @Test
    void simultaneousFirstReadsCreateOneRow() throws Exception {
        Player player = newPlayer();
        Callable<Integer> attempt = () -> mvc.perform(get(PREFERENCES).with(player.bearer())).andReturn()
                .getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(List.of(attempt, attempt, attempt, attempt)))
                    .allSatisfy(future -> assertThat(future.resultNow()).isEqualTo(200));
        }
        assertThat(count("notification_settings")).isEqualTo(1);
    }

    @Test
    void worksWithTheCookieSessionAndCsrf() throws Exception {
        Player player = newPlayer();
        long id = notification(player, "Aviso", 0);
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mvc.perform(get(PREFERENCES)).andExpect(status().isUnauthorized());
        Browser browser = browser();
        browser.login(player.email(), PASSWORD);
        browser.read(LIST).andExpect(status().isOk()).andExpect(jsonPath("$.unread_count").value(1));
        browser.create(LIST + "/" + id + "/read", Map.of()).andExpect(status().isOk());
        browser.create(READ_ALL, Map.of()).andExpect(status().isOk());
        browser.update(PREFERENCES, Map.of("security", false)).andExpect(status().isOk());
        browser.withoutCsrf();
        browser.create(LIST + "/" + id + "/read", Map.of()).andExpect(status().isForbidden());
        browser.create(READ_ALL, Map.of()).andExpect(status().isForbidden());
        browser.update(PREFERENCES, Map.of("security", true)).andExpect(status().isForbidden());
    }
}
