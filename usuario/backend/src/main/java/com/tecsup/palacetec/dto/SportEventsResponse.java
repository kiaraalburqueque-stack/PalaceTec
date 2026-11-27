package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.SportEvent;
import com.tecsup.palacetec.entity.SportSelection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SportEventsResponse(boolean simulation, @JsonProperty("server_time") String serverTime,
                                  List<Event> events) {

    public record Event(
            Long id,
            String home,
            String away,
            @JsonProperty("starts_at") String startsAt,
            @JsonProperty("ends_at") String endsAt,
            String phase,
            Map<String, String> odds,
            List<Short> score) {

        public static Event from(SportEvent event, Instant now) {
            boolean settled = event.getSettledAt() != null;
            String open = now.isBefore(event.getStartsAt()) ? "OPEN" : "LIVE";
            Map<String, String> odds = new LinkedHashMap<>();
            for (SportSelection selection : SportSelection.values()) {
                odds.put(selection.name(), Formats.money(event.oddsFor(selection)));
            }
            return new Event(event.getId(), event.getHome(), event.getAway(), Formats.utc(event.getStartsAt()),
                    Formats.utc(event.getEndsAt()), settled ? "SETTLED" : open, odds,
                    settled ? List.of(event.getHomeScore(), event.getAwayScore()) : null);
        }
    }
}
