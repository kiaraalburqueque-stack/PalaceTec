package com.tecsup.palacetec;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.JwtService;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Base de las pruebas: MockMvc sobre la aplicación completa y la base PostgreSQL de pruebas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected TestMailer mailer;
    @Autowired
    protected TestDeck deck;

    @BeforeEach
    void cleanDatabase() {
        // Candado: estas pruebas borran datos, así que solo corren en una base llamada ..._test.
        assertThat(jdbc.queryForObject("select current_database()", String.class)).endsWith("_test");
        // CASCADE alcanza billeteras, sesiones, apuestas, límites, favoritos y jugadas; no salas ni juegos.
        jdbc.execute("truncate table users cascade");
        jdbc.update("delete from game_rounds");
        jdbc.update("delete from casino_sportevent");
        mailer.clear();
        deck.clear();
        jdbc.update("delete from responsible_excludedperson");
        jdbc.update("delete from rooms where name like 'spring-test-%'");
        jdbc.update("delete from roulette_game where slug like 'spring-test-%'");
    }

    protected static final String PASSWORD = "Prueba#2026";
    /** Hash de PASSWORD generado por Django: las cuentas de prueba equivalen a las creadas desde la web. */
    private static final String DJANGO_HASH = "bcrypt_sha256$$2b$12$xqw0H0kJNTqMpgUrd6Y6FOZopA3BLpbCQTZ53WN.GDMLmdx4IjWSG";

    @Autowired
    private JwtService jwt;
    @Autowired
    private UserRepository users;
    private int sequence;

    /** Jugador ficticio con billetera y bono; token es un JWT con su sesión guardada. */
    protected record Player(long id, String email, String dni, String token) {

        public RequestPostProcessor bearer() {
            return request -> {
                request.addHeader("Authorization", "Bearer " + token);
                return request;
            };
        }
    }

    protected Browser browser() {
        return new Browser(mvc, mapper);
    }

    protected Player newPlayer() {
        sequence++;
        String email = "jugador" + sequence + "@example.test";
        String dni = String.valueOf(90000000 + sequence);
        Long id = jdbc.queryForObject("""
                insert into users (password, is_superuser, email, dni, birth_date, is_active, is_staff, created_at,
                    failed_attempts, avatar_url, auth_version, first_name, last_name, phone)
                values (?, false, ?, ?, date '2000-01-01', true, false, now(), 0, '/avatars/avatar-1.svg', 0, '', '', '')
                returning id""", Long.class, DJANGO_HASH, email, dni);
        jdbc.update("insert into wallets (user_id, balance, bonus_balance) values (?, 1000, 1000)", id);
        JwtService.IssuedToken token = jwt.issue(users.findById(id).orElseThrow());
        jdbc.update("insert into accounts_authsession (user_id, jti, started_at, expires_at) values (?, ?, ?, ?)",
                id, token.jti(), Timestamp.from(token.issuedAt()), Timestamp.from(token.expiresAt()));
        return new Player(id, email, dni, token.value());
    }

    protected long newGame(String name) {
        return jdbc.queryForObject("""
                insert into roulette_game (slug, title, is_published, category, cover, min_bet, max_bet, rtp,
                    payout_table, rules, play_kind, play_path, is_featured)
                values (?, ?, true, 'RULETA', '', 10, 500, 97.30, '[]', '', '', '', false)
                returning id""", Long.class, "spring-test-" + name, "Juego " + name);
    }

    protected long newRoom(long game, int minBet, int maxBet) {
        return jdbc.queryForObject("insert into rooms (name, is_active, min_bet, max_bet, game_id) "
                + "values ('spring-test-sala', true, ?, ?, ?) returning id", Long.class, minBet, maxBet, game);
    }

    /** Ronda abierta como la crea runroulette; closesIn negativo simula un plazo vencido aún sin cerrar. */
    protected long newOpenRound(long room, int closesIn) {
        return jdbc.queryForObject("""
                insert into game_rounds (room_id, status, opened_at, closes_at)
                values (?, 'OPEN', now() - interval '1 minute', now() + make_interval(secs => ?))
                returning id""", Long.class, room, closesIn);
    }

    protected long newSettledRound(long room, int number) {
        return jdbc.queryForObject("""
                insert into game_rounds (room_id, status, opened_at, closes_at, winning_number, settled_at)
                values (?, 'SETTLED', clock_timestamp() - interval '20 seconds',
                    clock_timestamp() - interval '5 seconds', ?, clock_timestamp())
                returning id""", Long.class, room, number);
    }

    /** Apuesta de 'rojo' en esa ronda; payout null si está pendiente. Las fechas son textos de PostgreSQL. */
    protected long newBet(Player owner, long round, String amount, String payout, String createdAt) {
        return jdbc.queryForObject("""
                insert into user_bets (user_id, round_id, kind, selection, chips, amount, payout, reference, created_at)
                values (?, ?, 'COLOR', 'rojo', '[10]', cast(? as numeric), cast(? as numeric), gen_random_uuid(),
                    cast(? as timestamptz))
                returning id""", Long.class, owner.id(), round, amount, payout, createdAt);
    }

    protected long newPlay(Player owner, String kind, String amount, String payout, String createdAt) {
        return jdbc.queryForObject("""
                insert into casino_play (user_id, kind, amount, payout, reference, description, created_at, settled_at)
                values (?, ?, cast(? as numeric), cast(? as numeric), gen_random_uuid(), 'Jugada',
                    cast(? as timestamptz), case when cast(? as numeric) is null then null else now() end)
                returning id""", Long.class, owner.id(), kind, amount, payout, createdAt, payout);
    }

    protected long seededGame(String slug) {
        return jdbc.queryForObject("select id from roulette_game where slug = ?", Long.class, slug);
    }

    protected void dailyLimit(Player player, int amount) {
        jdbc.update("insert into responsible_dailylimit (user_id, amount) values (?, ?) "
                + "on conflict (user_id) do update set amount = excluded.amount", player.id(), amount);
    }

    /** Primer grupo de la expresión; falla si no aparece. */
    protected static String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).as("%s en %s", regex, text).isTrue();
        return matcher.group(1);
    }

    protected long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
