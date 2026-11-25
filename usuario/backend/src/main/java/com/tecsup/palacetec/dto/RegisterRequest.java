package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterRequest(
        @NotNull(message = REQUIRED) @Size(max = 254, message = "Usa como máximo 254 caracteres.")
        @Pattern(regexp = Emails.PATTERN, message = Emails.MESSAGE) String email,
        @NotEmpty(message = REQUIRED) @StrongPassword String password,
        @NotNull(message = REQUIRED) @Pattern(regexp = "[0-9]{8}", message = "El DNI debe tener 8 dígitos.") String dni,
        @JsonProperty("birth_date") @NotNull(message = REQUIRED) @Adult LocalDate birthDate) {

    static final String REQUIRED = "Este campo es obligatorio.";

    public RegisterRequest {
        email = Emails.strip(email);
    }
}
