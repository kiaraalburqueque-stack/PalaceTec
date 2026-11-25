package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Preferencias del jugador por tipo de aviso (HU58). La fila se crea con todo activo: NotificationSettingRepository. */
@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean promotions;

    @Column(nullable = false)
    private boolean security;

    @Column(name = "game_alerts", nullable = false)
    private boolean gameAlerts;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSetting() {
    }

    /** Los valores null se conservan. */
    public void update(Boolean newPromotions, Boolean newSecurity, Boolean newGameAlerts, Instant now) {
        promotions = newPromotions == null ? promotions : newPromotions;
        security = newSecurity == null ? security : newSecurity;
        gameAlerts = newGameAlerts == null ? gameAlerts : newGameAlerts;
        updatedAt = now;
    }

    public boolean allows(Notification.Category category) {
        return switch (category) {
            case PROMOTION -> promotions;
            case SECURITY -> security;
            case GAME -> gameAlerts;
        };
    }

    public boolean isPromotions() {
        return promotions;
    }

    public boolean isSecurity() {
        return security;
    }

    public boolean isGameAlerts() {
        return gameAlerts;
    }
}
