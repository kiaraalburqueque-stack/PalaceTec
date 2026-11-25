package com.tecsup.palacetec.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** SimpleRateThrottle de Django REST Framework: solicitudes por minuto de cada cliente, en la memoria del proceso. */
final class SlidingWindow {

    private static final long WINDOW = Duration.ofMinutes(1).toMillis();
    private static final int MAX_CLIENTS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    SlidingWindow(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Registra la solicitud y devuelve 0, o los segundos que debe esperar. Las rechazadas no cuentan. */
    long register(String client) {
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
        if (wait[0] == 0 && attempts.size() > MAX_CLIENTS) {
            forgetInactive(now);
        }
        return Math.ceilDiv(wait[0], 1000L);
    }

    /** Evita que el mapa crezca sin límite con clientes que ya no llaman. */
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
