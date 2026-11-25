package com.tecsup.palacetec.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** post_json de Django: cualquier fallo de red, tiempo de espera, HTTP o JSON es un ProviderException. */
class JsonClient {

    private static final int FIRST_ERROR_STATUS = 300;

    private final ObjectMapper mapper;
    private final int timeout;

    /** El tiempo de espera vale para conectar y para cada lectura, como en urllib. */
    JsonClient(ObjectMapper mapper, Duration timeout) {
        this.mapper = mapper;
        this.timeout = Math.toIntExact(timeout.toMillis());
    }

    JsonNode post(String url, JsonNode payload, Map<String, String> headers) throws ProviderException {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try {
                return send(connection, payload, headers);
            } finally {
                connection.disconnect();
            }
        } catch (IOException exception) {
            throw new ProviderException(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } catch (RuntimeException exception) {
            // Sin el mensaje: el de una cabecera no válida incluye su valor, que es la clave.
            throw new ProviderException("Solicitud no válida (" + exception.getClass().getSimpleName() + ").");
        }
    }

    private JsonNode send(HttpURLConnection connection, JsonNode payload, Map<String, String> headers)
            throws IOException, ProviderException {
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        // Una redirección no debe llevarse la clave a otro servidor.
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        headers.forEach(connection::setRequestProperty);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(mapper.writeValueAsBytes(payload));
        }
        int status = connection.getResponseCode();
        if (status >= FIRST_ERROR_STATUS) {
            throw new ProviderException("HTTP Error " + status + ": " + connection.getResponseMessage());
        }
        try (InputStream input = connection.getInputStream()) {
            return mapper.readTree(input);
        }
    }
}
