package com.tecsup.palacetec.dto;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Filtro startDate/endDate (AAAA-MM-DD): días completos de Lima, con el final incluido. */
public record DateRange(Instant start, Instant end) {

    private static final Pattern DATE = Pattern.compile("([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})");
    private static final Instant MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("9999-12-31T00:00:00Z");

    public static DateRange since(Instant start) {
        return new DateRange(start, MAX);
    }

    public static DateRange parse(String startDate, String endDate) {
        LocalDate start = date("startDate", startDate);
        LocalDate end = date("endDate", endDate);
        if (start != null && end != null && start.isAfter(end)) {
            throw InvalidFieldsException.field("endDate", "La fecha final debe ser igual o posterior al inicio.");
        }
        if (end != null && end.getYear() == 9999) {
            throw InvalidFieldsException.field("endDate", "La fecha final excede el rango permitido.");
        }
        return of(start, end);
    }

    /** Días completos de Lima; null no limita. */
    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start == null ? MIN : startOfDay(start), end == null ? MAX : startOfDay(end.plusDays(1)));
    }

    private static LocalDate date(String field, String value) {
        if (value == null) {
            return null;
        }
        LocalDate date = parseDate(value);
        if (date == null) {
            throw InvalidFieldsException.field(field, "Usa el formato AAAA-MM-DD.");
        }
        return date;
    }

    /** strptime con %Y-%m-%d de Python; null si no es una fecha. */
    static LocalDate parseDate(String value) {
        Matcher matcher = DATE.matcher(value);
        try {
            if (matcher.matches() && Integer.parseInt(matcher.group(1)) > 0) {
                return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)));
            }
        } catch (DateTimeException ignored) {
            // Fecha inexistente, como 2026-02-30.
        }
        return null;
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(TimeConfig.LIMA).toInstant();
    }
}
