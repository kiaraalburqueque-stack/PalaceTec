package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import com.tecsup.palacetec.security.OneTimeTokens;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU38 (cambio) y HU55 (recuperación) de Django. */
class PasswordTest extends ApiTest {

    private static final String FORGOT = "/api/v1/auth/forgot-password";
    private static final String RESET = "/api/v1/auth/reset-password";
    private static final String CHANGE = "/api/v1/users/change-password";
    private static final String NEW = "NewPalace456!";
    private static final String GENERIC = "{\"detail\": \"Si el correo está registrado, recibirás un enlace para "
            + "restablecer tu contraseña.\"}";
    private static final String INVALID_LINK = "{\"code\": \"invalid_token\", \"detail\": \"El enlace no es válido, ya "
            + "se usó o venció. Solicita uno nuevo.\"}";

    private String requestLink(Player player) throws Exception {
        browser().create(FORGOT, Map.of("email", player.email())).andExpect(status().isOk());
        return firstMatch(mailer.last().text(), "http://localhost:5173/reset-password/([\\w-]{32})\n");
    }

    private ResultActions reset(String token, String password) throws Exception {
        return browser().create(RESET, Map.of("token", token, "new_password", password));
    }

    private ResultActions change(Browser browser, String current, String password) throws Exception {
        return browser.update(CHANGE, Map.of("current_password", current, "new_password", password));
    }

    private Browser loggedIn(Player player) throws Exception {
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        return browser;
    }

    private long openSessions(Player player) {
        return jdbc.queryForObject("select count(*) from accounts_authsession where user_id = ? "
                + "and revoked_at is null", Long.class, player.id());
    }

    @Test
    void forgotSendsALinkForFifteenMinutesAndStoresOnlyItsHash() throws Exception {
        Player player = newPlayer();
        browser().create(FORGOT, Map.of("email", "  " + player.email().replace("example", "EXAMPLE") + " "))
                .andExpect(status().isOk()).andExpect(content().json(GENERIC, true));
        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.last().to()).isEqualTo(player.email());
        assertThat(mailer.last().subject()).isEqualTo("PalaceTec · Restablece tu contraseña");
        assertThat(mailer.last().text()).startsWith("Hola " + player.email() + ",\n\nRecibimos una solicitud")
                .endsWith("— PalaceTec");
        String token = firstMatch(mailer.last().text(), "/reset-password/([\\w-]{32})\n");
        Map<String, Object> row = jdbc.queryForMap("select *, round(extract(epoch from expires_at - created_at)) life "
                + "from password_reset_tokens");
        assertThat(row).containsEntry("user_id", player.id()).containsEntry("used_at", null)
                .containsEntry("token_hash", OneTimeTokens.hash(token));
        assertThat(row.get("life")).hasToString("900");
    }

    @Test
    void forgotDoesNotRevealWhichEmailsExist() throws Exception {
        Player suspended = newPlayer();
        jdbc.update("update users set is_active = false where id = ?", suspended.id());
        for (String email : List.of("nadie@example.test", suspended.email())) {
            browser().create(FORGOT, Map.of("email", email)).andExpect(status().isOk())
                    .andExpect(content().json(GENERIC, true));
        }
        assertThat(mailer.sent()).isEmpty();
        assertThat(count("password_reset_tokens")).isZero();
        browser().create(FORGOT, Map.of("email", "no-es-correo")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"email\": [\"Enter a valid email address.\"]}", true));
    }

    @Test
    void recoveryRequiresCsrf() throws Exception {
        for (String path : List.of(FORGOT, RESET)) {
            browser().withoutCsrf().create(path, Map.of()).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("CSRF: CSRF cookie not set."));
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void resetChangesThePasswordUnlocksAndRevokesEverySession() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        jdbc.update("update users set failed_attempts = 5, locked_until = now() + interval '10 minutes'");
        reset(" " + requestLink(player) + " ", NEW).andExpect(status().isOk()).andExpect(content().json(
                "{\"detail\": \"Tu contraseña fue actualizada. Ya puedes iniciar sesión.\"}", true));
        assertThat(openSessions(player)).isZero();
        assertThat(jdbc.queryForMap("select failed_attempts, locked_until, auth_version from users"))
                .containsEntry("failed_attempts", 0).containsEntry("locked_until", null)
                .containsEntry("auth_version", 1);
        browser.read("/api/v1/auth/me").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").with(player.bearer())).andExpect(status().isUnauthorized());
        browser().login(player.email(), PASSWORD).andExpect(status().isUnauthorized());
        browser().login(player.email(), NEW).andExpect(status().isOk());
    }

    @Test
    void usedExpiredReplacedOrInventedLinksAreRejected() throws Exception {
        Player player = newPlayer();
        String used = requestLink(player);
        reset(used, NEW).andExpect(status().isOk());
        reset(used, "Otra4567!").andExpect(status().isBadRequest()).andExpect(content().json(INVALID_LINK, true));

        String replaced = requestLink(player);
        String expired = requestLink(player);
        reset(replaced, "Otra4567!").andExpect(content().json(INVALID_LINK, true));
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 second' where used_at is null");
        reset(expired, "Otra4567!").andExpect(status().isBadRequest()).andExpect(content().json(INVALID_LINK, true));
        reset("inventado", "Otra4567!").andExpect(status().isBadRequest());

        String suspended = requestLink(player);
        jdbc.update("update users set is_active = false");
        reset(suspended, "Otra4567!").andExpect(status().isBadRequest()).andExpect(content().json(INVALID_LINK, true));
    }

    @Test
    void anInvalidPasswordDoesNotSpendTheLink() throws Exception {
        Player player = newPlayer();
        String token = requestLink(player);
        reset(token, PASSWORD).andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"new_password\": [\"La nueva contraseña debe ser distinta de la anterior.\"]}", true));
        reset(token, "debil").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"new_password\": [\"La contraseña debe tener al menos 8 caracteres.\"]}", true));
        reset(token, "P@ssw0rd").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"new_password\": [\"This password is too common.\"]}", true));
        browser().create(RESET, "{}").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"token\": [\"This field is required.\"], \"new_password\": [\"This field is required.\"]}", true));
        reset("x".repeat(129), "A!" + "x".repeat(255)).andExpect(status().isBadRequest()).andExpect(content().json("""
                {"token": ["Ensure this field has no more than 128 characters."],
                "new_password": ["Ensure this field has no more than 256 characters."]}""", true));
        reset(token, NEW).andExpect(status().isOk());
    }

    @Test
    void aLinkUsedTwiceAtOnceChangesThePasswordOnce() throws Exception {
        Player player = newPlayer();
        String token = requestLink(player);
        Callable<Integer> first = () -> reset(token, "Primera456!").andReturn().getResponse().getStatus();
        Callable<Integer> second = () -> reset(token, "Segunda456!").andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(2)) {
            assertThat(pool.invokeAll(List.of(first, second)).stream().map(future -> future.resultNow()).toList())
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(jdbc.queryForObject("select auth_version from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void changeRevokesEverySessionAndClearsTheCookies() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        Browser other = loggedIn(player);
        change(browser, PASSWORD, NEW).andExpect(status().isOk()).andExpect(content().json(
                        "{\"detail\": \"Contraseña actualizada. Inicia sesion nuevamente.\"}", true))
                .andExpect(cookie().maxAge("access_token", 0)).andExpect(cookie().maxAge("refresh_token", 0));
        assertThat(openSessions(player)).isZero();
        other.read("/api/v1/auth/me").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sesion no disponible."));
        other.create("/api/v1/auth/refresh", Map.of()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").with(player.bearer())).andExpect(status().isUnauthorized());
        browser().login(player.email(), NEW).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Wrong!      | NewPalace456!    | current_password | La contraseña actual es incorrecta.
            Prueba#2026 | Prueba#2026      | new_password     | La nueva contraseña debe ser diferente.
            Prueba#2026 | weak             | new_password     | La contraseña debe tener al menos 8 caracteres.
            Prueba#2026 | sinmayuscula#1   | new_password     | La contraseña debe incluir una mayúscula.
            Prueba#2026 | SinSimbolo2026   | new_password     | La contraseña debe incluir un símbolo.
            Prueba#2026 | Password!        | new_password     | This password is too common.
            Prueba#2026 | Jugador1@        | new_password     | The password is too similar to the email.
            Prueba#2026 | Alburqueque!     | new_password     | The password is too similar to the last name.
            """)
    void changeRejectsInvalidPasswords(String current, String password, String field, String message)
            throws Exception {
        Player player = newPlayer();
        jdbc.update("update users set first_name = 'Kiara', last_name = 'Alburqueque' where id = ?", player.id());
        Browser browser = loggedIn(player);
        change(browser, current, password).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of(field, List.of(message))), true));
        browser.read("/api/v1/auth/me").andExpect(status().isOk());
        browser().login(player.email(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changeReportsMissingFields() throws Exception {
        Browser browser = loggedIn(newPlayer());
        browser.update(CHANGE, "{\"new_password\": null}").andExpect(status().isBadRequest())
                .andExpect(content().json("{\"current_password\": [\"This field is required.\"], "
                        + "\"new_password\": [\"This field may not be null.\"]}", true));
    }

    @Test
    void changeNeedsCsrfWithCookieButNotWithBearer() throws Exception {
        Player player = newPlayer();
        change(loggedIn(player).withoutCsrf(), PASSWORD, NEW).andExpect(status().isForbidden());
        mvc.perform(put(CHANGE)).andExpect(status().isUnauthorized());
        mvc.perform(get(CHANGE).with(player.bearer())).andExpect(status().isOk()).andExpect(content().json(
                "{\"id\": %d, \"email\": \"%s\", \"role\": \"USER\"}".formatted(player.id(), player.email()), true));
        mvc.perform(put(CHANGE).with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\": \"" + PASSWORD + "\", \"new_password\": \"" + NEW + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(CHANGE).with(player.bearer())).andExpect(status().isUnauthorized());
    }
}
