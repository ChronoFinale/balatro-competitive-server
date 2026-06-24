package com.balatro.engine.joker.def;

/**
 * The static, non-scoring <b>capabilities</b> a joker has — passive behavioural switches the run checks at the
 * right moment, which are NOT expressible as a number on a game variable (those live in {@link JokerDef#mods()},
 * the same {@link Modify} vocabulary decks/bosses/vouchers use — e.g. Burglar's "no discards" is
 * {@code min(Hand.DISCARDS, 0)}, and Oops! All 6s's "double every probability" is now
 * {@code multiply(Value.Var.PROBABILITY_MULTIPLIER, 2)}). What's left is genuinely passive / read-side:
 * {@code disablesBoss} (Chicot), {@code survivesLostBlindFraction} (Mr Bones), {@code handSizeDecayStart}
 * (Turtle Bean — a <i>dynamic</i> Hand.SIZE contribution that decays by round), and {@code pvpSkipBonus}
 * (Skip Off). None affect the per-hand score, and none are events.
 *
 * <p>Every event-driven joker behaviour is a data RULE now (Perkeo SHOP_EXIT, Luchador/Diet Cola/Invisible
 * SELL_SELF, Madness/Ceremonial BLIND_SELECTED, Penny Pincher SHOP_ENTER) — not a capability.
 */
public record RunMod(boolean disablesBoss, double survivesLostBlindFraction,
                     int handSizeDecayStart, boolean pvpSkipBonus) {

    public static final RunMod NONE = new RunMod(false, 0, 0, false);

    private static RunMod capability(boolean disablesBoss, double survivesFraction,
            int handSizeDecayStart, boolean pvpSkipBonus) {
        return new RunMod(disablesBoss, survivesFraction, handSizeDecayStart, pvpSkipBonus);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return capability(true, 0, 0, false);
    }

    /** Mr Bones: survive a lost blind (and be consumed) if you scored at least {@code fraction} of it. */
    public static RunMod survivesLostBlind(double fraction) {
        return capability(false, fraction, 0, false);
    }

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return capability(false, 0, start, false);
    }

    /** Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis. */
    public static RunMod skipBonus() {
        return capability(false, 0, 0, true);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return !disablesBoss && survivesLostBlindFraction == 0
                && handSizeDecayStart == 0 && !pvpSkipBonus;
    }
}
