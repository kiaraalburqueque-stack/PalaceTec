package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.RiskIncident;
import java.util.List;

public record RiskAlertsResponse(@JsonProperty("pause_until") String pauseUntil, List<Alert> results) {

    public record Alert(
            @JsonProperty("incident_id") Long incidentId,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("pause_started_at") String pauseStartedAt,
            @JsonProperty("suggested_pause_seconds") long suggestedPauseSeconds) {

        public static Alert from(RiskIncident incident, long suggestedPauseSeconds) {
            return new Alert(incident.getId(), Formats.utc(incident.getCreatedAt()),
                    Formats.utc(incident.getPauseStartedAt()), suggestedPauseSeconds);
        }
    }
}
