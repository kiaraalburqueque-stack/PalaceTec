package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.CatalogFilter;
import com.tecsup.palacetec.dto.CategoryCountsResponse;
import com.tecsup.palacetec.dto.FavoriteResponse;
import com.tecsup.palacetec.dto.FeaturedGameResponse;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.GameCategory;
import com.tecsup.palacetec.dto.GameResponse;
import com.tecsup.palacetec.dto.GameRulesResponse;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.entity.Room;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.repository.FavoriteGameRepository;
import com.tecsup.palacetec.repository.GameRepository;
import com.tecsup.palacetec.repository.GameRepository.RoomLimits;
import com.tecsup.palacetec.repository.RoomRepository;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

/** Catálogo del jugador, con los mismos filtros que roulette/catalog.py. */
@Service
public class CatalogService {

    private static final int FEATURED_LIMIT = 5;

    private final GameRepository games;
    private final RoomRepository rooms;
    private final FavoriteGameRepository favorites;
    private final String mediaBaseUrl;
    private final Clock clock;

    public CatalogService(GameRepository games, RoomRepository rooms, FavoriteGameRepository favorites,
                          PalaceTecProperties properties, Clock clock) {
        this.games = games;
        this.rooms = rooms;
        this.favorites = favorites;
        this.mediaBaseUrl = properties.mediaBaseUrl();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GameResponse> list(Long userId, String category, String search) {
        String slug = validCategory(category);
        String term = normalize(validSearch(search));
        Function<Game, GameResponse> response = responses(favorites.findGameIds(userId));
        return games.findAvailable().stream()
                .filter(game -> slug.isEmpty() || game.getCategory().equals(GameCategory.ofSlug(slug).name()))
                .filter(game -> matches(game, term))
                .map(response)
                .toList();
    }

    /** Cantidad por categoría, incluidas las vacías. Como en Django, la búsqueda cuenta y la categoría no. */
    @Transactional(readOnly = true)
    public CategoryCountsResponse categories(Long userId, CatalogFilter filter) {
        List<Game> found = search(filter);
        Set<Long> favoriteIds = favorites.findGameIds(userId);
        Map<GameCategory, Long> counts = found.stream()
                .collect(Collectors.groupingBy(game -> GameCategory.ofCode(game.getCategory()), Collectors.counting()));
        List<CategoryCountsResponse.Category> categories = Arrays.stream(GameCategory.values())
                .map(category -> new CategoryCountsResponse.Category(category.slug(), category.label(),
                        counts.getOrDefault(category, 0L)))
                .toList();
        return new CategoryCountsResponse(found.size(), categories,
                found.stream().filter(game -> favoriteIds.contains(game.getId())).count());
    }

    /** Favoritos disponibles, del más reciente al más antiguo. La categoría se valida, pero no filtra. */
    @Transactional(readOnly = true)
    public List<GameResponse> favorites(Long userId, CatalogFilter filter) {
        List<Long> order = favorites.findGameIdsNewestFirst(userId);
        Map<Long, Game> found = search(filter).stream().collect(Collectors.toMap(Game::getId, Function.identity()));
        Function<Game, GameResponse> response = responses(Set.copyOf(order));
        return order.stream().map(found::get).filter(Objects::nonNull).map(response).toList();
    }

    /** Un favorito se puede quitar aunque el juego ya no esté disponible; agregarlo, no. */
    @Transactional
    public FavoriteResponse toggleFavorite(Long userId, long gameId) {
        if (favorites.remove(userId, gameId) > 0) {
            return new FavoriteResponse(gameId, false);
        }
        if (games.findAvailable().stream().noneMatch(game -> game.getId() == gameId)) {
            throw notFound();
        }
        favorites.add(userId, gameId, TimeConfig.now(clock));
        return new FavoriteResponse(gameId, true);
    }

    @Transactional(readOnly = true)
    public GameRulesResponse rules(long gameId) {
        Game game = games.findPublished(gameId).orElseThrow(CatalogService::notFound);
        return GameRulesResponse.from(game, firstRooms().get(gameId));
    }

    @Transactional(readOnly = true)
    public List<FeaturedGameResponse> featured(Long userId) {
        Function<Game, GameResponse> response = responses(favorites.findGameIds(userId));
        Map<Long, Long> firstRooms = firstRooms();
        return games.findAvailable().stream().filter(Game::isFeatured).limit(FEATURED_LIMIT)
                .map(game -> new FeaturedGameResponse(response.apply(game), firstRooms.get(game.getId())))
                .toList();
    }

    /** Sala disponible de menor id de cada juego. */
    public Map<Long, Long> firstRooms() {
        return rooms.findAvailable().stream().filter(room -> room.getGame() != null)
                .collect(Collectors.toMap(room -> room.getGame().getId(), Room::getId, Math::min));
    }

    /** Ficha de un juego del catálogo, con sus límites actuales y si está entre los favoritos. */
    public Function<Game, GameResponse> responses(Set<Long> favoriteIds) {
        Map<Long, RoomLimits> limits = games.findRoomLimits().stream()
                .collect(Collectors.toMap(RoomLimits::getGameId, Function.identity()));
        return game -> response(game, limits.get(game.getId()), favoriteIds.contains(game.getId()));
    }

    private List<Game> search(CatalogFilter filter) {
        String term = normalize(filter.search());
        return games.findAvailable().stream().filter(game -> matches(game, term)).toList();
    }

    private static boolean matches(Game game, String term) {
        return term.isEmpty() || normalize(game.getTitle()).contains(term);
    }

    private GameResponse response(Game game, RoomLimits limits, boolean favorite) {
        String cover = game.getCover().isEmpty() ? null
                : mediaBaseUrl + "/media/" + UriUtils.encodePath(game.getCover(), StandardCharsets.UTF_8);
        return new GameResponse(game.getId(), game.getSlug(), game.getTitle(), game.getCategory(),
                GameCategory.ofCode(game.getCategory()).slug(), Formats.money(game.getRtp()), cover,
                Formats.money(limits != null ? limits.getMinBet() : game.getMinBet()),
                Formats.money(limits != null ? limits.getMaxBet() : game.getMaxBet()),
                limits != null ? "/api/v1/rooms?game=" + game.getId() : null,
                game.getPlayPath().isEmpty() ? null : game.getPlayPath(), favorite);
    }

    private static String validCategory(String category) {
        String slug = category == null ? "" : category.strip().toLowerCase(Locale.ROOT);
        if (!slug.isEmpty() && GameCategory.ofSlug(slug) == null) {
            throw InvalidFieldsException.field("category",
                    "Categoría no válida. Usa ruleta, cartas, deportes u otros.");
        }
        return slug;
    }

    private static String validSearch(String search) {
        String text = search == null ? "" : search.strip();
        if (text.length() > 80) {
            throw InvalidFieldsException.field("search", "Usa como máximo 80 caracteres.");
        }
        return text;
    }

    private static ApiException notFound() {
        return ApiException.notFound("No Game matches the given query.");
    }

    /** Minúsculas, sin tildes y con un solo espacio entre palabras. */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return plain.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
