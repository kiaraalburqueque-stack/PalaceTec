package com.tecsup.palacetec.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.tecsup.palacetec.security.RestAuthenticationEntryPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final SnakeCaseStrategy SNAKE_CASE = new SnakeCaseStrategy();

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> api(ApiException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        if (exception.getRetryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, exception.getRetryAfter().toString());
        }
        // Un login rechazado no pide autenticarse: en Django es una respuesta normal, no una excepción.
        if (exception.getStatus() == HttpStatus.UNAUTHORIZED && !(exception instanceof LoginRejectedException)) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, RestAuthenticationEntryPoint.CHALLENGE);
        }
        return response.body(exception.body());
    }

    @ExceptionHandler(InvalidFieldsException.class)
    public ResponseEntity<Object> fields(InvalidFieldsException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> validation(MethodArgumentNotValidException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // chips[3] se informa en el campo chips.
            String field = SNAKE_CASE.translate(error.getField().split("\\[")[0]);
            List<String> messages = errors.computeIfAbsent(field, key -> new ArrayList<>());
            if (!messages.contains(error.getDefaultMessage())) {
                messages.add(error.getDefaultMessage());
            }
        }
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> unreadable(HttpMessageNotReadableException exception) {
        // Un tipo equivocado se informa en su campo; un JSON mal formado, en detail.
        if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()
                && mapping.getPath().getFirst().getFieldName() != null) {
            return ResponseEntity.badRequest().body(
                    Map.of(mapping.getPath().getFirst().getFieldName(), List.of("Valor inválido.")));
        }
        return ResponseEntity.badRequest().body(Map.of("detail", "El cuerpo debe ser un JSON válido."));
    }

    @ExceptionHandler({NoResourceFoundException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Object> notFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "No encontrado."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> method(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(exception.getHeaders())
                .body(Map.of("detail", "Method \"" + exception.getMethod() + "\" not allowed."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> mediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("detail",
                "Unsupported media type \"" + Objects.toString(exception.getContentType(), "") + "\" in request."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return ResponseEntity.internalServerError().body(Map.of("detail", "Error interno. Inténtalo nuevamente."));
    }
}
