package com.tecsup.palacetec.service;

import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.BetRepository.RoomActivity;
import com.tecsup.palacetec.repository.GameRepository;
import com.tecsup.palacetec.repository.PlayRepository;
import com.tecsup.palacetec.repository.PlayRepository.KindActivity;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rondas jugadas y créditos apostados por juego, como roulette/game_activity.py: une las apuestas de ruleta (el juego
 * es el de su sala) con las jugadas de casino (el juego es el enlazado con su clase).
 */
@Service
public class GameActivityService {

    /** Las salas antiguas sin juego asignado cuentan como Ruleta Europea. */
    private static final String DEFAULT_GAME = "ruleta-europea";

    private final BetRepository bets;
    private final PlayRepository plays;
    private final GameRepository games;

    public GameActivityService(BetRepository bets, PlayRepository plays, GameRepository games) {
        this.bets = bets;
        this.plays = plays;
        this.games = games;
    }

    public record Activity(long gameId, long rounds, BigDecimal wagered) {

        private Activity plus(Activity other) {
            return new Activity(gameId, rounds + other.rounds, wagered.add(other.wagered));
        }
    }

    /** Por id de juego. Sin jugador, la actividad de todos. */
    @Transactional(readOnly = true)
    public Map<Long, Activity> byGame(Long userId, DateRange range) {
        Map<Long, Activity> result = new LinkedHashMap<>();
        for (RoomActivity row : bets.sumActivity(userId, range.start(), range.end())) {
            Long game = row.getGameId() != null ? row.getGameId()
                    : games.findBySlug(DEFAULT_GAME).map(Game::getId).orElse(null);
            add(result, game, row.getRounds(), row.getWagered());
        }
        List<KindActivity> casino = plays.sumActivity(userId, range.start(), range.end());
        if (!casino.isEmpty()) {
            Map<String, Long> gameByKind = new LinkedHashMap<>();
            games.findByPlayKindNotOrderById("").forEach(game -> gameByKind.put(game.getPlayKind(), game.getId()));
            casino.forEach(row -> add(result, gameByKind.get(row.getKind()), row.getRounds(), row.getWagered()));
        }
        return result;
    }

    /** Sin juego al que atribuirla, la actividad no se cuenta. */
    private static void add(Map<Long, Activity> result, Long game, long rounds, BigDecimal wagered) {
        if (game != null) {
            result.merge(game, new Activity(game, rounds, wagered), Activity::plus);
        }
    }
}
