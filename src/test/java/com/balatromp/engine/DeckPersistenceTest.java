package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Card mutations are permanent: the deck has stable card identity across blinds,
 * so Hiker's per-card chip bonus (and Midas Gold, Vampire strips) carry over
 * rather than resetting when the deck is reshuffled each blind.
 */
class DeckPersistenceTest {

    @Test
    void hikerBonusPersistsIntoTheNextBlind() {
        // Trivial-requirement ruleset + an 8-card deck so the whole deck is the hand.
        Ruleset r = new Ruleset("Persist", 4, 4, 3, 8, 1.0, 8,
                new int[]{10, 10, 10, 10, 10, 10, 10, 10});
        List<Card> kings = new ArrayList<>();
        for (int i = 0; i < 8; i++) kings.add(new Card(Rank.KING, Suit.HEARTS));

        Run run = new Run(r, "PERSIST", Deck.of(kings), List.of(JokerLibrary.create("j_hiker")));
        // play 5 of the 8 -> a huge hand that clears the tiny requirement; Hiker buffs those 5
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);

        run.proceed(); // -> next blind: deck reconstituted from the SAME card objects, full hand redrawn
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_ACTIVE);

        long buffed = run.state.hand.stream().filter(c -> c.permaChips == 5).count();
        assertThat(buffed).isEqualTo(5); // exactly the 5 played in blind 1 kept their Hiker chips
    }
}
