package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.BlackjackActionRequest;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.HandResponse;
import com.tecsup.palacetec.dto.StakeRequest;
import com.tecsup.palacetec.entity.BlackjackHand;
import com.tecsup.palacetec.entity.Play;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.BlackjackHandRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blackjack. Las manos abandonadas diez minutos las cierra runroulette (Django). */
@Service
public class BlackjackService {

    private final CasinoService casino;
    private final BlackjackHandRepository hands;
    private final BlackjackDeck deck;
    private final Clock clock;

    public BlackjackService(CasinoService casino, BlackjackHandRepository hands, BlackjackDeck deck, Clock clock) {
        this.casino = casino;
        this.hands = hands;
        this.deck = deck;
        this.clock = clock;
    }

    /** created es false si la referencia repite una mano ya jugada. */
    public record Started(HandResponse hand, boolean created) {
    }

    @Transactional(readOnly = true)
    public HandResponse latest(Long userId) {
        return hands.findLatest(userId, Limit.of(1)).stream().findFirst().map(BlackjackService::response)
                .orElseGet(() -> new HandResponse(null));
    }

    @Transactional
    public Started start(PlayerPrincipal player, StakeRequest stake) {
        CasinoService.Seat seat = casino.lock(player);
        Play existing = casino.existing(seat, stake.reference()).orElse(null);
        if (existing != null) {
            if (!existing.getKind().equals(Play.BLACKJACK) || existing.getAmount().compareTo(stake.amount()) != 0) {
                throw CasinoService.conflict("La referencia ya corresponde a otra jugada.");
            }
            return new Started(response(hands.findByPlay(existing.getId()).orElseThrow()), false);
        }
        casino.requireNotPaused(seat);
        if (hands.existsByUserIdAndActiveTrue(seat.user().getId())) {
            throw ApiException.badRequest(null, "Termina tu mano actual antes de comenzar otra.");
        }
        Play play = casino.reserve(seat, Play.BLACKJACK, stake, "Blackjack clásico");
        BlackjackHand hand = new BlackjackHand(play, deck.shuffled(), TimeConfig.now(clock));
        if (BlackjackRules.score(hand.getPlayer()) == BlackjackRules.LIMIT
                || BlackjackRules.score(hand.getDealer()) == BlackjackRules.LIMIT) {
            finish(hand, BlackjackRules.natural(hand));
        }
        hands.save(hand);
        return new Started(response(hand), true);
    }

    /** Una mano empezada se puede terminar en pausa: no hay una apuesta nueva. */
    @Transactional
    public HandResponse act(PlayerPrincipal player, long handId, BlackjackActionRequest request) {
        CasinoService.Seat seat = casino.lock(player);
        BlackjackHand hand = hands.lockByIdAndUserId(handId, seat.user().getId())
                .orElseThrow(() -> ApiException.notFound("No BlackjackHand matches the given query."));
        if (hand.getRevision() != request.revision() || !hand.isActive()) {
            // Reintento de una acción: no vuelve a repartir ni a pagar.
            return response(hand);
        }
        if (request.hit()) {
            hand.drawForPlayer();
        }
        if (!request.hit() || BlackjackRules.score(hand.getPlayer()) >= BlackjackRules.LIMIT) {
            finish(hand, BlackjackRules.playDealer(hand));
        }
        hand.registerAction(TimeConfig.now(clock));
        return response(hand);
    }

    private void finish(BlackjackHand hand, BlackjackRules.Outcome outcome) {
        casino.settle(hand.getPlay(), outcome.multiplier());
        hand.finish(outcome.name());
    }

    private static HandResponse response(BlackjackHand hand) {
        Play play = hand.getPlay();
        List<String> visible = hand.isActive() ? hand.getDealer().subList(0, 1) : hand.getDealer();
        return new HandResponse(new HandResponse.Hand(hand.getId(), Formats.money(play.getAmount()), hand.getPlayer(),
                visible, hand.isActive(), BlackjackRules.score(hand.getPlayer()), BlackjackRules.score(visible),
                hand.isActive(), hand.getRevision(), hand.getOutcome(), Formats.money(play.getPayout())));
    }
}
