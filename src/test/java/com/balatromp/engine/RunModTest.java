package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import org.junit.jupiter.api.Test;

/** Passive per-blind run modifiers (Juggler, Burglar, ...) applied at blind start. */
class RunModTest {

    private final Ruleset std = Ruleset.standard();

    @Test
    void jugglerAddsHandSize() {
        Run run = new Run(std, "J", stoneDeck(400), jokers("j_juggler"));
        assertThat(run.state.handSize).isEqualTo(std.handSize() + 1);
    }

    @Test
    void burglarGivesExtraHandsButZeroDiscards() {
        Run run = new Run(std, "B", stoneDeck(400), jokers("j_burglar"));
        assertThat(run.state.handsLeft).isEqualTo(std.hands() + 3);
        assertThat(run.state.discardsLeft).isZero();
    }

    @Test
    void troubadourTradesAHandForHandSize() {
        Run run = new Run(std, "T", stoneDeck(400), jokers("j_troubadour"));
        assertThat(run.state.handsLeft).isEqualTo(std.hands() - 1);
        assertThat(run.state.handSize).isEqualTo(std.handSize() + 2);
    }

    @Test
    void modsWithoutJokersAreUnchanged() {
        Run run = new Run(std, "N", stoneDeck(400), jokers("j_joker"));
        assertThat(run.state.handSize).isEqualTo(std.handSize());
        assertThat(run.state.discardsLeft).isEqualTo(std.discards());
    }
}
