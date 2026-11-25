package com.tecsup.palacetec.exception;

import org.springframework.http.HttpStatus;

/** Demasiados mensajes de un jugador al asistente. */
public class AssistantThrottledException extends ApiException {

    private final long wait;

    public AssistantThrottledException(long wait) {
        super(HttpStatus.TOO_MANY_REQUESTS, "assistant_rate_limited",
                "Enviaste muchos mensajes seguidos. Espera un momento y vuelve a intentarlo.");
        this.wait = wait;
    }

    @Override
    public Long getRetryAfter() {
        return wait;
    }
}
