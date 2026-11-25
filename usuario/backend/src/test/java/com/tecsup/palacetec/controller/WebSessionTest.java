package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.WebTokenService;
import com.tecsup.palacetec.security.WebTokenService.Type;
import com.tecsup.palacetec.service.ExclusionService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/** Sesión del frontend React: cookies HttpOnly y CSRF, como HU02, HU28 y HU30 en Django. */
class WebSessionTest extends ApiTest {

    private static final String ME = "/api/v1/auth/me";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String DAILY_LIMIT = "/api/v1/responsible-gaming/daily-limit";

    @Autowired
    private WebTokenService webTokens;
    @Autowired
    private PalaceTecProperties properties;
    @Autowired
    private UserRepository users;
    @Autowired
    private ExclusionService exclusions;

    private Browser loggedIn(Player player) throws Exception {
        Browser browser = browser();
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        return browser;
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        return response.getHeaders("Set-Cookie").stream().filter(value -> value.startsWith(name + "=")).findFirst()
                .orElseThrow();
    }

    @Test
    void csrfKeepsTheSecretAndChangesTheToken() throws Exception {
        Browser browser = browser();
        var first = browser.read("/api/v1/auth/csrf").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(header().stringValues("Vary", hasItem("Cookie")))
                .andReturn();
        assertThat(setCookie(first.getResponse(), "csrftoken")).contains("Path=/", "Max-Age=31449600", "SameSite=Lax")
                .doesNotContain("HttpOnly").doesNotContain("Secure");
        String secret = browser.cookie("csrftoken");
        assertThat(secret).matches("[a-zA-Z0-9]{32}");
        assertThat(json(first).get("csrfToken").asText()).matches("[a-zA-Z0-9]{64}");
        var second = browser.read("/api/v1/auth/csrf").andReturn();
        assertThat(browser.cookie("csrftoken")).isEqualTo(secret);
        assertThat(json(second).get("csrfToken")).isNotEqualTo(json(first).get("csrfToken"));

        browser.setCookie("csrftoken", "alterada");
        browser.read("/api/v1/auth/csrf").andExpect(status().isOk());
        assertThat(browser.cookie("csrftoken")).matches("[a-zA-Z0-9]{32}");
    }

    @Test
    void loginPutsTheTokensOnlyInHttpOnlyCookies() throws Exception {
        Player player = newPlayer();
        Browser browser = browser();
        var response = browser.login(player.email(), PASSWORD).andExpect(status().isOk())
                .andExpect(content().json("{\"id\": " + player.id() + ", \"email\": \"" + player.email() + "\"}", true))
                .andReturn().getResponse();
        assertThat(setCookie(response, "access_token"))
                .contains("Path=/;", "Max-Age=900", "HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        assertThat(setCookie(response, "refresh_token"))
                .contains("Path=/api/v1/auth/", "Max-Age=28800", "HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        var access = webTokens.parse(browser.cookie("access_token"), Type.ACCESS);
        var refresh = webTokens.parse(browser.cookie("refresh_token"), Type.REFRESH);
        assertThat(refresh.sid()).isEqualTo(access.sid());
        Map<String, Object> session = jdbc.queryForMap("select user_id, revoked_at, "
                + "round(extract(epoch from expires_at - started_at) / 60) minutes, "
                + "round(extract(epoch from refresh_expires_at - started_at) / 3600) hours "
                + "from accounts_authsession where jti = ?", access.sid());
        assertThat(session).containsEntry("user_id", player.id()).containsEntry("revoked_at", null);
        assertThat(session.get("minutes")).hasToString("15");
        assertThat(session.get("hours")).hasToString("8");
    }

    @Test
    void meReturnsTheRole() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        browser.read(ME).andExpect(status().isOk()).andExpect(content().json(
                "{\"id\": %d, \"email\": \"%s\", \"role\": \"USER\"}".formatted(player.id(), player.email()), true));
        jdbc.update("update users set is_staff = true where id = ?", player.id());
        browser.read(ME).andExpect(jsonPath("$.role").value("ADMIN"));
        jdbc.update("update users set is_staff = false, is_superuser = true where id = ?", player.id());
        browser.read(ME).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get(ME).with(player.bearer())).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void requestsWithoutAValidCookieAreRejectedLikeInDjango() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"api\""))
                .andExpect(content().json("{\"detail\": \"Authentication credentials were not provided.\"}", true));
        Browser browser = loggedIn(newPlayer());
        String refresh = browser.cookie("refresh_token");
        browser.setCookie("access_token", "invalid");
        browser.read(ME).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"api\""))
                .andExpect(content().json("""
                        {"detail": "Given token not valid for any token type", "code": "token_not_valid", "messages":
                        [{"token_class": "AccessToken", "token_type": "access", "message": "Token is invalid"}]}""", true));
        browser.setCookie("access_token", refresh);
        browser.read(ME).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messages[0].message").value("Token has wrong type"));
    }

    @Test
    void expiredAccessCookieIsRejected() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        browser.setCookie("access_token", expiredAccess(player));
        browser.read(ME).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_not_valid"))
                .andExpect(jsonPath("$.messages[0].message").value("Token is expired"));
    }

    private String expiredAccess(Player player) {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        return new WebTokenService(properties, past).startSession(users.findById(player.id()).orElseThrow()).value();
    }

    /** El Bearer de la app móvil y la cookie de la web no son intercambiables. */
    @Test
    void cookieAndBearerTokensAreNotInterchangeable() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        String access = browser.cookie("access_token");
        mvc.perform(get(ME).header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        browser.setCookie("access_token", player.token());
        browser.read(ME).andExpect(status().isUnauthorized());
    }

    @Test
    void loginAndRegisterFromABrowserRequireCsrf() throws Exception {
        Player player = newPlayer();
        Map<String, String> credentials = Map.of("email", player.email(), "password", PASSWORD);
        Browser browser = browser().withoutCsrf();
        browser.create(LOGIN, credentials).andExpect(status().isForbidden())
                .andExpect(content().json("{\"detail\": \"CSRF: CSRF cookie not set.\"}", true));
        browser.read("/api/v1/auth/csrf");
        browser.create(LOGIN, credentials).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF: CSRF token missing."));
        // El CSRF se revisa antes que el cuerpo.
        browser.create(LOGIN, "{mal").andExpect(status().isForbidden());
        browser.create("/api/v1/auth/register", Map.of()).andExpect(status().isForbidden());
        for (String path : List.of(REFRESH, LOGOUT)) {
            browser.create(path, Map.of()).andExpect(status().isForbidden());
            mvc.perform(post(path)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("CSRF: CSRF cookie not set."));
        }
        assertThat(browser.cookie("access_token")).isNull();
        browser().create("/api/v1/auth/register", Map.of("email", "web@example.test", "password", PASSWORD, "dni",
                "90000777", "birth_date", "2000-05-10")).andExpect(status().isCreated());
    }

    @Test
    void csrfTokenMustMatchTheCookie() throws Exception {
        String secret = "0qIjny4ECzszfmNLZRGhTA9HV6rMgk6A";
        String masked = "BtkuvxqYDjABOk7ofnhZ85sWpMBlgxNxrJSDIVks5IS0TwKZ44N6RvrtaISXmHJX"; // Enmascarado por Django.
        String wrong = "CSRF: CSRF token from the 'X-Csrftoken' HTTP header ";
        csrfAnswer(secret, "A" + masked.substring(1)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(wrong + "incorrect."));
        csrfAnswer(secret, "corto").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(wrong + "has incorrect length."));
        csrfAnswer(secret, "-".repeat(64)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(wrong + "has invalid characters."));
        csrfAnswer("corta", masked).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF: CSRF cookie has incorrect length."));
        csrfAnswer(secret, masked).andExpect(status().isOk());
        csrfAnswer(secret, secret).andExpect(status().isOk());
        csrfAnswer(masked, masked).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions csrfAnswer(String cookie, String token)
            throws Exception {
        return mvc.perform(post(LOGOUT).header("Origin", Browser.ORIGIN).cookie(new Cookie("csrftoken", cookie))
                .header("X-CSRFToken", token));
    }

    @Test
    void untrustedOriginIsRejectedEvenWithAValidToken() throws Exception {
        String secret = "0qIjny4ECzszfmNLZRGhTA9HV6rMgk6A";
        mvc.perform(post(LOGOUT).header("Origin", "https://untrusted.test").cookie(new Cookie("csrftoken", secret))
                .header("X-CSRFToken", secret)).andExpect(status().isForbidden());
        // Sin Origin, HTTPS exige un Referer de confianza.
        mvc.perform(post(LOGOUT).secure(true).cookie(new Cookie("csrftoken", secret)).header("X-CSRFToken", secret))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF: Referer checking failed - no Referer."));
        mvc.perform(post(LOGOUT).secure(true).cookie(new Cookie("csrftoken", secret)).header("X-CSRFToken", secret)
                .header("Referer", "https://localhost:5173/perfil")).andExpect(status().isOk());
        mvc.perform(post(LOGOUT).secure(true).cookie(new Cookie("csrftoken", secret)).header("X-CSRFToken", secret)
                .header("Referer", "https://untrusted.test/")).andExpect(status().isForbidden());
    }

    @Test
    void writesWithACookieNeedCsrfAndWithBearerDoNot() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        browser.create(DAILY_LIMIT, Map.of("amount", "50")).andExpect(status().isOk());
        browser.withoutCsrf().create(DAILY_LIMIT, Map.of("amount", "40")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF: CSRF token missing."));
        mvc.perform(post(DAILY_LIMIT).with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 30}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select amount from responsible_dailylimit", String.class)).isEqualTo("30.00");
    }

    @Test
    void loginReportsFieldErrorsWithTheDjangoMessages() throws Exception {
        browser().create(LOGIN, Map.of("email", "no-es-correo", "password", "")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"email\": [\"Enter a valid email address.\"], "
                        + "\"password\": [\"This field may not be blank.\"]}", true));
        browser().create(LOGIN, "{}").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"email\": [\"This field is required.\"], \"password\": [\"This field is required.\"]}", true));
        // La app móvil conserva los textos del MVP.
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"x\"}"))
                .andExpect(content().json("{\"email\": [\"Escribe un correo válido.\"], "
                        + "\"password\": [\"Este campo es obligatorio.\"]}", true));
    }

    @Test
    void rejectedLoginsFromTheWebMatchDjango() throws Exception {
        Player player = newPlayer();
        browser().login(player.email(), "Otra#Clave1").andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"detail\": \"Credenciales inválidas\"}", true))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(cookie().doesNotExist("access_token"));
        jdbc.update("update users set locked_until = now() + interval '10 minutes'");
        browser().login(player.email(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Cuenta bloqueada temporalmente."))
                .andExpect(header().exists("Retry-After")).andExpect(cookie().doesNotExist("access_token"));
        jdbc.update("update users set locked_until = null, is_active = false");
        browser().login(player.email(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_suspended"));
    }

    @Test
    void loginUpgradesAnOldDjangoHash() throws Exception {
        Player player = newPlayer();
        jdbc.update("update users set password = ? where id = ?", "pbkdf2_sha256$1000$saltDePrueba0123456789"
                + "$NkHZaK7NFAEGtfl25B2aXv1jvKPCMStMkFnRm+s3a3Y=", player.id());
        browser().login(player.email(), PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select password from users where id = ?", String.class, player.id()))
                .startsWith("bcrypt_sha256$$2b$12$");
        browser().login(player.email(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void refreshRenewsTheAccessWithoutRestartingTheSession() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        var first = webTokens.parse(browser.cookie("access_token"), Type.ACCESS);
        String firstAccess = browser.cookie("access_token");
        var startedAt = jdbc.queryForObject("select started_at from accounts_authsession where jti = ?",
                OffsetDateTime.class, first.sid());
        jdbc.update("update accounts_authsession set expires_at = now() - interval '1 second' where jti = ?",
                first.sid());
        browser.read(ME).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"detail\": \"Sesion no disponible.\"}", true));

        var answer = browser.create(REFRESH, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").value("Sesion renovada.")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(cookie().doesNotExist("refresh_token")).andReturn();
        String reported = json(answer).get("session_started_at").asText();
        assertThat(reported).endsWith("+00:00");
        assertThat(OffsetDateTime.parse(reported).toInstant()).isEqualTo(startedAt.toInstant());
        assertThat(setCookie(answer.getResponse(), "access_token")).contains("Max-Age=900", "HttpOnly");
        var second = webTokens.parse(browser.cookie("access_token"), Type.ACCESS);
        assertThat(second.sid()).isEqualTo(first.sid());
        assertThat(browser.cookie("access_token")).isNotEqualTo(firstAccess);
        browser.read(ME).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select started_at from accounts_authsession where jti = ?",
                OffsetDateTime.class, first.sid())).isEqualTo(startedAt);
    }

    @Test
    void refreshedAccessNeverOutlivesTheSession() throws Exception {
        Browser browser = loggedIn(newPlayer());
        Instant deadline = Instant.now().plusSeconds(60);
        jdbc.update("update accounts_authsession set refresh_expires_at = ? where refresh_expires_at is not null",
                java.sql.Timestamp.from(deadline));
        var response = browser.create(REFRESH, Map.of()).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(webTokens.parse(browser.cookie("access_token"), Type.ACCESS).expiresAt())
                .isBeforeOrEqualTo(deadline);
        assertThat(response.getCookie("access_token").getMaxAge()).isBetween(55, 60);
        jdbc.update("update accounts_authsession set refresh_expires_at = now() - interval '1 second' "
                + "where refresh_expires_at is not null");
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsMissingTamperedOrRevokedSessions() throws Exception {
        browser().create(REFRESH, Map.of()).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"api\""))
                .andExpect(content().json("{\"detail\": \"Inicia sesion nuevamente.\"}", true));
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        String refresh = browser.cookie("refresh_token");
        browser.setCookie("refresh_token", "tampered");
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());
        browser.setCookie("refresh_token", browser.cookie("access_token"));
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());

        browser.setCookie("refresh_token", refresh);
        jdbc.update("update users set auth_version = auth_version + 1 where id = ?", player.id());
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());
        jdbc.update("update users set auth_version = 0 where id = ?", player.id());
        browser.create(REFRESH, Map.of()).andExpect(status().isOk());
        jdbc.update("update users set is_active = false where id = ?", player.id());
        browser.create(REFRESH, Map.of()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_not_allowed"));
        jdbc.update("update users set is_active = true where id = ?", player.id());
        jdbc.update("update accounts_authsession set revoked_at = now()");
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookies() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        Browser other = loggedIn(player);
        String access = browser.cookie("access_token");
        String refresh = browser.cookie("refresh_token");
        var response = browser.create(LOGOUT, Map.of()).andExpect(status().isOk())
                .andExpect(content().json("{\"detail\": \"Sesion cerrada.\"}", true)).andReturn().getResponse();
        assertThat(setCookie(response, "access_token")).contains("Max-Age=0", "Path=/;", "SameSite=Lax");
        assertThat(setCookie(response, "refresh_token")).contains("Max-Age=0", "Path=/api/v1/auth/", "SameSite=Lax");

        browser.setCookie("access_token", access);
        browser.setCookie("refresh_token", refresh);
        browser.read(ME).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sesion no disponible."));
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());
        other.read(ME).andExpect(status().isOk());
        mvc.perform(get(ME).with(player.bearer())).andExpect(status().isOk());
    }

    @Test
    void logoutWorksWithAnExpiredAccessOrWithoutSession() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        String refresh = browser.cookie("refresh_token");
        browser.setCookie("access_token", expiredAccess(player));
        browser.create(LOGOUT, Map.of()).andExpect(status().isOk());
        browser.setCookie("refresh_token", refresh);
        browser.create(REFRESH, Map.of()).andExpect(status().isUnauthorized());

        Browser anonymous = browser();
        anonymous.setCookie("access_token", "expired-or-invalid");
        anonymous.create(LOGOUT, Map.of()).andExpect(status().isOk()).andExpect(cookie().maxAge("access_token", 0));
    }

    @Test
    void bearerLogoutRevokesOnlyThatSession() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        mvc.perform(post(LOGOUT).with(player.bearer())).andExpect(status().isOk());
        mvc.perform(get(ME).with(player.bearer())).andExpect(status().isUnauthorized());
        browser.read(ME).andExpect(status().isOk());
    }

    @Test
    void staleCookieDoesNotPreventANewLogin() throws Exception {
        Player player = newPlayer();
        Browser browser = browser();
        browser.setCookie("access_token", "vencida");
        browser.login(player.email(), PASSWORD).andExpect(status().isOk());
        browser.read(ME).andExpect(status().isOk());
    }

    @Test
    void exclusionsAndChangesMadeByDjangoCloseTheCookieSession() throws Exception {
        Player player = newPlayer();
        Browser browser = loggedIn(player);
        jdbc.update("update users set self_excluded_until = now() + interval '7 days' where id = ?", player.id());
        browser.read(ME).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_not_allowed"));
        jdbc.update("update users set self_excluded_until = null, is_active = false where id = ?", player.id());
        browser.read(ME).andExpect(status().isUnauthorized()).andExpect(content().json(
                "{\"detail\": \"User is inactive\", \"code\": \"user_inactive\"}", true));
        jdbc.update("update users set is_active = true, auth_version = 1 where id = ?", player.id());
        browser.read(ME).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sesion no disponible."));
        jdbc.update("update users set auth_version = 0 where id = ?", player.id());
        jdbc.update("insert into responsible_excludedperson (dni_hash, reason, source, created_at) "
                + "values (?, '', 'fixture', now())", exclusions.digest(player.dni()));
        browser.read(ME).andExpect(status().isForbidden());
        jdbc.execute("truncate table users cascade");
        browser.read(ME).andExpect(status().isUnauthorized()).andExpect(content().json(
                "{\"detail\": \"User not found\", \"code\": \"user_not_found\"}", true));
    }

    /** Otro origen nunca recibe permiso para usar las cookies. */
    @Test
    void corsAllowsCredentialsOnlyForTheFrontend() throws Exception {
        browser().read("/api/v1/auth/csrf")
                .andExpect(header().string("Access-Control-Allow-Origin", Browser.ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(ME)
                        .header("Origin", Browser.ORIGIN).header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type,x-csrftoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
