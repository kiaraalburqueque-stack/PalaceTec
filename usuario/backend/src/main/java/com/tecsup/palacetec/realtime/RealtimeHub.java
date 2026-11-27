package com.tecsup.palacetec.realtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Los grupos de Channels en memoria: room.&lt;sala&gt;, user.&lt;id&gt; y session.&lt;sid&gt;. Solo conoce los sockets de este
 * proceso; lo que ocurre en Django llega por el sondeo (RealtimePoller).
 */
@Component
public class RealtimeHub {

    /** FORWARDED del NotificationConsumer de Django: lo único del grupo user.&lt;id&gt; que llega a ese canal. */
    static final Set<String> NOTIFICATION_EVENTS = Set.of("notification:new", "wallet:update");

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public void add(Connection connection) {
        connections.put(connection.id(), connection);
    }

    public void remove(Connection connection) {
        connections.remove(connection.id());
    }

    /** Conexiones abiertas en este momento. */
    public List<Connection> all() {
        return connections.values().stream().filter(connection -> !connection.isClosing()).toList();
    }

    public void toRoom(Long roomId, String event, Object data) {
        each(connection -> connection.kind() == Connection.Kind.ROOM && roomId.equals(connection.roomId()),
                connection -> connection.send(event, data));
    }

    /** Grupo user.&lt;id&gt;: las salas reciben todo; el canal de notificaciones, solo NOTIFICATION_EVENTS. */
    public void toUser(Long userId, String event, Object data) {
        each(connection -> userId.equals(connection.userId())
                        && (connection.kind() == Connection.Kind.ROOM || NOTIFICATION_EVENTS.contains(event)),
                connection -> connection.send(event, data));
    }

    /** Cierra los sockets de una sesión (sid) o, con sid null, todos los del jugador. */
    public void revoke(Long userId, String sid) {
        each(connection -> userId.equals(connection.userId())
                        && (sid == null || sid.equals(connection.user().player().jti())),
                Connection::revoke);
    }

    public void each(Predicate<Connection> filter, Consumer<Connection> action) {
        all().stream().filter(filter).forEach(action);
    }
}
