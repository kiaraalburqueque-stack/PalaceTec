package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Aviso del centro de notificaciones del jugador (HU57). */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Category { PROMOTION, SECURITY, GAME }

    private static final int MAX_TITLE = 120;
    private static final int MAX_MESSAGE = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Category category;

    @Column(nullable = false, length = MAX_TITLE, updatable = false)
    private String title;

    @Column(nullable = false, length = MAX_MESSAGE, updatable = false)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(Long userId, Category category, String title, String message, Instant createdAt) {
        this.userId = userId;
        this.category = category;
        this.title = cut(title, MAX_TITLE);
        this.message = cut(message, MAX_MESSAGE);
        this.createdAt = createdAt;
    }

    /** Conserva la fecha de la primera lectura. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    private static String cut(String text, int length) {
        return text.codePointCount(0, text.length()) <= length ? text
                : text.substring(0, text.offsetByCodePoints(0, length));
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
