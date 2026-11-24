package com.tecsup.palacetec;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
