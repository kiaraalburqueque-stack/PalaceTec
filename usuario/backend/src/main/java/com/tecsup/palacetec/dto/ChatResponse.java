package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.entity.AssistantMessage;
import java.util.List;

public record ChatResponse(Message question, Message reply, String reminder) {

    public record Message(
            Long id,
            String role,
            String content,
            String source,
            JsonNode data,
            @JsonProperty("created_at") String createdAt) {

        public static Message from(AssistantMessage message) {
            return new Message(message.getId(), message.getRole().name(), message.getContent(), message.getSource(),
                    message.getData(), Formats.utc(message.getCreatedAt()));
        }
    }

    /** Historial, del más antiguo al más reciente. */
    public record History(List<Message> messages, String reminder) {
    }
}
