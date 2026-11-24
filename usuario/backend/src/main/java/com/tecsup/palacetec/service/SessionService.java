package com.tecsup.palacetec.service;

import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.AuthSessionRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Un JWT solo vale mientras su sesión siga vigente en la base que comparte con Django. */
@Service
public class SessionService {

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final ExclusionService exclusions;
    private final Clock clock;

    public SessionService(UserRepository users, AuthSessionRepository sessions, ExclusionService exclusions,
                          Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.exclusions = exclusions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void check(PlayerPrincipal player) {
        check(player, users.findById(player.userId()).orElseThrow(SessionService::unavailable));
    }

    /** Igual que check, con el usuario ya bloqueado por quien va a escribir. */
    @Transactional(readOnly = true)
    public AuthSession check(PlayerPrincipal player, User user) {
        if (!user.isActive()) {
            throw unavailable();
        }
        exclusions.ensureUserAllowed(user);
        if (player.version() != user.getAuthVersion()) {
            throw unavailable();
        }
        return sessions.findByJti(player.jti())
                .filter(session -> session.isUsableBy(user.getId(), clock.instant()))
                .orElseThrow(SessionService::unavailable);
    }

    private static ApiException unavailable() {
        return ApiException.unauthorized("Sesión no disponible. Inicia sesión nuevamente.");
    }
}
