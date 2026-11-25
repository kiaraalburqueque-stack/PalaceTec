package com.tecsup.palacetec.export;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.BetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Una apuesta del historial exportado. payout es null si está pendiente y balance, si no hay movimiento. */
public record ExportRow(Instant createdAt, String game, String bet, BigDecimal amount, BetStatus status,
                        BigDecimal payout, BigDecimal balance) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    public String date() {
        return DATE.format(createdAt.atZone(TimeConfig.LIMA));
    }

    public BigDecimal net() {
        return payout == null ? null : payout.subtract(amount);
    }
}
