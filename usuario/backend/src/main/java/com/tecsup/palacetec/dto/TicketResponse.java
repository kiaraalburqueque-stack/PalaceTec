package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.entity.SportTicket;

public record TicketResponse(
        Long id,
        @JsonProperty("event_id") Long eventId,
        String description,
        String selection,
        String odds,
        String amount,
        String payout,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("settled_at") String settledAt) {

    public static TicketResponse from(SportTicket ticket) {
        Play play = ticket.getPlay();
        return new TicketResponse(ticket.getId(), ticket.getEventId(), play.getDescription(),
                ticket.getSelection().name(), Formats.money(ticket.getOdds()), Formats.money(play.getAmount()),
                Formats.money(play.getPayout()), Formats.utc(play.getCreatedAt()), Formats.utc(play.getSettledAt()));
    }
}
