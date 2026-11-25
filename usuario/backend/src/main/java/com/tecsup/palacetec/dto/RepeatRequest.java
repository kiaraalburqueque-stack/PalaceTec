package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** sourceRoundId es opcional: null repite la última ronda sin comprobar cuál es. */
public record RepeatRequest(Long roundId, Long sourceRoundId, UUID reference) {

    public static RepeatRequest from(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        Long round = fields.integer("round_id", true, 1);
        Long source = fields.integer("source_round_id", false, 1);
        UUID reference = fields.uuid("reference");
        fields.throwIfInvalid();
        return new RepeatRequest(round, source, reference);
    }
}
