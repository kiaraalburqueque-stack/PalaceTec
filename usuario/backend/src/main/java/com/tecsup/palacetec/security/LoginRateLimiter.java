package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.exception.ThrottledException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * LoginRateThrottle de Django: solicitudes por minuto y por IP, sumando login, renovación y recuperación de contraseña.
 * Limita también correos inexistentes, además del bloqueo por cuenta. La cuenta vive en la memoria de este proceso.
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW = Duration.ofMinutes(1).toMillis();
    private static final int MAX_CLIENTS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    public LoginRateLimiter(PalaceTecProperties properties, Clock clock) {
        this.limit = properties.web().loginRateLimit();
        this.clock = clock;
    }

    /** Registra la solicitud; las rechazadas no cuentan. */
    public void check(String client) {
        long now = clock.millis();
        long[] wait = {0};
        attempts.compute(client, (key, history) -> {
            Deque<Long> recent = history == null ? new ArrayDeque<>() : history;
            removeExpired(recent, now);
            if (recent.size() >= limit) {
                wait[0] = WINDOW - (now - recent.peekFirst());
            } else {
                recent.addLast(now);
            }
            return recent;
        });
        if (wait[0] > 0) {
            throw new ThrottledException(Math.ceilDiv(wait[0], 1000L));
        }
        if (attempts.size() > MAX_CLIENTS) {
            forgetInactive(now);
        }
    }

    /** Evita que el mapa crezca sin límite con direcciones que ya no llaman. */
    private void forgetInactive(long now) {
        attempts.keySet().forEach(client -> attempts.computeIfPresent(client, (key, recent) -> {
            removeExpired(recent, now);
            return recent.isEmpty() ? null : recent;
        }));
    }

    private static void removeExpired(Deque<Long> recent, long now) {
        while (!recent.isEmpty() && recent.peekFirst() <= now - WINDOW) {
            recent.removeFirst();
        }
    }
}
