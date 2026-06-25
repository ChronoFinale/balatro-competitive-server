package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ReplayEntry;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.ArrayList;
import java.util.List;

/**
 * A readable harness for testing a Joker (or a card modifier) in a single scoring situation — the review's
 * answer to "how do I test individual DSL content?". It collapses the RunState/jokers/cards boilerplate into
 * one fluent sentence, and — the genuinely new bit — lets a test assert on the effect TRACE (the per-step
 * replay log the client renders), not just the final number. So a regression in *how* a joker scores, not
 * only *what* it totals, is visible. Pure read; commits nothing (uses a throwaway seed).
 *
 * <pre>{@code
 * Scenario.scoring().joker("j_lusty_joker").play(c(KING, HEARTS), c(KING, HEARTS))
 *         .expectTraceContains("Lusty Joker")           // it actually fired...
 *         .assertMultIsAtLeast(60);                       // ...and moved the mult.
 * }</pre>
 */
public final class Scenario {

    private final RunState run = new RunState();
    private final List<Card> played = new ArrayList<>();
    private final List<Card> held = new ArrayList<>();
    private String seed = "scenario";
    private ScoreResult result; // memoised so trace + score reflect one run

    public static Scenario scoring() { return new Scenario(); }

    public Scenario joker(String... keys) {
        for (String k : keys) run.addJoker(JokerLibrary.create(k));
        return invalidate();
    }

    public Scenario play(Card... cards) { played.addAll(List.of(cards)); return invalidate(); }

    public Scenario held(Card... cards) { held.addAll(List.of(cards)); return invalidate(); }

    public Scenario money(int m) { run.money = m; return invalidate(); }

    /** Pre-level a poker hand (e.g. {@code handLevel(PAIR, 3)} = Pair at level 3). */
    public Scenario handLevel(HandType t, int level) {
        for (int i = 1; i < level; i++) run.levelUpHand(t);
        return invalidate();
    }

    public Scenario seed(String s) { this.seed = s; return invalidate(); }

    /** The authoritative result of scoring the configured hand. */
    public ScoreResult score() {
        if (result == null) result = new ScoringEngine().score(played, held, run, new RandomStreams(seed));
        return result;
    }

    /** The per-step effect trace (the replay log the client renders). */
    public List<ReplayEntry> trace() { return score().replayLog(); }

    // --- fluent assertions (return this so they chain) ---

    /** A trace step whose source contains {@code label} fired — i.e. the joker/card actually contributed. */
    public Scenario expectTraceContains(String label) {
        assertThat(trace()).as("effect trace should contain a step from '%s'", label)
                .anySatisfy(e -> assertThat(e.source()).contains(label));
        return this;
    }

    /** No trace step from {@code label} — the joker stayed silent (a gate didn't fire). */
    public Scenario expectNoTraceFrom(String label) {
        assertThat(trace()).as("no trace step should come from '%s'", label)
                .noneSatisfy(e -> assertThat(e.source()).contains(label));
        return this;
    }

    public Scenario assertMultIsAtLeast(double m) {
        assertThat(score().mult()).as("mult").isGreaterThanOrEqualTo(m);
        return this;
    }

    public Scenario assertScoreIs(long expected) {
        assertThat(Math.round(score().score())).as("final score").isEqualTo(expected);
        return this;
    }

    private Scenario invalidate() { result = null; return this; }
}
