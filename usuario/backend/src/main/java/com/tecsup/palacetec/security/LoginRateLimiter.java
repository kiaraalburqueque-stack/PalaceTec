package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.exception.ThrottledException;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * LoginRateThrottle de Django: solicitudes por minuto y por IP, sumando login, renovación y recuperación de contraseña.
 * Limita también correos inexistentes, además del bloqueo por cuenta. La cuenta vive en la memoria de este proceso.
 */
@Component
public class LoginRateLimiter {

    private final SlidingWindow window;

    public LoginRateLimiter(PalaceTecProperties properties, Clock clock) {
        this.window = new SlidingWindow(properties.web().loginRateLimit(), clock);
    }

    /** Registra la solicitud; las rechazadas no cuentan. */
    public void check(String client) {
        long wait = window.register(client);
        if (wait > 0) {
            throw new ThrottledException(wait);
        }
    }
}
