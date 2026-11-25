package com.tecsup.palacetec.mail;

/**
 * Correo por enviar. Se publica como evento dentro de la transacción y sale solo si esta se confirma.
 *
 * @param html versión HTML, o null si solo hay texto
 */
public record MailMessage(String to, String subject, String text, String html) {

    public static MailMessage plain(String to, String subject, String text) {
        return new MailMessage(to, subject, text, null);
    }
}
