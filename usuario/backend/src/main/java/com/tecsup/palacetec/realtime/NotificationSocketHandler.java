package com.tecsup.palacetec.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.config.TimeConfig;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** HU57, NotificationConsumer de Django: ws/notifications/, el canal personal de avisos y saldo. */
@Component
public class NotificationSocketHandler extends TextWebSocketHandler {

    static final String PATH = "/ws/notifications/";
    private static final String CONNECTION = "palacetec.connection";

    private final RealtimeHub hub;
    private final RealtimePoller poller;
    private final RealtimeService service;
    private final ObjectMapper mapper;
    private final Clock clock;

    public NotificationSocketHandler(RealtimeHub hub, RealtimePoller poller, RealtimeService service,
                                     ObjectMapper mapper, Clock clock) {
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
        Connection connection = new Connection(session, mapper, Connection.Kind.NOTIFICATIONS, null, user,
                TimeConfig.now(clock).plus(RealtimePoller.HEARTBEAT));
        session.getAttributes().put(CONNECTION, connection);
        hub.add(connection);
        poller.track(connection);
        connection.send("notification:count", Map.of("unread_count", service.unread(connection.userId())));
    }

    /** Solo responde ping; lo demás se ignora. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = (Connection) session.getAttributes().get(CONNECTION);
        if (connection == null || message.getPayload().length() > RoomSocketHandler.MAX_MESSAGE_LENGTH) {
            return;
        }
        try {
            JsonNode content = mapper.readTree(message.getPayload());
            if (content != null && content.isObject() && "ping".equals(content.path("event").asText(null))) {
                connection.send("pong", Map.of());
            }
        } catch (JsonProcessingException exception) {
            // Mensaje inválido: se ignora.
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = (Connection) session.getAttributes().get(CONNECTION);
        if (connection != null) {
            hub.remove(connection);
        }
    }
}
