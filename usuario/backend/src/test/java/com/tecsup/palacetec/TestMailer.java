package com.tecsup.palacetec;

import com.tecsup.palacetec.mail.MailMessage;
import com.tecsup.palacetec.mail.Mailer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Bandeja de salida de las pruebas, como mail.outbox de Django. */
@Primary
@Component
public class TestMailer implements Mailer {

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        sent.add(message);
    }

    public List<MailMessage> sent() {
        return sent;
    }

    public MailMessage last() {
        return sent.getLast();
    }

    public void clear() {
        sent.clear();
    }
}
