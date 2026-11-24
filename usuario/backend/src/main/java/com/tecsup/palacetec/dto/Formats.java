package com.tecsup.palacetec.dto;

import com.tecsup.palacetec.config.TimeConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Textos idénticos a los que devuelve Django: dinero con dos decimales y fechas ISO 8601. */
public final class Formats {

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter MICROS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter OFFSET = DateTimeFormatter.ofPattern("xxx");

    private Formats() {
    }

    public static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** datetime.isoformat() en UTC, con +00:00. */
    public static String utc(Instant value) {
        return value == null ? null : iso(value, ZoneOffset.UTC);
    }

    /** DateTimeField de DRF: hora de Lima, con -05:00. */
    public static String lima(Instant value) {
        return value == null ? null : iso(value, TimeConfig.LIMA);
    }

    /** JSONEncoder de DRF sobre una fecha UTC: termina en Z. */
    public static String zulu(Instant value) {
        return value == null ? null : utc(value).replace("+00:00", "Z");
    }

    private static String iso(Instant value, ZoneId zone) {
        OffsetDateTime date = value.atZone(zone).toOffsetDateTime();
        return date.format(date.getNano() / 1000 == 0 ? SECONDS : MICROS) + date.format(OFFSET);
    }
}
