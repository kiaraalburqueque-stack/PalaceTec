package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU56 de Django: botón "No fui yo" del correo de acceso nuevo. */
class NotMeTest extends ApiTest {

    private static final String NOT_ME = "/api/v1/auth/not-me";
    private static final String DETAIL = "Bloqueamos tu cuenta temporalmente y cerramos todas las sesiones. "
            + "Restablece tu contraseña para volver a entrar.";
    private static final String INVALID_LINK =
            "{\"code\": \"invalid_token\", \"detail\": \"El enlace no es válido o venció.\"}";

    private Browser loggedIn(Player player, String ip) throws Exception {
        Browser browser = browser().from(ip);
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        return browser;
    }

    /** Token del correo que provoca un segundo acceso desde otra IP. */
    private String alertToken(Player player) throws Exception {
        loggedIn(player, "10.0.0.1");
        loggedIn(player, "200.48.1.9");
        return firstMatch(mailer.last().text(), "/seguridad/no-fui-yo/([\\w-]{32})\n");
    }

    private ResultActions notMe(String token) throws Exception {
        return browser().create(NOT_ME, Map.of("token", token));
    }

    private void expectInvalid(String token) throws Exception {
        notMe(token).andExpect(status().isBadRequest()).andExpect(content().json(INVALID_LINK, true));
    }

    private Instant lockedUntil(Player player) {
        Timestamp value = jdbc.queryForObject("select locked_until from users where id = ?", Timestamp.class,
                player.id());
        return value == null ? null : value.toInstant();
    }

    private int authVersion(Player player) {
        return jdbc.queryForObject("select auth_version from users where id = ?", Integer.class, player.id());
    }

    private long openSessions(Player player) {
        return jdbc.queryForObject("select count(*) from accounts_authsession where user_id = ? "
                + "and revoked_at is null", Long.class, player.id());
    }

    private long reported() {
        return jdbc.queryForObject("select count(*) from login_history where reported_at is not null", Long.class);
    }

    @Test
    void locksTheAccountForADayAndRevokesEverySession() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player, "10.0.0.1");
        String token = alertToken(player);
        Instant before = Instant.now();
        JsonNode body = json(notMe(token).andExpect(status().isOk()).andReturn());

        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get("detail").asText()).isEqualTo(DETAIL);
        assertThat(body.get("locked_until").asText()).isEqualTo(Formats.utc(lockedUntil(player))).endsWith("+00:00");
        assertThat(OffsetDateTime.parse(body.get("locked_until").asText()).toInstant())
                .isBetween(before.plus(24, ChronoUnit.HOURS).minusSeconds(1),
                        Instant.now().plus(24, ChronoUnit.HOURS));
        assertThat(openSessions(player)).isZero();
        assertThat(authVersion(player)).isEqualTo(1);
        assertThat(reported()).isEqualTo(1);

        browser.read("/api/v1/auth/me").andExpect(status().isUnauthorized());
        browser.create("/api/v1/auth/refresh", Map.of()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").with(player.bearer())).andExpect(status().isUnauthorized());
        browser().login(player.email(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Cuenta bloqueada temporalmente."));
    }

    @Test
    void otherPlayersKeepTheirSessions() throws Exception {
        Player player = newPlayer();
        Player other = newPlayer();
        notMe(alertToken(player)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").with(other.bearer())).andExpect(status().isOk());
        assertThat(lockedUntil(other)).isNull();
        assertThat(authVersion(other)).isZero();
    }

    @Test
    void inventedLinksAreRejectedAndChangeNothing() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        expectInvalid("falso");
        expectInvalid(token.substring(1));
        // El hash guardado en la base no sirve como token.
        expectInvalid(jdbc.queryForObject("select report_token_hash from login_history "
                + "where report_token_hash is not null", String.class));
        assertThat(lockedUntil(player)).isNull();
        assertThat(authVersion(player)).isZero();
        assertThat(reported()).isZero();
        mvc.perform(get("/api/v1/auth/me").with(player.bearer())).andExpect(status().isOk());
    }

    @Test
    void theLinkExpiresADayAfterTheAccess() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        jdbc.update("update login_history set created_at = now() - interval '24 hours'");
        expectInvalid(token);
        assertThat(lockedUntil(player)).isNull();
        assertThat(openSessions(player)).isPositive();
        jdbc.update("update login_history set created_at = now() - interval '23 hours 59 minutes'");
        notMe(token).andExpect(status().isOk());
        // Un enlace ya usado también vence.
        jdbc.update("update login_history set created_at = now() - interval '25 hours'");
        expectInvalid(token);
    }

    @Test
    void repeatingTheLinkAnswersTheSameWithoutLockingAgain() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        JsonNode first = json(notMe(token).andExpect(status().isOk()).andReturn());
        Instant locked = lockedUntil(player);
        Timestamp reportedAt = jdbc.queryForObject("select max(reported_at) from login_history", Timestamp.class);
        assertThat(json(notMe(token).andExpect(status().isOk()).andReturn())).isEqualTo(first);
        assertThat(lockedUntil(player)).isEqualTo(locked);
        assertThat(authVersion(player)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select max(reported_at) from login_history", Timestamp.class))
                .isEqualTo(reportedAt);
    }

    /** En Django este caso da un error 500: locked_until ya es None. */
    @Test
    void repeatingTheLinkAfterResettingThePasswordDoesNotLockAgain() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        notMe(token).andExpect(status().isOk());
        browser().create("/api/v1/auth/forgot-password", Map.of("email", player.email())).andExpect(status().isOk());
        String reset = firstMatch(mailer.last().text(), "/reset-password/([\\w-]{32})\n");
        browser().create("/api/v1/auth/reset-password", Map.of("token", reset, "new_password", "NewPalace456!"))
                .andExpect(status().isOk());
        Browser browser = browser().from("10.0.0.1");
        browser.login(player.email(), "NewPalace456!").andExpect(status().isOk());

        JsonNode body = json(notMe(token).andExpect(status().isOk()).andReturn());
        assertThat(body.get("detail").asText()).isEqualTo(DETAIL);
        assertThat(body.get("locked_until").isNull()).isTrue();
        assertThat(lockedUntil(player)).isNull();
        browser.read("/api/v1/auth/me").andExpect(status().isOk());
    }

    @Test
    void aLongerLockIsKeptAndAShorterOneIsExtended() throws Exception {
        Player longer = newPlayer();
        String token = alertToken(longer);
        jdbc.update("update users set failed_attempts = 3, locked_until = now() + interval '48 hours' where id = ?",
                longer.id());
        Instant locked = lockedUntil(longer);
        notMe(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked_until").value(Formats.utc(locked)));
        assertThat(lockedUntil(longer)).isEqualTo(locked);
        assertThat(authVersion(longer)).isEqualTo(1);
        assertThat(openSessions(longer)).isZero();
        assertThat(jdbc.queryForObject("select failed_attempts from users where id = ?", Integer.class, longer.id()))
                .isEqualTo(3);

        Player shorter = newPlayer();
        token = alertToken(shorter);
        jdbc.update("update users set locked_until = now() + interval '10 minutes' where id = ?", shorter.id());
        notMe(token).andExpect(status().isOk());
        assertThat(lockedUntil(shorter)).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
    }

    @Test
    void spacesAreTrimmedAndSuspendedAccountsCanReportToo() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        jdbc.update("update users set is_active = false where id = ?", player.id());
        notMe("  " + token + " ").andExpect(status().isOk());
        assertThat(lockedUntil(player)).isNotNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}               | This field is required.
            {"token": null}  | This field may not be null.
            {"token": " "}   | This field may not be blank.
            {"token": ["a"]} | Not a valid string.
            """)
    void theTokenIsValidated(String body, String message) throws Exception {
        browser().create(NOT_ME, body).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of("token", List.of(message))), true));
    }

    @Test
    void theTokenHasAMaximumLengthAndNumbersAreReadAsText() throws Exception {
        notMe("x".repeat(513)).andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"token\": [\"Ensure this field has no more than 512 characters.\"]}", true));
        expectInvalid("x".repeat(512));
        browser().create(NOT_ME, "{\"token\": 5}").andExpect(status().isBadRequest())
                .andExpect(content().json(INVALID_LINK, true));
    }

    @Test
    void requiresCsrfButNotASession() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        browser().withoutCsrf().create(NOT_ME, Map.of("token", token)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF: CSRF cookie not set."));
        mvc.perform(post(NOT_ME).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(lockedUntil(player)).isNull();
        // Una cookie de acceso vieja no impide usar el botón.
        Browser browser = browser();
        browser.setCookie("access_token", "vencido");
        browser.create(NOT_ME, Map.of("token", token)).andExpect(status().isOk());
    }

    @Test
    void severalClicksAtOnceLockOnlyOnce() throws Exception {
        Player player = newPlayer();
        String token = alertToken(player);
        Callable<Integer> click = () -> notMe(token).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            assertThat(pool.invokeAll(Collections.nCopies(4, click)).stream().map(future -> future.resultNow()))
                    .containsOnly(200);
        }
        assertThat(authVersion(player)).isEqualTo(1);
        assertThat(reported()).isEqualTo(1);
    }
}
