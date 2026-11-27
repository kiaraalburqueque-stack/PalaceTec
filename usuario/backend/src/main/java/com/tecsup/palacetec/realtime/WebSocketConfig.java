package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.config.PalaceTecProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Las rutas de roulette/routing.py, con WebSocket simple (sin STOMP): React no cambia. */
@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomSocketHandler rooms;
    private final NotificationSocketHandler notifications;
    private final SessionHandshake handshake;
    private final PalaceTecProperties properties;

    public WebSocketConfig(RoomSocketHandler rooms, NotificationSocketHandler notifications,
                           SessionHandshake handshake, PalaceTecProperties properties) {
        this.rooms = rooms;
        this.notifications = notifications;
        this.handshake = handshake;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rooms, RoomSocketHandler.PATH)
                .addHandler(notifications, NotificationSocketHandler.PATH)
                .addInterceptors(handshake)
                .setAllowedOrigins(properties.corsAllowedOrigins().toArray(String[]::new));
    }
}
