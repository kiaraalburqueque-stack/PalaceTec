package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.service.ExclusionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class LoginTest extends ApiTest {

    /** Ruta protegida sin controlador asociado: con token válido responde 404 en lugar de 401. */
    private static final String PROTECTED = "/api/v1/ruta-inexistente";

    @Autowired
    private ExclusionService exclusions;

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email", email, "password", password))));
    }

    @Test
    void returnsAJwtInTheBodyForAPasswordHashedByDjango() throws Exception {
        Player player = newPlayer();
        String token = json(login(player.email(), PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.id()))
                .andExpect(jsonPath("$.email").value(player.email()))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800))
                .andExpect(cookie().doesNotExist("access_token"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()).get("access_token").asText();
        mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from accounts_authsession where user_id = ? "
                + "and revoked_at is null and expires_at > now() + interval '7 hours'", Long.class, player.id()))
                .isEqualTo(2);
    }

    @Test
    void ignoresAStaleTokenOnLogin() throws Exception {
        Player player = newPlayer();
        mvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer vencido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", player.email(), "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
        Player player = newPlayer();
        login(player.email(), "Otra#Clave1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        login("nadie@example.test", "Otra#Clave1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        login("no-es-correo", "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").isArray());
    }

    @Test
    void fifthFailureLocksTheAccountForFifteenMinutes() throws Exception {
        Player player = newPlayer();
        for (int attempt = 1; attempt <= 4; attempt++) {
            login(player.email(), "Otra#Clave1").andExpect(status().isUnauthorized());
        }
        login(player.email(), "Otra#Clave1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retry_after").value(900)).andExpect(header().string("Retry-After", "900"));
        login(player.email(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retry_after").value(allOf(greaterThan(890), lessThanOrEqualTo(900))));
        assertThat(jdbc.queryForObject("select failed_attempts from users", Integer.class)).isEqualTo(5);

        jdbc.update("update users set locked_until = now() - interval '1 second'");
        login(player.email(), PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select failed_attempts, locked_until from users"))
                .containsEntry("failed_attempts", 0).containsEntry("locked_until", null);
    }

    @Test
    void afterAnExpiredLockTheCounterStartsAgain() throws Exception {
        Player player = newPlayer();
        jdbc.update("update users set failed_attempts = 5, locked_until = now() - interval '1 second'");
        login(player.email(), "Otra#Clave1").andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select failed_attempts from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void suspendedAccountOnlyLearnsItWithTheRightPassword() throws Exception {
        Player player = newPlayer();
        jdbc.update("update users set is_active = false");
        login(player.email(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_suspended"));
        login(player.email(), "Otra#Clave1").andExpect(status().isUnauthorized());
        mvc.perform(get(PROTECTED).with(player.bearer())).andExpect(status().isUnauthorized());
    }

    @Test
    void selfExcludedOrRegisteredExclusionCannotEnterNorUseAnOldToken() throws Exception {
        Player player = newPlayer();
        jdbc.update("update users set self_excluded_until = now() + interval '7 days' where id = ?", player.id());
        login(player.email(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_not_allowed"));
        mvc.perform(get(PROTECTED).with(player.bearer())).andExpect(status().isForbidden());
        jdbc.update("update users set self_excluded_until = now() - interval '1 second' where id = ?", player.id());
        mvc.perform(get(PROTECTED).with(player.bearer())).andExpect(status().isNotFound());

        jdbc.update("insert into responsible_excludedperson (dni_hash, reason, source, created_at) "
                + "values (?, '', 'fixture', now())", exclusions.digest(player.dni()));
        login(player.email(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_not_allowed"));
        mvc.perform(get(PROTECTED).with(player.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void tokenStopsWorkingWhenDjangoRevokesOrChangesTheVersion() throws Exception {
        Player revoked = newPlayer();
        jdbc.update("update accounts_authsession set revoked_at = now() where user_id = ?", revoked.id());
        mvc.perform(get(PROTECTED).with(revoked.bearer())).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"api\""));

        Player changedPassword = newPlayer();
        jdbc.update("update users set auth_version = auth_version + 1 where id = ?", changedPassword.id());
        mvc.perform(get(PROTECTED).with(changedPassword.bearer())).andExpect(status().isUnauthorized());

        Player withoutSession = newPlayer();
        jdbc.update("delete from accounts_authsession where user_id = ?", withoutSession.id());
        mvc.perform(get(PROTECTED).with(withoutSession.bearer())).andExpect(status().isUnauthorized());

        Player deleted = newPlayer();
        jdbc.update("delete from accounts_authsession where user_id = ?", deleted.id());
        jdbc.update("delete from wallets where user_id = ?", deleted.id());
        jdbc.update("delete from users where id = ?", deleted.id());
        mvc.perform(get(PROTECTED).with(deleted.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get(PROTECTED).header("Authorization", "Bearer basura")).andExpect(status().isUnauthorized());
    }
}
