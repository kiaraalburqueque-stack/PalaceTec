package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Solo lectura en Spring; su fila se bloquea al apostar para ordenar con el reloj de Django. */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    private Long id;

    @Column(nullable = false, length = 80, updatable = false)
    private String name;

    @Column(name = "is_active", nullable = false, updatable = false)
    private boolean active;

    @Column(name = "min_bet", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal minBet;

    @Column(name = "max_bet", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal maxBet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", updatable = false)
    private Game game;

    protected Room() {
    }

    public boolean isAvailable() {
        return active && (game == null || game.isAvailable());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Las salas antiguas sin juego asignado son de Ruleta Europea. */
    public String getGameTitle() {
        return game == null ? "Ruleta Europea" : game.getTitle();
    }

    public BigDecimal getMinBet() {
        return minBet;
    }

    public BigDecimal getMaxBet() {
        return maxBet;
    }

    public Game getGame() {
        return game;
    }
}
