package com.tecsup.palacetec.mail;

import org.springframework.stereotype.Component;

/**
 * Como el EmailBackend de consola que usa Django en el proyecto: escribe el correo en la salida estándar. No pasa por
 * el log porque lleva enlaces de un solo uso que no deben quedar en un archivo.
 */
@Component
public class ConsoleMailer implements Mailer {

    private static final String FROM = "PalaceTec <no-reply@palacetec.local>";
    private static final String SEPARATOR = "-".repeat(79);

    @Override
    public void send(MailMessage message) {
        StringBuilder output = new StringBuilder()
                .append("Subject: ").append(message.subject()).append('\n')
                .append("From: ").append(FROM).append('\n')
                .append("To: ").append(message.to()).append("\n\n")
                .append(message.text()).append('\n');
        if (message.html() != null) {
            output.append('\n').append(message.html()).append('\n');
        }
        System.out.println(output.append(SEPARATOR));
    }
}
