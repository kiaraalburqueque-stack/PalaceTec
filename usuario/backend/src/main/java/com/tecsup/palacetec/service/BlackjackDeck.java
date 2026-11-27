package com.tecsup.palacetec.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/** Baraja de 52 cartas (A_S, 10_H...), mezclada en cada mano con un generador seguro. */
@Component
public class BlackjackDeck {

    private static final List<String> SUITS = List.of("S", "H", "D", "C");
    private static final List<String> RANKS =
            List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
    private static final SecureRandom RANDOM = new SecureRandom();

    public List<String> shuffled() {
        List<String> cards = new ArrayList<>();
        SUITS.forEach(suit -> RANKS.forEach(rank -> cards.add(rank + "_" + suit)));
        Collections.shuffle(cards, RANDOM);
        return cards;
    }
}
