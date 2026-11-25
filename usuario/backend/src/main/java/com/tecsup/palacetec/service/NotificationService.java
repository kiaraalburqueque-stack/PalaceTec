package com.tecsup.palacetec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.NotificationListResponse;
import com.tecsup.palacetec.dto.NotificationReadAllResponse;
import com.tecsup.palacetec.dto.NotificationReadResponse;
import com.tecsup.palacetec.dto.NotificationResponse;
import com.tecsup.palacetec.dto.PreferencesResponse;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.entity.Notification;
import com.tecsup.palacetec.entity.NotificationSetting;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.NotificationRepository;
import com.tecsup.palacetec.repository.NotificationSettingRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Centro de notificaciones (HU57) y preferencias por tipo de aviso (HU58). */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationSettingRepository settings;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, NotificationSettingRepository settings,
                               Clock clock) {
        this.notifications = notifications;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse latest(Long userId) {
        return new NotificationListResponse(unreadCount(userId),
                notifications.findTop30ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                        .map(NotificationResponse::from).toList());
    }

    @Transactional
    public NotificationReadAllResponse readAll(Long userId) {
        return new NotificationReadAllResponse(notifications.markAllRead(userId, TimeConfig.now(clock)), 0);
    }

    @Transactional
    public NotificationReadResponse read(Long userId, Long notificationId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("No Notification matches the given query."));
        notification.markRead(TimeConfig.now(clock));
        return new NotificationReadResponse(NotificationResponse.from(notification), unreadCount(userId));
    }

    @Transactional
    public PreferencesResponse preferences(Long userId) {
        return PreferencesResponse.from(setting(userId));
    }

    /** Solo cambia las preferencias enviadas. */
    @Transactional
    public PreferencesResponse updatePreferences(Long userId, JsonNode body) {
        RequestFields fields = RequestFields.of(body);
        Boolean promotions = fields.flag("promotions");
        Boolean security = fields.flag("security");
        Boolean gameAlerts = fields.flag("game_alerts");
        fields.throwIfInvalid();
        NotificationSetting setting = setting(userId);
        setting.update(promotions, security, gameAlerts, TimeConfig.now(clock));
        return PreferencesResponse.from(setting);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean allows(Long userId, Notification.Category category) {
        return setting(userId).allows(category);
    }

    /** Guarda el aviso, salvo que el jugador haya desactivado esa categoría. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Long userId, Notification.Category category, String title, String message) {
        if (allows(userId, category)) {
            notifications.save(new Notification(userId, category, title, message, TimeConfig.now(clock)));
        }
    }

    private long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** get_or_create de Django. */
    private NotificationSetting setting(Long userId) {
        return settings.findByUserId(userId).orElseGet(() -> {
            settings.createDefaults(userId, TimeConfig.now(clock));
            return settings.findByUserId(userId).orElseThrow();
        });
    }
}
