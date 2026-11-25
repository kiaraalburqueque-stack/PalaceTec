package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.GameResponse;
import com.tecsup.palacetec.dto.RoomResponse;
import com.tecsup.palacetec.dto.RoomStateResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.CatalogService;
import com.tecsup.palacetec.service.RoomService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RouletteController {

    private final CatalogService catalog;
    private final RoomService rooms;

    public RouletteController(CatalogService catalog, RoomService rooms) {
        this.catalog = catalog;
        this.rooms = rooms;
    }

    @GetMapping("/games")
    public List<GameResponse> games(@AuthenticationPrincipal PlayerPrincipal player,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String search) {
        return catalog.list(player.userId(), category, search);
    }

    @GetMapping("/rooms")
    public List<RoomResponse> rooms(@RequestParam(required = false) String game) {
        return rooms.list(game);
    }

    @GetMapping("/rooms/{roomId}/state")
    public RoomStateResponse state(@PathVariable Long roomId) {
        return rooms.state(roomId);
    }
}
