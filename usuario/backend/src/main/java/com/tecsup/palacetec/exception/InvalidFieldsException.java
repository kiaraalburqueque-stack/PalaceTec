package com.tecsup.palacetec.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Errores por campo: {campo: ["mensaje"]}, como los serializadores de Django REST Framework. */
public class InvalidFieldsException extends RuntimeException {

    private final HttpStatus status;
    private final transient Object body;

    public InvalidFieldsException(HttpStatus status, Object body) {
        super("Datos inválidos");
        this.status = status;
        this.body = body;
    }

    public static InvalidFieldsException field(String field, String message) {
        return new InvalidFieldsException(HttpStatus.BAD_REQUEST, Map.of(field, List.of(message)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getBody() {
        return body;
    }
}
