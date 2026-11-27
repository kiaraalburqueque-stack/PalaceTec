package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.WalletSummaryResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lecturas del sondeo, una consulta por tipo para todos los sockets abiertos. Solo lee: lo escriben Django (runroulette,
 * el panel) y los servicios de Spring.
 */
@Component
public class RealtimeQueries {

    /** Lo creado en este plazo se compara con lo ya enviado; un aviso más antiguo ya no es "nuevo". */
    static final int RECENT_SECONDS = 120;

    private final NamedParameterJdbcTemplate jdbc;

    public RealtimeQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SessionRow(String sid, long userId, boolean usable, boolean active, int version, boolean excluded) {
    }

    public record RoomRow(long id, BigDecimal minBet, BigDecimal maxBet, boolean active, boolean gameAvailable) {

        public boolean available() {
            return active && gameAvailable;
        }
    }

    public record NotificationRow(long id, long userId, Map<String, Object> data) {
    }

    public record IncidentRow(long id, long userId, boolean roulette) {
    }

    /** Estado de las sesiones (por sid) y de sus cuentas, con la hora de la base. */
    public Map<String, SessionRow> sessions(Collection<String> sids) {
        Map<String, SessionRow> rows = new HashMap<>();
        jdbc.query("""
                select s.jti, s.user_id, s.revoked_at is null and s.expires_at > now() as usable, u.is_active,
                       u.auth_version, coalesce(u.self_excluded_until > now(), false) as excluded
                from accounts_authsession s join users u on u.id = s.user_id
                where s.jti in (:sids)""", new MapSqlParameterSource("sids", sids), (ResultSet rs) -> {
            rows.put(rs.getString("jti"), new SessionRow(rs.getString("jti"), rs.getLong("user_id"),
                    rs.getBoolean("usable"), rs.getBoolean("is_active"), rs.getInt("auth_version"),
                    rs.getBoolean("excluded")));
        });
        return rows;
    }

    /** Límites y disponibilidad de cada sala: activa y, si tiene juego, publicado y sin borrar. */
    public Map<Long, RoomRow> rooms(Collection<Long> ids) {
        Map<Long, RoomRow> rows = new HashMap<>();
        jdbc.query("""
                select r.id, r.min_bet, r.max_bet, r.is_active,
                       (g.id is null or (g.is_published and g.deleted_at is null)) as game_available
                from rooms r left join roulette_game g on g.id = r.game_id
                where r.id in (:ids)""", new MapSqlParameterSource("ids", ids), (ResultSet rs) -> {
            rows.put(rs.getLong("id"), new RoomRow(rs.getLong("id"), rs.getBigDecimal("min_bet"),
                    rs.getBigDecimal("max_bet"), rs.getBoolean("is_active"), rs.getBoolean("game_available")));
        });
        return rows;
    }

    /** Lo que envía wallet:update, desde la vista wallet_balances. */
    public Map<Long, WalletSummaryResponse> wallets(Collection<Long> users) {
        Map<Long, WalletSummaryResponse> rows = new HashMap<>();
        jdbc.query("""
                select user_id, balance, bonus_balance, winnings_balance from wallet_balances
                where user_id in (:users)""", new MapSqlParameterSource("users", users), (ResultSet rs) -> {
            rows.put(rs.getLong("user_id"), WalletSummaryResponse.of(rs.getBigDecimal("balance"),
                    rs.getBigDecimal("bonus_balance"), rs.getBigDecimal("winnings_balance")));
        });
        return rows;
    }

    /** Avisos recientes, con los campos de Notification.as_dict() de Django. */
    public List<NotificationRow> recentNotifications(Collection<Long> users) {
        return jdbc.query("""
                select id, user_id, category, title, message, read_at, created_at from notifications
                where user_id in (:users) and created_at > now() - make_interval(secs => :seconds)
                order by id""", recent(users), (rs, row) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", rs.getLong("id"));
            data.put("category", rs.getString("category"));
            data.put("title", rs.getString("title"));
            data.put("message", rs.getString("message"));
            data.put("is_read", rs.getObject("read_at") != null);
            data.put("created_at", Formats.utc(instant(rs, "created_at")));
            return new NotificationRow(rs.getLong("id"), rs.getLong("user_id"), data);
        });
    }

    public long unread(long userId) {
        Long count = jdbc.queryForObject("select count(*) from notifications where user_id = :user and read_at is null",
                new MapSqlParameterSource("user", userId), Long.class);
        return count == null ? 0 : count;
    }

    public List<IncidentRow> recentIncidents(Collection<Long> users) {
        return jdbc.query("""
                select id, user_id, round_id from responsible_riskincident
                where user_id in (:users) and created_at > now() - make_interval(secs => :seconds)
                order by id""", recent(users), (rs, row) -> new IncidentRow(rs.getLong("id"), rs.getLong("user_id"),
                rs.getObject("round_id") != null));
    }

    /** round:winnings: total cobrado por cada jugador que apostó en la ronda, aunque sea cero. */
    public Map<Long, BigDecimal> winnings(long roundId) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        jdbc.query("""
                select user_id, coalesce(sum(payout), 0) as payout from user_bets
                where round_id = :round group by user_id order by user_id""",
                new MapSqlParameterSource("round", roundId),
                (ResultSet rs) -> {
                    totals.put(rs.getLong("user_id"), rs.getBigDecimal("payout"));
                });
        return totals;
    }

    private static MapSqlParameterSource recent(Collection<Long> users) {
        return new MapSqlParameterSource("users", users).addValue("seconds", RECENT_SECONDS);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
