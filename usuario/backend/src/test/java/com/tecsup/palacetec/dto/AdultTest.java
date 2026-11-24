package com.tecsup.palacetec.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.tecsup.palacetec.dto.Adult.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdultTest {

    @ParameterizedTest
    @CsvSource({"2008-11-24,2026-11-24,true", "2008-11-25,2026-11-24,false", "2008-02-29,2026-02-28,false",
            "2008-02-29,2026-03-01,true", "2008-12-01,2026-11-30,false"})
    void requiresEighteenCompletedYears(LocalDate birthDate, LocalDate today, boolean adult) {
        assertThat(Validator.isAdult(birthDate, today)).isEqualTo(adult);
    }

    @Test
    void usesTheDateInLimaAndNotUtc() {
        // 03:00 UTC del 25 todavía es 24 de noviembre en Lima.
        Validator validator = new Validator(Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneOffset.UTC));
        assertThat(validator.isValid(LocalDate.of(2008, 11, 24), null)).isTrue();
        assertThat(validator.isValid(LocalDate.of(2008, 11, 25), null)).isFalse();
    }
}
