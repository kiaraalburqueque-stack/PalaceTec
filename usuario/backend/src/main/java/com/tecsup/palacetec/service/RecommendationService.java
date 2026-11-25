package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.dto.GameResponse;
import com.tecsup.palacetec.dto.RecommendationsResponse;
import com.tecsup.palacetec.entity.DailySpending;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.DailyLimitRepository;
import com.tecsup.palacetec.repository.DailySpendingRepository;
import com.tecsup.palacetec.repository.FavoriteGameRepository;
import com.tecsup.palacetec.repository.GameRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.service.GameActivityService.Activity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomendaciones por reglas (HU70), como roulette/recommendations.py. Puntaje de cada juego disponible: 3 por la parte
 * de las rondas del jugador en su categoría, 2 si es favorito y 1 por su popularidad, con la actividad de 30 días.
 */
@Service
public class RecommendationService {

    private static final int LIMIT = 4;
    private static final Duration WINDOW = Duration.ofDays(30);
    private static final int CATEGORY_WEIGHT = 3;
    private static final int FAVORITE_WEIGHT = 2;
    private static final int SCORE_DECIMALS = 3;

    private final UserRepository users;
    private final GameRepository games;
    private final FavoriteGameRepository favorites;
    private final DailyLimitRepository limits;
    private final DailySpendingRepository spendings;
    private final GameActivityService activity;
    private final CatalogService catalog;
    private final Clock clock;

    public RecommendationService(UserRepository users, GameRepository games, FavoriteGameRepository favorites,
                                 DailyLimitRepository limits, DailySpendingRepository spendings,
                                 GameActivityService activity, CatalogService catalog, Clock clock) {
        this.users = users;
        this.games = games;
        this.favorites = favorites;
        this.limits = limits;
        this.spendings = spendings;
        this.activity = activity;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Puntos de un juego. whole: son todos enteros y el puntaje se escribe sin decimales, como en Python. */
    private record Scored(Game game, double category, int favorite, double popularity, boolean whole, long rounds) {

        /** Suma exacta redondeada una sola vez, como sum() de Python con floats. */
        double total() {
            return new BigDecimal(category).add(new BigDecimal(favorite)).add(new BigDecimal(popularity)).doubleValue();
        }

        Number score() {
            if (whole) {
                return (int) total();
            }
            return new BigDecimal(total()).setScale(SCORE_DECIMALS, RoundingMode.HALF_EVEN).doubleValue();
        }
    }

    @Transactional(readOnly = true)
    public RecommendationsResponse recommend(Long userId) {
        Instant now = TimeConfig.now(clock);
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("No encontrado."));
        String reason = blockedReason(user, now);
        if (reason != null) {
            return RecommendationsResponse.empty(reason);
        }
        List<Game> available = games.findAvailable();
        if (available.isEmpty()) {
            return RecommendationsResponse.empty(null);
        }
        Map<Long, Game> byId = available.stream().collect(Collectors.toMap(Game::getId, Function.identity()));
        DateRange window = DateRange.since(now.minus(WINDOW));
        Map<Long, Activity> everyone = activity.byGame(null, window);
        Map<Long, Activity> mine = activity.byGame(userId, window);
        mine.keySet().retainAll(byId.keySet());
        Set<Long> favoriteIds = favorites.findGameIds(userId);

        Map<String, Long> categoryRounds = new HashMap<>();
        Map<String, Activity> topGame = new HashMap<>();
        for (Activity item : mine.values()) {
            String category = byId.get(item.gameId()).getCategory();
            categoryRounds.merge(category, item.rounds(), Long::sum);
            topGame.merge(category, item, (best, other) -> other.rounds() > best.rounds() ? other : best);
        }
        long myTotal = categoryRounds.values().stream().mapToLong(Long::longValue).sum();
        long mostPopular = byId.keySet().stream().mapToLong(id -> rounds(everyone, id)).max().orElse(0);

        Function<Game, GameResponse> response = catalog.responses(favoriteIds);
        Map<Long, Long> firstRooms = catalog.firstRooms();
        List<RecommendationsResponse.Item> results = available.stream()
                .map(game -> {
                    long rounds = rounds(everyone, game.getId());
                    boolean popular = mostPopular > 0 && everyone.containsKey(game.getId());
                    double category = myTotal == 0 ? 0 : CATEGORY_WEIGHT
                            * (categoryRounds.getOrDefault(game.getCategory(), 0L) / (double) myTotal);
                    return new Scored(game, category, favoriteIds.contains(game.getId()) ? FAVORITE_WEIGHT : 0,
                            popular ? rounds / (double) mostPopular : 0, myTotal == 0 && !popular, rounds);
                })
                .sorted(Comparator.comparingDouble(Scored::total).reversed()
                        .thenComparing(Comparator.comparingLong(Scored::rounds).reversed())
                        .thenComparing(item -> item.game().getId()))
                .limit(LIMIT)
                .map(item -> {
                    Activity top = topGame.get(item.game().getCategory());
                    return item(item, response.apply(item.game()), firstRooms.get(item.game().getId()),
                            top == null ? null : byId.get(top.gameId()).getTitle());
                })
                .toList();
        boolean likesSomething = favoriteIds.stream().anyMatch(byId::containsKey);
        return new RecommendationsResponse(results, null, myTotal > 0 || likesSomething);
    }

    /** El motivo es la parte que más suma; si empatan, primero la categoría y después el favorito. */
    private static RecommendationsResponse.Item item(Scored scored, GameResponse game, Long room, String played) {
        String code = "popular";
        String text = "Popular en PalaceTec";
        if (scored.total() > 0) {
            if (scored.category() >= scored.favorite() && scored.category() >= scored.popularity()) {
                code = "category";
                text = "Porque jugaste " + played;
            } else if (scored.favorite() >= scored.popularity()) {
                code = "favorite";
                text = "Está en tus favoritos";
            }
        }
        return new RecommendationsResponse.Item(game, room, text, code, scored.score());
    }

    private static long rounds(Map<Long, Activity> activity, Long game) {
        return activity.containsKey(game) ? activity.get(game).rounds() : 0;
    }

    /** Motivo por el que no se recomienda seguir jugando, o null. */
    private String blockedReason(User user, Instant now) {
        if (user.getSelfExcludedUntil() != null && user.getSelfExcludedUntil().isAfter(now)) {
            return "self_excluded";
        }
        if (user.getPauseUntil() != null && user.getPauseUntil().isAfter(now)) {
            return "pause_active";
        }
        BigDecimal limit = limits.findByUserId(user.getId()).map(found -> found.amountAt(now)).orElse(null);
        if (limit == null) {
            return null;
        }
        BigDecimal spent = spendings.findByUserIdAndDate(user.getId(), now.atZone(TimeConfig.LIMA).toLocalDate())
                .map(DailySpending::getAmount).orElse(BigDecimal.ZERO);
        return spent.compareTo(limit) >= 0 ? "daily_limit_reached" : null;
    }
}
