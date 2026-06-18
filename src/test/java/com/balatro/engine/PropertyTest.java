package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandEvaluator;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based tests (jqwik): instead of hand-picked cases, these assert
 * invariants across thousands of generated inputs — the right tool for fuzzing a
 * deterministic engine. Catches edge cases example-based tests miss.
 */
class PropertyTest {

    /** Same seed always yields the same shuffle, for ANY seed string. */
    @Property
    void rngIsDeterministicForAnySeed(@ForAll @StringLength(min = 1, max = 16) String seed) {
        List<Integer> a = seq(30);
        List<Integer> b = seq(30);
        new RandomStreams(seed).shuffle(a, "deal");
        new RandomStreams(seed).shuffle(b, "deal");
        assertThat(a).isEqualTo(b);
    }

    /** Scoring the same hand under the same seed is reproducible, for ANY seed. */
    @Property
    void scoringIsDeterministicForAnySeed(@ForAll @StringLength(min = 1, max = 16) String seed) {
        List<Card> hand = List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES));
        double s1 = score(seed, hand);
        double s2 = score(seed, hand);
        assertThat(s1).isEqualTo(s2);
    }

    /** Any 1–5 cards evaluate to a non-null hand type without throwing. */
    @Property
    void handEvaluationNeverFails(
            @ForAll @Size(min = 1, max = 5) List<@IntRange(min = 0, max = 51) Integer> indices) {
        List<Card> cards = new ArrayList<>();
        for (int idx : indices) {
            cards.add(c(Rank.values()[idx % 13], Suit.values()[idx % 4]));
        }
        assertThat(HandEvaluator.evaluate(cards).type()).isNotNull();
    }

    private static double score(String seed, List<Card> hand) {
        RunState run = new RunState();
        run.addJoker(JokerLibrary.create("j_joker"));
        return new ScoringEngine().score(hand, List.of(), run, new RandomStreams(seed)).score();
    }

    private static List<Integer> seq(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(i);
        return l;
    }
}
