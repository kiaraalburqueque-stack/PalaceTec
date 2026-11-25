package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.DailyLimitRequest;
import com.tecsup.palacetec.dto.DailyLimitResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.DailyLimitService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/responsible-gaming")
public class ResponsibleGamingController {

    private final DailyLimitService limits;

    public ResponsibleGamingController(DailyLimitService limits) {
        this.limits = limits;
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
}
