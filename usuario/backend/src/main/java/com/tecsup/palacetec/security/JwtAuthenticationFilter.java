package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.SecurityConfig;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.media.MediaStorage;
import com.tecsup.palacetec.security.WebTokenService.WebToken;
import com.tecsup.palacetec.service.SessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autentica con el Bearer de la app móvil o, si no lo hay, con la cookie de la web (que además exige CSRF). */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final JwtService jwt;
    private final WebTokenService webTokens;
    private final SessionCookies cookies;
    private final CsrfGuard csrf;
    private final SessionService sessions;
    private final RestAuthenticationEntryPoint errors;

    public JwtAuthenticationFilter(JwtService jwt, WebTokenService webTokens, SessionCookies cookies, CsrfGuard csrf,
                                   SessionService sessions, RestAuthenticationEntryPoint errors) {
        this.jwt = jwt;
        this.webTokens = webTokens;
        this.cookies = cookies;
        this.csrf = csrf;
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Un token viejo no debe impedir registrarse, volver a entrar, renovar ni cerrar la sesión.
        String path = request.getRequestURI();
        // Los WebSockets leen la cookie en su propio handshake, para cerrar con 4401 como Django.
        return SecurityConfig.PUBLIC_PATHS.contains(path) || path.startsWith(MediaStorage.URL)
                || path.startsWith(SecurityConfig.WEBSOCKET_PATHS);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String bearer = Clients.bearer(request);
            String access = cookies.access(request);
            if (bearer != null) {
                authenticate(parse(bearer));
            } else if (access != null) {
                WebToken token = webTokens.parse(access, WebTokenService.Type.ACCESS);
                authenticate(PlayerPrincipal.web(token));
                if (!SAFE_METHODS.contains(request.getMethod())) {
                    csrf.enforce(request, response);
                }
            }
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            errors.write(response, exception.getStatus(), exception.body());
            return;
        }
        chain.doFilter(request, response);
    }

    private PlayerPrincipal parse(String bearer) {
        try {
            return jwt.parse(bearer);
        } catch (JwtException | IllegalArgumentException exception) {
            throw ApiException.unauthorized("El token no es válido o venció.");
        }
    }

    private void authenticate(PlayerPrincipal player) {
        sessions.check(player);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(player, null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
