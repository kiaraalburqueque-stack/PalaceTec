package com.tecsup.palacetec.dto;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formato y normalización de correos equivalentes a los de Django. */
public final class Emails {

    public static final String PATTERN = "(?i)[-!#$%&'*+/=?^_`{}|~0-9A-Z]+(\\.[-!#$%&'*+/=?^_`{}|~0-9A-Z]+)*"
            + "@(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z0-9-]{2,63}(?<!-)";
    public static final String MESSAGE = "Escribe un correo válido.";

    private static final int MAX_LENGTH = 320;
    /** Letras, dígitos y caracteres de un dominio internacional. */
    private static final String WORD = "a-z0-9\\x{a1}-\\x{ffff}";
    private static final Pattern USER = Pattern.compile("(?i)[-!#$%&'*+/=?^_`{}|~0-9A-Z]+"
            + "(\\.[-!#$%&'*+/=?^_`{}|~0-9A-Z]+)*"
            + "|\"([\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f!#-\\[\\]-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\"");
    private static final Pattern DOMAIN = Pattern.compile("(?iu)[" + WORD + "](?:[" + WORD + "-]{0,61}[" + WORD + "])?"
            + "(?:\\.(?!-)[" + WORD + "-]{1,63}(?<!-))*"
            + "\\.(?!-)(?:[a-z\\x{a1}-\\x{ffff}-]{2,63}|xn--[a-z0-9]{1,59})(?<!-)");
    private static final Pattern LITERAL = Pattern.compile("(?i)\\[([A-F0-9:.]+)]");
    private static final String OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile(OCTET + "(?:\\." + OCTET + "){3}");

    private Emails() {
    }

    public static String strip(String email) {
        return email == null ? null : email.strip();
    }

    /** EmailValidator de Django. La longitud se revisa antes que los patrones. */
    public static boolean isValid(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || email.length() > MAX_LENGTH || !USER.matcher(email.substring(0, at)).matches()) {
            return false;
        }
        String domain = email.substring(at + 1);
        return domain.equals("localhost") || DOMAIN.matcher(domain).matches() || isIpLiteral(domain);
    }

    private static boolean isIpLiteral(String domain) {
        Matcher literal = LITERAL.matcher(domain);
        if (!literal.matches()) {
            return false;
        }
        String address = literal.group(1);
        if (!address.contains(":")) {
            return IPV4.matcher(address).matches();
        }
        try {
            // Con dos puntos siempre es una dirección: no se consulta DNS.
            return InetAddress.getByName(address) != null;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    /** UserManager.normalize_email: solo el dominio pasa a minúsculas. */
    public static String normalize(String email) {
        int at = email.lastIndexOf('@');
        return email.substring(0, at) + "@" + email.substring(at + 1).toLowerCase(Locale.ROOT);
    }
}
