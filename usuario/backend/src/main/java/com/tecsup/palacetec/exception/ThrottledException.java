package com.tecsup.palacetec.exception;

import org.springframework.http.HttpStatus;

/** Demasiadas solicitudes desde una misma IP (Throttled de Django REST Framework). */
public class ThrottledException extends ApiException {

    private final long wait;

    public ThrottledException(long wait) {
        super(HttpStatus.TOO_MANY_REQUESTS, null, "Request was throttled. Expected available in " + wait
                + (wait == 1 ? " second." : " seconds."));
        this.wait = wait;
    }

    @Override
    public Long getRetryAfter() {
        return wait;
    }
}
