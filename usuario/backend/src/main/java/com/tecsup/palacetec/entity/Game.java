package com.tecsup.palacetec.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Catálogo administrado desde el panel de Django. */
@Entity
@Immutable
@Table(name = "roulette_game")
public class Game {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String slug;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(nullable = false, length = 100)
    private String cover;

    @Column(name = "min_bet", nullable = false, precision = 12, scale = 2)
    private BigDecimal minBet;

    @Column(name = "max_bet", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxBet;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rtp;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "play_kind", nullable = false, length = 12)
    private String playKind;

    @Column(name = "play_path", nullable = false, length = 60)
    private String playPath;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    @Column(nullable = false, columnDefinition = "text")
    private String rules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payout_table", nullable = false)
    private JsonNode payoutTable;

    protected Game() {
    }

    public boolean isAvailable() {
        return published && deletedAt == null;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getCover() {
        return cover;
    }

    public BigDecimal getMinBet() {
        return minBet;
    }

    public BigDecimal getMaxBet() {
        return maxBet;
    }

    public BigDecimal getRtp() {
        return rtp;
    }

    /** Clase de jugada de casino enlazada; vacía si el juego usa salas. */
    public String getPlayKind() {
        return playKind;
    }

    public String getPlayPath() {
        return playPath;
    }

    public boolean isFeatured() {
        return featured;
    }

    public String getRules() {
        return rules;
    }

    public JsonNode getPayoutTable() {
        return payoutTable;
    }
}
