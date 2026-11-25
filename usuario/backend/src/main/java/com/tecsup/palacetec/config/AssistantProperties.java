package com.tecsup.palacetec.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Asistente virtual (HU68), con las mismas variables que Django.
 *
 * @param provider faq, gemini u openai
 * @param apiKey sin ella siempre se responde con las preguntas frecuentes
 * @param model vacío usa el predeterminado del proveedor
 * @param baseUrl servidor compatible con OpenAI; vacío usa el oficial
 * @param timeout segundos de espera
 * @param rateLimit mensajes por minuto y por jugador
 */
@Validated
@ConfigurationProperties("palacetec.assistant")
public record AssistantProperties(String provider, String apiKey, String model, String baseUrl, @Min(1) int timeout,
                                  @Min(1) int rateLimit) {

    public AssistantProperties {
        provider = provider == null ? "" : provider;
        apiKey = apiKey == null ? "" : apiKey;
        model = model == null ? "" : model;
        baseUrl = baseUrl == null ? "" : baseUrl;
    }

    /** Sin la clave: no debe llegar a ningún log. */
    @Override
    public String toString() {
        return "AssistantProperties[provider=" + provider + ", model=" + model + "]";
    }
}
