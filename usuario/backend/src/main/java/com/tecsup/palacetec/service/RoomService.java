package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.RoomResponse;
import com.tecsup.palacetec.dto.RoomStateResponse;
import com.tecsup.palacetec.entity.GameRound;
import com.tecsup.palacetec.entity.Room;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.repository.GameRoundRepository;
import com.tecsup.palacetec.repository.RoomRepository;
import com.tecsup.palacetec.repository.RoomRepository.RoomPlayers;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Salas y estado de la mesa. Spring solo consulta: las rondas las mueve runroulette de Django. */
@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository rooms;
    private final GameRoundRepository rounds;
    private final Clock clock;

    public RoomService(RoomRepository rooms, GameRoundRepository rounds, Clock clock) {
        this.rooms = rooms;
        this.rounds = rounds;
        this.clock = clock;
    }

    public List<RoomResponse> list(String game) {
        Long gameId = game == null ? null : positiveId(game);
        Map<Long, Long> players = rooms.countPlayers(clock.instant()).stream()
                .collect(Collectors.toMap(RoomPlayers::getRoomId, RoomPlayers::getPlayers));
        return rooms.findAvailable().stream()
                .filter(room -> gameId == null
                        || (room.getGame() != null && room.getGame().getId().equals(gameId)))
                .map(room -> RoomResponse.from(room, players.getOrDefault(room.getId(), 0L)))
                .toList();
    }

    public RoomStateResponse state(Long roomId) {
        Room room = rooms.findById(roomId).filter(Room::isAvailable).orElseThrow(RoomService::notAvailable);
        Instant now = TimeConfig.now(clock);
        RoomStateResponse.Round round = rounds.findFirstByRoomIdOrderByIdDesc(roomId)
                .map(current -> RoomStateResponse.Round.from(current, now)).orElse(null);
        List<RoomStateResponse.Result> results = rounds
                .findTop10ByRoomIdAndStatusOrderBySettledAtDescIdDesc(roomId, GameRound.Status.SETTLED).stream()
                .map(settled -> new RoomStateResponse.Result(settled.getId(), settled.getWinningNumber(),
                        RouletteTable.color(settled.getWinningNumber())))
                .toList();
        List<RoomStateResponse.NumberColor> numbers = IntStream.rangeClosed(0, RouletteTable.MAX_NUMBER)
                .mapToObj(number -> new RoomStateResponse.NumberColor(number, RouletteTable.color(number)))
                .toList();
        return new RoomStateResponse(roomId, Formats.utc(now), Formats.money(room.getMinBet()),
                Formats.money(room.getMaxBet()), round, RouletteTable.CHIP_VALUES, results, numbers);
    }

    /** get_object_or_404 de las vistas de Django: se revisa antes de validar la solicitud. */
    public void requireVisible(Long roomId) {
        if (rooms.findById(roomId).filter(Room::isAvailable).isEmpty()) {
            throw ApiException.notFound("No Room matches the given query.");
        }
    }

    static ApiException notAvailable() {
        return ApiException.notFound("Sala no disponible.");
    }

    private static Long positiveId(String value) {
        try {
            long id = Long.parseLong(value.strip());
            if (id >= 1) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Se informa abajo, igual que un número menor que uno.
        }
        // Django responde una lista sin nombre de campo en este filtro.
        throw new InvalidFieldsException(HttpStatus.BAD_REQUEST, List.of("El juego debe ser un número entero positivo."));
    }
}
