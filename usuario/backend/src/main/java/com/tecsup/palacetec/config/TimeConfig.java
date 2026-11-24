package com.tecsup.palacetec.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Día calendario del casino (edad, límite diario y filtros de fecha), igual que TIME_ZONE de Django. */
    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** PostgreSQL guarda microsegundos: truncar evita que la respuesta difiera de lo guardado. */
    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
