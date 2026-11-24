package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.GameResponse;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.repository.FavoriteGameRepository;
import com.tecsup.palacetec.repository.GameRepository;
import com.tecsup.palacetec.repository.GameRepository.RoomLimits;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

/** Catálogo del jugador, con los mismos filtros que roulette/catalog.py. */
@Service
public class CatalogService {

    private static final Map<String, String> CATEGORY_BY_SLUG =
            Map.of("ruleta", "RULETA", "cartas", "CARTAS", "deportes", "DEPORTES", "otros", "OTROS");
    private static final Map<String, String> SLUG_BY_CATEGORY = CATEGORY_BY_SLUG.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private final GameRepository games;
    private final FavoriteGameRepository favorites;
    private final String mediaBaseUrl;

    public CatalogService(GameRepository games, FavoriteGameRepository favorites, PalaceTecProperties properties) {
        this.games = games;
        this.favorites = favorites;
        this.mediaBaseUrl = properties.mediaBaseUrl();
    }

    @Transactional(readOnly = true)
    public List<GameResponse> list(Long userId, String category, String search) {
        String slug = validCategory(category);
        String term = normalize(validSearch(search));
        Map<Long, RoomLimits> limits = games.findRoomLimits().stream()
                .collect(Collectors.toMap(RoomLimits::getGameId, Function.identity()));
        Set<Long> favoriteIds = favorites.findGameIds(userId);
        return games.findAvailable().stream()
                .filter(game -> slug.isEmpty() || game.getCategory().equals(CATEGORY_BY_SLUG.get(slug)))
                .filter(game -> term.isEmpty() || normalize(game.getTitle()).contains(term))
                .map(game -> response(game, limits.get(game.getId()), favoriteIds.contains(game.getId())))
                .toList();
    }

    private GameResponse response(Game game, RoomLimits limits, boolean favorite) {
        String cover = game.getCover().isEmpty() ? null
                : mediaBaseUrl + "/media/" + UriUtils.encodePath(game.getCover(), StandardCharsets.UTF_8);
        return new GameResponse(game.getId(), game.getSlug(), game.getTitle(), game.getCategory(),
                SLUG_BY_CATEGORY.getOrDefault(game.getCategory(), "otros"), Formats.money(game.getRtp()), cover,
                Formats.money(limits != null ? limits.getMinBet() : game.getMinBet()),
                Formats.money(limits != null ? limits.getMaxBet() : game.getMaxBet()),
                limits != null ? "/api/v1/rooms?game=" + game.getId() : null,
                game.getPlayPath().isEmpty() ? null : game.getPlayPath(), favorite);
    }

    private static String validCategory(String category) {
        String slug = category == null ? "" : category.strip().toLowerCase(Locale.ROOT);
        if (!slug.isEmpty() && !CATEGORY_BY_SLUG.containsKey(slug)) {
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

    /** Minúsculas, sin tildes y con un solo espacio entre palabras. */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return plain.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
