package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.NotificationListResponse;
import com.tecsup.palacetec.dto.NotificationReadAllResponse;
import com.tecsup.palacetec.dto.NotificationReadResponse;
import com.tecsup.palacetec.dto.PreferencesResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public NotificationListResponse latest(@AuthenticationPrincipal PlayerPrincipal player) {
        return notifications.latest(player.userId());
    }

    @PostMapping("/notifications/read-all")
    public NotificationReadAllResponse readAll(@AuthenticationPrincipal PlayerPrincipal player) {
        return notifications.readAll(player.userId());
    }

    @PostMapping("/notifications/{notificationId}/read")
    public NotificationReadResponse read(@AuthenticationPrincipal PlayerPrincipal player,
                                         @PathVariable Long notificationId) {
        return notifications.read(player.userId(), notificationId);
    }

    @GetMapping("/users/notification-preferences")
    public PreferencesResponse preferences(@AuthenticationPrincipal PlayerPrincipal player) {
        return notifications.preferences(player.userId());
    }

    @PutMapping("/users/notification-preferences")
    public PreferencesResponse updatePreferences(@AuthenticationPrincipal PlayerPrincipal player,
                                                 @RequestBody(required = false) JsonNode body) {
        return notifications.updatePreferences(player.userId(), body);
    }
}
