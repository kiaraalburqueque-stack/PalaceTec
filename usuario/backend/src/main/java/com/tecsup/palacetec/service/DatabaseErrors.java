package com.tecsup.palacetec.service;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

final class DatabaseErrors {

    private static final String UNIQUE_VIOLATION = "23505";

    private DatabaseErrors() {
    }

    static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        return exception.getMostSpecificCause() instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
