package com.tecsup.palacetec.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Login rechazado. No revierte la transacción: el contador de intentos debe quedar guardado. */
public class LoginRejectedException extends ApiException {

    private final Long retryAfter;

    private LoginRejectedException(HttpStatus status, String code, String detail, Long retryAfter) {
        super(status, code, detail);
        this.retryAfter = retryAfter;
    }

    public static LoginRejectedException invalidCredentials() {
        return new LoginRejectedException(HttpStatus.UNAUTHORIZED, null, "Credenciales inválidas", null);
    }

    public static LoginRejectedException locked(long retryAfter) {
        return new LoginRejectedException(HttpStatus.TOO_MANY_REQUESTS, null, "Cuenta bloqueada temporalmente.", retryAfter);
    }

    public static LoginRejectedException suspended() {
        return new LoginRejectedException(HttpStatus.FORBIDDEN, "account_suspended",
                "Tu cuenta está suspendida. Comunícate con soporte.", null);
    }

    @Override
    public Long getRetryAfter() {
        return retryAfter;
    }

    @Override
    public Map<String, Object> body() {
        Map<String, Object> body = super.body();
        if (retryAfter != null) {
            body.put("retry_after", retryAfter);
        }
        return body;
    }
}
