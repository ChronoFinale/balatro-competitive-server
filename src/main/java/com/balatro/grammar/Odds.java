package com.balatro.grammar;

/**
 * A "1 in X" probability — {@code numerator}/{@code denominator} — the named thing Balatro's odds system
 * is built on (Lucky, Glass, Bloodstone, Wheel, Hanging Chad, Cavendish, …). Pulled out of {@link Condition.Chance}
 * so odds is a value you pass around, not two loose ints. The actual draw comes from a game-long queue (see
 * {@code QueueSet}); this is only the threshold the roll is compared against, scaled run-wide by
 * {@code PROBABILITY_MULTIPLIER} (Oops! All 6s) — the stream stays fixed, only the bar moves.
 */
public record Odds(int numerator, int denominator) {

    /** A "1 in {@code n}" chance. */
    public static Odds oneIn(int n) {
        return new Odds(1, n);
    }
}
