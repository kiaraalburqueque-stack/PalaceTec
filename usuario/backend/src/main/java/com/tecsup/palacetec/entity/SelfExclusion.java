package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Constancia de una autoexclusión de 7, 30 o 90 días (HU27). */
@Entity
@Table(name = "responsible_selfexclusion")
public class SelfExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private short days;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "excluded_until", nullable = false, updatable = false)
    private Instant excludedUntil;

    protected SelfExclusion() {
    }

    public SelfExclusion(Long userId, int days, Instant createdAt, Instant excludedUntil) {
        this.userId = userId;
        this.days = (short) days;
        this.createdAt = createdAt;
        this.excludedUntil = excludedUntil;
    }
}
