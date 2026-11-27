package com.tecsup.palacetec.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * ws/roulette/&lt;sala&gt;/ y ws/notifications/ con un servidor real. Los cambios de runroulette y del panel de Django se
 * simulan escribiendo en la base y llamando al sondeo a mano.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeTest extends ApiTest {

    @LocalServerPort
    private int port;
    @Autowired
    private RealtimePoller poller;
    @Autowired
    private RealtimeHub hub;

    private final List<SocketClient> sockets = new ArrayList<>();
    private Player player;
    private Browser web;
    private long game;
    private long room;
    private long round;

    @BeforeEach
    void openTable() throws Exception {
        player = newPlayer();
        web = browser();
        web.login(player.email(), PASSWORD).andExpect(status().isOk());
        game = newGame("ws");
        room = newRoom(game, 10, 500);
        round = newOpenRound(room, 60);
    }

    @AfterEach
    void closeSockets() throws Exception {
        for (SocketClient socket : sockets) {
            socket.close();
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (!hub.all().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(hub.all()).isEmpty();
    }

    private String cookie(Browser browser) {
        return "access_token=" + browser.cookie("access_token");
    }

    private SocketClient open(String path, String cookie, String origin) throws Exception {
        SocketClient socket = SocketClient.open(mapper, port, path, cookie, origin);
        sockets.add(socket);
        return socket;
    }

    private SocketClient roomSocket(Browser browser) throws Exception {
        SocketClient socket = open("/ws/roulette/" + room + "/", cookie(browser), Browser.ORIGIN);
        socket.next("room:state");
        socket.next("room:user_joined");
        return socket;
    }

    private SocketClient notificationSocket(Browser browser) throws Exception {
        SocketClient socket = open("/ws/notifications/", cookie(browser), Browser.ORIGIN);
        socket.next("notification:count");
        return socket;
    }

    // ---- Handshake --------------------------------------------------------------------------------------------------

    @Test
    void sinSesionCierraCon4401() throws Exception {
        assertThat(open("/ws/roulette/" + room + "/", null, Browser.ORIGIN).closeCode()).isEqualTo(4401);
        assertThat(open("/ws/notifications/", null, Browser.ORIGIN).closeCode()).isEqualTo(4401);
        assertThat(open("/ws/notifications/", "access_token=no-es-un-jwt", Browser.ORIGIN).closeCode())
                .isEqualTo(4401);
    }

    @Test
    void elBearerDeLaAppNoSirveComoCookie() throws Exception {
        assertThat(open("/ws/notifications/", "access_token=" + player.token(), Browser.ORIGIN).closeCode())
                .isEqualTo(4401);
    }

    @Test
    void rechazaOrigenesDesconocidosOAusentes() {
        assertThatThrownBy(() -> open("/ws/notifications/", cookie(web), "http://evil.example"))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> open("/ws/roulette/" + room + "/", cookie(web), null))
                .isInstanceOf(ExecutionException.class);
        assertThat(count("room_connections")).isZero();
    }

    @Test
    void salaInexistenteOInactivaCierraCon4404() throws Exception {
        assertThat(open("/ws/roulette/999999/", cookie(web), Browser.ORIGIN).closeCode()).isEqualTo(4404);
        jdbc.update("update rooms set is_active = false where id = ?", room);
        assertThat(open("/ws/roulette/" + room + "/", cookie(web), Browser.ORIGIN).closeCode()).isEqualTo(4404);
        assertThatThrownBy(() -> open("/ws/roulette/abc/", cookie(web), Browser.ORIGIN));
        assertThat(count("room_connections")).isZero();
    }

    // ---- Sala: estado, mensajes y presencia -------------------------------------------------------------------------

    @Test
    void salaEnviaElEstadoYRegistraLaConexion() throws Exception {
        SocketClient socket = open("/ws/roulette/" + room + "/", cookie(web), Browser.ORIGIN);

        JsonNode state = socket.next("room:state");
        assertThat(state.get("room_id").asLong()).isEqualTo(room);
        assertThat(state.get("min_bet").asText()).isEqualTo("10.00");
        assertThat(state.get("round").get("id").asLong()).isEqualTo(round);
        assertThat(state.get("round").get("status").asText()).isEqualTo("OPEN");
        assertThat(socket.next("room:user_joined").get("user_id").asLong()).isEqualTo(player.id());
        assertThat(jdbc.queryForObject("select count(*) from room_connections where room_id = ? and user_id = ? "
                + "and channel_name like 'spring.%' and expires_at > now()", Long.class, room, player.id()))
                .isEqualTo(1);

        socket.close();
        long deadline = System.currentTimeMillis() + 5000;
        while (count("room_connections") > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(count("room_connections")).isZero();
    }

    @Test
    void respondeLosMensajesDelCliente() throws Exception {
        SocketClient socket = roomSocket(web);

        socket.send("{\"event\": \"ping\"}");
        assertThat(socket.next("pong")).isEmpty();
        socket.send("{\"event\": \"room:sync\"}");
        assertThat(socket.next("room:state").get("room_id").asLong()).isEqualTo(room);
        socket.send("{\"event\": \"bet:place\"}");
        assertThat(socket.next("error").get("detail").asText())
                .isEqualTo("Evento no permitido. Envia apuestas por HTTP.");
        socket.send("no es json");
        assertThat(socket.next("error").get("detail").asText()).isEqualTo("JSON invalido.");
        socket.send("[1, 2]");
        assertThat(socket.next("error").get("detail").asText()).isEqualTo("Se requiere un objeto JSON.");
        assertThat(socket.isOpen()).isTrue();

        socket.send("{\"event\": \"ping\", \"relleno\": \"" + "x".repeat(4100) + "\"}");
        assertThat(socket.closeCode()).isEqualTo(1009);
    }

    @Test
    void reconexionDevuelveLasApuestasPendientes() throws Exception {
        newBet(player, round, "50.00", null, "2026-11-27 10:00:00+00");
        newBet(player, round, "10.00", null, "2026-11-27 10:00:01+00");
        SocketClient socket = roomSocket(web);

        socket.send("{\"event\": \"room:resume\"}");
        JsonNode resumed = socket.next("RECONNECT_SUCCESS");
        assertThat(resumed.get("round_id").asLong()).isEqualTo(round);
        assertThat(resumed.get("total_amount").asText()).isEqualTo("60.00");
        assertThat(resumed.get("bets")).hasSize(2);
        assertThat(resumed.get("bets").get(0).get("amount").asText()).isEqualTo("50.00");
        assertThat(resumed.get("state").get("round").get("id").asLong()).isEqualTo(round);

        jdbc.update("update game_rounds set status = 'SETTLED', winning_number = 3, settled_at = now() where id = ?",
                round);
        socket.send("{\"event\": \"room:resume\"}");
        resumed = socket.next("RECONNECT_SUCCESS");
        assertThat(resumed.get("bets")).isEmpty();
        assertThat(resumed.get("total_amount").asText()).isEqualTo("0.00");
    }

    @Test
    void avisaQuienEntraYSaleDeLaSala() throws Exception {
        SocketClient first = roomSocket(web);
        Player other = newPlayer();
        Browser otherWeb = browser();
        otherWeb.login(other.email(), PASSWORD).andExpect(status().isOk());

        SocketClient second = roomSocket(otherWeb);
        assertThat(first.next("room:user_joined").get("user_id").asLong()).isEqualTo(other.id());
        second.close();
        assertThat(first.next("room:user_left").get("user_id").asLong()).isEqualTo(other.id());
    }

    // ---- Rondas de runroulette --------------------------------------------------------------------------------------

    @Test
    void rondasEnVivoDesdeLaBase() throws Exception {
        SocketClient socket = roomSocket(web);

        poller.poll();
        JsonNode tick = socket.next("timer:tick");
        assertThat(tick.get("round_id").asLong()).isEqualTo(round);
        assertThat(tick.get("remaining_seconds").asLong()).isBetween(55L, 60L);
        assertThat(tick.get("closes_at").asText()).endsWith("+00:00");

        jdbc.update("update game_rounds set status = 'CLOSED' where id = ?", round);
        poller.poll();
        assertThat(socket.settle()).containsExactly("bets:closed", "timer:stop");

        newBet(player, round, "10.00", "20.00", "2026-11-27 10:00:00+00");
        jdbc.update("update game_rounds set status = 'SETTLED', winning_number = 12, settled_at = now() where id = ?",
                round);
        poller.poll();
        JsonNode result = socket.next("round:result");
        assertThat(result.get("round_id").asLong()).isEqualTo(round);
        assertThat(result.get("winning_number").asInt()).isEqualTo(12);
        assertThat(result.get("color").asText()).isEqualTo("rojo");
        assertThat(result.get("parity").asText()).isEqualTo("par");
        JsonNode winnings = socket.next("round:winnings");
        assertThat(winnings.get("round_id").asLong()).isEqualTo(round);
        assertThat(winnings.get("payout").asText()).isEqualTo("20.00");

        poller.poll();
        assertThat(socket.settle()).isEmpty();

        long next = newOpenRound(room, 15);
        poller.poll();
        JsonNode started = socket.next("timer:start");
        assertThat(started.get("id").asLong()).isEqualTo(next);
        assertThat(started.get("status").asText()).isEqualTo("OPEN");
        assertThat(socket.next("timer:tick").get("round_id").asLong()).isEqualTo(next);
    }

    @Test
    void siUnaRondaTerminaEntreDosLecturasSeEnviaEnOrden() throws Exception {
        SocketClient socket = roomSocket(web);
        jdbc.update("update game_rounds set status = 'SETTLED', winning_number = 0, settled_at = now() where id = ?",
                round);
        long next = newOpenRound(room, 15);

        poller.poll();
        assertThat(socket.settle()).containsExactly("bets:closed", "timer:stop", "round:result", "timer:start",
                "timer:tick");
        poller.poll();
        assertThat(socket.settle()).containsExactly("timer:tick");
        assertThat(next).isGreaterThan(round);
    }

    @Test
    void elPremioSoloLlegaAQuienApostoYElResultadoATodaLaSala() throws Exception {
        newBet(player, round, "10.00", "0.00", "2026-11-27 10:00:00+00");
        SocketClient mine = roomSocket(web);
        Player other = newPlayer();
        Browser otherWeb = browser();
        otherWeb.login(other.email(), PASSWORD).andExpect(status().isOk());
        SocketClient theirs = roomSocket(otherWeb);
        SocketClient myNotifications = notificationSocket(web);
        mine.drain();

        jdbc.update("update game_rounds set status = 'SETTLED', winning_number = 7, settled_at = now() where id = ?",
                round);
        poller.poll();

        assertThat(mine.next("round:winnings").get("payout").asText()).isEqualTo("0.00");
        assertThat(theirs.settle()).contains("round:result").doesNotContain("round:winnings");
        assertThat(myNotifications.settle()).isEmpty();
    }

    @Test
    void limitesNuevosYJuegoDesactivado() throws Exception {
        SocketClient socket = roomSocket(web);

        jdbc.update("update rooms set min_bet = 50, max_bet = 400 where id = ?", room);
        poller.poll();
        JsonNode limits = socket.next("room:limits");
        assertThat(limits.get("room_id").asLong()).isEqualTo(room);
        assertThat(limits.get("min_bet").asText()).isEqualTo("50.00");
        assertThat(limits.get("max_bet").asText()).isEqualTo("400.00");

        jdbc.update("update roulette_game set is_published = false, deleted_at = now() where id = ?", game);
        poller.poll();
        JsonNode closed = socket.next("room:closed");
        assertThat(closed.get("room_id").asLong()).isEqualTo(room);
        assertThat(closed.get("reason").asText()).isEqualTo("game_deactivated");
        assertThat(socket.closeCode()).isEqualTo(4404);
    }

    // ---- Saldo, avisos e incidentes ---------------------------------------------------------------------------------

    @Test
    void notificacionesNuevasConElContador() throws Exception {
        notification("Antes de conectarse");
        SocketClient socket = open("/ws/notifications/", cookie(web), Browser.ORIGIN);
        assertThat(socket.next("notification:count").get("unread_count").asLong()).isEqualTo(1);
        SocketClient table = roomSocket(web);

        poller.poll();
        assertThat(socket.settle()).isEmpty();

        long id = notification("¡Ganaste en la ruleta!");
        poller.poll();
        JsonNode data = socket.next("notification:new");
        assertThat(data.get("id").asLong()).isEqualTo(id);
        assertThat(data.get("category").asText()).isEqualTo("GAME");
        assertThat(data.get("title").asText()).isEqualTo("¡Ganaste en la ruleta!");
        assertThat(data.get("is_read").asBoolean()).isFalse();
        assertThat(data.get("created_at").asText()).endsWith("+00:00");
        assertThat(data.get("unread_count").asLong()).isEqualTo(2);
        assertThat(table.next("notification:new").get("id").asLong()).isEqualTo(id);

        poller.poll();
        assertThat(socket.settle()).isEmpty();
    }

    @Test
    void saldoQueCambiaDjango() throws Exception {
        SocketClient socket = notificationSocket(web);
        poller.poll();
        assertThat(socket.settle()).isEmpty();

        jdbc.update("update wallets set balance = 5.50, bonus_balance = 0 where user_id = ?", player.id());
        poller.poll();
        JsonNode wallet = socket.next("wallet:update");
        assertThat(wallet.get("balance").asText()).isEqualTo("5.50");
        assertThat(wallet.get("bonus_balance").asText()).isEqualTo("0.00");
        assertThat(wallet.get("winnings_balance").asText()).isEqualTo("5.50");
        assertThat(wallet.get("low_balance_threshold").asText()).isEqualTo("10.00");
        assertThat(wallet.get("is_low_balance").asBoolean()).isTrue();
        poller.poll();
        assertThat(socket.settle()).isEmpty();
    }

    @Test
    void apostarEnSpringActualizaElSaldoAlInstante() throws Exception {
        dailyLimit(player, 2000);
        SocketClient socket = roomSocket(web);
        SocketClient notifications = notificationSocket(web);

        web.create("/api/v1/rooms/" + room + "/bets", Map.of("round_id", round, "kind", "COLOR",
                "selection", "rojo", "chips", List.of(10), "reference", UUID.randomUUID().toString()))
                .andExpect(status().isCreated());

        JsonNode wallet = socket.next("wallet:update");
        assertThat(wallet.get("balance").asText()).isEqualTo("990.00");
        assertThat(wallet.get("bonus_balance").asText()).isEqualTo("990.00");
        assertThat(wallet.get("winnings_balance").asText()).isEqualTo("0.00");
        assertThat(notifications.next("wallet:update").get("balance").asText()).isEqualTo("990.00");
        poller.poll();
        assertThat(notifications.settle()).isEmpty();
    }

    @Test
    void alertaDeRiesgoSoloEnLaSala() throws Exception {
        SocketClient table = roomSocket(web);
        SocketClient notifications = notificationSocket(web);
        poller.poll();
        table.drain();

        long incident = jdbc.queryForObject("""
                insert into responsible_riskincident (created_at, kind, evidence, round_id, user_id)
                values (now(), 'LOSS_ESCALATION', '[]', ?, ?) returning id""", Long.class, round, player.id());
        poller.poll();

        JsonNode alert = table.next("responsible:risk-alert");
        assertThat(alert.get("incident_id").asLong()).isEqualTo(incident);
        assertThat(alert.get("suggested_pause_seconds").asInt()).isEqualTo(600);
        assertThat(alert.get("detail").asText()).contains("pausa de 10 minutos");
        assertThat(notifications.settle()).isEmpty();
        poller.poll();
        assertThat(table.settle()).doesNotContain("responsible:risk-alert");
    }

    // ---- Sesiones ---------------------------------------------------------------------------------------------------

    @Test
    void autoexcluirseCierraLosSocketsAlInstante() throws Exception {
        SocketClient table = roomSocket(web);
        SocketClient notifications = notificationSocket(web);

        web.create("/api/v1/responsible-gaming/self-exclude", "{\"days\": 7}").andExpect(status().isOk());

        assertThat(table.next("session:revoked")).isEmpty();
        assertThat(table.closeCode()).isEqualTo(4401);
        notifications.next("session:revoked");
        assertThat(notifications.closeCode()).isEqualTo(4401);
    }

    @Test
    void cerrarSesionSoloCortaEsaSesion() throws Exception {
        Browser otherTab = browser();
        otherTab.login(player.email(), PASSWORD).andExpect(status().isOk());
        SocketClient closing = notificationSocket(web);
        SocketClient staying = notificationSocket(otherTab);

        web.create("/api/v1/auth/logout", Map.of()).andExpect(status().isOk());

        closing.next("session:revoked");
        assertThat(closing.closeCode()).isEqualTo(4401);
        poller.poll();
        assertThat(staying.isOpen()).isTrue();
    }

    @Test
    void sesionRevocadaPorDjango() throws Exception {
        SocketClient table = roomSocket(web);
        jdbc.update("update accounts_authsession set revoked_at = now() where user_id = ?", player.id());

        poller.poll();
        table.next("session:revoked");
        assertThat(table.closeCode()).isEqualTo(4401);
    }

    @Test
    void cuentaSuspendidaPorElAdministrador() throws Exception {
        SocketClient table = roomSocket(web);
        SocketClient notifications = notificationSocket(web);
        jdbc.update("update users set is_active = false where id = ?", player.id());

        poller.poll();
        assertThat(table.next("FORCE_DISCONNECT").get("reason").asText()).isEqualTo("account_suspended");
        assertThat(table.closeCode()).isEqualTo(4403);
        notifications.next("FORCE_DISCONNECT");
        assertThat(notifications.closeCode()).isEqualTo(4403);
    }

    private long notification(String title) {
        return jdbc.queryForObject("""
                insert into notifications (category, title, message, created_at, user_id)
                values ('GAME', ?, 'Mensaje', now(), ?) returning id""", Long.class, title, player.id());
    }
}
