package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.StakeRequest;
import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.entity.Wallet;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.PlayRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lo común a blackjack y deportes: el jugador bloqueado, el cobro y el pago, como casino/services.py. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CasinoService {

    private static final int DECIMALS = 2;

    private final UserRepository users;
    private final SessionService sessions;
    private final PlayRepository plays;
    private final DailyLimitService limits;
    private final WalletService wallets;
    private final LossEscalationDetector detector;
    private final Clock clock;

    public CasinoService(UserRepository users, SessionService sessions, PlayRepository plays, DailyLimitService limits,
                         WalletService wallets, LossEscalationDetector detector, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.plays = plays;
        this.limits = limits;
        this.wallets = wallets;
        this.detector = detector;
        this.clock = clock;
    }

    /** Jugador bloqueado, con su sesión vigente y sin exclusiones. */
    public record Seat(User user, AuthSession session) {
    }

    public Seat lock(PlayerPrincipal player) {
        User user = users.lockById(player.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesión no disponible. Inicia sesión nuevamente."));
        return new Seat(user, sessions.check(player, user));
    }

    /** Jugada que ya usó esa referencia: un reintento la recibe de nuevo, sin otro cobro y aunque haya una pausa. */
    public Optional<Play> existing(Seat seat, UUID reference) {
        return plays.findByUserIdAndReference(seat.user().getId(), reference);
    }

    public void requireNotPaused(Seat seat) {
        Instant until = seat.user().getPauseUntil();
        if (until != null && until.isAfter(clock.instant())) {
            throw ApiException.badRequest("pause_active", "Tu pausa de juego sigue activa.");
        }
    }

    /** Descuenta el cupo diario y el saldo, y registra la jugada: todo o nada. */
    public Play reserve(Seat seat, String kind, StakeRequest stake, String description) {
        Long userId = seat.user().getId();
        limits.checkAndReserve(userId, stake.amount());
        Instant now = TimeConfig.now(clock);
        Play play = plays.save(new Play(userId, seat.session().getId(), kind, stake.amount(), stake.reference(),
                description, now));
        wallets.debit(wallets.lock(userId), stake.amount(), "casino:bet:" + play.getId(), now);
        return play;
    }

    /** Paga lo que corresponda. Una jugada ya liquidada no se paga otra vez. */
    public void settle(Play play, BigDecimal multiplier) {
        if (play.getSettledAt() != null) {
            return;
        }
        Instant now = TimeConfig.now(clock);
        BigDecimal payout = play.getAmount().multiply(multiplier).setScale(DECIMALS, RoundingMode.HALF_UP);
        if (payout.signum() > 0) {
            Wallet wallet = wallets.lock(play.getUserId());
            wallets.credit(wallet, payout, "casino:win:" + play.getId(), now);
        }
        play.settle(payout, now);
        detector.check(play, now);
    }

    static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "reference_conflict", detail);
    }
}
