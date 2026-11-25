package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Encuentro simulado. Crearlo y liquidarlo es trabajo exclusivo de runroulette (Django). */
@Entity
@Table(name = "casino_sportevent")
public class SportEvent {

    @Id
    private Long id;

    @Column(nullable = false, length = 80, updatable = false)
    private String home;

    @Column(nullable = false, length = 80, updatable = false)
    private String away;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "home_odds", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal homeOdds;

    @Column(name = "draw_odds", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal drawOdds;

    @Column(name = "away_odds", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal awayOdds;

    @Column(name = "home_score", updatable = false)
    private Short homeScore;

    @Column(name = "away_score", updatable = false)
    private Short awayScore;

    @Column(name = "settled_at", updatable = false)
    private Instant settledAt;

    protected SportEvent() {
    }

    public boolean acceptsBets(Instant now) {
        return settledAt == null && now.isBefore(startsAt);
    }

    public BigDecimal oddsFor(SportSelection selection) {
        return switch (selection) {
            case HOME -> homeOdds;
            case DRAW -> drawOdds;
            case AWAY -> awayOdds;
        };
    }

    public Long getId() {
        return id;
    }

    public String getHome() {
        return home;
    }

    public String getAway() {
        return away;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Short getHomeScore() {
        return homeScore;
    }

    public Short getAwayScore() {
        return awayScore;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
