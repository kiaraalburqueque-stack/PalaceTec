package com.tecsup.palacetec.dto;

/** Mensajes de los errores de formato. La web espera los de Django REST Framework; la app móvil, los del MVP. */
public record FieldMessages(String required, String nullValue, String blank, String notText, String maxLength,
                            String email) {

    public static final FieldMessages DJANGO = new FieldMessages("This field is required.",
            "This field may not be null.", "This field may not be blank.", "Not a valid string.",
            "Ensure this field has no more than %d characters.", "Enter a valid email address.");

    public static final FieldMessages MOBILE = new FieldMessages(RegisterRequest.REQUIRED, RegisterRequest.REQUIRED,
            RegisterRequest.REQUIRED, "Valor inválido.", "Usa como máximo %d caracteres.", Emails.MESSAGE);
}
