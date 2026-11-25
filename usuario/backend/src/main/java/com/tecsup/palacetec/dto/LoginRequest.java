package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** Credenciales con formato válido. */
public record LoginRequest(String email, String password) {

    public static LoginRequest from(JsonNode body, FieldMessages messages) {
        RequestFields fields = RequestFields.of(body, messages);
        String email = fields.text("email", TextRule.required().max(254).email());
        String password = fields.text("password", TextRule.required().keepSpaces());
        fields.throwIfInvalid();
        return new LoginRequest(Emails.normalize(email), password);
    }
}
