package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.entity.DailyLimit;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyLimitResponse(
        boolean configured,
        String limit,
        String spent,
        String remaining,
        @JsonProperty("pending_limit") String pendingLimit,
        @JsonProperty("effective_at") String effectiveAt,
        String date,
        String timezone) {

    public static DailyLimitResponse from(DailyLimit limit, BigDecimal spent, LocalDate day) {
        BigDecimal amount = limit.getAmount();
        BigDecimal remaining = amount == null ? null : amount.subtract(spent).max(BigDecimal.ZERO);
        return new DailyLimitResponse(amount != null, Formats.money(amount), Formats.money(spent),
                Formats.money(remaining), Formats.money(limit.getPendingAmount()),
                Formats.utc(limit.getEffectiveAt()), day.toString(), TimeConfig.LIMA.getId());
    }
}
