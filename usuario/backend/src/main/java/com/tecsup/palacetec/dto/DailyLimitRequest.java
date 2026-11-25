package com.tecsup.palacetec.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Cero es válido: bloquea nuevas apuestas. */
public record DailyLimitRequest(
        @NotNull(message = RegisterRequest.REQUIRED)
        @DecimalMin(value = "0", message = "El límite no puede ser negativo.")
        @Digits(integer = 10, fraction = 2, message = "Usa hasta diez enteros y dos decimales.") BigDecimal amount) {
}
