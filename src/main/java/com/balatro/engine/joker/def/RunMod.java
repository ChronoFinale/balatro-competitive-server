package com.balatro.engine.joker.def;

/**
 * The static, non-scoring <b>capabilities</b> a joker has — passive behavioural switches the run checks at the
 * right moment. After this session most of the old residents became grammar: Oops! All 6s is
 * {@code multiply(PROBABILITY_MULTIPLIER, 2)}, Mr Bones is a {@code BLIND_LOST} rule, Chicot is a
 * {@code max(BOSS_ABILITY_DISABLED, 1)} policy. What's left is two genuinely <i>dynamic</i> contributions —
 * a value that changes per round / per PvP state, which a static {@link Modify} (a constant) can't yet hold:
 * {@code handSizeDecayStart} (Turtle Bean — +N hand size decaying by round) and {@code pvpSkipBonus}
 * (Skip-Off — +hands/discards per blind skipped vs the Nemesis). Both drop out once {@code Modify} carries a
 * {@code Value} instead of a {@code double}. None affect the per-hand score, and none are events.
 */
public record RunMod(int handSizeDecayStart, boolean pvpSkipBonus) {

    public static final RunMod NONE = new RunMod(0, false);

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return new RunMod(start, false);
    }

    /** Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis. */
    public static RunMod skipBonus() {
        return new RunMod(0, true);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return handSizeDecayStart == 0 && !pvpSkipBonus;
    }
}
