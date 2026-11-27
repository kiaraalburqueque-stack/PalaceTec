package com.tecsup.palacetec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecsup.palacetec.assistant.AssistantProvider;
import com.tecsup.palacetec.assistant.AssistantProviders;
import com.tecsup.palacetec.assistant.ProviderException;
import com.tecsup.palacetec.config.AssistantProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Proveedor de IA simulado: las pruebas nunca llaman a uno real. Sin respuesta preparada, equivale a no tener clave. */
@Primary
@Component
public class TestAssistantProviders extends AssistantProviders {

    public record Call(String system, List<AssistantProvider.Turn> history, String message) {
    }

    private final List<Call> calls = new ArrayList<>();
    private AssistantProvider provider;

    public TestAssistantProviders(AssistantProperties properties, ObjectMapper mapper) {
        super(properties, mapper);
    }

    public void answer(String text) {
        provider = new Fake(text, null);
    }

    public void fail(String error) {
        provider = new Fake(null, error);
    }

    public void clear() {
        provider = null;
        calls.clear();
    }

    public List<Call> calls() {
        return calls;
    }

    @Override
    public Optional<AssistantProvider> current() {
        return Optional.ofNullable(provider);
    }

    private final class Fake implements AssistantProvider {

        private final String text;
        private final String error;

        private Fake(String text, String error) {
            this.text = text;
            this.error = error;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public String complete(String system, List<Turn> history, String message) throws ProviderException {
            calls.add(new Call(system, history, message));
            if (error != null) {
                throw new ProviderException(error);
            }
            return text;
        }
    }
}
