package com.tecsup.palacetec.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** transaction.on_commit de Django: un fallo del correo no revierte ni hace fallar la operación. */
@Component
public class MailListener {

    private static final Logger log = LoggerFactory.getLogger(MailListener.class);

    private final Mailer mailer;

    public MailListener(Mailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener
    public void send(MailMessage message) {
        try {
            mailer.send(message);
        } catch (RuntimeException exception) {
            log.error("No se pudo enviar el correo \"{}\"", message.subject(), exception);
        }
    }
}
