package com.tecsup.palacetec.dto;

/** Login de la web: los tokens van en cookies, no en el cuerpo. */
public record AccountResponse(Long id, String email) {
}
