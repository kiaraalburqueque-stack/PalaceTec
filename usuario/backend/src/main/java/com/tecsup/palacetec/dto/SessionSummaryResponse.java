package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Lo jugado en la sesión actual (HU28). */
public record SessionSummaryResponse(
        @JsonUnwrapped PlayTotals totals,
        @JsonProperty("session_started_at") String startedAt,
        @JsonProperty("duration_seconds") long durationSeconds,
        @JsonProperty("pending_wagered") String pendingWagered,
        @JsonProperty("pending_count") long pendingCount,
        @JsonProperty("reality_check_interval_seconds") int realityCheckIntervalSeconds,
        String scope) {
}
