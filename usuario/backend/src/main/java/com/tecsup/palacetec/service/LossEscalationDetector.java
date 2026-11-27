package com.tecsup.palacetec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.entity.RiskIncident;
import com.tecsup.palacetec.repository.PlayRepository;
import com.tecsup.palacetec.repository.RiskIncidentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** detect_play_escalation de Django: cuatro pérdidas seguidas, cada una con al menos el triple que la anterior. */
@Component
public class LossEscalationDetector {

    private static final int PLAYS = 4;
    private static final BigDecimal GROWTH = BigDecimal.valueOf(3);

    private final PlayRepository plays;
    private final RiskIncidentRepository incidents;
    private final ObjectMapper mapper;

    public LossEscalationDetector(PlayRepository plays, RiskIncidentRepository incidents, ObjectMapper mapper) {
        this.plays = plays;
        this.incidents = incidents;
        this.mapper = mapper;
    }

    /** Quien llama acaba de liquidar la jugada y tiene bloqueado al usuario. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void check(Play play, Instant now) {
        List<Play> rows = plays.findTop4ByUserIdAndKindAndSettledAtIsNotNullOrderBySettledAtDescIdDesc(
                play.getUserId(), play.getKind()).reversed();
        if (rows.size() != PLAYS || !rows.getLast().getId().equals(play.getId())
                || rows.stream().anyMatch(row -> row.getPayout().compareTo(row.getAmount()) >= 0)) {
            return;
        }
        for (int index = 1; index < PLAYS; index++) {
            BigDecimal expected = rows.get(index - 1).getAmount().multiply(GROWTH);
            if (rows.get(index).getAmount().compareTo(expected) < 0) {
                return;
            }
        }
        if (!incidents.existsByUserIdAndPlayId(play.getUserId(), play.getId())) {
            ArrayNode evidence = mapper.createArrayNode();
            rows.forEach(row -> evidence.addObject().put("play_id", row.getId())
                    .put("amount", Formats.money(row.getAmount())).put("paid", Formats.money(row.getPayout())));
            incidents.save(new RiskIncident(play.getUserId(), play.getId(), evidence, now));
        }
    }
}
