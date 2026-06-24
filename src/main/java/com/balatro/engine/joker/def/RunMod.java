package com.balatro.engine.joker.def;

/**
 * The static, non-scoring <b>capabilities</b> a joker has — passive behavioural switches the run checks at the
 * right moment, which are NOT expressible as a number on a game variable (those live in {@link JokerDef#mods()},
 * the same {@link Modify} vocabulary decks/bosses/vouchers use — e.g. Burglar's "no discards" is
 * {@code min(Hand.DISCARDS, 0)}, and Oops! All 6s's "double every probability" is
 * {@code multiply(Value.Var.PROBABILITY_MULTIPLIER, 2)}). What's left is genuinely passive / read-side:
 * {@code disablesBoss} (Chicot — a boss-ability toggle, not a variable), {@code handSizeDecayStart}
 * (Turtle Bean — a <i>dynamic</i> Hand.SIZE contribution that decays by round), and {@code pvpSkipBonus}
 * (Skip Off). None affect the per-hand score, and none are events.
 *
 * <p>Every event-driven joker behaviour is a data RULE now (Perkeo SHOP_EXIT, Luchador/Diet Cola/Invisible
 * SELL_SELF, Madness/Ceremonial BLIND_SELECTED, Penny Pincher SHOP_ENTER, <b>Mr Bones BLIND_LOST</b>) — not a
 * capability. The Blind lifecycle is hookable now, so "survive a lost blind" is a rule, not a RunMod field.
 */
public record RunMod(boolean disablesBoss, int handSizeDecayStart, boolean pvpSkipBonus) {

    public static final RunMod NONE = new RunMod(false, 0, false);

    private static RunMod capability(boolean disablesBoss, int handSizeDecayStart, boolean pvpSkipBonus) {
        return new RunMod(disablesBoss, handSizeDecayStart, pvpSkipBonus);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return capability(true, 0, false);
    }

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return capability(false, start, false);
    }

    /** Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis. */
    public static RunMod skipBonus() {
        return capability(false, 0, true);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return !disablesBoss && handSizeDecayStart == 0 && !pvpSkipBonus;
    }
}
