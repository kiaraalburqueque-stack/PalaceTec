package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** En Django, /users/me y /users/profile son la misma vista: las dos leen y escriben. */
@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping({"/me", "/profile"})
    public ProfileResponse read(@AuthenticationPrincipal PlayerPrincipal player) {
        return profiles.profile(player.userId());
    }

    @PutMapping({"/me", "/profile"})
    public ProfileResponse update(@AuthenticationPrincipal PlayerPrincipal player,
                                  @RequestBody(required = false) JsonNode body) {
        return profiles.update(player, body);
    }
}
