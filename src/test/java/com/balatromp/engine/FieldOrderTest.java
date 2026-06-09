package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerEffect;
import com.balatromp.engine.joker.JokerInfo;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The per-source scoring field order (doc 30 §1b): within one effect, additive
 * mult is applied before its own ×mult, and a nested {@code extra} chain applies
 * after the parent's scoring fields — so {@code +mult} then {@code ×mult} (not the
 * reverse), and {@code xChips} multiplies the running chips.
 */
class FieldOrderTest {

    private static Card c(Rank r, Suit s) {
        return new Card(r, s);
    }

    /** A probe joker returning a fixed effect at JOKER_MAIN. */
    private static Joker probe(JokerEffect effect) {
        return new Joker() {
            public JokerInfo info() {
                return new JokerInfo("j_probe", "Probe", "test", "Common", 0, 0, 0);
            }
            public JokerEffect calculate(EvaluationContext ctx) {
                return ctx.phase == Trigger.JOKER_MAIN ? effect : null;
            }
        };
    }

    private ScoreResult scorePairWith(JokerEffect effect) {
        RunState run = new RunState();
        run.addJoker(probe(effect));
        List<Card> pair = List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        return new ScoringEngine().score(pair, List.of(), run, new RandomStreams("X"));
    }

    @Test
    void additiveMultThenExtraXMult() {
        // Pair base mult = 2. Effect: +10 mult, then extra ×2 mult.
        // Correct order -> (2 + 10) * 2 = 24. Wrong order (×2 first) -> 2*2 + 10 = 14.
        JokerEffect e = JokerEffect.mult(10).andThen(JokerEffect.xMult(2));
        assertThat(scorePairWith(e).mult()).isEqualTo(24.0);
    }

    @Test
    void xChipsMultipliesRunningChips() {
        // Pair base chips 10 + two scored Kings (10 each) = 30; ×2 chips -> 60.
        ScoreResult baseline = scorePairWith(new JokerEffect()); // no-op effect
        long baseChips = baseline.chips();
        ScoreResult doubled = scorePairWith(JokerEffect.xChips(2));
        assertThat(doubled.chips()).isEqualTo(baseChips * 2);
    }

    @Test
    void finalScoringStepSeamFiresOnceAfterTheMainPass() {
        int[] fired = {0};
        Joker j = new Joker() {
            public JokerInfo info() {
                return new JokerInfo("j_final_probe", "Final Probe", "test", "Common", 0, 0, 0);
            }
            public JokerEffect calculate(EvaluationContext ctx) {
                if (ctx.phase == Trigger.FINAL_SCORING_STEP) {
                    fired[0]++;
                    return JokerEffect.mult(1); // observable: +1 mult at the final step
                }
                return null;
            }
        };
        RunState run = new RunState();
        run.addJoker(j);
        List<Card> pair = List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        ScoreResult r = new ScoringEngine().score(pair, List.of(), run, new RandomStreams("X"));
        assertThat(fired[0]).isEqualTo(1);          // fired exactly once
        assertThat(r.mult()).isEqualTo(3.0);        // pair base 2 + 1 at final step
    }
}
