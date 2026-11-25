package com.tecsup.palacetec.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Alerta por apuestas que crecen tras varias pérdidas (HU29). Django crea las de ruleta y deportes al liquidar; aquí se
 * crean las de blackjack y se inicia la pausa de cualquiera.
 */
@Entity
@DynamicUpdate
@Table(name = "responsible_riskincident")
public class RiskIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Jugada que la provocó; null si fue una ronda de ruleta. */
    @Column(name = "play_id", updatable = false)
    private Long playId;

    @Column(nullable = false, length = 40, updatable = false)
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private JsonNode evidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "pause_started_at")
    private Instant pauseStartedAt;

    protected RiskIncident() {
    }

    public RiskIncident(Long userId, Long playId, JsonNode evidence, Instant createdAt) {
        this.userId = userId;
        this.playId = playId;
        this.kind = "LOSS_ESCALATION";
        this.evidence = evidence;
        this.createdAt = createdAt;
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
