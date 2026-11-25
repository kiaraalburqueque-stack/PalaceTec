package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.BetDetailResponse;
import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.dto.ExportFilter;
import com.tecsup.palacetec.dto.HistoryItemResponse;
import com.tecsup.palacetec.dto.PageResponse;
import com.tecsup.palacetec.dto.PathIds;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.HistoryExportService;
import com.tecsup.palacetec.service.HistoryService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    private final HistoryService history;
    private final HistoryExportService exports;

    public HistoryController(HistoryService history, HistoryExportService exports) {
        this.history = history;
        this.exports = exports;
    }

    /** Solo acepta filtros de fecha y página: el jugador siempre sale del JWT. */
    @GetMapping("/users/bets-history")
    public PageResponse<HistoryItemResponse> history(@AuthenticationPrincipal PlayerPrincipal player,
                                                     @RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     @RequestParam(required = false) String page,
                                                     @RequestParam(name = "page_size", required = false) String pageSize,
                                                     HttpServletRequest request) {
        HistoryService.HistoryPage found =
                history.find(player.userId(), DateRange.parse(startDate, endDate), page, pageSize);
        String next = found.page() < found.pages() ? link(request, found.page() + 1) : null;
        String previous = found.page() > 1 ? link(request, found.page() - 1) : null;
        return new PageResponse<>(found.count(), next, previous, found.results());
    }

    @GetMapping("/users/bets-history/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal PlayerPrincipal player, HttpServletRequest request) {
        ExportFilter filter = ExportFilter.of(request);
        byte[] content = exports.export(player.userId(), filter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filter.fileName() + "\"")
                .contentType(MediaType.parseMediaType(filter.format().contentType())).body(content);
    }

    @GetMapping("/users/bets/{betId" + PathIds.DIGITS)
    public BetDetailResponse detail(@AuthenticationPrincipal PlayerPrincipal player, @PathVariable String betId) {
        return history.detail(player.userId(), PathIds.parse(betId));
    }

    private static String link(HttpServletRequest request, long page) {
        Map<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((name, values) -> params.put(name, values[values.length - 1]));
        if (page == 1) {
            params.remove("page");
        } else {
            params.put("page", String.valueOf(page));
        }
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return request.getRequestURL() + (query.isEmpty() ? "" : "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
