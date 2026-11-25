package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.NotificationSetting;

public record PreferencesResponse(boolean promotions, boolean security,
                                  @JsonProperty("game_alerts") boolean gameAlerts) {

    public static PreferencesResponse from(NotificationSetting setting) {
        return new PreferencesResponse(setting.isPromotions(), setting.isSecurity(), setting.isGameAlerts());
    }
}
