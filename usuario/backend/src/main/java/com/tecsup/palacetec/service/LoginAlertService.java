package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.entity.LoginHistory;
import com.tecsup.palacetec.entity.Notification;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.mail.MailMessage;
import com.tecsup.palacetec.repository.LoginHistoryRepository;
import com.tecsup.palacetec.security.ClientInfo;
import com.tecsup.palacetec.security.OneTimeTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registro de accesos y alerta por IP o dispositivo nuevos (HU56). */
@Service
public class LoginAlertService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");

    private final LoginHistoryRepository history;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final String frontendUrl;
    private final Clock clock;

    public LoginAlertService(LoginHistoryRepository history, NotificationService notifications,
                             ApplicationEventPublisher events, PalaceTecProperties properties, Clock clock) {
        this.history = history;
        this.notifications = notifications;
        this.events = events;
        this.frontendUrl = properties.web().frontendUrl();
        this.clock = clock;
    }

    /** El primer acceso de la cuenta no avisa: todavía no hay con qué comparar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void register(User user, ClientInfo client) {
        boolean known = history.existsByUserId(user.getId());
        boolean newIp = known && !history.existsByUserIdAndIpAddress(user.getId(), client.address());
        boolean newDevice = known && !history.existsByUserIdAndUserAgent(user.getId(), client.userAgent());
        Instant now = TimeConfig.now(clock);
        LoginHistory entry = history.save(new LoginHistory(user.getId(), client.address(), client.userAgent(), newIp,
                newDevice, now));
        if ((newIp || newDevice) && notifications.allows(user.getId(), Notification.Category.SECURITY)) {
            alert(user, client, entry, DATE.format(now.atZone(TimeConfig.LIMA)));
        }
    }

    private void alert(User user, ClientInfo client, LoginHistory entry, String when) {
        String device = client.device();
        String ip = client.ip();
        // Token en la ruta, sin "?token=": el enlace no se corta en la consola de correo.
        String token = OneTimeTokens.create();
        String link = frontendUrl + "/seguridad/no-fui-yo/" + token;
        notifications.notify(user.getId(), Notification.Category.SECURITY, "Nuevo acceso a tu cuenta",
                "Iniciaste sesión el %s desde %s (IP %s). Si no fuiste tú, cambia tu contraseña."
                        .formatted(when, device, ip));
        String text = """
                Hola %s,

                Detectamos un nuevo acceso a tu cuenta de PalaceTec:

                  Fecha: %s (hora de Lima)
                  Dispositivo: %s
                  IP: %s

                Si fuiste tú, no necesitas hacer nada.

                Si NO fuiste tú, pulsa "No fui yo" para bloquear tu cuenta temporalmente y cerrar todas las sesiones:

                NO FUI YO:
                %s

                Después restablece tu contraseña desde la pantalla de inicio de sesión.

                — PalaceTec""".formatted(user.getDisplayName(), when, device, ip, link);
        String html = ("<p>Hola %s,</p><p>Detectamos un nuevo acceso a tu cuenta de PalaceTec:</p>"
                + "<ul><li>Fecha: %s (hora de Lima)</li><li>Dispositivo: %s</li><li>IP: %s</li></ul>"
                + "<p>Si no fuiste tú:</p><p><a href=\"%s\" style=\"background:#bb2737;color:#fff;padding:10px 18px;"
                + "border-radius:6px;text-decoration:none;font-weight:bold\">No fui yo</a></p>")
                .formatted(user.getDisplayName(), when, device, ip, link);
        events.publishEvent(new MailMessage(user.getEmail(), "PalaceTec · Nuevo acceso a tu cuenta", text, html));
        entry.alertSent(OneTimeTokens.hash(token));
    }
}
