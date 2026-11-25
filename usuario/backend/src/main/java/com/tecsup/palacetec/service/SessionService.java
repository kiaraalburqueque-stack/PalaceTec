package com.tecsup.palacetec.service;

import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.AuthSessionRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
        check(player, users.findById(player.userId()).orElseThrow(() -> unknownUser(player)));
    }

    /** Igual que check, con el usuario ya bloqueado por quien va a escribir. */
    @Transactional(readOnly = true)
    public AuthSession check(PlayerPrincipal player, User user) {
        if (!user.isActive()) {
            throw player.web() ? unauthorized("user_inactive", "User is inactive") : unavailable(player);
        }
        exclusions.ensureUserAllowed(user);
        if (player.version() != user.getAuthVersion()) {
            throw unavailable(player);
        }
        return sessions.findByJti(player.jti())
                .filter(session -> session.isUsableBy(user.getId(), clock.instant()))
                .orElseThrow(() -> unavailable(player));
    }

    /** Bloquea la fila del usuario antes de escribir y revisa que su sesión siga vigente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User lock(PlayerPrincipal player) {
        User user = users.lockById(player.userId()).orElseThrow(() -> unknownUser(player));
        check(player, user);
        return user;
    }

    private static ApiException unknownUser(PlayerPrincipal player) {
        return player.web() ? unauthorized("user_not_found", "User not found") : unavailable(player);
    }

    /** La web recibe los textos de Django y la app móvil, los del MVP. */
    private static ApiException unavailable(PlayerPrincipal player) {
        return ApiException.unauthorized(player.web() ? "Sesion no disponible."
                : "Sesión no disponible. Inicia sesión nuevamente.");
    }

    private static ApiException unauthorized(String code, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, detail);
    }
}
