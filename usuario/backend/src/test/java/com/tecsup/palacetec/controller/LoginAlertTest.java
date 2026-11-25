package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.security.OneTimeTokens;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** HU56 de Django: alerta por acceso desde una IP o un dispositivo nuevo. */
class LoginAlertTest extends ApiTest {

    private static final String CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36";
    private static final String FIREFOX = "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";

    private void login(Player player, String ip, String agent) throws Exception {
        browser().from(ip).using(agent).login(player.email(), PASSWORD).andExpect(status().isOk());
    }

    private Map<String, Object> lastAccess() {
        return jdbc.queryForMap("select *, host(ip_address) ip from login_history order by id desc limit 1");
    }

    @Test
    void firstAndKnownLoginsSendNoAlert() throws Exception {
        Player player = newPlayer();
        login(player, "10.0.0.1", CHROME);
        login(player, "10.0.0.1", CHROME);
        assertThat(mailer.sent()).isEmpty();
        assertThat(count("notifications")).isZero();
        assertThat(count("login_history")).isEqualTo(2);
        assertThat(lastAccess()).containsEntry("user_id", player.id()).containsEntry("ip", "10.0.0.1")
                .containsEntry("user_agent", CHROME).containsEntry("is_new_ip", false)
                .containsEntry("is_new_device", false).containsEntry("alert_sent", false)
                .containsEntry("report_token_hash", null).containsEntry("reported_at", null);
    }

    @Test
    void newIpSendsTheMailWithTheNotMeButton() throws Exception {
        Player player = newPlayer();
        login(player, "10.0.0.1", CHROME);
        login(player, "200.48.1.9", CHROME);
        assertThat(mailer.sent()).hasSize(1);
        var mail = mailer.last();
        assertThat(mail.to()).isEqualTo(player.email());
        assertThat(mail.subject()).isEqualTo("PalaceTec · Nuevo acceso a tu cuenta");
        assertThat(mail.text()).startsWith("Hola " + player.email() + ",\n\nDetectamos un nuevo acceso")
                .contains("  Dispositivo: Chrome en Windows\n  IP: 200.48.1.9\n").endsWith("— PalaceTec");
        assertThat(mail.html()).contains(">No fui yo</a>").contains("<li>IP: 200.48.1.9</li>");
        String token = firstMatch(mail.text(), "http://localhost:5173/seguridad/no-fui-yo/([\\w-]{32})\n");
        assertThat(mail.html()).contains("/seguridad/no-fui-yo/" + token + "\"");
        assertThat(lastAccess()).containsEntry("is_new_ip", true).containsEntry("is_new_device", false)
                .containsEntry("alert_sent", true).containsEntry("report_token_hash", OneTimeTokens.hash(token));
        Map<String, Object> notification = jdbc.queryForMap("select * from notifications");
        assertThat(notification).containsEntry("user_id", player.id()).containsEntry("category", "SECURITY")
                .containsEntry("title", "Nuevo acceso a tu cuenta").containsEntry("read_at", null);
        assertThat(notification.get("message").toString()).matches("Iniciaste sesión el \\d{2}/\\d{2}/\\d{4} \\d{2}:"
                + "\\d{2} desde Chrome en Windows \\(IP 200\\.48\\.1\\.9\\)\\. Si no fuiste tú, cambia tu contraseña\\.");
    }

    @Test
    void newDeviceAlertsAndFailedLoginsAreNotRecorded() throws Exception {
        Player player = newPlayer();
        jdbc.update("update users set nickname = 'kiara_1' where id = ?", player.id());
        login(player, "::1", CHROME);
        browser().login(player.email(), "Incorrecta1!").andExpect(status().isUnauthorized());
        assertThat(count("login_history")).isEqualTo(1);
        login(player, "0:0:0:0:0:0:0:1", FIREFOX);
        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.last().text()).startsWith("Hola kiara_1,").contains("Firefox en Linux", "IP: ::1\n");
        assertThat(lastAccess()).containsEntry("is_new_ip", false).containsEntry("is_new_device", true);
    }

    @Test
    void securityPreferenceOffSkipsTheAlertButKeepsTheRecord() throws Exception {
        Player player = newPlayer();
        login(player, "10.0.0.1", CHROME);
        jdbc.update("insert into notification_settings (user_id, promotions, security, game_alerts, updated_at) "
                + "values (?, true, false, true, now())", player.id());
        login(player, "200.48.1.9", CHROME);
        assertThat(mailer.sent()).isEmpty();
        assertThat(count("notifications")).isZero();
        assertThat(lastAccess()).containsEntry("is_new_ip", true).containsEntry("alert_sent", false);
    }

    @Test
    void theMobileAppIsRecordedToo() throws Exception {
        Player player = newPlayer();
        login(player, "10.0.0.1", CHROME);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).header("User-Agent", "okhttp/5")
                .content(mapper.writeValueAsBytes(Map.of("email", player.email(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty());
        assertThat(mailer.last().text()).contains("navegador desconocido en sistema desconocido");
        assertThat(lastAccess()).containsEntry("user_agent", "okhttp/5").containsEntry("is_new_device", true);
    }

    @Test
    void longUserAgentsAreCut() throws Exception {
        login(newPlayer(), "10.0.0.1", "x".repeat(300));
        assertThat(lastAccess()).containsEntry("user_agent", "x".repeat(255));
    }
}
