package com.tecsup.palacetec.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Protección de una ruta pública de autenticación. Se aplica antes de leer el cuerpo, como en las vistas de Django:
 * primero el límite por IP y después el CSRF.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthGuard {

    enum Csrf {
        ALWAYS,
        /** La app móvil no es un navegador: no tiene cookies que otro sitio pueda aprovechar. */
        UNLESS_NATIVE,
        /** Con Authorization no se usan las cookies, y otro sitio no puede enviar esa cabecera. */
        UNLESS_BEARER
    }

    Csrf csrf() default Csrf.ALWAYS;

    boolean throttled() default false;
}
