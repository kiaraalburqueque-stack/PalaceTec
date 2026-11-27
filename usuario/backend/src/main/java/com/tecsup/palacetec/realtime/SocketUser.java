package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Instant;

/** Jugador del handshake: su sesión web y el vencimiento de la cookie de acceso, que también cierra el socket. */
public record SocketUser(PlayerPrincipal player, Instant expiresAt) {
}
