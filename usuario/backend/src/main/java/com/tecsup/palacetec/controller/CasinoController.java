package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.BlackjackActionRequest;
import com.tecsup.palacetec.dto.HandResponse;
import com.tecsup.palacetec.dto.PathIds;
import com.tecsup.palacetec.dto.SportEventsResponse;
import com.tecsup.palacetec.dto.SportsBetRequest;
import com.tecsup.palacetec.dto.StakeRequest;
import com.tecsup.palacetec.dto.TicketResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.BlackjackService;
import com.tecsup.palacetec.service.SportsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Al apostar: 201 si la jugada es nueva y 200 si la referencia la repite. */
@RestController
@RequestMapping("/api/v1/casino")
public class CasinoController {

    private final BlackjackService blackjack;
    private final SportsService sports;

    public CasinoController(BlackjackService blackjack, SportsService sports) {
        this.blackjack = blackjack;
        this.sports = sports;
    }

    @GetMapping("/blackjack")
    public HandResponse hand(@AuthenticationPrincipal PlayerPrincipal player) {
        return blackjack.latest(player.userId());
    }

    @PostMapping("/blackjack")
    public ResponseEntity<HandResponse> start(@AuthenticationPrincipal PlayerPrincipal player,
                                              @RequestBody(required = false) JsonNode body) {
        BlackjackService.Started started = blackjack.start(player, StakeRequest.from(body));
        return ResponseEntity.status(status(started.created())).body(started.hand());
    }

    @PostMapping("/blackjack/{handId" + PathIds.DIGITS + "/action")
    public HandResponse act(@AuthenticationPrincipal PlayerPrincipal player, @PathVariable String handId,
                            @RequestBody(required = false) JsonNode body) {
        return blackjack.act(player, PathIds.parse(handId), BlackjackActionRequest.from(body));
    }

    @GetMapping("/sports/events")
    public SportEventsResponse events() {
        return sports.events();
    }

    @GetMapping("/sports/tickets")
    public List<TicketResponse> tickets(@AuthenticationPrincipal PlayerPrincipal player) {
        return sports.tickets(player.userId());
    }

    @PostMapping("/sports/tickets")
    public ResponseEntity<TicketResponse> place(@AuthenticationPrincipal PlayerPrincipal player,
                                                @RequestBody(required = false) JsonNode body) {
        SportsService.Placed placed = sports.place(player, SportsBetRequest.from(body));
        return ResponseEntity.status(status(placed.created())).body(placed.ticket());
    }

    private static HttpStatus status(boolean created) {
        return created ? HttpStatus.CREATED : HttpStatus.OK;
    }
}
