package com.tecsup.palacetec.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotNull(message = RegisterRequest.REQUIRED) @Size(max = 254, message = "Usa como máximo 254 caracteres.")
        @Pattern(regexp = Emails.PATTERN, message = Emails.MESSAGE) String email,
        @NotEmpty(message = RegisterRequest.REQUIRED) String password) {

    public LoginRequest {
        email = Emails.strip(email);
    }
}
