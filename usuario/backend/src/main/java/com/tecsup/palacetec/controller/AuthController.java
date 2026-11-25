package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.config.SecurityConfig;
import com.tecsup.palacetec.dto.AccountResponse;
import com.tecsup.palacetec.dto.FieldMessages;
import com.tecsup.palacetec.dto.LoginRequest;
import com.tecsup.palacetec.dto.MeResponse;
import com.tecsup.palacetec.dto.RegisterRequest;
import com.tecsup.palacetec.dto.RegisterResponse;
import com.tecsup.palacetec.security.AuthGuard;
import com.tecsup.palacetec.security.AuthGuard.Csrf;
import com.tecsup.palacetec.security.Clients;
import com.tecsup.palacetec.security.CsrfGuard;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.security.SessionCookies;
import com.tecsup.palacetec.service.AuthService;
import com.tecsup.palacetec.service.WebSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Autenticación de los dos clientes: la web usa cookies HttpOnly con CSRF y la app móvil, un Bearer. */
@RestController
public class AuthController {

    private final AuthService auth;
    private final WebSessionService webSessions;
    private final SessionCookies cookies;
    private final CsrfGuard csrf;

    public AuthController(AuthService auth, WebSessionService webSessions, SessionCookies cookies, CsrfGuard csrf) {
        this.auth = auth;
        this.webSessions = webSessions;
        this.cookies = cookies;
        this.csrf = csrf;
    }

    @GetMapping(SecurityConfig.CSRF_PATH)
    public Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
        return Map.of("csrfToken", csrf.issue(request, response));
    }

    @PostMapping(SecurityConfig.REGISTER_PATH)
    @ResponseStatus(HttpStatus.CREATED)
    @AuthGuard(csrf = Csrf.UNLESS_NATIVE)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    /** La app móvil recibe el token en el cuerpo; el navegador, solo en cookies que JavaScript no puede leer. */
    @PostMapping(SecurityConfig.LOGIN_PATH)
    @AuthGuard(csrf = Csrf.UNLESS_NATIVE, throttled = true)
    public Object login(@RequestBody(required = false) JsonNode body, HttpServletRequest request,
                        HttpServletResponse response) {
        if (Clients.isNative(request)) {
            return auth.login(LoginRequest.from(body, FieldMessages.MOBILE));
        }
        AuthService.WebLogin login = auth.loginWeb(LoginRequest.from(body, FieldMessages.DJANGO));
        cookies.setAccess(response, login.access());
        cookies.setRefresh(response, login.refresh());
        return new AccountResponse(login.userId(), login.email());
    }

    @GetMapping("/api/v1/auth/me")
    public MeResponse me(@AuthenticationPrincipal PlayerPrincipal player) {
        return webSessions.me(player.userId());
    }

    @PostMapping(SecurityConfig.REFRESH_PATH)
    @AuthGuard(throttled = true)
    public Map<String, String> refresh(HttpServletRequest request, HttpServletResponse response) {
        WebSessionService.Renewal renewal = webSessions.refresh(cookies.refresh(request));
        cookies.setAccess(response, renewal.access());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("detail", "Sesion renovada.");
        body.put("session_started_at", renewal.startedAt());
        return body;
    }

    @PostMapping(SecurityConfig.LOGOUT_PATH)
    @AuthGuard(csrf = Csrf.UNLESS_BEARER)
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        webSessions.logout(Clients.bearer(request), cookies.access(request), cookies.refresh(request));
        cookies.clear(response);
        return Map.of("detail", "Sesion cerrada.");
    }
}
