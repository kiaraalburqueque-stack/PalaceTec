package com.tecsup.palacetec.dto;

import jakarta.servlet.http.HttpServletRequest;

/** Formato y fechas de la exportación del historial. */
public record ExportFilter(Format format, DateFilter dates) {

    public enum Format {

        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("pdf", "application/pdf");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    public static ExportFilter of(HttpServletRequest request) {
        RequestFields query = RequestFields.ofQuery(request);
        DateFilter dates = DateFilter.read(query);
        String value = query.choice("format", "Formato no válido. Usa xlsx o pdf.", Format.XLSX.extension,
                Format.PDF.extension);
        query.throwIfInvalid();
        dates.check();
        return new ExportFilter(Format.XLSX.extension.equals(value) ? Format.XLSX : Format.PDF, dates);
    }

    /** Por ejemplo, "2026-11-01 a hoy". */
    public String label() {
        return start() + " a " + end();
    }

    public String fileName() {
        return "historial_palacetec_" + start() + "_" + end() + "." + format.extension();
    }

    private String start() {
        return dates.startDate() == null ? "inicio" : dates.startDate().toString();
    }

    private String end() {
        return dates.endDate() == null ? "hoy" : dates.endDate().toString();
    }
}
