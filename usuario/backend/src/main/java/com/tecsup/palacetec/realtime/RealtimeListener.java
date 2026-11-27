package com.tecsup.palacetec.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * after_commit de realtime/events.py para lo que hace Spring: se publica solo si la transacción se confirmó, y un fallo
 * al enviar nunca afecta la respuesta HTTP ya confirmada.
 */
@Component
public class RealtimeListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeListener.class);

    private final RealtimeHub hub;
    private final RealtimePoller poller;

    public RealtimeListener(RealtimeHub hub, RealtimePoller poller) {
        this.hub = hub;
        this.poller = poller;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void sessionsRevoked(SessionsRevoked event) {
        try {
            hub.revoke(event.userId(), event.sid());
        } catch (RuntimeException exception) {
            log.warn("No se pudo cerrar los sockets del jugador {}", event.userId(), exception);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void walletChanged(WalletChanged event) {
        try {
            poller.walletChanged(event.userId(), event.wallet());
        } catch (RuntimeException exception) {
            log.warn("No se pudo enviar wallet:update al jugador {}", event.userId(), exception);
        }
    }
}
