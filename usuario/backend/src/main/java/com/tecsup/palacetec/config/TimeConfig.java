package com.tecsup.palacetec.config;

import java.time.Clock;
import java.time.ZoneId;
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
}
