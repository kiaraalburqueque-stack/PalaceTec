package com.tecsup.palacetec.assistant;

/** Textos fijos del asistente, los mismos de Django. */
public final class AssistantTexts {

    public static final String RESPONSIBLE_REMINDER =
            "Juega con responsabilidad: son créditos virtuales y puedes fijar tu límite diario en "
                    + "Mis límites.";

    static final String GUARD_REPLY =
            "No puedo darte estrategias, trucos ni consejos para ganar o para apostar más. Cada "
                    + "resultado lo decide un generador aleatorio del servidor: las rondas son independientes "
                    + "y ningún sistema cambia la ventaja de la casa. Juega solo por entretenimiento, con "
                    + "créditos virtuales, y define tu límite diario en Mis límites. Si sientes que necesitas "
                    + "recuperar lo perdido, es un buen momento para tomar una pausa.";

    static final String SYSTEM_PROMPT =
            "Eres el asistente virtual de PalaceTec, un casino de práctica con créditos virtuales "
                    + "(sin dinero real). Responde en español, en pocas frases, solo sobre las reglas y pagos "
                    + "de sus juegos, los límites de juego responsable y el uso de la cuenta. Nunca des "
                    + "estrategias, trucos ni consejos para ganar, recuperar pérdidas o apostar más; recuerda "
                    + "que cada resultado es aleatorio. No inventes probabilidades ni datos de la cuenta del "
                    + "jugador: si te los piden, indica que el asistente los calcula aparte. Usa solo esta "
                    + "información del catálogo:\n";

    private AssistantTexts() {
    }
}
