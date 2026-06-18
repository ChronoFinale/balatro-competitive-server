package com.balatro.engine;

import static com.balatro.engine.TestSupport.heartsKings;
import static com.balatro.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ouija in multiplayer is reworked: destroy 3 random cards and convert the rest to a
 * single random rank, with NO hand-size reduction (vanilla Ouija converts the whole
 * hand and loses 1 hand size).
 */
class OuijaMpTest {

    private static Ruleset mp() {
        return new Ruleset("MP", 4, 4, 3, 8, 1.0, 8, Ruleset.standard().blindBaseAmounts(),
                List.of("j_joker"), "multiplayer");
    }

    @Test
    void mpOuijaDestroysThreeConvertsRestAndKeepsHandSize() {
        Run run = new Run(mp(), "OUIJA", heartsKings(60), jokers("j_joker"));
        // Give the hand varied ranks so "converted to one rank" is observable.
        for (int i = 0; i < run.state.hand.size(); i++) {
            run.state.hand.get(i).rank = com.balatro.engine.card.Rank.values()[i % 13];
        }
        int handBefore = run.state.hand.size();
        int handSizeBefore = run.state.handSize;
        run.state.consumables.add("c_ouija");
        assertThat(run.useConsumable(0)).isNull();

        assertThat(run.state.hand).hasSize(handBefore - 3);                 // 3 destroyed
        assertThat(run.state.hand.stream().map(c -> c.rank).distinct().count())
                .isEqualTo(1);                                             // rest converted to one rank
        assertThat(run.state.handSize).isEqualTo(handSizeBefore);          // NO hand-size reduction
    }

    @Test
    void singlePlayerOuijaConvertsWholeHandAndLosesHandSize() {
        Run run = new Run(Ruleset.standard(), "OUIJA", heartsKings(60), jokers("j_joker"));
        int handBefore = run.state.hand.size();
        int handSizeBefore = run.state.handSize;
        run.state.consumables.add("c_ouija");
        assertThat(run.useConsumable(0)).isNull();

        assertThat(run.state.hand).hasSize(handBefore);                    // nothing destroyed
        assertThat(run.state.hand.stream().map(c -> c.rank).distinct().count()).isEqualTo(1);
        assertThat(run.state.handSize).isEqualTo(handSizeBefore - 1);      // vanilla -1 hand size
    }
}
