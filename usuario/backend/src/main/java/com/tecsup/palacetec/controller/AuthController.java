package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.config.SecurityConfig;
import com.tecsup.palacetec.dto.LoginRequest;
import com.tecsup.palacetec.dto.LoginResponse;
import com.tecsup.palacetec.dto.RegisterRequest;
import com.tecsup.palacetec.dto.RegisterResponse;
import com.tecsup.palacetec.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping(SecurityConfig.REGISTER_PATH)
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping(SecurityConfig.LOGIN_PATH)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }
}
