package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.dto.WalletSummaryResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/users/me")
    public ProfileResponse me(@AuthenticationPrincipal PlayerPrincipal player) {
        return profiles.profile(player.userId());
    }

    @GetMapping("/wallet/summary")
    public WalletSummaryResponse walletSummary(@AuthenticationPrincipal PlayerPrincipal player) {
        return profiles.walletSummary(player.userId());
    }
}
