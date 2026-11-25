package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.CatalogFilter;
import com.tecsup.palacetec.dto.CategoryCountsResponse;
import com.tecsup.palacetec.dto.FavoriteResponse;
import com.tecsup.palacetec.dto.FeaturedGameResponse;
import com.tecsup.palacetec.dto.GameResponse;
import com.tecsup.palacetec.dto.GameRulesResponse;
import com.tecsup.palacetec.dto.PathIds;
import com.tecsup.palacetec.dto.RecommendationsResponse;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.CatalogService;
import com.tecsup.palacetec.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {

    private final CatalogService catalog;
    private final RecommendationService recommendations;

    public GameController(CatalogService catalog, RecommendationService recommendations) {
        this.catalog = catalog;
        this.recommendations = recommendations;
    }

    @GetMapping("/categories")
    public CategoryCountsResponse categories(@AuthenticationPrincipal PlayerPrincipal player,
                                             HttpServletRequest request) {
        return catalog.categories(player.userId(), CatalogFilter.read(RequestFields.ofQuery(request)));
    }

    @GetMapping("/favorites")
    public List<GameResponse> favorites(@AuthenticationPrincipal PlayerPrincipal player, HttpServletRequest request) {
        return catalog.favorites(player.userId(), CatalogFilter.read(RequestFields.ofQuery(request)));
    }

    @GetMapping("/featured")
    public List<FeaturedGameResponse> featured(@AuthenticationPrincipal PlayerPrincipal player) {
        return catalog.featured(player.userId());
    }

    @GetMapping("/recommendations")
    public RecommendationsResponse recommendations(@AuthenticationPrincipal PlayerPrincipal player) {
        return recommendations.recommend(player.userId());
    }

    /** Marca el juego como favorito o, si ya lo era, lo quita. */
    @PostMapping("/{gameId" + PathIds.DIGITS + "/favorite")
    public FavoriteResponse favorite(@AuthenticationPrincipal PlayerPrincipal player, @PathVariable String gameId) {
        return catalog.toggleFavorite(player.userId(), PathIds.parse(gameId));
    }

    @GetMapping("/{gameId" + PathIds.DIGITS + "/rules")
    public GameRulesResponse rules(@PathVariable String gameId) {
        return catalog.rules(PathIds.parse(gameId));
    }
}
