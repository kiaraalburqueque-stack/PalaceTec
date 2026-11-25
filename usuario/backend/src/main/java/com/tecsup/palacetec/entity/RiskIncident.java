package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/** Alerta por apuestas que crecen tras varias pérdidas (HU29). La crea Django al liquidar; aquí solo se inicia su pausa. */
@Entity
@DynamicUpdate
@Table(name = "responsible_riskincident")
public class RiskIncident {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "pause_started_at")
    private Instant pauseStartedAt;

    protected RiskIncident() {
    }

    public void startPause(Instant now) {
        pauseStartedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPauseStartedAt() {
        return pauseStartedAt;
    }
}
