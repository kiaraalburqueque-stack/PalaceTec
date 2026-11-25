package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NotificationListResponse(@JsonProperty("unread_count") long unreadCount,
                                       List<NotificationResponse> results) {
}
