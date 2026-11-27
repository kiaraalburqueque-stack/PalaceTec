package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.security.SessionCookies;
import com.tecsup.palacetec.security.WebTokenService;
import com.tecsup.palacetec.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * OriginValidator y CookieJWTMiddleware de config/asgi.py. Sin una sesión válida el socket igual se abre y el handler lo
 * cierra con 4401, el código con el que React renueva la sesión.
 */
@Component
public class SessionHandshake implements HandshakeInterceptor {

    static final String USER = "palacetec.user";
    static final String ROOM = "palacetec.room";

    /** &lt;int:room_id&gt; de roulette/routing.py. */
    private static final Pattern ROOM_PATH = Pattern.compile("^/ws/roulette/([0-9]+)/$");

    private final SessionCookies cookies;
    private final WebTokenService webTokens;
    private final SessionService sessions;
    private final List<String> origins;

    public SessionHandshake(SessionCookies cookies, WebTokenService webTokens, SessionService sessions,
                            PalaceTecProperties properties) {
        this.cookies = cookies;
        this.webTokens = webTokens;
        this.sessions = sessions;
        this.origins = properties.corsAllowedOrigins();
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        HttpServletRequest servlet = ((ServletServerHttpRequest) request).getServletRequest();
        if (!originAllowed(servlet)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (WebSocketHandlerDecorator.unwrap(handler) instanceof RoomSocketHandler) {
            Long roomId = roomId(servlet.getRequestURI());
            if (roomId == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return false;
            }
            attributes.put(ROOM, roomId);
        }
        webTokens.find(cookies.access(servlet), WebTokenService.Type.ACCESS).ifPresent(token -> {
            PlayerPrincipal player = PlayerPrincipal.web(token);
            try {
                sessions.check(player);
                attributes.put(USER, new SocketUser(player, token.expiresAt()));
            } catch (ApiException exception) {
                // Sesión cerrada, cuenta inactiva o excluida: el handler cierra con 4401.
            }
        });
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // Nada que hacer: el handler toma los atributos.
    }

    /** Como OriginValidator de Channels: sin Origin se rechaza; vale el propio servidor y los orígenes de React. */
    private boolean originAllowed(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            return false;
        }
        String own = request.getScheme() + "://" + request.getHeader(HttpHeaders.HOST);
        return origin.equals(own) || origins.contains(origin);
    }

    private static Long roomId(String path) {
        Matcher matcher = ROOM_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
