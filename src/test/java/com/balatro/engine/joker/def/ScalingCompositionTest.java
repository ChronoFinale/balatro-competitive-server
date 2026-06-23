package com.balatro.engine.joker.def;

import static com.balatro.dsl.Cond.always;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.balatro.dsl.Jokers;
import com.balatro.dsl.Val;
import com.balatro.engine.card.Card;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Suit;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.Trigger;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the design-50 claim: a joker's "kind" — static / scaling / multi-subject — is NOT a structural type
 * but a composition of two independent primitives, an <b>accumulator</b> ({@code .gain} → {@code MutateState})
 * and a <b>read</b> ({@code Val.state} → {@code Value.State}). Because the accumulator is separate from the
 * read, one counter can feed several subjects at once, and a read can scale {@code xMult} (operation MULTIPLY)
 * just as easily as {@code +mult}. No "scaling primitive" is needed; the value the rule reads IS the scaling.
 */
class ScalingCompositionTest {

    private static Card c(Rank r, Suit s) { return new Card(r, s); }

    // A neutral 5-card high-card hand; we only ever compare with-joker vs without-joker deltas.
    private static final List<Card> HAND = List.of(
            c(Rank.TWO, Suit.SPADES), c(Rank.FOUR, Suit.HEARTS), c(Rank.SIX, Suit.CLUBS),
            c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.TEN, Suit.SPADES));

    private static Joker dj(JokerDef def) { return new DataJoker(def); }

    /** Score the hand with an optional joker, optionally pre-seeding its scaling counter. */
    private static ScoreResult score(Joker j, String counter, int value) {
        RunState run = new RunState();
        if (j != null) {
            run.addJoker(j);
            if (counter != null) run.jokerState(j).put(counter, value);
        }
        return new ScoringEngine().score(HAND, List.of(), run, new RandomStreams("S"));
    }

    @Test
    void staticJokerIsJustAConstValue() { // +4 Mult, always — the degenerate (no accumulator) case
        Joker flat = dj(Jokers.of("j_flat", "Flat").desc("+4 Mult").cost(2).whenHand().add(Effect.Term.MULT, 4).build());
        assertThat(score(flat, null, 0).mult() - score(null, null, 0).mult()).isEqualTo(4.0);
    }

    @Test
    void oneCounterFeedsMultipleSubjectsAtOnce() {
        // A single counter "g" read TWICE: as +10 Chips per g AND as x(1 + 0.1*g) Mult. The accumulator
        // (gain on CARD_ADDED) and the two reads are independent rules over the same var — "scale multiple
        // things" needs no new machinery, because the read is not fused to the counter.
        Joker dynamo = dj(Jokers.of("j_dynamo", "Dynamo").desc("scales chips + xMult").cost(6)
                .mutate(Trigger.CARD_ADDED).when(always()).gain("g", 1)
                .whenHand().add(Effect.Term.CHIPS, Val.perState("g", 10))         // +10 Chips per g   (ADD, CHIPS)
                .whenHand().multiply(Effect.Term.MULT, Val.state("g", 1.0, 0.1))  // x(1 + 0.1*g) Mult (MULTIPLY, MULT)
                .build());

        ScoreResult without = score(null, null, 0);
        ScoreResult g3 = score(dynamo, "g", 3); // counter at 3

        assertThat(g3.chips() - without.chips()).isEqualTo(30);             // 10 * 3, the additive subject
        assertThat(g3.mult()).isCloseTo(without.mult() * 1.3, within(1e-9)); // x(1 + 0.1*3), the xMult subject
    }

    @Test
    void scalingXMultIsJustMultiplyWithAStateValue() {
        // Hologram-shape: xMult grows with a counter. "Scaling xMult" = operation MULTIPLY + a State read.
        Joker holo = dj(Jokers.of("j_holo_test", "Holo").desc("xMult grows").cost(6)
                .mutate(Trigger.CARD_ADDED).when(always()).gain("x", 0.25)
                .whenHand().multiply(Effect.Term.MULT, Val.state("x", 1.0, 1.0)) // x(1 + x)
                .build());

        ScoreResult without = score(null, null, 0);
        ScoreResult x2 = score(holo, "x", 2); // base 1.0 + 2 = x3
        assertThat(x2.mult()).isCloseTo(without.mult() * 3.0, within(1e-9));
    }
}
