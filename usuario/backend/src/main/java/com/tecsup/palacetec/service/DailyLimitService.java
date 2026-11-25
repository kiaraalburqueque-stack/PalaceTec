package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.DailyLimitResponse;
import com.tecsup.palacetec.entity.DailyLimit;
import com.tecsup.palacetec.entity.DailySpending;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.DailyLimitRepository;
import com.tecsup.palacetec.repository.DailySpendingRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Límite diario: mide lo apostado en el día de Lima; los premios no devuelven cupo. */
@Service
public class DailyLimitService {

    private final UserRepository users;
    private final DailyLimitRepository limits;
    private final DailySpendingRepository spendings;
    private final SessionService sessions;
    private final Clock clock;

    public DailyLimitService(UserRepository users, DailyLimitRepository limits, DailySpendingRepository spendings,
                             SessionService sessions, Clock clock) {
        this.users = users;
        this.limits = limits;
        this.spendings = spendings;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public DailyLimitResponse summary(PlayerPrincipal player) {
        lockPlayer(player);
        Instant now = TimeConfig.now(clock);
        return summary(player.userId(), activeLimit(player.userId(), now), now);
    }

    @Transactional
    public DailyLimitResponse setLimit(PlayerPrincipal player, BigDecimal amount) {
        lockPlayer(player);
        Instant now = TimeConfig.now(clock);
        DailyLimit limit = activeLimit(player.userId(), now);
        limit.request(amount, now);
        limits.save(limit);
        return summary(player.userId(), limit, now);
    }

    /** Descuenta cupo dentro de la apuesta; quien llama ya bloqueó al usuario, así que no hay carreras entre salas. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkAndReserve(Long userId, BigDecimal amount) {
        Instant now = TimeConfig.now(clock);
        DailyLimit limit = activeLimit(userId, now);
        if (limit.getAmount() == null) {
            throw ApiException.badRequest("daily_limit_required", "Define tu limite diario antes de apostar.");
        }
        LocalDate day = day(now);
        DailySpending spending = spendings.findByUserIdAndDate(userId, day)
                .orElseGet(() -> new DailySpending(userId, day));
        if (spending.getAmount().add(amount).compareTo(limit.getAmount()) > 0) {
            throw ApiException.badRequest("daily_limit_exceeded", "La apuesta supera tu limite diario disponible.");
        }
        spending.add(amount);
        spendings.save(spending);
    }

    private void lockPlayer(PlayerPrincipal player) {
        User user = users.lockById(player.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesión no disponible. Inicia sesión nuevamente."));
        sessions.check(player, user);
    }

    private DailyLimit activeLimit(Long userId, Instant now) {
        DailyLimit limit = limits.findByUserId(userId).orElseGet(() -> new DailyLimit(userId));
        limit.activate(now);
        return limit;
    }

    private DailyLimitResponse summary(Long userId, DailyLimit limit, Instant now) {
        LocalDate day = day(now);
        BigDecimal spent = spendings.findByUserIdAndDate(userId, day).map(DailySpending::getAmount)
                .orElse(BigDecimal.ZERO);
        return DailyLimitResponse.from(limit, spent, day);
    }

    private static LocalDate day(Instant now) {
        return now.atZone(TimeConfig.LIMA).toLocalDate();
    }
}
