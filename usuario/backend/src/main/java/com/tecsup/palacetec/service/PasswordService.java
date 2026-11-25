package com.tecsup.palacetec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Emails;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.dto.StrongPassword;
import com.tecsup.palacetec.dto.TextRule;
import com.tecsup.palacetec.entity.PasswordResetToken;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.mail.MailMessage;
import com.tecsup.palacetec.repository.PasswordResetTokenRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.OneTimeTokens;
import com.tecsup.palacetec.security.PasswordRules;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cambio (HU38) y recuperación (HU55) de la contraseña. Los dos cierran todas las sesiones del jugador. */
@Service
public class PasswordService {

    private static final Duration RESET_LIFETIME = Duration.ofMinutes(15);
    private static final String NEW_PASSWORD = "new_password";
    private static final int MAX_PASSWORD = 256;

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionService sessions;
    private final PasswordEncoder passwords;
    private final PasswordRules rules;
    private final ApplicationEventPublisher events;
    private final String frontendUrl;
    private final Clock clock;

    public PasswordService(UserRepository users, PasswordResetTokenRepository resetTokens, SessionService sessions,
                           PasswordEncoder passwords, PasswordRules rules, ApplicationEventPublisher events,
                           PalaceTecProperties properties, Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.passwords = passwords;
        this.rules = rules;
        this.events = events;
        this.frontendUrl = properties.web().frontendUrl();
        this.clock = clock;
    }

    /** No informa si el correo está registrado: quien llama responde siempre lo mismo. */
    @Transactional
    public void forgot(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        String email = fields.text("email", TextRule.required().max(254).email());
        fields.throwIfInvalid();
        users.findByEmailAndActiveTrue(Emails.normalize(email)).ifPresent(user -> {
            Instant now = TimeConfig.now(clock);
            String token = OneTimeTokens.create();
            // Un enlace nuevo invalida los anteriores que no se usaron.
            resetTokens.invalidate(user.getId(), now);
            resetTokens.save(new PasswordResetToken(user.getId(), OneTimeTokens.hash(token), now,
                    now.plus(RESET_LIFETIME)));
            events.publishEvent(resetMail(user, token));
        });
    }

    @Transactional
    public void reset(JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        String token = fields.text("token", TextRule.required().max(128));
        String password = fields.text(NEW_PASSWORD, password());
        String weakness = password == null ? null : StrongPassword.Validator.problem(password);
        if (weakness != null) {
            fields.error(NEW_PASSWORD, weakness);
        }
        fields.throwIfInvalid();
        Instant now = TimeConfig.now(clock);
        PasswordResetToken link = resetTokens.lockByHash(OneTimeTokens.hash(token))
                .filter(found -> found.isUsable(now)).orElseThrow(PasswordService::invalidLink);
        User user = users.lockById(link.getUserId()).filter(User::isActive)
                .orElseThrow(PasswordService::invalidLink);
        if (passwords.matches(password, user.getPassword())) {
            throw InvalidFieldsException.field(NEW_PASSWORD,
                    "La nueva contraseña debe ser distinta de la anterior.");
        }
        checkRules(password, user);
        user.changePassword(passwords.encode(password));
        user.registerLoginResult(0, null);
        sessions.revokeAll(user);
        link.use(now);
    }

    @Transactional
    public void change(PlayerPrincipal player, JsonNode body) {
        User user = sessions.lock(player);
        RequestFields fields = RequestFields.of(body);
        String current = fields.text("current_password", password());
        String password = fields.text(NEW_PASSWORD, password());
        fields.throwIfInvalid();
        if (!passwords.matches(current, user.getPassword())) {
            throw InvalidFieldsException.field("current_password", "La contraseña actual es incorrecta.");
        }
        if (passwords.matches(password, user.getPassword())) {
            throw InvalidFieldsException.field(NEW_PASSWORD, "La nueva contraseña debe ser diferente.");
        }
        String weakness = StrongPassword.Validator.problem(password);
        if (weakness != null) {
            throw InvalidFieldsException.field(NEW_PASSWORD, weakness);
        }
        checkRules(password, user);
        user.changePassword(passwords.encode(password));
        sessions.revokeAll(user);
    }

    private static TextRule password() {
        return TextRule.required().keepSpaces().max(MAX_PASSWORD);
    }

    private void checkRules(String password, User user) {
        List<String> problems = rules.problems(password, user);
        if (!problems.isEmpty()) {
            throw new InvalidFieldsException(HttpStatus.BAD_REQUEST, Map.of(NEW_PASSWORD, problems));
        }
    }

    private MailMessage resetMail(User user, String token) {
        return MailMessage.plain(user.getEmail(), "PalaceTec · Restablece tu contraseña", """
                Hola %s,

                Recibimos una solicitud para restablecer tu contraseña. Abre este enlace en los próximos 15 minutos:

                %s/reset-password/%s

                Si no fuiste tú, ignora este mensaje: tu contraseña actual sigue siendo válida.

                — PalaceTec""".formatted(user.getDisplayName(), frontendUrl, token));
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("invalid_token",
                "El enlace no es válido, ya se usó o venció. Solicita uno nuevo.");
    }
}
