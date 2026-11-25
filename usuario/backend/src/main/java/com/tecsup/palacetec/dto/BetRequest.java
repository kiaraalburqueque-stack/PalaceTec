package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.BetKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Las fichas y la selección se comprueban con las reglas de la mesa en RouletteTable. */
public record BetRequest(
        @JsonProperty("round_id") @NotNull(message = RegisterRequest.REQUIRED)
        @Min(value = 1, message = "La ronda debe ser un número positivo.") Long roundId,
        @NotNull(message = RegisterRequest.REQUIRED) BetKind kind,
        @NotNull(message = RegisterRequest.REQUIRED)
        @Size(max = 5, message = "Usa como máximo 5 caracteres.") String selection,
        @NotNull(message = RegisterRequest.REQUIRED)
        @Size(min = 1, max = 100, message = "Envía entre 1 y 100 fichas.")
        List<@NotNull(message = "Cada ficha debe ser un número.") Integer> chips,
        @NotNull(message = RegisterRequest.REQUIRED) UUID reference) {
}
