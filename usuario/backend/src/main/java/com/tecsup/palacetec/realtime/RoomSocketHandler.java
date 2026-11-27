package com.tecsup.palacetec.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.exception.ApiException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** RouletteConsumer de Django: ws/roulette/&lt;sala&gt;/. Las apuestas no van por aquí sino por HTTP. */
@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    static final String PATH = "/ws/roulette/*/";
    /** Mensajes más largos se cierran con 1009, como en Django. */
    static final int MAX_MESSAGE_LENGTH = 4096;
    private static final String CONNECTION = "palacetec.connection";

    private final RealtimeHub hub;
    private final RealtimePoller poller;
    private final RealtimeService service;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RoomSocketHandler(RealtimeHub hub, RealtimePoller poller, RealtimeService service, ObjectMapper mapper,
                             Clock clock) {
        this.hub = hub;
        this.poller = poller;
        this.service = service;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        SocketUser user = (SocketUser) session.getAttributes().get(SessionHandshake.USER);
        if (user == null) {
            session.close(new CloseStatus(Connection.UNAUTHORIZED));
            return;
        }
        Long roomId = (Long) session.getAttributes().get(SessionHandshake.ROOM);
        Connection connection = new Connection(session, mapper, Connection.Kind.ROOM, roomId, user,
                now().plus(RealtimePoller.HEARTBEAT));
        if (!service.join(connection)) {
            session.close(new CloseStatus(Connection.NOT_FOUND));
            return;
        }
        session.getAttributes().put(CONNECTION, connection);
        hub.add(connection);
        poller.track(connection);
        if (sendState(connection)) {
            hub.toRoom(roomId, "room:user_joined", Map.of("user_id", connection.userId()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = connection(session);
        if (connection == null) {
            return;
        }
        String text = message.getPayload();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            connection.close(Connection.TOO_BIG);
            return;
        }
        JsonNode content;
        try {
            content = mapper.readTree(text);
        } catch (JsonProcessingException exception) {
            content = null;
        }
        if (content == null || content.isMissingNode()) {
            connection.send("error", Map.of("detail", "JSON invalido."));
            return;
        }
        // Cada mensaje vuelve a revisar la sesión y renueva la conexión, como receive_json en Django.
        if (!connection.user().expiresAt().isAfter(now()) || !service.refresh(connection)) {
            connection.close(Connection.UNAUTHORIZED);
            return;
        }
        String event = content.isObject() ? content.path("event").asText(null) : null;
        if (!content.isObject()) {
            connection.send("error", Map.of("detail", "Se requiere un objeto JSON."));
        } else if ("room:sync".equals(event)) {
            sendState(connection);
        } else if ("room:resume".equals(event)) {
            try {
                connection.send("RECONNECT_SUCCESS", service.resume(connection.userId(), connection.roomId()));
            } catch (ApiException exception) {
                connection.close(Connection.NOT_FOUND);
            }
        } else if ("ping".equals(event)) {
            connection.send("pong", Map.of());
        } else {
            connection.send("error", Map.of("detail", "Evento no permitido. Envia apuestas por HTTP."));
        }
    }

    /** Django cierra con 1009 todo lo que no sea texto. */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Connection connection = connection(session);
        if (connection != null) {
            connection.close(Connection.TOO_BIG);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connection(session);
        if (connection == null) {
            return;
        }
        hub.remove(connection);
        service.leave(connection);
        hub.toRoom(connection.roomId(), "room:user_left", Map.of("user_id", connection.userId()));
    }

    /** room:state; si la sala dejó de estar disponible, se cierra con 4404. */
    private boolean sendState(Connection connection) {
        try {
            connection.send("room:state", service.state(connection.roomId()));
            return true;
        } catch (ApiException exception) {
            connection.close(Connection.NOT_FOUND);
            return false;
        }
    }

    private static Connection connection(WebSocketSession session) {
        return (Connection) session.getAttributes().get(CONNECTION);
    }

    private Instant now() {
        return TimeConfig.now(clock);
    }
}
