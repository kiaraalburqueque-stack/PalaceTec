package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.BetResponse;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.RoomStateResponse;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.Room;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.NotificationRepository;
import com.tecsup.palacetec.repository.RoomRepository;
import com.tecsup.palacetec.service.RoomService;
import com.tecsup.palacetec.service.SessionService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lo que los consumers de Django hacen en la base: room_connections, la reconexión y el contador de avisos. */
@Service
public class RealtimeService {

    /** RoomConnection.expires_at de Django: runroulette borra las vencidas. */
    static final Duration CONNECTION_TTL = Duration.ofSeconds(60);
    private static final Set<String> PENDING = Set.of("OPEN", "CLOSED");

    private final RoomRepository rooms;
    private final RoomService roomService;
    private final BetRepository bets;
    private final NotificationRepository notifications;
    private final SessionService sessions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RealtimeService(RoomRepository rooms, RoomService roomService, BetRepository bets,
                           NotificationRepository notifications, SessionService sessions, JdbcTemplate jdbc,
                           Clock clock) {
        this.rooms = rooms;
        this.roomService = roomService;
        this.bets = bets;
        this.notifications = notifications;
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** join_room: con la sala bloqueada y disponible se registra la conexión, que cuenta como jugador en la sala. */
    @Transactional
    public boolean join(Connection connection) {
        if (rooms.lockById(connection.roomId()).filter(Room::isAvailable).isEmpty()) {
            return false;
        }
        jdbc.update("insert into room_connections (channel_name, expires_at, room_id, user_id) values (?, ?, ?, ?)",
                connection.channelName(), expiry(), connection.roomId(), connection.userId());
        return true;
    }

    /**
     * refresh_connection de Django: cuenta permitida, sesión vigente y, en una sala, que siga disponible. Renueva el
     * vencimiento de la conexión. Fuera de una transacción, para que un rechazo no la deje marcada para deshacer.
     */
    public boolean refresh(Connection connection) {
        try {
            sessions.check(connection.user().player());
        } catch (ApiException exception) {
            return false;
        }
        return connection.kind() == Connection.Kind.NOTIFICATIONS || touch(connection);
    }

    private boolean touch(Connection connection) {
        if (rooms.findById(connection.roomId()).filter(Room::isAvailable).isEmpty()) {
            return false;
        }
        return jdbc.update("""
                update room_connections c set expires_at = ?
                where c.channel_name = ?
                  and exists (select 1 from rooms r where r.id = c.room_id and r.is_active)
                  and exists (select 1 from users u where u.id = c.user_id and u.is_active)""",
                expiry(), connection.channelName()) > 0;
    }

    @Transactional
    public void leave(Connection connection) {
        jdbc.update("delete from room_connections where channel_name = ?", connection.channelName());
    }

    public RoomStateResponse state(Long roomId) {
        return roomService.state(roomId);
    }

    /** HU59, room:resume: la ronda en curso y las apuestas del jugador que aún no se liquidan. */
    @Transactional(readOnly = true)
    public Map<String, Object> resume(Long userId, Long roomId) {
        RoomStateResponse state = roomService.state(roomId);
        RoomStateResponse.Round round = state.round();
        List<Bet> pending = round != null && PENDING.contains(round.status())
                ? bets.findByRound(userId, round.id()) : List.of();
        BigDecimal total = pending.stream().map(Bet::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", state);
        data.put("round_id", round == null ? null : round.id());
        data.put("bets", pending.stream().map(BetResponse::from).toList());
        data.put("total_amount", Formats.money(total));
        return data;
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    private Timestamp expiry() {
        return Timestamp.from(TimeConfig.now(clock).plus(CONNECTION_TTL));
    }
}
