package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.BalanceResponse;
import com.tecsup.palacetec.dto.PreBetCheckResponse;
import com.tecsup.palacetec.dto.WalletSummaryResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    /** En Django, PreBetCheckView hereda el GET de BalanceView. */
    @GetMapping({"/balance", "/pre-bet-check"})
    public BalanceResponse balance(@AuthenticationPrincipal PlayerPrincipal player) {
        return wallets.balance(player.userId());
    }

    @GetMapping("/summary")
    public WalletSummaryResponse summary(@AuthenticationPrincipal PlayerPrincipal player) {
        return wallets.summary(player.userId());
    }

    @PostMapping("/pre-bet-check")
    public PreBetCheckResponse preBetCheck(@AuthenticationPrincipal PlayerPrincipal player,
                                           @RequestBody(required = false) JsonNode body) {
        return wallets.preBetCheck(player.userId(), body == null ? null : body.get("amount"));
    }
}
