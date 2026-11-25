package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Repetición de las apuestas de una ronda en otra (HU35). Su referencia es única por jugador. */
@Entity
@Table(name = "roulette_repeatoperation")
public class RepeatOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "source_round_id", nullable = false, updatable = false)
    private Long sourceRoundId;

    @Column(name = "target_round_id", nullable = false, updatable = false)
    private Long targetRoundId;

    @Column(nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RepeatOperation() {
    }

    public RepeatOperation(Long userId, Long roomId, Long sourceRoundId, Long targetRoundId, UUID reference,
                           Instant createdAt) {
        this.userId = userId;
        this.roomId = roomId;
        this.sourceRoundId = sourceRoundId;
        this.targetRoundId = targetRoundId;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getSourceRoundId() {
        return sourceRoundId;
    }

    public Long getTargetRoundId() {
        return targetRoundId;
    }

    public UUID getReference() {
        return reference;
    }
}
