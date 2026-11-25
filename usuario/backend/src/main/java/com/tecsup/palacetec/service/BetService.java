package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.BetRequest;
import com.tecsup.palacetec.dto.BetResponse;
import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.GameRound;
import com.tecsup.palacetec.entity.Room;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.entity.Wallet;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.GameRoundRepository;
import com.tecsup.palacetec.repository.RoomRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registro de apuestas, con las mismas reglas y el mismo orden de candados que RouletteService.place_bet. */
@Service
public class BetService {

    private final RoomRepository rooms;
    private final GameRoundRepository rounds;
    private final UserRepository users;
    private final BetRepository bets;
    private final SessionService sessions;
    private final WalletService wallets;
    private final DailyLimitService limits;
    private final Clock clock;

    public BetService(RoomRepository rooms, GameRoundRepository rounds, UserRepository users, BetRepository bets,
                      SessionService sessions, WalletService wallets, DailyLimitService limits, Clock clock) {
        this.rooms = rooms;
        this.rounds = rounds;
        this.users = users;
        this.bets = bets;
        this.sessions = sessions;
        this.wallets = wallets;
        this.limits = limits;
        this.clock = clock;
    }

    /** created es false cuando la referencia repite una apuesta ya aceptada: no hay un segundo cargo. */
    public record PlacedBet(BetResponse bet, boolean created) {
    }

    @Transactional
    public PlacedBet place(PlayerPrincipal player, Long roomId, BetRequest request) {
        validateTable(request);
        // Orden compartido con la liquidación de Django: sala -> ronda -> usuario -> billetera.
        Room room = rooms.lockById(roomId).filter(Room::isAvailable).orElseThrow(RoomService::notAvailable);
        GameRound round = rounds.lockByIdAndRoomId(request.roundId(), roomId).orElse(null);
        User user = users.lockById(player.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesión no disponible. Inicia sesión nuevamente."));
        AuthSession session = sessions.check(player, user);
        return place(user, session, room, round, request, null);
    }

    /** Con la sala, la ronda y el usuario ya bloqueados. round es null si no es de la sala. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PlacedBet place(User user, AuthSession session, Room room, GameRound round, BetRequest request,
                           Long repeatOperationId) {
        Wallet wallet = wallets.lock(user.getId());
        Bet existing = bets.findByReference(user.getId(), request.reference()).orElse(null);
        if (existing != null) {
            requireSameBet(existing, room.getId(), request);
            return new PlacedBet(BetResponse.from(existing), false);
        }

        Instant now = TimeConfig.now(clock);
        if (round == null || !round.acceptsBets(now)) {
            throw ApiException.badRequest("bets_closed", "No va mas: la ronda no acepta apuestas.");
        }
        if (user.getPauseUntil() != null && user.getPauseUntil().isAfter(now)) {
            throw ApiException.badRequest("pause_active", "Tu pausa de juego todavia esta activa.");
        }
        BigDecimal amount = BigDecimal.valueOf(request.chips().stream().mapToLong(Integer::longValue).sum());
        requireRoomLimits(room, amount, bets.sumByRound(user.getId(), round.getId()));
        limits.checkAndReserve(user.getId(), amount);
        Bet bet = bets.save(new Bet(user.getId(), session.getId(), repeatOperationId, round, request.kind(),
                request.selection(), request.chips(), amount, request.reference(), now));
        // La referencia monetaria usa el ID propio de la apuesta, no texto del cliente.
        wallets.debit(wallet, amount, "roulette:bet:" + bet.getId(), now);
        return new PlacedBet(BetResponse.from(bet), true);
    }

    private static void validateTable(BetRequest request) {
        if (!RouletteTable.CHIP_VALUES.containsAll(request.chips())) {
            throw InvalidFieldsException.field("chips", "Fichas autorizadas: 10, 50, 100 y 500.");
        }
        RouletteTable.validateSelection(request.kind(), request.selection());
    }

    private static void requireSameBet(Bet existing, Long roomId, BetRequest request) {
        boolean same = existing.getRound().getRoom().getId().equals(roomId)
                && existing.getRound().getId().equals(request.roundId())
                && existing.getKind() == request.kind()
                && existing.getSelection().equals(request.selection())
                && existing.getChips().equals(request.chips());
        if (!same) {
            throw new ApiException(HttpStatus.CONFLICT, "reference_conflict",
                    "Esta referencia corresponde a otra apuesta.");
        }
    }

    private static void requireRoomLimits(Room room, BigDecimal amount, BigDecimal accumulated) {
        boolean belowMinimum = amount.compareTo(room.getMinBet()) < 0;
        boolean aboveMaximum = accumulated.add(amount).compareTo(room.getMaxBet()) > 0;
        if (belowMinimum || aboveMaximum) {
            // Aquí Django responde el mensaje como texto, no como lista.
            throw new InvalidFieldsException(HttpStatus.BAD_REQUEST, Map.of("amount",
                    "El monto debe respetar el minimo por apuesta y el maximo acumulado por ronda."));
        }
    }
}
