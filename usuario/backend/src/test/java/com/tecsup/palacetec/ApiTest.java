package com.tecsup.palacetec;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.JwtService;
import java.sql.Timestamp;
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

    @BeforeEach
    void cleanDatabase() {
        // Candado: estas pruebas borran datos, así que solo corren en una base llamada ..._test.
        assertThat(jdbc.queryForObject("select current_database()", String.class)).endsWith("_test");
        // CASCADE alcanza billeteras, sesiones, apuestas, límites, favoritos y jugadas; no salas ni juegos.
        jdbc.execute("truncate table users cascade");
        jdbc.update("delete from game_rounds");
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

    protected long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
