package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Suit;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerEffect;
import com.balatro.grammar.JokerInfo;
import com.balatro.grammar.Trigger;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
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
                return new JokerInfo("j_probe", "Probe", "test", com.balatro.grammar.Rarity.COMMON, 0, 0, 0);
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
    void exponentialMultScalesBeyondDoubleRange() {
        // A Cryptid-style ^mult joker: raise the running mult to a big power at the
        // final step. The score blows past double's ~1.8e308 ceiling but BigNum holds it.
        Joker cryptid = new Joker() {
            public JokerInfo info() {
                return new JokerInfo("j_cryptid_probe", "Cryptid Probe", "test", com.balatro.grammar.Rarity.COMMON, 0, 0, 0);
            }
            public JokerEffect calculate(EvaluationContext ctx) {
                if (ctx.phase != Trigger.FINAL_SCORING_STEP) return null;
                JokerEffect e = new JokerEffect();
                e.powMult = 400.0; // mult := mult ^ 400
                return e;
            }
        };
        RunState run = new RunState();
        run.addJoker(cryptid);
        // a flush of tens -> chips and a modest mult, then ^400 explodes it
        List<Card> hand = List.of(c(Rank.TEN, Suit.HEARTS), c(Rank.TEN, Suit.SPADES),
                c(Rank.TEN, Suit.CLUBS), c(Rank.TEN, Suit.DIAMONDS), c(Rank.TWO, Suit.HEARTS));
        ScoreResult r = new ScoringEngine().score(hand, List.of(), run, new RandomStreams("X"));
        // plain double would be Infinity; the big-number score is finite and enormous
        assertThat(Double.isInfinite(r.score())).isTrue();
        assertThat(r.bigScore().compareTo(com.balatro.engine.scoring.BigNum.of(1e308))).isPositive();
    }

    @Test
    void perCardPermanentBonusIsScored() {
        // A card can carry permanent chip/mult bonuses (Hiker etc.) that the engine scores.
        List<Card> plain = List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        Card buffedKing = c(Rank.KING, Suit.HEARTS);
        buffedKing.permaChips = 15;
        buffedKing.permaMult = 3;
        List<Card> buffed = List.of(buffedKing, c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        ScoreResult a = new ScoringEngine().score(plain, List.of(), new RunState(), new RandomStreams("X"));
        ScoreResult b = new ScoringEngine().score(buffed, List.of(), new RunState(), new RandomStreams("X"));
        assertThat(b.chips() - a.chips()).isEqualTo(15);
        assertThat(b.mult() - a.mult()).isEqualTo(3.0);
    }

    @Test
    void finalScoringStepSeamFiresOnceAfterTheMainPass() {
        int[] fired = {0};
        Joker j = new Joker() {
            public JokerInfo info() {
                return new JokerInfo("j_final_probe", "Final Probe", "test", com.balatro.grammar.Rarity.COMMON, 0, 0, 0);
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
