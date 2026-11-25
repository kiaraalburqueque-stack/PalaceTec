package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.Notification;

public record NotificationResponse(
        Long id,
        String category,
        String title,
        String message,
        @JsonProperty("is_read") boolean read,
        @JsonProperty("created_at") String createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getCategory().name(),
                notification.getTitle(), notification.getMessage(), notification.isRead(),
                Formats.utc(notification.getCreatedAt()));
    }
}
