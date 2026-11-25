package com.tecsup.palacetec.export;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Contenido del historial exportado, común al Excel y al PDF. */
public record HistoryReport(String email, String rangeLabel, List<ExportRow> rows) {

    static final String TITLE = "Historial de apuestas · PalaceTec";
    static final String NOTICE = "Créditos virtuales, sin dinero real";
    static final String TOTALS = "Totales";
    static final List<String> COLUMNS = List.of("Fecha (Lima)", "Juego", "Apuesta", "Monto", "Resultado", "Pago total",
            "Neto", "Saldo tras apostar");
    static final String HEADER_COLOR = "1F2937";

    String countLabel() {
        return rows.size() + " apuestas";
    }

    /** Incluye las pendientes. */
    BigDecimal wagered() {
        return sum(ExportRow::amount);
    }

    BigDecimal paid() {
        return sum(ExportRow::payout);
    }

    /** Solo de las apuestas con resultado. */
    BigDecimal net() {
        return sum(ExportRow::net);
    }

    private BigDecimal sum(Function<ExportRow, BigDecimal> value) {
        return rows.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sin caracteres de control, que no son válidos dentro de un archivo de Excel. */
    static String clean(String text) {
        return text.replaceAll("\\p{Cntrl}", "");
    }
}
