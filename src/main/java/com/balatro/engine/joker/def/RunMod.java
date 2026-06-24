package com.balatro.engine.joker.def;

/**
 * The last static capability that isn't yet grammar. Almost everything that lived here became data this
 * session — Oops! is {@code multiply(PROBABILITY_MULTIPLIER, 2)}, Mr Bones is a {@code BLIND_LOST} rule,
 * Chicot is a {@code max(BOSS_ABILITY_DISABLED, 1)} policy, Skip-Off is a dynamic {@code Modify} carrying a
 * {@code Diff} Value. What remains is {@code handSizeDecayStart} (Turtle Bean — a hand-size contribution that
 * decays by round since acquired): a Counter, but a <i>per-instance</i> one applied in the <i>aggregate</i>
 * resource fold, so it needs that fold to resolve a {@link Value} against the owning joker's own state. Once
 * the resource fold is per-joker-aware, this drops out too and RunMod is gone.
 */
public record RunMod(int handSizeDecayStart) {

    public static final RunMod NONE = new RunMod(0);

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return new RunMod(start);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return handSizeDecayStart == 0;
    }
}
