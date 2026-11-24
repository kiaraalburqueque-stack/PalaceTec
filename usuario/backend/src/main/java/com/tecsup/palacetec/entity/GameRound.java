package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Solo lectura: abrir, cerrar y liquidar rondas es trabajo exclusivo de runroulette (Django). */
@Entity
@Table(name = "game_rounds")
public class GameRound {

    public enum Status { OPEN, CLOSED, SETTLED }

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7, updatable = false)
    private Status status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closes_at", nullable = false, updatable = false)
    private Instant closesAt;

    @Column(name = "winning_number", updatable = false)
    private Short winningNumber;

    @Column(name = "settled_at", updatable = false)
    private Instant settledAt;

    protected GameRound() {
    }

    public boolean acceptsBets(Instant now) {
        return status == Status.OPEN && now.isBefore(closesAt);
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public Short getWinningNumber() {
        return winningNumber;
    }
}
