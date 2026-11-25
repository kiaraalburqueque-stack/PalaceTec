package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** HU04, HU20 y HU37 de Django. */
class ProfileUpdateTest extends ApiTest {

    private static final String PROFILE = "/api/v1/users/profile";

    private ResultActions update(Player player, String body) throws Exception {
        return mvc.perform(put(PROFILE).with(player.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> stored(Player player) {
        return jdbc.queryForMap("select * from users where id = ?", player.id());
    }

    @Test
    void updatesOnlyTheSentFieldsAndNormalizesTheNickname() throws Exception {
        Player player = newPlayer();
        update(player, """
                {"nickname": " Kiara_1 ", "avatar_url": "/avatars/avatar-2.svg", "first_name": " María José ",
                "last_name": "O'Connor-Ñahui Jr.", "phone": "+51 999 123 456"}""").andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id": %d, "email": "%s", "dni": "%s", "nickname": "kiara_1",
                        "avatar_url": "/avatars/avatar-2.svg", "first_name": "María José",
                        "last_name": "O'Connor-Ñahui Jr.", "phone": "+51 999 123 456"}"""
                        .formatted(player.id(), player.email(), player.dni()), true));
        update(player, "{\"first_name\": \"\", \"phone\": \"  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.first_name").value("")).andExpect(jsonPath("$.phone").value(""))
                .andExpect(jsonPath("$.nickname").value("kiara_1"));
        update(player, "{}").andExpect(status().isOk());
        assertThat(stored(player)).containsEntry("nickname", "kiara_1").containsEntry("first_name", "")
                .containsEntry("last_name", "O'Connor-Ñahui Jr.").containsEntry("email", player.email())
                .containsEntry("avatar_url", "/avatars/avatar-2.svg").containsEntry("auth_version", 0);
    }

    @Test
    void meAndProfileAreTheSameView() throws Exception {
        Player player = newPlayer();
        mvc.perform(put("/api/v1/users/me").with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"desde_me\"}")).andExpect(status().isOk());
        mvc.perform(get(PROFILE).with(player.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("desde_me")).andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void identityCannotBeChanged() throws Exception {
        Player player = newPlayer();
        update(player, "{\"email\": \"otro@example.test\", \"first_name\": \"Changed\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"email\": [\"Este campo no es editable.\"]}", true));
        update(player, "{\"dni\": \"87654321\", \"email\": null}").andExpect(status().isBadRequest())
                .andExpect(content().json("{\"email\": [\"Este campo no es editable.\"], "
                        + "\"dni\": [\"Este campo no es editable.\"]}", true));
        // Primero se informan los errores de formato, como en el serializador.
        update(player, "{\"dni\": \"87654321\", \"nickname\": \"a\"}").andExpect(status().isBadRequest())
                .andExpect(content().json("{\"nickname\": [\"This value does not match the required pattern.\"]}", true));
        assertThat(stored(player)).containsEntry("first_name", "").containsEntry("dni", player.dni());
        // id se ignora: es de solo lectura.
        update(player, "{\"id\": 999}").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(player.id()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            nickname   | "a"                        | This value does not match the required pattern.
            nickname   | "con espacio"              | This value does not match the required pattern.
            nickname   | ""                         | This field may not be blank.
            nickname   | null                       | This field may not be null.
            avatar_url | "https://x.test/a.svg"     | Elige uno de los avatares disponibles o sube tu foto.
            avatar_url | "/avatars/avatar-7.svg"    | Elige uno de los avatares disponibles o sube tu foto.
            avatar_url | "/media/avatars/otro.webp" | Elige uno de los avatares disponibles o sube tu foto.
            first_name | "<script>"                 | Usa letras y separadores de nombre validos.
            last_name  | "R2D2"                     | Usa letras y separadores de nombre validos.
            first_name | null                       | This field may not be null.
            phone      | "javascript:123"           | This value does not match the required pattern.
            phone      | "12345"                    | This value does not match the required pattern.
            phone      | true                       | This value does not match the required pattern.
            last_name  | true                       | Not a valid string.
            """)
    void rejectsInvalidValues(String field, String value, String message) throws Exception {
        Player player = newPlayer();
        update(player, "{\"%s\": %s}".formatted(field, value)).andExpect(status().isBadRequest())
                .andExpect(content().json(mapper.writeValueAsString(Map.of(field, List.of(message))), true));
    }

    @Test
    void limitsTheLengths() throws Exception {
        Player player = newPlayer();
        update(player, "{\"last_name\": \"" + "x".repeat(81) + "\", \"phone\": \"+" + "1".repeat(20) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(content().json("""
                        {"last_name": ["Ensure this field has no more than 80 characters."],
                        "phone": ["Ensure this field has no more than 20 characters."]}""", true));
        update(player, "{\"last_name\": \"" + "ñ".repeat(80) + "\", \"phone\": \"" + "1".repeat(20) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void nicknamesAreUniqueIgnoringCase() throws Exception {
        update(newPlayer(), "{\"nickname\": \"taken\"}").andExpect(status().isOk());
        Player player = newPlayer();
        update(player, "{\"nickname\": \"TAKEN\"}").andExpect(status().isBadRequest())
                .andExpect(content().json("{\"nickname\": [\"Este apodo ya esta registrado.\"]}", true));
        update(player, "{\"nickname\": \"propio\"}").andExpect(status().isOk());
        update(player, "{\"nickname\": \"PROPIO\"}").andExpect(status().isOk());
        // Un apodo con mayúsculas guardado antes solo lo detecta el índice único de la base.
        jdbc.update("update users set nickname = 'Antiguo' where nickname = 'taken'");
        update(player, "{\"nickname\": \"antiguo\"}").andExpect(status().isConflict())
                .andExpect(content().json("{\"nickname\": [\"Este apodo ya esta registrado.\"]}", true));
        assertThat(stored(player)).containsEntry("nickname", "propio");
    }

    @Test
    void twoPlayersCannotTakeTheSameNicknameAtOnce() throws Exception {
        Player first = newPlayer();
        Player second = newPlayer();
        Callable<Integer> one = () -> update(first, "{\"nickname\": \"carrera\"}").andReturn().getResponse().getStatus();
        Callable<Integer> two = () -> update(second, "{\"nickname\": \"carrera\"}").andReturn().getResponse()
                .getStatus();
        try (var pool = Executors.newFixedThreadPool(2)) {
            // 400 si la validación ya lo ve; 409 si solo lo detecta el índice único.
            assertThat(pool.invokeAll(List.of(one, two)).stream().map(future -> future.resultNow()).sorted().toList())
                    .isIn(List.of(200, 400), List.of(200, 409));
        }
    }

    @Test
    void aBodyThatIsNotAnObjectIsRejected() throws Exception {
        update(newPlayer(), "[]").andExpect(status().isBadRequest()).andExpect(content().json(
                "{\"non_field_errors\": [\"Invalid data. Expected a dictionary, but got list.\"]}", true));
    }

    @Test
    void worksWithTheCookieSessionAndCsrf() throws Exception {
        mvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        mvc.perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        Player player = newPlayer();
        Browser browser = browser();
        browser.login(player.email(), PASSWORD);
        browser.read("/api/v1/users/me").andExpect(status().isOk());
        browser.update(PROFILE, Map.of("nickname", "secure")).andExpect(status().isOk());
        browser.withoutCsrf().update(PROFILE, Map.of("nickname", "sin_csrf")).andExpect(status().isForbidden());
        assertThat(stored(player)).containsEntry("nickname", "secure");
    }
}
