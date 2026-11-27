package com.tecsup.palacetec.assistant;

/** El proveedor de IA no respondió o su respuesta no sirve. El mensaje va al log: nunca lleva la clave. */
public class ProviderException extends Exception {

    public ProviderException(String message) {
        super(message);
    }
}
