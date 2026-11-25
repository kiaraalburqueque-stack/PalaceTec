package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.BalanceHistoryResponse;
import com.tecsup.palacetec.dto.BiggestWinResponse;
import com.tecsup.palacetec.dto.BiggestWinResponse.Win;
import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.PlayTotals;
import com.tecsup.palacetec.dto.StatsSummaryResponse;
import com.tecsup.palacetec.dto.TopGamesResponse;
import com.tecsup.palacetec.dto.WinRateResponse;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.Game;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.entity.WalletTransaction;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.GameRepository;
import com.tecsup.palacetec.repository.PlayRepository;
import com.tecsup.palacetec.repository.WalletTransactionRepository;
import com.tecsup.palacetec.service.GameActivityService.Activity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estadísticas privadas del jugador (HU40, HU51, HU52, HU53 y HU67), como stats/views.py. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private static final int TOP_GAMES = 5;

    private final BetRepository bets;
    private final PlayRepository plays;
    private final WalletTransactionRepository transactions;
    private final GameRepository games;
    private final GameActivityService activity;
    private final Clock clock;

    public StatsService(BetRepository bets, PlayRepository plays, WalletTransactionRepository transactions,
                        GameRepository games, GameActivityService activity, Clock clock) {
        this.bets = bets;
        this.plays = plays;
        this.transactions = transactions;
        this.games = games;
        this.activity = activity;
        this.clock = clock;
    }

    public StatsSummaryResponse summary(Long userId, DateRange range) {
        return new StatsSummaryResponse(PlayTotals.sum(bets.sumSettled(userId, range.start(), range.end()),
                plays.sumSettled(userId, range.start(), range.end())));
    }

    public WinRateResponse winRate(Long userId, DateRange range) {
        return WinRateResponse.of(bets.countOutcomes(userId, range.start(), range.end()),
                plays.countOutcomes(userId, range.start(), range.end()));
    }

    /** Entre ruleta y casino gana la mayor ganancia neta; si empatan, la más antigua y después la de ruleta. */
    public BiggestWinResponse biggestWin(Long userId) {
        Bet bet = bets.findWins(userId, Limit.of(1)).stream().findFirst().orElse(null);
        Play play = plays.findWins(userId, Limit.of(1)).stream().findFirst().orElse(null);
        if (bet == null) {
            return new BiggestWinResponse(play == null ? null : Win.from(play));
        }
        if (play == null) {
            return new BiggestWinResponse(Win.from(bet));
        }
        int order = profit(play.getAmount(), play.getPayout()).compareTo(profit(bet.getAmount(), bet.getPayout()));
        boolean casino = order > 0 || order == 0 && play.getCreatedAt().isBefore(bet.getCreatedAt());
        return new BiggestWinResponse(casino ? Win.from(play) : Win.from(bet));
    }

    /** Saldo al cierre de cada uno de los últimos días de Lima; un día sin movimientos conserva el anterior. */
    public BalanceHistoryResponse balanceHistory(Long userId, int days) {
        LocalDate first = clock.instant().atZone(TimeConfig.LIMA).toLocalDate().minusDays(days - 1L);
        Instant start = startOfDay(first);
        BigDecimal opening = transactions.findBefore(userId, start, Limit.of(1)).stream().findFirst()
                .map(WalletTransaction::getBalanceAfter).orElse(BigDecimal.ZERO);
        List<WalletTransaction> movements = transactions.findSince(userId, start);
        List<BalanceHistoryResponse.Point> points = new ArrayList<>();
        BigDecimal balance = opening;
        int index = 0;
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = first.plusDays(offset);
            Instant end = startOfDay(day.plusDays(1));
            int count = 0;
            while (index < movements.size() && movements.get(index).getCreatedAt().isBefore(end)) {
                balance = movements.get(index).getBalanceAfter();
                index++;
                count++;
            }
            points.add(new BalanceHistoryResponse.Point(day.toString(), Formats.money(balance), count));
        }
        return new BalanceHistoryResponse(days, Formats.money(opening), Formats.money(balance), "America/Lima", points);
    }

    /** Los juegos con más rondas y, si empatan, con más créditos apostados. */
    public TopGamesResponse topGames(Long userId, DateRange range) {
        Collection<Activity> played = activity.byGame(userId, range).values();
        Map<Long, Game> byId = games.findByIdIn(played.stream().map(Activity::gameId).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        List<TopGamesResponse.Item> results = played.stream()
                .sorted(Comparator.comparingLong(Activity::rounds).reversed()
                        .thenComparing(Activity::wagered, Comparator.reverseOrder())
                        .thenComparingLong(Activity::gameId))
                .limit(TOP_GAMES)
                .map(item -> new TopGamesResponse.Item(item.gameId(), byId.get(item.gameId()).getTitle(),
                        byId.get(item.gameId()).getCategory(), item.rounds(), Formats.money(item.wagered())))
                .toList();
        return new TopGamesResponse(results, played.stream().mapToLong(Activity::rounds).sum(), "America/Lima");
    }

    private static BigDecimal profit(BigDecimal amount, BigDecimal payout) {
        return payout.subtract(amount);
    }

    private static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(TimeConfig.LIMA).toInstant();
    }
}
