package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mano de blackjack. Las cartas se reparten del final del mazo, que el cliente nunca recibe. */
@Entity
@DynamicUpdate
@Table(name = "casino_blackjackhand")
public class BlackjackHand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "play_id", nullable = false, updatable = false)
    private Play play;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> deck;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> player;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> dealer;

    @Column(nullable = false)
    private boolean active;

    /** Aumenta con cada acción: una solicitud con otra revisión es un reintento. */
    @Column(nullable = false)
    private int revision;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "last_action_at", nullable = false)
    private Instant lastActionAt;

    protected BlackjackHand() {
    }

    /** Reparte dos cartas al jugador y dos a la banca. */
    public BlackjackHand(Play play, List<String> shuffledDeck, Instant now) {
        this.play = play;
        this.userId = play.getUserId();
        this.deck = new ArrayList<>(shuffledDeck);
        this.player = List.of();
        this.dealer = List.of();
        this.active = true;
        this.outcome = "";
        this.lastActionAt = now;
        drawForPlayer();
        drawForPlayer();
        drawForDealer();
        drawForDealer();
    }

    public void drawForPlayer() {
        player = plus(player, drawCard());
    }

    public void drawForDealer() {
        dealer = plus(dealer, drawCard());
    }

    public void finish(String result) {
        outcome = result;
        active = false;
    }

    public void registerAction(Instant now) {
        revision++;
        lastActionAt = now;
    }

    /** Las listas se reemplazan, no se modifican: así Hibernate ve el cambio. */
    private String drawCard() {
        List<String> rest = new ArrayList<>(deck);
        String card = rest.removeLast();
        deck = rest;
        return card;
    }

    private static List<String> plus(List<String> cards, String card) {
        List<String> result = new ArrayList<>(cards);
        result.add(card);
        return result;
    }

    public Long getId() {
        return id;
    }

    public Play getPlay() {
        return play;
    }

    public List<String> getPlayer() {
        return player;
    }

    public List<String> getDealer() {
        return dealer;
    }

    public boolean isActive() {
        return active;
    }

    public int getRevision() {
        return revision;
    }

    public String getOutcome() {
        return outcome;
    }
}
