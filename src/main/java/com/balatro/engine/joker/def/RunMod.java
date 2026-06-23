package com.balatro.engine.joker.def;

/**
 * The static, non-scoring <b>capabilities</b> a joker has — passive behavioural switches the run checks at the
 * right moment, which are NOT expressible as a number on a game variable (those live in {@link JokerDef#mods()},
 * the same {@link Modify} vocabulary decks/bosses/vouchers use — e.g. Burglar's "no discards" is
 * {@code min(Hand.DISCARDS, 0)}). These are: {@code disablesBoss} (Chicot), {@code survivesLostBlindFraction}
 * (Mr Bones), {@code doublesProbability} (Oops! All 6s), {@code handSizeDecayStart} (Turtle Bean — a
 * <i>dynamic</i> HAND_SIZE contribution that decays by round), {@code pvpSkipBonus} (Skip Off), and the
 * {@link OnSell} group (Invisible). None affect the per-hand score.
 *
 * <p>(Perkeo/Luchador/Diet Cola/Madness/Ceremonial/Penny Pincher are data RULES now — SHOP_EXIT / SELL_SELF /
 * BLIND_SELECTED / SHOP_ENTER — not capabilities.)
 */
public record RunMod(boolean disablesBoss, double survivesLostBlindFraction, OnSell onSell,
                     boolean doublesProbability, int handSizeDecayStart, boolean pvpSkipBonus) {

    public static final RunMod NONE = new RunMod(false, 0, OnSell.NONE, false, 0, false);

    /** What happens when THIS joker is sold (a SELL_SELF reaction, expressed as data). */
    public record OnSell(boolean disablesBoss, int duplicatesJokerAfterRounds, String createsTag) {
        public static final OnSell NONE = new OnSell(false, -1, null);

        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isNone() {
            return !disablesBoss && duplicatesJokerAfterRounds < 0 && createsTag == null;
        }
    }

    /** Canonical constructor: null-safes the sub-record. */
    public RunMod {
        onSell = onSell == null ? OnSell.NONE : onSell;
    }

    private static RunMod capability(boolean disablesBoss, double survivesFraction, OnSell sell,
            boolean doublesProbability, int handSizeDecayStart, boolean pvpSkipBonus) {
        return new RunMod(disablesBoss, survivesFraction, sell, doublesProbability, handSizeDecayStart, pvpSkipBonus);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return capability(true, 0, OnSell.NONE, false, 0, false);
    }

    /** Mr Bones: survive a lost blind (and be consumed) if you scored at least {@code fraction} of it. */
    public static RunMod survivesLostBlind(double fraction) {
        return capability(false, fraction, OnSell.NONE, false, 0, false);
    }

    /** Oops! All 6s: doubles every listed probability while owned (stacks per copy). */
    public static RunMod probabilityDoubler() {
        return capability(false, 0, OnSell.NONE, true, 0, false);
    }

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return capability(false, 0, OnSell.NONE, false, start, false);
    }

    /** Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis. */
    public static RunMod skipBonus() {
        return capability(false, 0, OnSell.NONE, false, 0, true);
    }

    /** Build from a single {@link OnSell} reaction (Invisible). */
    public static RunMod onSell(OnSell sell) {
        return capability(false, 0, sell, false, 0, false);
    }

    /** Invisible Joker: sold after {@code rounds}+ rounds owned, duplicate a random remaining joker. */
    public static RunMod duplicatesJokerOnSell(int rounds) {
        return onSell(new OnSell(false, rounds, null));
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return !disablesBoss && survivesLostBlindFraction == 0 && onSell.isNone()
                && !doublesProbability && handSizeDecayStart == 0 && !pvpSkipBonus;
    }
}
