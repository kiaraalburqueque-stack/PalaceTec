package com.tecsup.palacetec.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.tecsup.palacetec.assistant.AssistantProvider.Turn;
import com.tecsup.palacetec.config.AssistantProperties;
import com.tecsup.palacetec.entity.AssistantMessage.Role;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Los clientes de IA contra un servidor local: ninguna prueba sale a Internet. */
class AssistantProvidersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY = "clave-ficticia";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final List<Turn> HISTORY = List.of(new Turn(Role.USER, "a"), new Turn(Role.ASSISTANT, "b"));

    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;
    private int status = 200;
    private String answer = "";
    private boolean hang;
    private String path;
    private Headers headers;
    private JsonNode payload;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            path = exchange.getRequestURI().toString();
            headers = exchange.getRequestHeaders();
            payload = MAPPER.readTree(exchange.getRequestBody());
            if (hang) {
                await();
            }
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private void await() {
        try {
            release.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private GeminiProvider gemini(String key, String model, Duration timeout) {
        return new GeminiProvider(MAPPER, url(), key, model, timeout);
    }

    private String askGemini() throws ProviderException {
        return gemini(KEY, "modelo-x", TIMEOUT).complete("sistema", HISTORY, "c");
    }

    private static AssistantProviders providers(String provider, String key, String model) {
        return new AssistantProviders(new AssistantProperties(provider, key, model, "", 10, 20), MAPPER);
    }

    @Test
    void geminiSendsTheConversationAndTheKeyInAHeader() throws Exception {
        answer = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \" Hola\"}, {}, {\"text\": \", ñandú. \"}]}}]}";
        assertThat(askGemini()).isEqualTo("Hola, ñandú.");
        assertThat(path).isEqualTo("/v1beta/models/modelo-x:generateContent");
        assertThat(headers.getFirst("x-goog-api-key")).isEqualTo(KEY);
        assertThat(headers.getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(payload).isEqualTo(MAPPER.readTree("""
                {"system_instruction": {"parts": [{"text": "sistema"}]},
                 "contents": [{"role": "user", "parts": [{"text": "a"}]}, {"role": "model", "parts": [{"text": "b"}]},
                              {"role": "user", "parts": [{"text": "c"}]}],
                 "generationConfig": {"maxOutputTokens": 400, "temperature": 0.3}}"""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"candidates\": []}", "{\"candidates\": [{\"finishReason\": \"SAFETY\"}]}", "[]", "\"x\"",
            "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": null}]}}]}",
            "{\"candidates\": [{\"content\": {\"parts\": [\"texto\"]}}]}",
            "{\"candidates\": [{\"content\": {\"parts\": {\"a\": 1}}}]}", "", "no es json", "{\"error\": \"sin cuota\"}"})
    void unexpectedAnswersOfGeminiAreAFailure(String body) {
        answer = body;
        assertThatThrownBy(this::askGemini).isInstanceOf(ProviderException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"text\": \"  \"}]", "[{}]"})
    void anEmptyTextIsNotAFailure(String parts) throws Exception {
        answer = "{\"candidates\": [{\"content\": {\"parts\": " + parts + "}}]}";
        assertThat(askGemini()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 403, 404, 429, 500, 503})
    void httpErrorsAreAFailure(int code) {
        status = code;
        answer = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Hola\"}]}}]}";
        assertThatThrownBy(this::askGemini).isInstanceOf(ProviderException.class).hasMessageContaining("" + code)
                .hasMessageNotContaining(KEY);
    }

    @Test
    void aSlowProviderIsAFailure() {
        hang = true;
        long start = System.nanoTime();
        assertThatThrownBy(() -> gemini(KEY, "", Duration.ofMillis(300)).complete("s", List.of(), "m"))
                .isInstanceOf(ProviderException.class).hasMessageContaining("SocketTimeoutException");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void anUnreachableProviderIsAFailure() {
        String closed = url();
        server.stop(0);
        assertThatThrownBy(() -> new GeminiProvider(MAPPER, closed, KEY, "", TIMEOUT).complete("s", List.of(), "m"))
                .isInstanceOf(ProviderException.class);
    }

    /** El error de Java para una cabecera no válida incluye su valor. */
    @Test
    void theKeyNeverAppearsInAnError() {
        assertThatThrownBy(() -> gemini("secreta\nrota", "", TIMEOUT).complete("s", List.of(), "m"))
                .isInstanceOf(ProviderException.class).hasMessageNotContaining("secreta");
        assertThat(new AssistantProperties("gemini", KEY, "", "", 10, 20).toString()).doesNotContain(KEY);
    }

    @Test
    void openAiSendsTheConversation() throws Exception {
        answer = "{\"choices\": [{\"message\": {\"content\": \" Listo. \"}}]}";
        OpenAiProvider provider = new OpenAiProvider(MAPPER, url() + "/v1/", KEY, "", TIMEOUT);
        assertThat(provider.complete("sistema", HISTORY, "c")).isEqualTo("Listo.");
        assertThat(path).isEqualTo("/v1/chat/completions");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer " + KEY);
        assertThat(payload).isEqualTo(MAPPER.readTree("""
                {"model": "gpt-4o-mini", "max_tokens": 400, "temperature": 0.3, "messages": [
                 {"role": "system", "content": "sistema"}, {"role": "user", "content": "a"},
                 {"role": "assistant", "content": "b"}, {"role": "user", "content": "c"}]}"""));
        answer = "{\"error\": \"sin cuota\"}";
        assertThatThrownBy(() -> provider.complete("s", List.of(), "m")).isInstanceOf(ProviderException.class)
                .hasMessage("Respuesta inesperada de OpenAI.");
    }

    @ParameterizedTest
    @CsvSource({"gemini, ''", "faq, clave", "FAQ, clave", "'', clave", "otro, clave"})
    void withoutKeyOrWithAnotherNameThereIsNoProvider(String provider, String key) {
        assertThat(providers(provider, key, "").current()).isEmpty();
    }

    @Test
    void theProviderAndTheModelComeFromTheConfiguration() {
        assertThat(providers("Gemini", "clave", "").current()).get().isInstanceOfSatisfying(GeminiProvider.class,
                provider -> assertThat(provider.model()).isEqualTo("gemini-3.6-flash"));
        assertThat(providers("gemini", "clave", "mi-modelo").current()).get().isInstanceOfSatisfying(
                GeminiProvider.class, provider -> assertThat(provider.model()).isEqualTo("mi-modelo"));
        assertThat(providers("openai", "clave", "mi-modelo").current()).get().isInstanceOfSatisfying(
                OpenAiProvider.class, provider -> assertThat(provider.model()).isEqualTo("mi-modelo"));
    }
}
