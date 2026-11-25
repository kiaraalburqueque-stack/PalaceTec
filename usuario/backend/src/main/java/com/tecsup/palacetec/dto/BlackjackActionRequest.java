package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record BlackjackActionRequest(boolean hit, long revision) {

    private static final String HIT = "hit";

    public static BlackjackActionRequest from(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        String action = fields.choice("action", List.of(HIT, "stand"));
        Long revision = fields.integer("revision", true, 0);
        fields.throwIfInvalid();
        return new BlackjackActionRequest(HIT.equals(action), revision);
    }
}
