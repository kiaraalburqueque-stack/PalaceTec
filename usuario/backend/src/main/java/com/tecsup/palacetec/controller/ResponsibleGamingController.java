package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.DailyLimitRequest;
import com.tecsup.palacetec.dto.DailyLimitResponse;
import com.tecsup.palacetec.dto.PauseResponse;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.dto.RiskAlertsResponse;
import com.tecsup.palacetec.dto.SelfExclusionResponse;
import com.tecsup.palacetec.dto.SessionSummaryResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.security.SessionCookies;
import com.tecsup.palacetec.service.DailyLimitService;
import com.tecsup.palacetec.service.ResponsibleGamingService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/responsible-gaming")
public class ResponsibleGamingController {

    private static final List<Integer> EXCLUSION_DAYS = List.of(7, 30, 90);

    private final DailyLimitService limits;
    private final ResponsibleGamingService responsible;
    private final SessionCookies cookies;

    public ResponsibleGamingController(DailyLimitService limits, ResponsibleGamingService responsible,
                                       SessionCookies cookies) {
        this.limits = limits;
        this.responsible = responsible;
        this.cookies = cookies;
    }

    @GetMapping("/daily-limit")
    public DailyLimitResponse dailyLimit(@AuthenticationPrincipal PlayerPrincipal player) {
        return limits.summary(player);
    }

    @PostMapping("/daily-limit")
    public DailyLimitResponse setDailyLimit(@AuthenticationPrincipal PlayerPrincipal player,
                                            @Valid @RequestBody DailyLimitRequest request) {
        return limits.setLimit(player, request.amount());
    }

    @PostMapping("/self-exclude")
    public SelfExclusionResponse selfExclude(@AuthenticationPrincipal PlayerPrincipal player,
                                             @RequestBody(required = false) JsonNode body,
                                             HttpServletResponse response) {
        RequestFields fields = RequestFields.of(body);
        Integer days = fields.choice("days", EXCLUSION_DAYS);
        fields.throwIfInvalid();
        SelfExclusionResponse confirmation = responsible.selfExclude(player, days);
        cookies.clear(response);
        return confirmation;
    }

    @GetMapping("/session-summary")
    public SessionSummaryResponse sessionSummary(@AuthenticationPrincipal PlayerPrincipal player) {
        return responsible.sessionSummary(player);
    }

    @PostMapping("/pause")
    public PauseResponse pause(@AuthenticationPrincipal PlayerPrincipal player,
                               @RequestBody(required = false) JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        Long incident = fields.integer("incident_id", true, 1);
        fields.throwIfInvalid();
        return responsible.pause(player, incident);
    }

    @GetMapping("/risk-alerts")
    public RiskAlertsResponse riskAlerts(@AuthenticationPrincipal PlayerPrincipal player) {
        return responsible.riskAlerts(player.userId());
    }
}
