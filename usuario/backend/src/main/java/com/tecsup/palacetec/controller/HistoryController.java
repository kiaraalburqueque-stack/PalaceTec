package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.DateRange;
import com.tecsup.palacetec.dto.HistoryItemResponse;
import com.tecsup.palacetec.dto.PageResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.HistoryService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
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
