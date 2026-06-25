package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;

import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates the {@link Scenario} harness: a joker test reads as one sentence, and you can assert on the
 * effect TRACE (did it fire, in what step) — not just the final total.
 */
class ScenarioHarnessTest {

    @Test
    void lustyJokerFiresOnHeartsAndShowsInTheTrace() {
        Scenario.scoring()
                .joker("j_lusty_joker")                                  // +3 Mult per scored Heart
                .play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.HEARTS))
                .expectTraceContains("Lusty Joker")                       // it actually contributed
                .assertMultIsAtLeast(2 + 3 + 3);                          // base 2 + 3 per Heart, two Hearts
    }

    @Test
    void lustyJokerStaysSilentWhenNoHeartsAreScored() {
        Scenario.scoring()
                .joker("j_lusty_joker")
                .play(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.SPADES))
                .expectNoTraceFrom("Lusty Joker");                        // the suit gate didn't fire
    }

    @Test
    void preLevelingAHandRaisesItsBaseScore() {
        double lvl1 = Scenario.scoring().play(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.SPADES)).score().score();
        double lvl3 = Scenario.scoring().handLevel(HandType.PAIR, 3)
                .play(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.SPADES)).score().score();
        org.assertj.core.api.Assertions.assertThat(lvl3).isGreaterThan(lvl1);
    }
}
