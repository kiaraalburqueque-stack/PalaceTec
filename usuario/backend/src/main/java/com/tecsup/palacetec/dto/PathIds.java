package com.tecsup.palacetec.dto;

import java.math.BigInteger;

/** Ids de la ruta, como el conversor <int:...> de Django: solo dígitos, sin signo. */
public final class PathIds {

    /** Expresión para la ruta; lo que no son dígitos no llega al controlador (404). */
    public static final String DIGITS = ":[0-9]+}";

    private PathIds() {
    }

    /** Un número que no cabe en un bigint no es el id de ninguna fila. */
    public static long parse(String digits) {
        return new BigInteger(digits).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}
