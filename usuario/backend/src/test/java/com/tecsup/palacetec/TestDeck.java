package com.tecsup.palacetec;

import com.tecsup.palacetec.service.BlackjackDeck;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Mazo de las pruebas: reparte las cartas pedidas o, si no se pidió ninguna, una baraja mezclada. */
@Primary
@Component
public class TestDeck extends BlackjackDeck {

    private List<String> next;

    /** deal de casino/tests.py: dos cartas al jugador, dos a la banca y después el resto en ese orden. */
    public void deal(String player, String dealer, String... following) {
        List<String> order = new ArrayList<>(List.of(player.split(" ")));
        order.addAll(List.of(dealer.split(" ")));
        order.addAll(List.of(following));
        List<String> cards = super.shuffled();
        cards.removeAll(order);
        cards.addAll(order.reversed());
        next = cards;
    }

    public void clear() {
        next = null;
    }

    @Override
    public List<String> shuffled() {
        return next == null ? super.shuffled() : next;
    }
}
