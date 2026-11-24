package com.tecsup.palacetec.service;

import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.dto.HistoryItemResponse;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.BetRepository.HistoryRow;
import com.tecsup.palacetec.repository.PlayRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historial privado: apuestas de ruleta y jugadas de casino, de la más reciente a la más antigua. */
@Service
public class HistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROULETTE = "roulette";

    private final BetRepository bets;
    private final PlayRepository plays;

    public HistoryService(BetRepository bets, PlayRepository plays) {
        this.bets = bets;
        this.plays = plays;
    }

    public record HistoryPage(long count, long page, long pages, List<HistoryItemResponse> results) {
    }

    @Transactional(readOnly = true)
    public HistoryPage find(Long userId, DateRange range, String page, String pageSize) {
        int size = size(pageSize);
        long count = bets.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, range.start(), range.end())
                + plays.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, range.start(), range.end());
        long pages = Math.max(1, Math.ceilDiv(count, size));
        long number = number(page, pages);
        List<HistoryRow> rows = bets.findHistoryPage(userId, range.start(), range.end(), size, (number - 1) * size);
        return new HistoryPage(count, number, pages, items(userId, rows));
    }

    private List<HistoryItemResponse> items(Long userId, List<HistoryRow> rows) {
        Map<Boolean, List<Long>> ids = rows.stream().collect(Collectors.partitioningBy(
                row -> row.getSource().equals(ROULETTE), Collectors.mapping(HistoryRow::getId, Collectors.toList())));
        Map<Long, Bet> betsById = bets.findForHistory(userId, ids.get(true)).stream()
                .collect(Collectors.toMap(Bet::getId, Function.identity()));
        Map<Long, Play> playsById = plays.findByUserIdAndIdIn(userId, ids.get(false)).stream()
                .collect(Collectors.toMap(Play::getId, Function.identity()));
        return rows.stream().map(row -> row.getSource().equals(ROULETTE)
                ? HistoryItemResponse.from(betsById.get(row.getId()))
                : HistoryItemResponse.from(playsById.get(row.getId()))).toList();
    }

    /** Como DRF: un tamaño inválido usa el predeterminado y uno excesivo se recorta. */
    private static int size(String value) {
        try {
            int size = Integer.parseInt(value == null ? "" : value.strip());
            return size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        } catch (NumberFormatException exception) {
            return DEFAULT_PAGE_SIZE;
        }
    }

    private static long number(String value, long pages) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        if (value.equals("last")) {
            return pages;
        }
        try {
            long number = Long.parseLong(value.strip());
            if (number >= 1 && number <= pages) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // Igual que una página fuera de rango.
        }
        throw ApiException.notFound("Página inválida.");
    }
}
