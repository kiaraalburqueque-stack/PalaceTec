package com.tecsup.palacetec.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Cookie de sesión inválida o vencida, con el cuerpo de InvalidToken de SimpleJWT. */
public class InvalidWebTokenException extends ApiException {

    private final String reason;

    public InvalidWebTokenException(String reason) {
        super(HttpStatus.UNAUTHORIZED, "token_not_valid", "Given token not valid for any token type");
        this.reason = reason;
    }

    @Override
    public Map<String, Object> body() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token_class", "AccessToken");
        message.put("token_type", "access");
        message.put("message", reason);
        Map<String, Object> body = super.body();
        body.put("messages", List.of(message));
        return body;
    }
}
