package com.tecsup.palacetec.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuración propia. La aplicación no arranca si falta una clave. */
@Validated
@ConfigurationProperties("palacetec")
public record PalaceTecProperties(
        @Valid @NotNull Jwt jwt,
        @NotBlank String exclusionHmacKey,
        @NotBlank String mediaBaseUrl,
        @NotEmpty List<String> corsAllowedOrigins) {

    public record Jwt(
            @NotNull @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres") String secret,
            @NotNull Duration expiration) {
    }
}
