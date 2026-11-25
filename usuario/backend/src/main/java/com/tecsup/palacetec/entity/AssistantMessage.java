package com.tecsup.palacetec.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Conversación del jugador con el asistente virtual (HU68). */
@Entity
@Table(name = "assistant_messages")
public class AssistantMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9, updatable = false)
    private Role role;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    /** faq, ai, guard u odds: de dónde salió la respuesta. Vacío en las preguntas. */
    @Column(nullable = false, length = 10, updatable = false)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private JsonNode data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssistantMessage() {
    }

    public AssistantMessage(Long userId, Role role, String content, String source, JsonNode data, Instant createdAt) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.source = source;
        this.data = data;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public JsonNode getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
