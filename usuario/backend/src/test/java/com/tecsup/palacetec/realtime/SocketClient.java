package com.tecsup.palacetec.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Navegador mínimo de WebSocket: guarda cada mensaje {"event", "data"} y el código de cierre. */
final class SocketClient extends TextWebSocketHandler {

    private static final long WAIT_SECONDS = 5;

    private final ObjectMapper mapper;
    private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
    private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
    private WebSocketSession session;

    private SocketClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Abre ws://localhost:port + path con esas cookies y ese Origin (null: sin cabecera). */
    static SocketClient open(ObjectMapper mapper, int port, String path, String cookie, String origin)
            throws Exception {
        SocketClient client = new SocketClient(mapper);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (cookie != null) {
            headers.add("Cookie", cookie);
        }
        if (origin != null) {
            headers.setOrigin(origin);
        }
        client.session = new StandardWebSocketClient()
                .execute(client, headers, URI.create("ws://localhost:" + port + path))
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        return client;
    }

    @Override
    protected void handleTextMessage(WebSocketSession ignored, TextMessage message) throws Exception {
        messages.add(mapper.readTree(message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ignored, CloseStatus status) {
        closed.complete(status);
    }

    void send(String text) throws Exception {
        session.sendMessage(new TextMessage(text));
    }

    /** Siguiente mensaje con ese evento; los anteriores con otro evento se descartan. Devuelve su data. */
    JsonNode next(String event) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        List<String> skipped = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            JsonNode message = messages.poll(50, TimeUnit.MILLISECONDS);
            if (message == null) {
                continue;
            }
            assertThat(message.size()).as("solo event y data: %s", message).isEqualTo(2);
            if (event.equals(message.get("event").asText())) {
                return message.get("data");
            }
            skipped.add(message.get("event").asText());
        }
        return fail("No llegó %s; llegaron %s", event, skipped);
    }

    /** Eventos recibidos hasta ahora, en orden, sin esperar. */
    List<String> drain() {
        List<JsonNode> pending = new ArrayList<>();
        messages.drainTo(pending);
        return pending.stream().map(message -> message.get("event").asText()).toList();
    }

    /** Lo que quede en la cola, más lo que llegue en un momento. */
    List<String> settle() throws InterruptedException {
        Thread.sleep(150);
        return drain();
    }

    int closeCode() throws Exception {
        return closed.get(WAIT_SECONDS, TimeUnit.SECONDS).getCode();
    }

    boolean isOpen() {
        return session.isOpen() && !closed.isDone();
    }

    void close() throws Exception {
        if (session.isOpen()) {
            session.close();
        }
    }
}
