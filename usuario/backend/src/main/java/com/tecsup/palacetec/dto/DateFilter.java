package com.tecsup.palacetec.dto;

import com.tecsup.palacetec.exception.InvalidFieldsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;

/** startDate y endDate de la consulta, con las reglas y los mensajes de DateRangeInput de Django. null no filtra. */
public record DateFilter(LocalDate startDate, LocalDate endDate) {

    private static final int LAST_YEAR = 9999;

    public static DateFilter of(HttpServletRequest request) {
        RequestFields query = RequestFields.ofQuery(request);
        DateFilter filter = read(query);
        query.throwIfInvalid();
        filter.check();
        return filter;
    }

    /** Deja anotados en query los errores de formato; quien llama los lanza y después revisa el rango con check. */
    public static DateFilter read(RequestFields query) {
        return new DateFilter(query.date("startDate"), query.date("endDate"));
    }

    public void check() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw InvalidFieldsException.field("endDate", "La fecha final debe ser igual o posterior al inicio.");
        }
        if (endDate != null && endDate.getYear() == LAST_YEAR) {
            throw InvalidFieldsException.field("endDate", "La fecha final excede el rango permitido.");
        }
    }

    public DateRange range() {
        return DateRange.of(startDate, endDate);
    }
}
