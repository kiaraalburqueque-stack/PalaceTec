package com.tecsup.palacetec.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;

/** Distingue a la web (cookies y CSRF) de la app móvil (Authorization: Bearer). */
public final class Clients {

    private static final String SCHEME = "Bearer ";
    /** Cabeceras que un navegador agrega por su cuenta y que una página no puede quitar. */
    private static final List<String> BROWSER_HEADERS = List.of(HttpHeaders.ORIGIN, HttpHeaders.REFERER,
            HttpHeaders.COOKIE, "Sec-Fetch-Site", CsrfGuard.HEADER);

    private Clients() {
    }

    /** Valor del token Bearer, o null si la solicitud no lo trae. */
    public static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())
                ? header.substring(SCHEME.length()).trim() : null;
    }

    /**
     * Cliente que no es un navegador. Ante la duda es un navegador, que debe pasar el CSRF; además, al cliente nativo
     * nunca se le ponen cookies, así que saltarse el CSRF no permite iniciar sesión en el navegador de otra persona.
     */
    public static boolean isNative(HttpServletRequest request) {
        return request.getCookies() == null
                && BROWSER_HEADERS.stream().allMatch(name -> request.getHeader(name) == null);
    }
}
