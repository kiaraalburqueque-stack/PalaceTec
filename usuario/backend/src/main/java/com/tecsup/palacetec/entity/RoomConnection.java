package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Conexiones WebSocket vigentes de cada sala; las mantiene Django. */
@Entity
@Immutable
@Table(name = "room_connections")
public class RoomConnection {

    @Id
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RoomConnection() {
    }
}
