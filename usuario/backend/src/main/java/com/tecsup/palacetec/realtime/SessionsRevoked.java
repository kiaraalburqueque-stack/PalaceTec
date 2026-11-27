package com.tecsup.palacetec.realtime;

/**
 * Se cerraron sesiones del jugador: todas (sid null) o solo una. Al confirmarse la transacción se cortan sus sockets con
 * session:revoked, igual que el grupo session.&lt;jti&gt; o user.&lt;id&gt; de Django.
 */
public record SessionsRevoked(Long userId, String sid) {

    public static SessionsRevoked all(Long userId) {
        return new SessionsRevoked(userId, null);
    }
}
