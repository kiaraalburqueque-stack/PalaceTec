package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationReadAllResponse(int updated, @JsonProperty("unread_count") long unreadCount) {
}
