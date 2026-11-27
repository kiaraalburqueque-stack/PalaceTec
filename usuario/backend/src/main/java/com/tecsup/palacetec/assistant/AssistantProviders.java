package com.tecsup.palacetec.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.config.AssistantProperties;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** get_provider de Django: elige el proveedor según la configuración. */
@Component
public class AssistantProviders {

    private static final Logger log = LoggerFactory.getLogger(AssistantProviders.class);
    private static final String FAQ = "faq";

    private final AssistantProperties properties;
    private final ObjectMapper mapper;

    public AssistantProviders(AssistantProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Vacío para usar las preguntas frecuentes. */
    public Optional<AssistantProvider> current() {
        String name = properties.provider().isEmpty() ? FAQ : properties.provider().toLowerCase(Locale.ROOT);
        if (properties.apiKey().isEmpty() || name.equals(FAQ)) {
            return Optional.empty();
        }
        Duration timeout = Duration.ofSeconds(properties.timeout());
        return switch (name) {
            case "gemini" -> Optional.of(new GeminiProvider(mapper, GeminiProvider.SERVER, properties.apiKey(),
                    properties.model(), timeout));
            case "openai" -> Optional.of(new OpenAiProvider(mapper, properties.baseUrl(), properties.apiKey(),
                    properties.model(), timeout));
            default -> {
                log.warn("ASSISTANT_PROVIDER={} no es válido; se usan las preguntas frecuentes.", name);
                yield Optional.empty();
            }
        };
    }
}
