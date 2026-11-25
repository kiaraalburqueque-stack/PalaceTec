package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.PauseResponse;
import com.tecsup.palacetec.dto.PlayTotals;
import com.tecsup.palacetec.dto.RiskAlertsResponse;
import com.tecsup.palacetec.dto.SelfExclusionResponse;
import com.tecsup.palacetec.dto.SessionSummaryResponse;
import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.RiskIncident;
import com.tecsup.palacetec.entity.SelfExclusion;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.AuthSessionRepository;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.PendingTotals;
import com.tecsup.palacetec.repository.PlayRepository;
import com.tecsup.palacetec.repository.RiskIncidentRepository;
import com.tecsup.palacetec.repository.SelfExclusionRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autoexclusión (HU27), resumen de la sesión (HU28) y alertas de riesgo (HU29), como responsible/views.py. */
@Service
public class ResponsibleGamingService {

    private static final Duration PAUSE = Duration.ofMinutes(10);
    private static final int REALITY_CHECK_SECONDS = 3600;

    private final UserRepository users;
    private final AuthSessionRepository authSessions;
    private final SessionService sessions;
    private final SelfExclusionRepository exclusions;
    private final RiskIncidentRepository incidents;
    private final BetRepository bets;
    private final PlayRepository plays;
    private final Clock clock;

    public ResponsibleGamingService(UserRepository users, AuthSessionRepository authSessions, SessionService sessions,
                                    SelfExclusionRepository exclusions, RiskIncidentRepository incidents,
                                    BetRepository bets, PlayRepository plays, Clock clock) {
        this.users = users;
        this.authSessions = authSessions;
        this.sessions = sessions;
        this.exclusions = exclusions;
        this.incidents = incidents;
        this.bets = bets;
        this.plays = plays;
        this.clock = clock;
    }

    /** Cierra todas las sesiones: el jugador no puede volver a entrar hasta que termine el plazo. */
    @Transactional
    public SelfExclusionResponse selfExclude(PlayerPrincipal player, int days) {
        User user = sessions.lock(player);
        Instant now = TimeConfig.now(clock);
        Instant until = now.plus(Duration.ofDays(days));
        user.selfExclude(until);
        exclusions.save(new SelfExclusion(user.getId(), days, now, until));
        sessions.revokeAll(user);
        return new SelfExclusionResponse(Formats.utc(until),
                "Autoexclusion confirmada. No se puede cancelar anticipadamente.");
    }

    @Transactional(readOnly = true)
    public SessionSummaryResponse sessionSummary(PlayerPrincipal player) {
        AuthSession session = authSessions.findByJti(player.jti())
                .orElseThrow(() -> ApiException.unauthorized("Sesion no disponible."));
        PlayTotals totals = PlayTotals.sum(bets.sumSettledBySession(session.getId()),
                plays.sumSettledBySession(session.getId()));
        PendingTotals roulette = bets.sumPendingBySession(session.getId());
        PendingTotals casino = plays.sumPendingBySession(session.getId());
        long seconds = Duration.between(session.getStartedAt(), clock.instant()).toSeconds();
        return new SessionSummaryResponse(totals, Formats.utc(session.getStartedAt()), Math.max(0, seconds),
                Formats.money(roulette.getWagered().add(casino.getWagered())), roulette.getCount() + casino.getCount(),
                REALITY_CHECK_SECONDS, "settled_session_bets");
    }

    /** Acepta la pausa que sugirió una alerta propia. Cada alerta da una sola pausa: repetirla no la alarga. */
    @Transactional
    public PauseResponse pause(PlayerPrincipal player, Long incidentId) {
        User user = sessions.lock(player);
        RiskIncident incident = incidents.findByIdAndUserId(incidentId, user.getId())
                .orElseThrow(() -> ApiException.notFound("No RiskIncident matches the given query."));
        if (incident.getPauseStartedAt() == null) {
            Instant now = TimeConfig.now(clock);
            incident.startPause(now);
            user.pause(now.plus(PAUSE));
        }
        return new PauseResponse(Formats.utc(user.getPauseUntil()));
    }

    @Transactional(readOnly = true)
    public RiskAlertsResponse riskAlerts(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("No encontrado."));
        return new RiskAlertsResponse(Formats.utc(user.getPauseUntil()),
                incidents.findTop20ByUserIdOrderByIdDesc(userId).stream()
                        .map(incident -> RiskAlertsResponse.Alert.from(incident, PAUSE.toSeconds())).toList());
    }
}
