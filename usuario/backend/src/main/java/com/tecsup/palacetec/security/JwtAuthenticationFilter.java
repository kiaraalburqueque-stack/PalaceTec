package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.SecurityConfig;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.service.SessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String SCHEME = "Bearer ";

    private final JwtService jwt;
    private final SessionService sessions;
    private final RestAuthenticationEntryPoint errors;

    public JwtAuthenticationFilter(JwtService jwt, SessionService sessions, RestAuthenticationEntryPoint errors) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Un token viejo en la cabecera no debe impedir registrarse ni volver a entrar.
        String path = request.getRequestURI();
        return path.equals(SecurityConfig.LOGIN_PATH) || path.equals(SecurityConfig.REGISTER_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            try {
                authenticate(header.substring(SCHEME.length()).trim());
            } catch (JwtException | IllegalArgumentException exception) {
                ApiException error = ApiException.unauthorized("El token no es válido o venció.");
                errors.write(response, error.getStatus(), error.body());
                return;
            } catch (ApiException exception) {
                errors.write(response, exception.getStatus(), exception.body());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        PlayerPrincipal player = jwt.parse(token);
        sessions.check(player);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(player, null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
