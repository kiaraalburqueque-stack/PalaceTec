package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.BalanceHistoryResponse;
import com.tecsup.palacetec.dto.BiggestWinResponse;
import com.tecsup.palacetec.dto.DateFilter;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.dto.StatsSummaryResponse;
import com.tecsup.palacetec.dto.TopGamesResponse;
import com.tecsup.palacetec.dto.WinRateResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.StatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/summary")
    public StatsSummaryResponse summary(@AuthenticationPrincipal PlayerPrincipal player, HttpServletRequest request) {
        return stats.summary(player.userId(), DateFilter.of(request).range());
    }

    @GetMapping("/win-rate")
    public WinRateResponse winRate(@AuthenticationPrincipal PlayerPrincipal player, HttpServletRequest request) {
        return stats.winRate(player.userId(), DateFilter.of(request).range());
    }

    @GetMapping("/biggest-win")
    public BiggestWinResponse biggestWin(@AuthenticationPrincipal PlayerPrincipal player) {
        return stats.biggestWin(player.userId());
    }

    @GetMapping("/balance-history")
    public BalanceHistoryResponse balanceHistory(@AuthenticationPrincipal PlayerPrincipal player,
                                                 HttpServletRequest request) {
        RequestFields query = RequestFields.ofQuery(request);
        String period = query.choiceOr("period", "7", "7", "30");
        query.throwIfInvalid();
        return stats.balanceHistory(player.userId(), Integer.parseInt(period));
    }

    @GetMapping("/top-games")
    public TopGamesResponse topGames(@AuthenticationPrincipal PlayerPrincipal player, HttpServletRequest request) {
        return stats.topGames(player.userId(), DateFilter.of(request).range());
    }
}
