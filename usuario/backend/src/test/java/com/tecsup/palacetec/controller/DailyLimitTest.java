package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.config.TimeConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DailyLimitTest extends ApiTest {

    private static final String PATH = "/api/v1/responsible-gaming/daily-limit";

    private ResultActions read(Player player) throws Exception {
        return mvc.perform(get(PATH).with(player.bearer()));
    }

    private ResultActions set(Player player, String amount) throws Exception {
        return mvc.perform(post(PATH).with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"));
    }

    @Test
    void startsWithoutALimit() throws Exception {
        read(newPlayer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.limit").value(nullValue()))
                .andExpect(jsonPath("$.spent").value("0.00"))
                .andExpect(jsonPath("$.remaining").value(nullValue()))
                .andExpect(jsonPath("$.pending_limit").value(nullValue()))
                .andExpect(jsonPath("$.effective_at").value(nullValue()))
                .andExpect(jsonPath("$.date").value(LocalDate.now(TimeConfig.LIMA).toString()))
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void firstLimitAndReductionsAreImmediate() throws Exception {
        Player player = newPlayer();
        set(player, "\"100.00\"").andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.limit").value("100.00")).andExpect(jsonPath("$.remaining").value("100.00"));
        set(player, "50.5").andExpect(jsonPath("$.limit").value("50.50"))
                .andExpect(jsonPath("$.pending_limit").value(nullValue()));
        set(player, "0").andExpect(jsonPath("$.limit").value("0.00")).andExpect(jsonPath("$.remaining").value("0.00"));
        assertThat(count("responsible_dailylimit")).isEqualTo(1);
    }

    @Test
    void increasesWaitTwentyFourHoursAndRepeatingDoesNotRestartThem() throws Exception {
        Player player = newPlayer();
        set(player, "100");
        String effective = json(set(player, "300").andExpect(jsonPath("$.limit").value("100.00"))
                .andExpect(jsonPath("$.pending_limit").value("300.00"))
                .andExpect(jsonPath("$.effective_at").value(endsWith("+00:00"))).andReturn())
                .get("effective_at").asText();
        long minutes = ChronoUnit.MINUTES.between(Instant.now(), OffsetDateTime.parse(effective).toInstant());
        assertThat(minutes).isBetween(1438L, 1440L);
        set(player, "300").andExpect(jsonPath("$.effective_at").value(effective));
        String changed = json(set(player, "400").andExpect(jsonPath("$.pending_limit").value("400.00")).andReturn())
                .get("effective_at").asText();
        assertThat(changed).isGreaterThan(effective);
        // Una reducción elimina el aumento pendiente.
        set(player, "80").andExpect(jsonPath("$.limit").value("80.00"))
                .andExpect(jsonPath("$.pending_limit").value(nullValue()));
    }

    @Test
    void pendingIncreaseActivatesWhenItsTimeComes() throws Exception {
        Player player = newPlayer();
        set(player, "100");
        set(player, "300");
        jdbc.update("update responsible_dailylimit set effective_at = now() - interval '1 second'");
        read(player).andExpect(jsonPath("$.limit").value("300.00"))
                .andExpect(jsonPath("$.pending_limit").value(nullValue()));
        assertThat(jdbc.queryForObject("select amount from responsible_dailylimit", String.class)).isEqualTo("300.00");
    }

    @Test
    void onlyTodaysSpendingInLimaCounts() throws Exception {
        Player player = newPlayer();
        set(player, "100");
        LocalDate today = LocalDate.now(TimeConfig.LIMA);
        jdbc.update("insert into responsible_dailyspending (user_id, date, amount) values (?, ?, 90)",
                player.id(), java.sql.Date.valueOf(today.minusDays(1)));
        read(player).andExpect(jsonPath("$.spent").value("0.00"));
        jdbc.update("insert into responsible_dailyspending (user_id, date, amount) values (?, ?, 120)",
                player.id(), java.sql.Date.valueOf(today));
        read(player).andExpect(jsonPath("$.spent").value("120.00")).andExpect(jsonPath("$.remaining").value("0.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.005", "12345678901", "\"abc\"", "\"NaN\"", "null", "true", "\"\""})
    void rejectsInvalidAmounts(String amount) throws Exception {
        set(newPlayer(), amount).andExpect(status().isBadRequest()).andExpect(jsonPath("$.amount").isArray());
        assertThat(count("responsible_dailylimit")).isZero();
    }
}
