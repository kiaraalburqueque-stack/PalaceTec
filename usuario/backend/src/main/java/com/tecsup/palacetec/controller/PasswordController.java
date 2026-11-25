package com.tecsup.palacetec.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.config.SecurityConfig;
import com.tecsup.palacetec.dto.MeResponse;
import com.tecsup.palacetec.security.AuthGuard;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.security.SessionCookies;
import com.tecsup.palacetec.service.PasswordService;
import com.tecsup.palacetec.service.WebSessionService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordController {

    private static final String CHANGE_PATH = "/api/v1/users/change-password";

    private final PasswordService passwords;
    private final WebSessionService webSessions;
    private final SessionCookies cookies;

    public PasswordController(PasswordService passwords, WebSessionService webSessions, SessionCookies cookies) {
        this.passwords = passwords;
        this.webSessions = webSessions;
        this.cookies = cookies;
    }

    @PostMapping(SecurityConfig.FORGOT_PASSWORD_PATH)
    @AuthGuard(throttled = true)
    public Map<String, String> forgot(@RequestBody(required = false) JsonNode body) {
        passwords.forgot(body);
        // Misma respuesta exista o no la cuenta: no revela correos registrados.
        return Map.of("detail",
                "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.");
    }

    @PostMapping(SecurityConfig.RESET_PASSWORD_PATH)
    @AuthGuard(throttled = true)
    public Map<String, String> reset(@RequestBody(required = false) JsonNode body) {
        passwords.reset(body);
        return Map.of("detail", "Tu contraseña fue actualizada. Ya puedes iniciar sesión.");
    }

    /** En Django, ChangePasswordView hereda el GET de MeView. */
    @GetMapping(CHANGE_PATH)
    public MeResponse me(@AuthenticationPrincipal PlayerPrincipal player) {
        return webSessions.me(player.userId());
    }

    @PutMapping(CHANGE_PATH)
    public Map<String, String> change(@AuthenticationPrincipal PlayerPrincipal player,
                                      @RequestBody(required = false) JsonNode body, HttpServletResponse response) {
        passwords.change(player, body);
        cookies.clear(response);
        return Map.of("detail", "Contraseña actualizada. Inicia sesion nuevamente.");
    }
}
