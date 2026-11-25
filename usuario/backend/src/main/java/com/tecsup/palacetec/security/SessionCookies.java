package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.security.WebTokenService.WebToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/** Cookies HttpOnly de la sesión web, con los nombres y atributos que usa Django. */
@Component
public class SessionCookies {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    /** El token de renovación solo viaja a las rutas de autenticación. */
    private static final String REFRESH_PATH = "/api/v1/auth/";

    private final boolean secure;
    private final Clock clock;

    public SessionCookies(PalaceTecProperties properties, Clock clock) {
        this.secure = properties.web().cookieSecure();
        this.clock = clock;
    }

    public String access(HttpServletRequest request) {
        return read(request, ACCESS);
    }

    public String refresh(HttpServletRequest request) {
        return read(request, REFRESH);
    }

    public void setAccess(HttpServletResponse response, WebToken token) {
        add(response, cookie(ACCESS, token.value(), "/", remaining(token, WebTokenService.ACCESS_LIFETIME)));
    }

    public void setRefresh(HttpServletResponse response, WebToken token) {
        add(response, cookie(REFRESH, token.value(), REFRESH_PATH, WebTokenService.SESSION_LIFETIME));
    }

    public void clear(HttpServletResponse response) {
        add(response, ResponseCookie.from(ACCESS, "").path("/").maxAge(0).sameSite("Lax").build());
        add(response, ResponseCookie.from(REFRESH, "").path(REFRESH_PATH).maxAge(0).sameSite("Lax").build());
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value).path(path).maxAge(maxAge).httpOnly(true).sameSite("Lax")
                .secure(secure).build();
    }

    /** Segundos que le quedan al token, redondeados hacia arriba y sin pasar de su duración normal. */
    private Duration remaining(WebToken token, Duration lifetime) {
        long millis = Duration.between(clock.instant(), token.expiresAt()).toMillis();
        long seconds = Math.ceilDiv(millis, 1000L);
        return Duration.ofSeconds(Math.max(0, Math.min(lifetime.toSeconds(), seconds)));
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String read(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return cookie == null || cookie.getValue().isEmpty() ? null : cookie.getValue();
    }
}
