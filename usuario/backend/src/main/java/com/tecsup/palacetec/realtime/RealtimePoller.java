package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.RoomStateResponse;
import com.tecsup.palacetec.dto.WalletSummaryResponse;
import com.tecsup.palacetec.entity.GameRound;
import com.tecsup.palacetec.realtime.RealtimeQueries.IncidentRow;
import com.tecsup.palacetec.realtime.RealtimeQueries.NotificationRow;
import com.tecsup.palacetec.realtime.RealtimeQueries.RoomRow;
import com.tecsup.palacetec.realtime.RealtimeQueries.SessionRow;
import com.tecsup.palacetec.repository.GameRoundRepository;
import com.tecsup.palacetec.service.RouletteTable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Emite en Spring los eventos que en Django publica after_commit. Las rondas las mueve runroulette y Spring no recibe sus
 * mensajes de Channels, así que cada segundo lee la base y envía solo lo que cambió desde la lectura anterior. Solo
 * trabaja mientras haya sockets abiertos. Todo el estado vive en este objeto y se usa con su candado.
 */
@Component
public class RealtimePoller {

    /** Cada cuánto el consumer de Django renueva la conexión y revisa la sesión completa. */
    static final Duration HEARTBEAT = Duration.ofSeconds(20);
    /** Pausa que sugiere responsible:risk-alert, como en Django. */
    private static final int SUGGESTED_PAUSE_SECONDS = 600;

    private static final Logger log = LoggerFactory.getLogger(RealtimePoller.class);

    private final RealtimeHub hub;
    private final RealtimeQueries queries;
    private final RealtimeService service;
    private final GameRoundRepository rounds;
    private final Clock clock;
    private final boolean enabled;
    private final Map<Long, RoomSnapshot> roomSnapshots = new HashMap<>();
    private final Map<Long, UserSnapshot> userSnapshots = new HashMap<>();

    public RealtimePoller(RealtimeHub hub, RealtimeQueries queries, RealtimeService service,
                          GameRoundRepository rounds, Clock clock,
                          @Value("${palacetec.realtime.polling:true}") boolean enabled) {
        this.hub = hub;
        this.queries = queries;
        this.service = service;
        this.rounds = rounds;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** Últimos límites y ronda vistos de una sala. */
    private static final class RoomSnapshot {
        BigDecimal minBet;
        BigDecimal maxBet;
        Long roundId;
        GameRound.Status status;
    }

    /** Saldo, avisos e incidentes ya enviados (o ya existentes al conectarse) de un jugador. */
    private static final class UserSnapshot {
        WalletSummaryResponse wallet;
        final Set<Long> notifications = new HashSet<>();
        final Set<Long> incidents = new HashSet<>();
    }

    @Scheduled(fixedDelayString = "${palacetec.realtime.poll-interval-ms:1000}")
    public void scheduled() {
        if (enabled) {
            poll();
        }
    }

    /** Un socket nuevo parte del estado actual: solo recibe lo que cambie desde ahora, como en Django. */
    public synchronized void track(Connection connection) {
        userSnapshots.computeIfAbsent(connection.userId(), this::seedUser);
        if (connection.kind() == Connection.Kind.ROOM) {
            roomSnapshots.computeIfAbsent(connection.roomId(), this::seedRoom);
        }
    }

    public synchronized void poll() {
        if (hub.all().isEmpty()) {
            roomSnapshots.clear();
            userSnapshots.clear();
            return;
        }
        Instant now = TimeConfig.now(clock);
        step("sesiones", () -> checkSessions(now));
        step("salas", () -> pollRooms(now));
        step("conexiones", () -> heartbeat(now));
        step("jugadores", this::pollUsers);
    }

    /** Un saldo que movió Spring: se envía al confirmarse, y el sondeo ya no lo repite. */
    public synchronized void walletChanged(Long userId, WalletSummaryResponse wallet) {
        UserSnapshot snapshot = userSnapshots.get(userId);
        if (snapshot != null && !wallet.equals(snapshot.wallet)) {
            snapshot.wallet = wallet;
            hub.toUser(userId, "wallet:update", wallet);
        }
    }

    private static void step(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // Un fallo (por ejemplo, la base caída) no detiene el resto: el siguiente segundo se reintenta.
            log.warn("Fallo el sondeo de {}; se reintentara.", name, exception);
        }
    }

    // ---- Sesiones -------------------------------------------------------------------------------------------------

    /**
     * Sesiones revocadas o vencidas, cuentas desactivadas o autoexcluidas: lo que en Django avisan session:revoked y
     * FORCE_DISCONNECT. El token de acceso vencido cierra con 4401 sin aviso, para que React lo renueve.
     */
    private void checkSessions(Instant now) {
        List<Connection> open = hub.all();
        Map<String, SessionRow> rows = queries.sessions(open.stream()
                .map(connection -> connection.user().player().jti()).collect(Collectors.toSet()));
        for (Connection connection : open) {
            SessionRow row = rows.get(connection.user().player().jti());
            boolean mine = row != null && row.userId() == connection.userId();
            if (!connection.user().expiresAt().isAfter(now)) {
                connection.close(Connection.UNAUTHORIZED);
            } else if (mine && !row.active()) {
                connection.forceDisconnect();
            } else if (!mine || !row.usable() || row.excluded()
                    || row.version() != connection.user().player().version()) {
                connection.revoke();
            }
        }
    }

    /** El heartbeat de 20 s de Django: revisa la sesión completa (también el DNI excluido) y renueva la conexión. */
    private void heartbeat(Instant now) {
        for (Connection connection : hub.all()) {
            if (!now.isBefore(connection.nextCheck())) {
                connection.nextCheck(now.plus(HEARTBEAT));
                if (!service.refresh(connection)) {
                    connection.close(Connection.UNAUTHORIZED);
                }
            }
        }
    }

    // ---- Salas y rondas -------------------------------------------------------------------------------------------

    private RoomSnapshot seedRoom(Long roomId) {
        RoomRow row = queries.rooms(Set.of(roomId)).get(roomId);
        if (row == null) {
            return null;
        }
        RoomSnapshot snapshot = new RoomSnapshot();
        snapshot.minBet = row.minBet();
        snapshot.maxBet = row.maxBet();
        rounds.findFirstByRoomIdOrderByIdDesc(roomId).ifPresent(round -> {
            snapshot.roundId = round.getId();
            snapshot.status = round.getStatus();
        });
        return snapshot;
    }

    private void pollRooms(Instant now) {
        Set<Long> ids = hub.all().stream().filter(connection -> connection.kind() == Connection.Kind.ROOM)
                .map(Connection::roomId).collect(Collectors.toSet());
        roomSnapshots.keySet().retainAll(ids);
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, RoomRow> rows = queries.rooms(ids);
        for (Long roomId : ids) {
            RoomRow row = rows.get(roomId);
            if (row == null || !row.available()) {
                closeRoom(roomId, row);
                continue;
            }
            RoomSnapshot snapshot = roomSnapshots.computeIfAbsent(roomId, this::seedRoom);
            if (snapshot == null) {
                continue;
            }
            if (row.minBet().compareTo(snapshot.minBet) != 0 || row.maxBet().compareTo(snapshot.maxBet) != 0) {
                snapshot.minBet = row.minBet();
                snapshot.maxBet = row.maxBet();
                Map<String, Object> limits = new LinkedHashMap<>();
                limits.put("room_id", roomId);
                limits.put("min_bet", Formats.money(row.minBet()));
                limits.put("max_bet", Formats.money(row.maxBet()));
                hub.toRoom(roomId, "room:limits", limits);
            }
            advanceRounds(roomId, snapshot, now);
        }
    }

    /**
     * room:closed y cierre con 4404. Django publica el mismo evento, pero su consumer cierra con 4401 antes de
     * enviarlo porque la sala ya no está disponible; aquí React sí recibe el aviso.
     */
    private void closeRoom(Long roomId, RoomRow row) {
        roomSnapshots.remove(roomId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room_id", roomId);
        data.put("reason", row != null && !row.gameAvailable() ? "game_deactivated" : "room_deactivated");
        hub.each(connection -> connection.kind() == Connection.Kind.ROOM && roomId.equals(connection.roomId()),
                connection -> {
                    connection.send("room:closed", data);
                    connection.close(Connection.NOT_FOUND);
                });
    }

    /**
     * Repite, en orden, los eventos de RouletteService.tick para cada ronda que cambió: timer:start, bets:closed y
     * timer:stop, round:result y round:winnings. Si entre dos lecturas terminó una ronda y empezó otra, primero se
     * cierra la anterior. Mientras la última siga abierta, timer:tick cada segundo.
     */
    private void advanceRounds(Long roomId, RoomSnapshot snapshot, Instant now) {
        List<GameRound> changed = snapshot.roundId == null
                ? rounds.findFirstByRoomIdOrderByIdDesc(roomId).stream().toList()
                : rounds.findByRoomIdAndIdGreaterThanEqualOrderByIdAsc(roomId, snapshot.roundId);
        GameRound last = null;
        for (GameRound round : changed) {
            GameRound.Status before = round.getId().equals(snapshot.roundId) ? snapshot.status : null;
            int from = before == null ? -1 : before.ordinal();
            int to = round.getStatus().ordinal();
            if (before == null && round.getStatus() == GameRound.Status.OPEN) {
                hub.toRoom(roomId, "timer:start", RoomStateResponse.Round.from(round, now));
            }
            if (from < GameRound.Status.CLOSED.ordinal() && to >= GameRound.Status.CLOSED.ordinal()) {
                hub.toRoom(roomId, "bets:closed", Map.of("round_id", round.getId()));
                hub.toRoom(roomId, "timer:stop", Map.of("round_id", round.getId()));
            }
            if (from < GameRound.Status.SETTLED.ordinal() && round.getStatus() == GameRound.Status.SETTLED) {
                settled(roomId, round);
            }
            snapshot.roundId = round.getId();
            snapshot.status = round.getStatus();
            last = round;
        }
        if (last != null && last.getStatus() == GameRound.Status.OPEN) {
            Map<String, Object> tick = new LinkedHashMap<>();
            tick.put("round_id", last.getId());
            tick.put("closes_at", Formats.utc(last.getClosesAt()));
            tick.put("remaining_seconds", RoomStateResponse.Round.from(last, now).remainingSeconds());
            hub.toRoom(roomId, "timer:tick", tick);
        }
    }

    private void settled(Long roomId, GameRound round) {
        int number = round.getWinningNumber();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("round_id", round.getId());
        result.put("winning_number", number);
        result.put("color", RouletteTable.color(number));
        result.put("parity", number == 0 ? null : number % 2 == 0 ? "par" : "impar");
        result.put("settled_at", Formats.utc(round.getSettledAt()));
        hub.toRoom(roomId, "round:result", result);
        queries.winnings(round.getId()).forEach((userId, payout) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("round_id", round.getId());
            data.put("payout", Formats.money(payout));
            hub.toUser(userId, "round:winnings", data);
        });
    }

    // ---- Jugadores: saldo, avisos e incidentes --------------------------------------------------------------------

    private UserSnapshot seedUser(Long userId) {
        UserSnapshot snapshot = new UserSnapshot();
        Set<Long> ids = Set.of(userId);
        snapshot.wallet = queries.wallets(ids).get(userId);
        queries.recentNotifications(ids).forEach(row -> snapshot.notifications.add(row.id()));
        queries.recentIncidents(ids).forEach(row -> snapshot.incidents.add(row.id()));
        return snapshot;
    }

    private void pollUsers() {
        Set<Long> ids = hub.all().stream().map(Connection::userId).collect(Collectors.toSet());
        userSnapshots.keySet().retainAll(ids);
        if (ids.isEmpty()) {
            return;
        }
        ids.forEach(userId -> userSnapshots.computeIfAbsent(userId, this::seedUser));
        queries.wallets(ids).forEach((userId, wallet) -> {
            UserSnapshot snapshot = userSnapshots.get(userId);
            if (!wallet.equals(snapshot.wallet)) {
                boolean known = snapshot.wallet != null;
                snapshot.wallet = wallet;
                if (known) {
                    hub.toUser(userId, "wallet:update", wallet);
                }
            }
        });
        pollNotifications(ids);
        pollIncidents(ids);
    }

    private void pollNotifications(Set<Long> ids) {
        Map<Long, List<NotificationRow>> byUser = queries.recentNotifications(ids).stream()
                .collect(Collectors.groupingBy(NotificationRow::userId));
        for (Long userId : ids) {
            Set<Long> sent = userSnapshots.get(userId).notifications;
            List<NotificationRow> recent = byUser.getOrDefault(userId, List.of());
            List<NotificationRow> fresh = recent.stream().filter(row -> !sent.contains(row.id())).toList();
            sent.retainAll(recent.stream().map(NotificationRow::id).collect(Collectors.toSet()));
            if (fresh.isEmpty()) {
                continue;
            }
            long unread = queries.unread(userId);
            for (NotificationRow row : fresh) {
                sent.add(row.id());
                Map<String, Object> data = new LinkedHashMap<>(row.data());
                data.put("unread_count", unread);
                hub.toUser(userId, "notification:new", data);
            }
        }
    }

    private void pollIncidents(Set<Long> ids) {
        Map<Long, List<IncidentRow>> byUser = queries.recentIncidents(ids).stream()
                .collect(Collectors.groupingBy(IncidentRow::userId));
        for (Long userId : ids) {
            Set<Long> sent = userSnapshots.get(userId).incidents;
            List<IncidentRow> recent = byUser.getOrDefault(userId, List.of());
            Map<Long, IncidentRow> byId = recent.stream()
                    .collect(Collectors.toMap(IncidentRow::id, Function.identity()));
            for (IncidentRow row : recent) {
                if (sent.add(row.id())) {
                    hub.toUser(userId, "responsible:risk-alert", riskAlert(row));
                }
            }
            sent.retainAll(byId.keySet());
        }
    }

    /** Los textos de responsible/pattern_detector.py: uno para la ruleta y otro para los demás juegos. */
    private static Map<String, Object> riskAlert(IncidentRow row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("incident_id", row.id());
        data.put("suggested_pause_seconds", SUGGESTED_PAUSE_SECONDS);
        data.put("detail", row.roulette()
                ? "Tus apuestas aumentaron tras varias perdidas. Te sugerimos una pausa de 10 minutos."
                : "Tus apuestas aumentaron tras varias perdidas. Considera una pausa.");
        return data;
    }
}
