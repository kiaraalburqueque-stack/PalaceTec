package com.tecsup.palacetec.security;

/** Jugador autenticado: lo que identifica a su JWT y a su fila en accounts_authsession. */
public record PlayerPrincipal(Long userId, String jti, int version) {
}
