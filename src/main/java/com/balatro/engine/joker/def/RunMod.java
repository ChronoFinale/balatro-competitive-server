package com.balatro.engine.joker.def;

/**
 * The static, non-scoring <b>capabilities</b> a joker has — behavioural switches the run checks at the
 * right moment, which are NOT expressible as a number on a game variable (those live in
 * {@link JokerDef#mods()} now, the same {@link Modify} vocabulary decks/bosses/vouchers use — e.g. Burglar's
 * "no discards" is {@code min(Hand.DISCARDS, 0)}). These are:
 * {@code disablesBoss} (Chicot), {@code survivesLostBlindFraction}
 * (Mr Bones), {@code blindSelectConsume} (Madness / Ceremonial), {@code doublesProbability}
 * (Oops! All 6s), {@code handSizeDecayStart} (Turtle Bean — a <i>dynamic</i> HAND_SIZE contribution that
 * decays by round, so it can't be a static Modify), {@code pvpShopSpendDenominator} (Penny Pincher),
 * {@code pvpSkipBonus} (Skip Off), and the {@link OnSell} group. None affect the per-hand score.
 * (Perkeo's shop-exit consumable duplication is now a SHOP_EXIT rule, not a capability.)
 */
public record RunMod(boolean disablesBoss, double survivesLostBlindFraction,
                     BlindSelectConsume blindSelectConsume, OnSell onSell,
                     boolean doublesProbability, int handSizeDecayStart,
                     int pvpShopSpendDenominator, boolean pvpSkipBonus) {

    public static final RunMod NONE = new RunMod(false, 0,
            BlindSelectConsume.NONE, OnSell.NONE, false, 0, 0, false);

    /** On selecting a blind, this joker eats a Joker — a random other one (Madness, Small/Big only)
     *  or its right neighbour (Ceremonial Dagger, any blind). */
    public enum BlindSelectConsume { NONE, RANDOM_OTHER, RIGHT_NEIGHBOR }

    /** What happens when THIS joker is sold (a SELL_SELF reaction, expressed as data). */
    public record OnSell(boolean disablesBoss, int duplicatesJokerAfterRounds, String createsTag) {
        public static final OnSell NONE = new OnSell(false, -1, null);

        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isNone() {
            return !disablesBoss && duplicatesJokerAfterRounds < 0 && createsTag == null;
        }
    }

    /** Canonical constructor: null-safes the sub-records. */
    public RunMod {
        blindSelectConsume = blindSelectConsume == null ? BlindSelectConsume.NONE : blindSelectConsume;
        onSell = onSell == null ? OnSell.NONE : onSell;
    }

    private static RunMod capability(boolean disablesBoss, double survivesFraction, BlindSelectConsume consume,
            OnSell sell, boolean doublesProbability, int handSizeDecayStart,
            int pvpShopSpendDenominator, boolean pvpSkipBonus) {
        return new RunMod(disablesBoss, survivesFraction, consume, sell, doublesProbability,
                handSizeDecayStart, pvpShopSpendDenominator, pvpSkipBonus);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return capability(true, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, 0, false);
    }

    /** Mr Bones: survive a lost blind (and be consumed) if you scored at least {@code fraction} of it. */
    public static RunMod survivesLostBlind(double fraction) {
        return capability(false, fraction, BlindSelectConsume.NONE, OnSell.NONE, false, 0, 0, false);
    }

    /** Madness: on selecting a Small/Big blind, destroy a random other (non-eternal) Joker. */
    public static RunMod jokerEater() {
        return capability(false, 0, BlindSelectConsume.RANDOM_OTHER, OnSell.NONE, false, 0, 0, false);
    }

    /** Ceremonial Dagger: on selecting any blind, destroy the right-neighbour Joker. */
    public static RunMod ceremonialDagger() {
        return capability(false, 0, BlindSelectConsume.RIGHT_NEIGHBOR, OnSell.NONE, false, 0, 0, false);
    }

    /** Oops! All 6s: doubles every listed probability while owned (stacks per copy). */
    public static RunMod probabilityDoubler() {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, true, 0, 0, false);
    }

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, start, 0, false);
    }

    /** Penny Pincher (Nemesis): on entering the shop, gain $1 per {@code denom} your Nemesis spent last ante. */
    public static RunMod pvpShopSpendShare(int denom) {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, denom, false);
    }

    /** Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis. */
    public static RunMod skipBonus() {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, 0, true);
    }

    /** Build from a single {@link OnSell} reaction (Luchador / Diet Cola / Invisible). */
    public static RunMod onSell(OnSell sell) {
        return capability(false, 0, BlindSelectConsume.NONE, sell, false, 0, 0, false);
    }

    /** Invisible Joker: sold after {@code rounds}+ rounds owned, duplicate a random remaining joker.
     *  (Luchador's disable-boss-on-sell and Diet Cola's tag-on-sell are SELL_SELF rules now, not capabilities.) */
    public static RunMod duplicatesJokerOnSell(int rounds) {
        return onSell(new OnSell(false, rounds, null));
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return !disablesBoss && survivesLostBlindFraction == 0
                && blindSelectConsume == BlindSelectConsume.NONE && onSell.isNone()
                && !doublesProbability && handSizeDecayStart == 0
                && pvpShopSpendDenominator == 0 && !pvpSkipBonus;
    }
}
