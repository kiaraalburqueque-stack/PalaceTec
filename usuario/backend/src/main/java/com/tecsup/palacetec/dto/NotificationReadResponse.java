package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** La notificación leída y cuántas quedan sin leer. */
public record NotificationReadResponse(@JsonUnwrapped NotificationResponse notification,
                                       @JsonProperty("unread_count") long unreadCount) {
}
