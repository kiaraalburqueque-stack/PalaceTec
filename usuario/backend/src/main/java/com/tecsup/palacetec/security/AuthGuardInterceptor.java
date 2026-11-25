package com.tecsup.palacetec.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthGuardInterceptor implements HandlerInterceptor {

    private final CsrfGuard csrf;
    private final LoginRateLimiter limiter;

    public AuthGuardInterceptor(CsrfGuard csrf, LoginRateLimiter limiter) {
        this.csrf = csrf;
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthGuard guard = handler instanceof HandlerMethod method ? method.getMethodAnnotation(AuthGuard.class) : null;
        if (guard == null) {
            return true;
        }
        if (guard.throttled()) {
            limiter.check(request.getRemoteAddr());
        }
        boolean exempt = switch (guard.csrf()) {
            case ALWAYS -> false;
            case UNLESS_NATIVE -> Clients.isNative(request);
            case UNLESS_BEARER -> Clients.bearer(request) != null;
        };
        if (!exempt) {
            csrf.enforce(request, response);
        }
        return true;
    }
}
