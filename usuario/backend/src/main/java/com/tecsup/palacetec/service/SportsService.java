package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.SportEventsResponse;
import com.tecsup.palacetec.dto.SportsBetRequest;
import com.tecsup.palacetec.dto.TicketResponse;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.entity.SportEvent;
import com.tecsup.palacetec.entity.SportTicket;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.SportEventRepository;
import com.tecsup.palacetec.repository.SportTicketRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deportes simulados. El calendario y los resultados son de runroulette (Django): aquí se consulta y se apuesta. */
@Service
public class SportsService {

    private static final int TICKETS = 50;

    private final CasinoService casino;
    private final SportEventRepository events;
    private final SportTicketRepository tickets;
    private final Clock clock;

    public SportsService(CasinoService casino, SportEventRepository events, SportTicketRepository tickets,
                         Clock clock) {
        this.casino = casino;
        this.events = events;
        this.tickets = tickets;
        this.clock = clock;
    }

    /** created es false si la referencia repite un boleto ya comprado. */
    public record Placed(TicketResponse ticket, boolean created) {
    }

    @Transactional(readOnly = true)
    public SportEventsResponse events() {
        Instant now = TimeConfig.now(clock);
        return new SportEventsResponse(true, Formats.utc(now), events.findTop12ByOrderByStartsAtDesc().stream()
                .map(event -> SportEventsResponse.Event.from(event, now)).toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> tickets(Long userId) {
        return tickets.findLatest(userId, Limit.of(TICKETS)).stream().map(TicketResponse::from).toList();
    }

    @Transactional
    public Placed place(PlayerPrincipal player, SportsBetRequest request) {
        // Orden compartido con la liquidación de Django: evento -> usuario -> billetera.
        SportEvent event = events.lockById(request.eventId())
                .orElseThrow(() -> ApiException.notFound("No SportEvent matches the given query."));
        CasinoService.Seat seat = casino.lock(player);
        Play existing = casino.existing(seat, request.stake().reference()).orElse(null);
        if (existing != null) {
            return new Placed(TicketResponse.from(sameTicket(existing, request)), false);
        }
        casino.requireNotPaused(seat);
        if (!event.acceptsBets(TimeConfig.now(clock))) {
            throw ApiException.badRequest(null, "El mercado esta cerrado. No se aceptan apuestas en vivo.");
        }
        BigDecimal odds = event.oddsFor(request.selection());
        if (odds.compareTo(request.quotedOdds()) != 0) {
            throw ApiException.badRequest(null, "La cuota cambio. Consulta y confirma nuevamente.");
        }
        Play play = casino.reserve(seat, Play.SPORTS, request.stake(),
                event.getHome() + " vs " + event.getAway() + " · " + request.selection());
        return new Placed(TicketResponse.from(
                tickets.save(new SportTicket(play, event.getId(), request.selection(), odds))), true);
    }

    private SportTicket sameTicket(Play existing, SportsBetRequest request) {
        return tickets.findByPlay(existing.getId())
                .filter(ticket -> existing.getAmount().compareTo(request.stake().amount()) == 0
                        && ticket.getEventId().equals(request.eventId())
                        && ticket.getSelection() == request.selection()
                        && ticket.getOdds().compareTo(request.quotedOdds()) == 0)
                .orElseThrow(() -> CasinoService.conflict("La referencia ya corresponde a otro boleto."));
    }
}
