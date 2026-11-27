package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.BetRequest;
import com.tecsup.palacetec.dto.BetResponse;
import com.tecsup.palacetec.dto.PathIds;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.BetService;
import com.tecsup.palacetec.service.RoomService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1")
public class BetController {

    private static final String PATH = "/rooms/{roomId" + PathIds.DIGITS + "/bets";

    private final BetService bets;
    private final RoomService rooms;

    public BetController(BetService bets, RoomService rooms) {
        this.bets = bets;
        this.rooms = rooms;
    }

    @GetMapping(PATH)
    public List<BetResponse> list(@AuthenticationPrincipal PlayerPrincipal player, @PathVariable String roomId) {
        long room = PathIds.parse(roomId);
        rooms.requireVisible(room);
        return bets.list(player.userId(), room);
    }

    /** 201 al aceptar la apuesta; 200 si la referencia repite la misma. */
    @PostMapping(PATH)
    public ResponseEntity<BetResponse> place(@AuthenticationPrincipal PlayerPrincipal player,
                                             @PathVariable Long roomId, @Valid @RequestBody BetRequest request) {
        BetService.PlacedBet placed = bets.place(player, roomId, request);
        return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK).body(placed.bet());
    }
}
