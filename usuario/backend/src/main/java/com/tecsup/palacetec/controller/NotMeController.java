package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.config.SecurityConfig;
import com.tecsup.palacetec.dto.NotMeResponse;
import com.tecsup.palacetec.security.AuthGuard;
import com.tecsup.palacetec.service.NotMeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotMeController {

    private final NotMeService notMe;

    public NotMeController(NotMeService notMe) {
        this.notMe = notMe;
    }

    @PostMapping(SecurityConfig.NOT_ME_PATH)
    @AuthGuard(throttled = true)
    public NotMeResponse report(@RequestBody(required = false) JsonNode body) {
        return notMe.report(body);
    }
}
