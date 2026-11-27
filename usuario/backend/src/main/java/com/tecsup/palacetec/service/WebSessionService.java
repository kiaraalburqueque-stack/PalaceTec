package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.MeResponse;
import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.realtime.SessionsRevoked;
import com.tecsup.palacetec.repository.AuthSessionRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.JwtService;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.security.WebTokenService;
import com.tecsup.palacetec.security.WebTokenService.Type;
import com.tecsup.palacetec.security.WebTokenService.WebToken;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta, renovación y cierre de la sesión. */
@Service
public class WebSessionService {

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final ExclusionService exclusions;
    private final WebTokenService webTokens;
    private final JwtService jwt;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public WebSessionService(UserRepository users, AuthSessionRepository sessions, ExclusionService exclusions,
                             WebTokenService webTokens, JwtService jwt, Clock clock,
                             ApplicationEventPublisher events) {
        this.users = users;
        this.sessions = sessions;
        this.exclusions = exclusions;
        this.webTokens = webTokens;
        this.jwt = jwt;
        this.clock = clock;
        this.events = events;
    }

    /** Nuevo token de acceso y el inicio, que no cambia, de su sesión. */
    public record Renewal(WebToken access, String startedAt) {
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return users.findById(userId).map(MeResponse::from)
                .orElseThrow(() -> ApiException.notFound("No encontrado."));
    }

    @Transactional
    public Renewal refresh(String refreshCookie) {
        WebToken refresh = webTokens.find(refreshCookie, Type.REFRESH).orElseThrow(WebSessionService::expired);
        User user = users.lockById(refresh.userId())
                .filter(found -> found.getAuthVersion() == refresh.version())
                .orElseThrow(WebSessionService::expired);
        exclusions.ensureUserAllowed(user);
        Instant now = clock.instant();
        AuthSession session = sessions.findByJti(refresh.sid())
                .filter(found -> found.isRenewableBy(user.getId(), now))
                .orElseThrow(WebSessionService::expired);
        WebToken access = webTokens.renew(user, session.getJti(), session.getRefreshExpiresAt());
        session.renew(access.expiresAt());
        return new Renewal(access, Formats.utc(session.getStartedAt()));
    }

    /**
     * Revoca la sesión del Bearer o, sin él, la de las cookies: si el acceso venció, sirve el token de renovación.
     * Sin ningún token válido no hay nada que revocar y solo se limpian las cookies.
     */
    @Transactional
    public void logout(String bearer, String accessCookie, String refreshCookie) {
        if (bearer != null) {
            findBearer(bearer).ifPresent(player -> revoke(player.userId(), player.jti()));
            return;
        }
        webTokens.find(accessCookie, Type.ACCESS).or(() -> webTokens.find(refreshCookie, Type.REFRESH))
                .ifPresent(token -> revoke(token.userId(), token.sid()));
    }

    /** El mismo bloqueo de usuario serializa el cierre de sesión con sus escrituras. */
    private void revoke(Long userId, String jti) {
        users.lockById(userId).flatMap(user -> sessions.findByJti(jti))
                .ifPresent(session -> {
                    session.revoke(TimeConfig.now(clock));
                    events.publishEvent(new SessionsRevoked(userId, jti));
                });
    }

    private Optional<PlayerPrincipal> findBearer(String bearer) {
        try {
            return Optional.of(jwt.parse(bearer));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static ApiException expired() {
        return ApiException.unauthorized("Inicia sesion nuevamente.");
    }
}
