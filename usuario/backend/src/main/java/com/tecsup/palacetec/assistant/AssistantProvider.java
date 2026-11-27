package com.tecsup.palacetec.assistant;

import com.tecsup.palacetec.entity.AssistantMessage;
import java.util.List;

/** Cliente de un modelo de IA. */
public interface AssistantProvider {

    /** Mensaje anterior de la conversación. */
    record Turn(AssistantMessage.Role role, String text) {
    }

    String name();

    String complete(String system, List<Turn> history, String message) throws ProviderException;
}
