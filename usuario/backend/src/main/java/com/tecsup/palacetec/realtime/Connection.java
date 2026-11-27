package com.tecsup.palacetec.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** Un WebSocket abierto: la sala (o el canal de notificaciones) y la sesión web que lo autenticó. */
public final class Connection {

    public enum Kind { ROOM, NOTIFICATIONS }

    /** Códigos de cierre de los consumers de Django. */
    public static final int UNAUTHORIZED = 4401;
    public static final int FORBIDDEN = 4403;
    public static final int NOT_FOUND = 4404;
    public static final int TOO_BIG = 1009;

    private static final Logger log = LoggerFactory.getLogger(Connection.class);
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final WebSocketSession session;
    private final ObjectMapper mapper;
    private final Kind kind;
    private final Long roomId;
    private final SocketUser user;
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile Instant nextCheck;

    public Connection(WebSocketSession session, ObjectMapper mapper, Kind kind, Long roomId, SocketUser user,
                      Instant nextCheck) {
        // Varios hilos envían a la vez (sondeo, eventos de Spring y respuestas): el decorador los ordena.
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
        this.mapper = mapper;
        this.kind = kind;
        this.roomId = roomId;
        this.user = user;
        this.nextCheck = nextCheck;
    }

    /** Mensaje con el formato de Django: {"event": ..., "data": ...}. */
    public record Message(String event, Object data) {
    }

    public void send(String event, Object data) {
        if (closing.get() || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(new Message(event, data))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar " + event, exception);
        } catch (IOException | IllegalStateException exception) {
            // El navegador se fue: afterConnectionClosed limpia la conexión.
            log.debug("No se pudo enviar {} a {}", event, id(), exception);
        }
    }

    public void close(int code) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        try {
            session.close(new CloseStatus(code));
        } catch (IOException exception) {
            log.debug("No se pudo cerrar {}", id(), exception);
        }
    }

    /** session:revoked: la sesión ya no vale. React la da por terminada; el código 4401 es el mismo de Django. */
    public void revoke() {
        send("session:revoked", Map.of());
        close(UNAUTHORIZED);
    }

    /** HU42: el administrador suspendió la cuenta. */
    public void forceDisconnect() {
        send("FORCE_DISCONNECT", Map.of("reason", "account_suspended"));
        close(FORBIDDEN);
    }

    public boolean isClosing() {
        return closing.get() || !session.isOpen();
    }

    public String id() {
        return session.getId();
    }

    /** channel_name de room_connections: único y distinto de los de Channels. */
    public String channelName() {
        return "spring." + session.getId();
    }

    public Kind kind() {
        return kind;
    }

    public Long roomId() {
        return roomId;
    }

    public SocketUser user() {
        return user;
    }

    public Long userId() {
        return user.player().userId();
    }

    public Instant nextCheck() {
        return nextCheck;
    }

    public void nextCheck(Instant value) {
        nextCheck = value;
    }
}
