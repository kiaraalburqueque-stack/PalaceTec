package com.tecsup.palacetec.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
        @NotEmpty List<String> corsAllowedOrigins,
        @Valid @NotNull Web web) {

    public record Jwt(
            @NotNull @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres") String secret,
            @NotNull Duration expiration) {
    }

    /**
     * Sesión del frontend React.
     *
     * @param cookieSecure cookies solo por HTTPS; en desarrollo local, sin HTTPS, debe ser false
     * @param loginRateLimit solicitudes de autenticación por minuto y por IP
     * @param frontendUrl adonde apuntan los enlaces de los correos
     */
    public record Web(boolean cookieSecure, @Min(1) int loginRateLimit, @NotBlank String frontendUrl) {
    }
}
