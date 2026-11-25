package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.exception.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Protección CSRF de Django: secreto en la cookie csrftoken y el mismo secreto, enmascarado, en X-CSRFToken. Usa su
 * mismo formato porque las cookies no distinguen puertos: en localhost los dos backends comparten csrftoken.
 */
@Component
public class CsrfGuard {

    public static final String COOKIE = "csrftoken";
    public static final String HEADER = "X-CSRFToken";

    private static final Duration COOKIE_AGE = Duration.ofDays(7 * 52);
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SECRET_LENGTH = 32;
    private static final int TOKEN_LENGTH = 2 * SECRET_LENGTH;
    private static final String HEADER_SOURCE = "CSRF token from the 'X-Csrftoken' HTTP header ";

    private final SecureRandom random = new SecureRandom();
    private final List<String> trustedOrigins;
    private final boolean secure;

    public CsrfGuard(PalaceTecProperties properties) {
        this.trustedOrigins = properties.corsAllowedOrigins();
        this.secure = properties.web().cookieSecure();
    }

    /** get_token: conserva el secreto del navegador, o crea otro, y devuelve el token para X-CSRFToken. */
    public String issue(HttpServletRequest request, HttpServletResponse response) {
        String cookie = cookie(request);
        String secret = cookie == null || formatError(cookie) != null ? newSecret() : unmask(cookie);
        setCookie(response, secret);
        return mask(secret);
    }

    private void setCookie(HttpServletResponse response, String secret) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, secret).path("/").maxAge(COOKIE_AGE)
                .sameSite("Lax").secure(secure).build().toString());
        response.addHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
    }

    /** enforce_csrf: 403 si la solicitud no demuestra venir del frontend. Una cookie dañada se reemplaza. */
    public void enforce(HttpServletRequest request, HttpServletResponse response) {
        String cookie = cookie(request);
        if (cookie != null && formatError(cookie) != null) {
            setCookie(response, newSecret());
        }
        rejection(request).ifPresent(reason -> {
            throw ApiException.forbidden("CSRF: " + reason);
        });
    }

    private Optional<String> rejection(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null) {
            if (!origin.equals(ownOrigin(request)) && !trustedOrigins.contains(origin)) {
                return Optional.of("Origin checking failed - " + origin + " does not match any trusted origins.");
            }
        } else if (request.isSecure()) {
            String problem = refererProblem(request);
            if (problem != null) {
                return Optional.of(problem);
            }
        }
        return Optional.ofNullable(tokenProblem(request));
    }

    /** En HTTPS, sin Origin, la página de origen tiene que ser una de confianza. */
    private String refererProblem(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null) {
            return "Referer checking failed - no Referer.";
        }
        URI uri;
        try {
            uri = URI.create(referer);
        } catch (IllegalArgumentException exception) {
            return "Referer checking failed - Referer is malformed.";
        }
        if (uri.getScheme() == null || uri.getRawAuthority() == null) {
            return "Referer checking failed - Referer is malformed.";
        }
        if (!uri.getScheme().equals("https")) {
            return "Referer checking failed - Referer is insecure while host is secure.";
        }
        String authority = uri.getRawAuthority();
        boolean trusted = authority.equals(request.getHeader(HttpHeaders.HOST))
                || trustedOrigins.stream().anyMatch(origin -> authority.equals(URI.create(origin).getRawAuthority()));
        return trusted ? null : "Referer checking failed - " + referer + " does not match any trusted origins.";
    }

    private static String tokenProblem(HttpServletRequest request) {
        String cookie = cookie(request);
        if (cookie == null) {
            return "CSRF cookie not set.";
        }
        String cookieError = formatError(cookie);
        if (cookieError != null) {
            return "CSRF cookie " + cookieError + ".";
        }
        String token = request.getHeader(HEADER);
        if (token == null) {
            return "CSRF token missing.";
        }
        String tokenError = formatError(token);
        if (tokenError != null) {
            return HEADER_SOURCE + tokenError + ".";
        }
        boolean matches = MessageDigest.isEqual(unmask(token).getBytes(StandardCharsets.US_ASCII),
                unmask(cookie).getBytes(StandardCharsets.US_ASCII));
        return matches ? null : HEADER_SOURCE + "incorrect.";
    }

    private static String ownOrigin(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getHeader(HttpHeaders.HOST);
    }

    private static String cookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    private static String formatError(String value) {
        if (value.length() != SECRET_LENGTH && value.length() != TOKEN_LENGTH) {
            return "has incorrect length";
        }
        return value.chars().allMatch(character -> ALPHABET.indexOf(character) >= 0) ? null : "has invalid characters";
    }

    private String newSecret() {
        return random.ints(SECRET_LENGTH, 0, ALPHABET.length())
                .collect(StringBuilder::new, (text, index) -> text.append(ALPHABET.charAt(index)), StringBuilder::append)
                .toString();
    }

    /** Máscara aleatoria seguida del secreto cifrado con ella: el token cambia en cada respuesta. */
    private String mask(String secret) {
        String mask = newSecret();
        StringBuilder cipher = new StringBuilder(mask);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            int sum = ALPHABET.indexOf(secret.charAt(i)) + ALPHABET.indexOf(mask.charAt(i));
            cipher.append(ALPHABET.charAt(sum % ALPHABET.length()));
        }
        return cipher.toString();
    }

    private static String unmask(String token) {
        if (token.length() == SECRET_LENGTH) {
            return token;
        }
        StringBuilder secret = new StringBuilder();
        for (int i = 0; i < SECRET_LENGTH; i++) {
            int difference = ALPHABET.indexOf(token.charAt(SECRET_LENGTH + i)) - ALPHABET.indexOf(token.charAt(i));
            secret.append(ALPHABET.charAt(Math.floorMod(difference, ALPHABET.length())));
        }
        return secret.toString();
    }
}
