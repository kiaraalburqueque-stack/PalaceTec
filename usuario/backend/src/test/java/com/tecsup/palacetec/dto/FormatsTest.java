package com.tecsup.palacetec.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FormatsTest {

    @Test
    void moneyAlwaysHasTwoDecimals() {
        assertThat(Formats.money(new BigDecimal("1E+3"))).isEqualTo("1000.00");
        assertThat(Formats.money(BigDecimal.ZERO)).isEqualTo("0.00");
        assertThat(Formats.money(null)).isNull();
    }

    @Test
    void datesMatchTheThreeFormatsUsedByDjango() {
        Instant micros = Instant.parse("2026-11-24T15:00:00.123400Z");
        assertThat(Formats.utc(micros)).isEqualTo("2026-11-24T15:00:00.123400+00:00");
        assertThat(Formats.lima(micros)).isEqualTo("2026-11-24T10:00:00.123400-05:00");
        assertThat(Formats.zulu(micros)).isEqualTo("2026-11-24T15:00:00.123400Z");
        assertThat(Formats.utc(Instant.parse("2026-11-24T15:00:00Z"))).isEqualTo("2026-11-24T15:00:00+00:00");
    }
}
