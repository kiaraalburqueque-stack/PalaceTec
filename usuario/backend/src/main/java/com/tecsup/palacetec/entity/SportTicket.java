package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Boleto de deportes, con la cuota del momento de apostar. */
@Entity
@Table(name = "casino_sportticket")
public class SportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "play_id", nullable = false, updatable = false)
    private Play play;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false)
    private SportSelection selection;

    @Column(nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal odds;

    protected SportTicket() {
    }

    public SportTicket(Play play, Long eventId, SportSelection selection, BigDecimal odds) {
        this.play = play;
        this.eventId = eventId;
        this.selection = selection;
        this.odds = odds;
    }

    public Long getId() {
        return id;
    }

    public Play getPlay() {
        return play;
    }

    public Long getEventId() {
        return eventId;
    }

    public SportSelection getSelection() {
        return selection;
    }

    public BigDecimal getOdds() {
        return odds;
    }
}
