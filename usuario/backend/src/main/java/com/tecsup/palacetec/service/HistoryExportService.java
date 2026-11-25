package com.tecsup.palacetec.service;

import com.tecsup.palacetec.dto.BetStatus;
import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.dto.ExportFilter;
import com.tecsup.palacetec.entity.Bet;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.export.ExportRow;
import com.tecsup.palacetec.export.HistoryPdf;
import com.tecsup.palacetec.export.HistoryReport;
import com.tecsup.palacetec.export.HistoryWorkbook;
import com.tecsup.palacetec.repository.BetRepository;
import com.tecsup.palacetec.repository.PlayRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.repository.WalletTransactionRepository;
import com.tecsup.palacetec.repository.WalletTransactionRepository.BalanceAfter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exporta las apuestas propias en Excel o PDF (HU66), como roulette/history_export.py. */
@Service
public class HistoryExportService {

    static final int MAX_ROWS = 5000;

    private final UserRepository users;
    private final BetRepository bets;
    private final PlayRepository plays;
    private final WalletTransactionRepository transactions;

    public HistoryExportService(UserRepository users, BetRepository bets, PlayRepository plays,
                                WalletTransactionRepository transactions) {
        this.users = users;
        this.bets = bets;
        this.plays = plays;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public byte[] export(Long userId, ExportFilter filter) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("No encontrado."));
        HistoryReport report = new HistoryReport(user.getEmail(), filter.label(), rows(userId, filter.dates().range()));
        return filter.format() == ExportFilter.Format.XLSX ? HistoryWorkbook.build(report) : HistoryPdf.build(report);
    }

    /** De la más antigua a la más reciente. Si no caben todas se conservan las más recientes (Django, las antiguas). */
    private List<ExportRow> rows(Long userId, DateRange range) {
        List<Bet> foundBets = bets.findForExport(userId, range.start(), range.end(), Limit.of(MAX_ROWS));
        List<Play> foundPlays = plays.findForExport(userId, range.start(), range.end(), Limit.of(MAX_ROWS));
        List<String> references = Stream.concat(foundBets.stream().map(HistoryExportService::reference),
                foundPlays.stream().map(HistoryExportService::reference)).toList();
        Map<String, BigDecimal> balances = references.isEmpty() ? Map.of()
                : transactions.findBalances(userId, references).stream()
                        .collect(Collectors.toMap(BalanceAfter::getReference, BalanceAfter::getBalanceAfter));
        List<ExportRow> rows = new ArrayList<>();
        foundBets.reversed().forEach(bet -> rows.add(new ExportRow(bet.getCreatedAt(),
                bet.getRound().getRoom().getGameTitle(), bet.getKind().label() + ": " + bet.getSelection(),
                bet.getAmount(), BetStatus.ofRoulette(bet.getPayout()), bet.getPayout(),
                balances.get(reference(bet)))));
        foundPlays.reversed().forEach(play -> rows.add(new ExportRow(play.getCreatedAt(), play.getKindLabel(),
                play.getDescription().isEmpty() ? play.getKindLabel() : play.getDescription(), play.getAmount(),
                BetStatus.ofCasino(play.getAmount(), play.getPayout()), play.getPayout(),
                balances.get(reference(play)))));
        rows.sort(Comparator.comparing(ExportRow::createdAt));
        return rows.subList(Math.max(0, rows.size() - MAX_ROWS), rows.size());
    }

    private static String reference(Bet bet) {
        return "roulette:bet:" + bet.getId();
    }

    private static String reference(Play play) {
        return "casino:bet:" + play.getId();
    }
}
