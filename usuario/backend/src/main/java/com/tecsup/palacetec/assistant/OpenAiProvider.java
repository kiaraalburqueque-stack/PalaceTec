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

/** OpenAI o un servidor compatible. */
class OpenAiProvider implements AssistantProvider {

    static final String SERVER = "https://api.openai.com/v1";
    static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final int MAX_TOKENS = 400;
    private static final double TEMPERATURE = 0.3;

    private final ObjectMapper mapper;
    private final JsonClient client;
    private final String server;
    private final String apiKey;
    private final String model;

    OpenAiProvider(ObjectMapper mapper, String server, String apiKey, String model, Duration timeout) {
        this.mapper = mapper;
        this.client = new JsonClient(mapper, timeout);
        this.server = (server.isEmpty() ? SERVER : server).replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model.isEmpty() ? DEFAULT_MODEL : model;
    }

    @Override
    public String name() {
        return "openai";
    }

    String model() {
        return model;
    }

    @Override
    public String complete(String system, List<Turn> history, String message) throws ProviderException {
        ObjectNode payload = mapper.createObjectNode().put("model", model);
        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        history.forEach(turn -> messages.addObject()
                .put("role", turn.role() == Role.ASSISTANT ? "assistant" : "user").put("content", turn.text()));
        messages.addObject().put("role", "user").put("content", message);
        payload.put("max_tokens", MAX_TOKENS).put("temperature", TEMPERATURE);
        JsonNode content = client.post(server + "/chat/completions", payload,
                Map.of("Authorization", "Bearer " + apiKey)).path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new ProviderException("Respuesta inesperada de OpenAI.");
        }
        return RequestFields.strip(content.textValue());
    }
}
