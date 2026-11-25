package com.tecsup.palacetec.security;

/**
 * Jugador autenticado: lo que identifica a su JWT y a su fila en accounts_authsession.
 *
 * @param jti clave de la sesión: el jti del Bearer o el sid de la cookie
 * @param web entró con la cookie de la web y no con Bearer
 */
public record PlayerPrincipal(Long userId, String jti, int version, boolean web) {

    public PlayerPrincipal(Long userId, String jti, int version) {
        this(userId, jti, version, false);
    }

    public static PlayerPrincipal web(WebTokenService.WebToken token) {
        return new PlayerPrincipal(token.userId(), token.sid(), token.version(), true);
    }
}
