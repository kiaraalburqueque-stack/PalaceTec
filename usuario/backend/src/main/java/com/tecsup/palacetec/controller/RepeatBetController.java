package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.PathIds;
import com.tecsup.palacetec.dto.PreviousBetsResponse;
import com.tecsup.palacetec.dto.RepeatRequest;
import com.tecsup.palacetec.dto.RepeatResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.RepeatBetService;
import com.tecsup.palacetec.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/{roomId" + PathIds.DIGITS + "/bets")
public class RepeatBetController {

    private final RepeatBetService repeats;
    private final RoomService rooms;

    public RepeatBetController(RepeatBetService repeats, RoomService rooms) {
        this.repeats = repeats;
        this.rooms = rooms;
    }

    @GetMapping("/previous")
    public PreviousBetsResponse previous(@AuthenticationPrincipal PlayerPrincipal player,
                                         @PathVariable String roomId) {
        long room = PathIds.parse(roomId);
        rooms.requireVisible(room);
        return repeats.previous(player.userId(), room);
    }

    /** 201 al repetir; 200 si la referencia ya se usó para la misma repetición. */
    @PostMapping("/repeat")
    public ResponseEntity<RepeatResponse> repeat(@AuthenticationPrincipal PlayerPrincipal player,
                                                 @PathVariable String roomId,
                                                 @RequestBody(required = false) JsonNode body) {
        long room = PathIds.parse(roomId);
        rooms.requireVisible(room);
        RepeatBetService.Repeated repeated = repeats.repeat(player, room, RepeatRequest.from(body));
        return ResponseEntity.status(repeated.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(repeated.operation());
    }
}
