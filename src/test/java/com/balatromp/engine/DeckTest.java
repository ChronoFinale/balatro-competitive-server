package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import org.junit.jupiter.api.Test;

/** Deck variants apply their starting / per-blind modifiers. */
class DeckTest {

    private final int[] amounts = Ruleset.standard().blindBaseAmounts();

    private Ruleset withDeck(String deck) {
        return new Ruleset("D", 4, 4, 3, 8, 1.0, 8, amounts, null, "default", deck);
    }

    @Test
    void yellowDeckStartsWithExtraMoney() {
        Run run = new Run(withDeck("d_yellow"), "Y", stoneDeck(400), jokers());
        assertThat(run.state.money).isEqualTo(4 + 10);
    }

    @Test
    void blueDeckGivesAnExtraHandEachBlind() {
        Run run = new Run(withDeck("d_blue"), "B", stoneDeck(400), jokers());
        assertThat(run.state.handsLeft).isEqualTo(5); // 4 + 1
    }

    @Test
    void blackDeckAddsAJokerSlotButRemovesAHand() {
        Run run = new Run(withDeck("d_black"), "K", stoneDeck(400), jokers());
        assertThat(run.state.jokerSlots).isEqualTo(6); // 5 + 1
        assertThat(run.state.handsLeft).isEqualTo(3); // 4 - 1
    }

    @Test
    void baseDeckIsUnmodified() {
        Run run = new Run(withDeck("d_base"), "0", stoneDeck(400), jokers());
        assertThat(run.state.money).isEqualTo(4);
        assertThat(run.state.handsLeft).isEqualTo(4);
        assertThat(run.state.jokerSlots).isEqualTo(5);
    }
}
