package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.service.ExclusionService;
import com.tecsup.palacetec.service.RegistrationService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RegisterTest extends ApiTest {

    @Autowired
    private ExclusionService exclusions;
    @Autowired
    private RegistrationService registration;

    private static Map<String, Object> valid() {
        return new HashMap<>(Map.of("email", "nuevo@example.test", "password", PASSWORD, "dni", "90000001",
                "birth_date", "2000-05-10"));
    }

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)));
    }

    @Test
    void createsUserWalletAndWelcomeBonus() throws Exception {
        Map<String, Object> body = valid();
        body.put("email", "  Nuevo@EXAMPLE.Test ");
        register(body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("Nuevo@example.test"))
                .andExpect(jsonPath("$.length()").value(2));
        Map<String, Object> row = jdbc.queryForMap("""
                select u.password, u.is_active, u.is_staff, u.is_superuser, u.avatar_url, u.nickname, u.first_name,
                    w.balance, w.bonus_balance, t.type, t.amount, t.balance_after, t.reference
                from users u join wallets w on w.user_id = u.id join wallet_transactions t on t.wallet_id = w.id""");
        assertThat(row.get("password").toString()).startsWith("bcrypt_sha256$$2b$12$");
        assertThat(row).containsEntry("is_active", true).containsEntry("is_staff", false)
                .containsEntry("is_superuser", false).containsEntry("avatar_url", "/avatars/avatar-1.svg")
                .containsEntry("nickname", null).containsEntry("first_name", "")
                .containsEntry("type", "WELCOME_BONUS").containsEntry("reference", null);
        for (String column : List.of("balance", "bonus_balance", "amount", "balance_after")) {
            assertThat(row.get(column)).hasToString("1000.00");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            email      | no-es-correo
            email      | a@b
            password   | Corta#1
            password   | sinmayuscula#1
            password   | SinSimbolo2026
            dni        | 1234567
            dni        | 1234567a
            dni        | ١٢٣٤٥٦٧٨
            birth_date | 2020-01-01
            birth_date | 10/05/2000
            """)
    void rejectsInvalidFields(String field, String value) throws Exception {
        Map<String, Object> body = valid();
        body.put(field, value);
        register(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$." + field + "[0]").isNotEmpty())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(count("users")).isZero();
    }

    @Test
    void reportsEveryMissingField() throws Exception {
        register(Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").isArray()).andExpect(jsonPath("$.password").isArray())
                .andExpect(jsonPath("$.dni").isArray()).andExpect(jsonPath("$.birth_date").isArray());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{mal"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void rejectsRepeatedEmailAndDni() throws Exception {
        register(valid()).andExpect(status().isCreated());
        register(valid()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.email[0]").value("Este correo ya está registrado."))
                .andExpect(jsonPath("$.dni[0]").value("Este DNI ya está registrado."));
        Map<String, Object> otherEmail = valid();
        otherEmail.put("email", "otro@example.test");
        register(otherEmail).andExpect(status().isConflict()).andExpect(jsonPath("$.email").doesNotExist());
        assertThat(count("users")).isEqualTo(1);
    }

    @Test
    void rejectsExcludedPeopleWithAGenericMessage() throws Exception {
        jdbc.update("insert into responsible_excludedperson (dni_hash, reason, source, created_at) "
                + "values (?, 'motivo reservado', 'fixture', now())", exclusions.digest("90000001"));
        register(valid()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_not_allowed"))
                .andExpect(jsonPath("$.detail").value("No es posible habilitar el acceso."));
        assertThat(count("users")).isZero();
    }

    @Test
    void simultaneousRegistrationsCreateOneAccount() throws Exception {
        Callable<Integer> attempt = () -> register(valid()).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Integer> codes = pool.invokeAll(List.of(attempt, attempt, attempt, attempt)).stream()
                    .map(future -> future.resultNow()).sorted().toList();
            assertThat(codes).containsExactly(201, 409, 409, 409);
        }
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("wallets")).isEqualTo(1);
        assertThat(count("wallet_transactions")).isEqualTo(1);
    }

    @Test
    void failureInsideTheTransactionLeavesNothing() {
        // La base rechaza a un menor aunque se salte la validación: no debe quedar nada a medias.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registration.create("menor@example.test", "90000002",
                LocalDate.now().minusYears(10), "bcrypt_sha256$x")).isInstanceOf(RuntimeException.class);
        assertThat(count("users")).isZero();
        assertThat(count("wallets")).isZero();
        assertThat(count("wallet_transactions")).isZero();
    }
}
