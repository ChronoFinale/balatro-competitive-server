package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.NINE;
import static com.balatro.engine.card.Suit.HEARTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.GameEvents;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Self-destruct jokers, now modeled with {@code Effect.Destroy(Selector.Self())} instead of
 * faked (Gros Michel had no destroy at all; Ice Cream/Popcorn/Ramen merely clamped to 0). Numbers/triggers
 * match real Balatro (card.lua): Gros Michel/Cavendish roll at end of round; Popcorn/Ice Cream deplete at
 * end of round; Ramen depletes on discard. The destroy condition mirrors the scoring formula, so the joker
 * is consumed exactly when its contribution bottoms out.
 */
class SelfDestructJokerTest {

    private final RandomStreams rng = new RandomStreams("DESTRUCT");

    private RunState withJoker(String key) {
        RunState run = new RunState();
        run.queues = new QueueSet(rng);
        run.addJoker(JokerLibrary.create(key));
        return run;
    }

    private static boolean owns(RunState run, String key) {
        return run.jokers().stream().anyMatch(j -> j.key().equals(key));
    }

    @Test
    void popcornIsConsumedWhenItsMultDecaysToZero() {
        RunState run = withJoker("j_popcorn");
        run.roundsPlayedTotal = 4;            // 20 - 4*4 = +4 Mult, still alive
        GameEvents.endOfRound(run, rng, false);
        assertThat(owns(run, "j_popcorn")).as("4 rounds: alive").isTrue();
        run.roundsPlayedTotal = 5;            // 20 - 4*5 = 0 -> consumed
        GameEvents.endOfRound(run, rng, false);
        assertThat(owns(run, "j_popcorn")).as("5 rounds: consumed").isFalse();
    }

    @Test
    void iceCreamIsConsumedWhenItsChipsReachZero() {
        RunState run = withJoker("j_ice_cream");
        run.handsPlayedTotal = 19;            // 100 - 5*19 = +5 Chips, alive
        GameEvents.endOfRound(run, rng, false);
        assertThat(owns(run, "j_ice_cream")).as("19 hands: alive").isTrue();
        run.handsPlayedTotal = 20;            // 100 - 5*20 = 0 -> consumed
        GameEvents.endOfRound(run, rng, false);
        assertThat(owns(run, "j_ice_cream")).as("20 hands: consumed").isFalse();
    }

    @Test
    void ramenIsConsumedWhenItsXMultReachesOne() {
        RunState run = withJoker("j_ramen");
        run.cardsDiscardedTotal = 99;         // x(2 - 0.99) = x1.01, alive
        GameEvents.preDiscard(run, rng, List.of(c(NINE, HEARTS)));
        assertThat(owns(run, "j_ramen")).as("99 discards: alive").isTrue();
        run.cardsDiscardedTotal = 100;        // x(2 - 1.00) = x1.0 -> consumed
        GameEvents.preDiscard(run, rng, List.of(c(NINE, HEARTS)));
        assertThat(owns(run, "j_ramen")).as("100 discards: consumed").isFalse();
    }

    @Test
    void grosMichelEventuallyGoesExtinctAtEndOfRound() {
        RunState run = withJoker("j_gros_michel");
        int round = 0;
        while (owns(run, "j_gros_michel") && round < 200) { // 1-in-6 each round -> all but certain by 200
            GameEvents.endOfRound(run, rng, false);
            round++;
        }
        assertThat(owns(run, "j_gros_michel")).as("destroyed by its 1-in-6 roll within 200 rounds").isFalse();
        assertThat(round).as("not on the very first round every time (it's a chance)").isGreaterThan(0);
    }
}
