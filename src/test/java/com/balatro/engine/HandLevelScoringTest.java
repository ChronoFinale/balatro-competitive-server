package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.EIGHT;
import static com.balatro.engine.card.Rank.FOUR;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.SIX;
import static com.balatro.engine.card.Rank.TEN;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand-level scaling: base = (baseChips + (level-1)*levelChips) × (baseMult + (level-1)*levelMult).
 * Planet cards / level-up jokers raise the level; this verifies the per-level deltas end-to-end.
 * PAIR levels +15 chips / +1 mult; FLUSH levels +15 chips / +2 mult.
 */
class HandLevelScoringTest {

    private static double score(List<Card> played, RunState run) {
        return new ScoringEngine().score(played, List.of(), run, new RandomStreams("T")).score();
    }

    private static final List<Card> PAIR_OF_KINGS = List.of(c(KING, SPADES), c(KING, HEARTS));
    private static final List<Card> HEART_FLUSH =
            List.of(c(TWO, HEARTS), c(FOUR, HEARTS), c(SIX, HEARTS), c(EIGHT, HEARTS), c(TEN, HEARTS));

    @Test
    void levelOneIsTheBaseline() {
        assertThat(score(PAIR_OF_KINGS, new RunState())).isEqualTo(60.0); // (10+20)*2
    }

    @Test
    void levelUpHandRaisesPairByFifteenChipsAndOneMult() {
        RunState run = new RunState();
        run.levelUpHand(HandType.PAIR); // -> level 2
        assertThat(score(PAIR_OF_KINGS, run)).isEqualTo(135.0); // (10+15+20)*(2+1)
    }

    @Test
    void pairAtLevelThree() {
        RunState run = new RunState();
        run.setHandLevel(HandType.PAIR, 3);
        assertThat(score(PAIR_OF_KINGS, run)).isEqualTo(240.0); // (10+30+20)*(2+2)
    }

    @Test
    void flushLevelsByFifteenChipsAndTwoMult() {
        RunState run = new RunState();
        run.setHandLevel(HandType.FLUSH, 2);
        assertThat(score(HEART_FLUSH, run)).isEqualTo(480.0); // (35+15+30)*(4+2)
    }

    @Test
    void levelingOneHandDoesNotAffectAnother() {
        RunState run = new RunState();
        run.setHandLevel(HandType.FLUSH, 5);          // bump an unrelated hand
        assertThat(score(PAIR_OF_KINGS, run)).isEqualTo(60.0); // pair still level 1
    }
}
