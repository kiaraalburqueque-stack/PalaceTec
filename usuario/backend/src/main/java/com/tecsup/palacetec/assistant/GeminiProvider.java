package com.tecsup.palacetec.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.entity.AssistantMessage.Role;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Gemini de Google. */
class GeminiProvider implements AssistantProvider {

    static final String SERVER = "https://generativelanguage.googleapis.com";
    /** El de Django, gemini-2.0-flash, fue retirado por Google el 1 de junio de 2026. */
    static final String DEFAULT_MODEL = "gemini-3.6-flash";
    private static final int MAX_TOKENS = 400;
    private static final double TEMPERATURE = 0.3;

    private final ObjectMapper mapper;
    private final JsonClient client;
    private final String server;
    private final String apiKey;
    private final String model;

    GeminiProvider(ObjectMapper mapper, String server, String apiKey, String model, Duration timeout) {
        this.mapper = mapper;
        this.client = new JsonClient(mapper, timeout);
        this.server = server;
        this.apiKey = apiKey;
        this.model = model.isEmpty() ? DEFAULT_MODEL : model;
    }

    @Override
    public String name() {
        return "gemini";
    }

    String model() {
        return model;
    }

    @Override
    public String complete(String system, List<Turn> history, String message) throws ProviderException {
        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("system_instruction").putArray("parts").addObject().put("text", system);
        ArrayNode contents = payload.putArray("contents");
        history.forEach(turn -> add(contents, turn.role() == Role.ASSISTANT ? "model" : "user", turn.text()));
        add(contents, "user", message);
        payload.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS).put("temperature", TEMPERATURE);
        JsonNode answer = client.post(server + "/v1beta/models/" + model + ":generateContent", payload,
                Map.of("x-goog-api-key", apiKey));
        JsonNode parts = answer.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            throw unexpected();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.isObject() || part.has("text") && !part.get("text").isTextual()) {
                throw unexpected();
            }
            text.append(part.path("text").asText());
        }
        return RequestFields.strip(text.toString());
    }

    private static void add(ArrayNode contents, String role, String text) {
        contents.addObject().put("role", role).putArray("parts").addObject().put("text", text);
    }

    private static ProviderException unexpected() {
        return new ProviderException("Respuesta inesperada de Gemini.");
    }
}
