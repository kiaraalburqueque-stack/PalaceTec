package com.tecsup.palacetec.dto;

import java.util.Locale;

/** Formato y normalización de correos equivalentes a los de Django. */
public final class Emails {

    public static final String PATTERN = "(?i)[-!#$%&'*+/=?^_`{}|~0-9A-Z]+(\\.[-!#$%&'*+/=?^_`{}|~0-9A-Z]+)*"
            + "@(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z0-9-]{2,63}(?<!-)";
    public static final String MESSAGE = "Escribe un correo válido.";

    private Emails() {
    }

    public static String strip(String email) {
        return email == null ? null : email.strip();
    }

    /** UserManager.normalize_email: solo el dominio pasa a minúsculas. */
    public static String normalize(String email) {
        int at = email.lastIndexOf('@');
        return email.substring(0, at) + "@" + email.substring(at + 1).toLowerCase(Locale.ROOT);
    }
}
