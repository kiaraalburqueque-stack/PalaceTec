package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.BetRequest;
import com.tecsup.palacetec.dto.BetResponse;
import com.tecsup.palacetec.dto.PreviousBetsResponse;
import com.tecsup.palacetec.dto.RepeatRequest;
import com.tecsup.palacetec.dto.RepeatResponse;
import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.GameRound;
import com.tecsup.palacetec.entity.RepeatOperation;
import com.tecsup.palacetec.entity.Room;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.GameRoundRepository;
import com.tecsup.palacetec.repository.RepeatOperationRepository;
import com.tecsup.palacetec.repository.RoomRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repite las apuestas de la ronda anterior (HU35), todas o ninguna, como roulette/repeat.py. */
@Service
public class RepeatBetService {

    private final RoomRepository rooms;
    private final GameRoundRepository rounds;
    private final UserRepository users;
    private final BetRepository bets;
    private final RepeatOperationRepository operations;
    private final SessionService sessions;
    private final BetService betService;
    private final Clock clock;

    public RepeatBetService(RoomRepository rooms, GameRoundRepository rounds, UserRepository users, BetRepository bets,
                            RepeatOperationRepository operations, SessionService sessions, BetService betService,
                            Clock clock) {
        this.rooms = rooms;
        this.rounds = rounds;
        this.users = users;
        this.bets = bets;
        this.operations = operations;
        this.sessions = sessions;
        this.betService = betService;
        this.clock = clock;
    }

    /** created es false cuando la referencia repite una operación ya hecha. */
    public record Repeated(RepeatResponse operation, boolean created) {
    }

    @Transactional(readOnly = true)
    public PreviousBetsResponse previous(Long userId, Long roomId) {
        return rounds.findPreviousId(userId, roomId)
                .map(round -> new PreviousBetsResponse(round, responses(bets.findByRound(userId, round))))
                .orElseGet(() -> new PreviousBetsResponse(null, List.of()));
    }

    @Transactional
    public Repeated repeat(PlayerPrincipal player, Long roomId, RepeatRequest request) {
        Room room = rooms.lockById(roomId).filter(Room::isAvailable).orElseThrow(RoomService::notAvailable);
        GameRound target = rounds.lockByIdAndRoomId(request.roundId(), roomId).orElse(null);
        User user = users.lockById(player.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesión no disponible. Inicia sesión nuevamente."));
        AuthSession session = sessions.check(player, user);

        RepeatOperation existing = operations.findByUserIdAndReference(user.getId(), request.reference()).orElse(null);
        if (existing != null) {
            requireSameOperation(existing, roomId, request);
            return new Repeated(response(existing, responses(bets.findByRepeatOperation(existing.getId()))), false);
        }
        if (target == null) {
            throw ApiException.badRequest("bets_closed", "Ronda no disponible.");
        }
        Long source = rounds.findPreviousId(user.getId(), roomId).orElse(null);
        if (source == null || request.sourceRoundId() != null && !request.sourceRoundId().equals(source)) {
            // Aquí Django responde el mensaje como texto, no como lista.
            throw new InvalidFieldsException(HttpStatus.BAD_REQUEST,
                    Map.of("source_round_id", "No existe esa ronda anterior para repetir."));
        }
        RepeatOperation operation = operations.save(new RepeatOperation(user.getId(), roomId, source, target.getId(),
                request.reference(), TimeConfig.now(clock)));
        List<BetResponse> placed = new ArrayList<>();
        for (Bet previous : bets.findByRound(user.getId(), source)) {
            BetRequest copy = new BetRequest(target.getId(), previous.getKind(), previous.getSelection(),
                    previous.getChips(), NameUuid.version5(request.reference(),
                    "roulette:" + user.getId() + ":" + previous.getId()));
            BetService.PlacedBet result = betService.place(user, session, room, target, copy, operation.getId());
            if (!result.created()) {
                throw conflict("La referencia de repeticion ya fue utilizada.");
            }
            placed.add(result.bet());
        }
        return new Repeated(response(operation, placed), true);
    }

    private static void requireSameOperation(RepeatOperation existing, Long roomId, RepeatRequest request) {
        boolean same = existing.getRoomId().equals(roomId) && existing.getTargetRoundId().equals(request.roundId())
                && (request.sourceRoundId() == null || request.sourceRoundId().equals(existing.getSourceRoundId()));
        if (!same) {
            throw conflict("Esta referencia corresponde a otra repeticion.");
        }
    }

    private static RepeatResponse response(RepeatOperation operation, List<BetResponse> placed) {
        return new RepeatResponse(operation.getSourceRoundId(), operation.getTargetRoundId(), operation.getReference(),
                placed);
    }

    private static List<BetResponse> responses(List<Bet> found) {
        return found.stream().map(BetResponse::from).toList();
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "reference_conflict", detail);
    }
}
