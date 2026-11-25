package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.assistant.AssistantService;
import com.tecsup.palacetec.assistant.Odds;
import com.tecsup.palacetec.config.WebConfig;
import com.tecsup.palacetec.dto.ChatResponse;
import com.tecsup.palacetec.dto.OddsResponse;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.dto.TextRule;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.RouletteTable;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Asistente virtual (HU68) y probabilidades de la ruleta (HU69). */
@RestController
public class AssistantController {

    private static final int MAX_MESSAGE = 500;
    private static final int MAX_BET = 20;
    private static final int MAX_SELECTION = 10;

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping(WebConfig.ASSISTANT_CHAT_PATH)
    public ChatResponse.History history(@AuthenticationPrincipal PlayerPrincipal player) {
        return assistant.history(player.userId());
    }

    @PostMapping(WebConfig.ASSISTANT_CHAT_PATH)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatResponse ask(@AuthenticationPrincipal PlayerPrincipal player,
                            @RequestBody(required = false) JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        String message = fields.text("message", TextRule.required().max(MAX_MESSAGE));
        fields.throwIfInvalid();
        return assistant.answer(player, message);
    }

    @GetMapping("/api/v1/assistant/odds")
    public OddsResponse odds(HttpServletRequest request) {
        RequestFields query = RequestFields.ofQuery(request);
        String bet = query.text("bet", TextRule.required().max(MAX_BET));
        Long number = query.hasText("number") ? query.integer("number", false, 0, RouletteTable.MAX_NUMBER) : null;
        String selection = query.hasText("selection") ? query.text("selection", TextRule.optional().max(MAX_SELECTION))
                : null;
        query.throwIfInvalid();
        if (number == null && selection == null) {
            throw InvalidFieldsException.field("selection",
                    "Indica number (pleno) o selection (color, paridad, docena o columna).");
        }
        return Odds.compute(RequestFields.strip(bet).toLowerCase(Locale.ROOT),
                number != null ? number.toString() : selection);
    }
}
