package com.tecsup.palacetec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.NotMeResponse;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.dto.TextRule;
import com.tecsup.palacetec.entity.LoginHistory;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.LoginHistoryRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.OneTimeTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Botón "No fui yo" del correo de acceso nuevo (HU56): bloquea la cuenta y cierra todas las sesiones. */
@Service
public class NotMeService {

    private static final Duration LINK_MAX_AGE = Duration.ofHours(24);
    private static final Duration LOCK_DURATION = Duration.ofHours(24);

    private final LoginHistoryRepository history;
    private final UserRepository users;
    private final SessionService sessions;
    private final Clock clock;

    public NotMeService(LoginHistoryRepository history, UserRepository users, SessionService sessions, Clock clock) {
        this.history = history;
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public NotMeResponse report(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        String token = fields.text("token", TextRule.required().max(512));
        fields.throwIfInvalid();
        LoginHistory entry = history.lockByReportTokenHash(OneTimeTokens.hash(token))
                .orElseThrow(NotMeService::invalidLink);
        Instant now = TimeConfig.now(clock);
        if (!entry.getCreatedAt().plus(LINK_MAX_AGE).isAfter(now)) {
            throw invalidLink();
        }
        User user = users.lockById(entry.getUserId()).orElseThrow(NotMeService::invalidLink);
        // Como en Django, repetir el enlace responde lo mismo, sin volver a bloquear ni alargar el bloqueo.
        if (entry.getReportedAt() == null) {
            entry.report(now);
            user.lockAfterReport(now.plus(LOCK_DURATION));
            sessions.revokeAll(user);
        }
        return new NotMeResponse("Bloqueamos tu cuenta temporalmente y cerramos todas las sesiones. "
                + "Restablece tu contraseña para volver a entrar.", Formats.utc(user.getLockedUntil()));
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("invalid_token", "El enlace no es válido o venció.");
    }
}
