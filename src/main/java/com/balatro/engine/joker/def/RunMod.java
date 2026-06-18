package com.balatro.engine.joker.def;

/**
 * The static, non-scoring capabilities a joker has — its "while owned" / "when sold" behaviour, as
 * data. Most are per-blind stat deltas applied when a blind starts (Juggler +1 hand size, Drunkard
 * +1 discard, Burglar +3 hands / no discards). The rest are capabilities the run checks at the right
 * moment: {@code disablesBoss} (Chicot), {@code survivesLostBlindFraction} (Mr Bones),
 * {@code blindSelectConsume} (Madness / Ceremonial), {@code doublesProbability} (Oops! All 6s),
 * {@code handSizeDecayStart} (Turtle Bean), {@code duplicatesConsumableOnShopExit} (Perkeo),
 * {@code pvpShopSpendDenominator} (Penny Pincher), {@code pvpSkipBonus} (Skip Off), and the
 * {@link OnSell} group (Luchador / Diet Cola / Invisible). None affect the per-hand score, so none
 * are part of the client preview.
 */
public record RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards,
                     boolean disablesBoss, double survivesLostBlindFraction,
                     BlindSelectConsume blindSelectConsume, OnSell onSell,
                     boolean doublesProbability, int handSizeDecayStart,
                     boolean duplicatesConsumableOnShopExit, int pvpShopSpendDenominator,
                     boolean pvpSkipBonus) {

    public static final RunMod NONE = new RunMod(0, 0, 0, false, false, 0,
            BlindSelectConsume.NONE, OnSell.NONE, false, 0, false, 0, false);

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

    /** Canonical constructor: null-safes the {@link OnSell} / {@link BlindSelectConsume} groups. */
    public RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards,
                  boolean disablesBoss, double survivesLostBlindFraction,
                  BlindSelectConsume blindSelectConsume, OnSell onSell,
                  boolean doublesProbability, int handSizeDecayStart,
                  boolean duplicatesConsumableOnShopExit, int pvpShopSpendDenominator,
                  boolean pvpSkipBonus) {
        this.handsDelta = handsDelta;
        this.discardsDelta = discardsDelta;
        this.handSizeDelta = handSizeDelta;
        this.noDiscards = noDiscards;
        this.disablesBoss = disablesBoss;
        this.survivesLostBlindFraction = survivesLostBlindFraction;
        this.blindSelectConsume = blindSelectConsume == null ? BlindSelectConsume.NONE : blindSelectConsume;
        this.onSell = onSell == null ? OnSell.NONE : onSell;
        this.doublesProbability = doublesProbability;
        this.handSizeDecayStart = handSizeDecayStart;
        this.duplicatesConsumableOnShopExit = duplicatesConsumableOnShopExit;
        this.pvpShopSpendDenominator = pvpShopSpendDenominator;
        this.pvpSkipBonus = pvpSkipBonus;
    }

    /** A pure stat modifier (the common case) — no special capability. */
    public RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards) {
        this(handsDelta, discardsDelta, handSizeDelta, noDiscards, false, 0,
                BlindSelectConsume.NONE, OnSell.NONE, false, 0, false, 0, false);
    }

    /** A capability joker (no stat deltas) — set exactly one of the capability fields via a factory. */
    private static RunMod capability(boolean disablesBoss, double survivesFraction, BlindSelectConsume consume,
            OnSell sell, boolean doublesProbability, int handSizeDecayStart,
            boolean dupConsumableOnShopExit, int pvpShopSpendDenominator, boolean pvpSkipBonus) {
        return new RunMod(0, 0, 0, false, disablesBoss, survivesFraction, consume, sell, doublesProbability,
                handSizeDecayStart, dupConsumableOnShopExit, pvpShopSpendDenominator, pvpSkipBonus);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return capability(true, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, false, 0, false);
    }

    /** Mr Bones: survive a lost blind (and be consumed) if you scored at least {@code fraction} of it. */
    public static RunMod survivesLostBlind(double fraction) {
        return capability(false, fraction, BlindSelectConsume.NONE, OnSell.NONE, false, 0, false, 0, false);
    }

    /** Madness: on selecting a Small/Big blind, destroy a random other (non-eternal) Joker. */
    public static RunMod jokerEater() {
        return capability(false, 0, BlindSelectConsume.RANDOM_OTHER, OnSell.NONE, false, 0, false, 0, false);
    }

    /** Ceremonial Dagger: on selecting any blind, destroy the right-neighbour Joker. */
    public static RunMod ceremonialDagger() {
        return capability(false, 0, BlindSelectConsume.RIGHT_NEIGHBOR, OnSell.NONE, false, 0, false, 0, false);
    }

    /** Oops! All 6s: doubles every listed probability while owned (stacks per copy). */
    public static RunMod probabilityDoubler() {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, true, 0, false, 0, false);
    }

    /** Turtle Bean: +{@code start} hand size, decaying by 1 each round since acquired (floors at 0). */
    public static RunMod decayingHandSize(int start) {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, start, false, 0, false);
    }

    /** Perkeo: on leaving the shop, duplicate a random held consumable (a Negative copy). */
    public static RunMod consumableDuplicator() {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, true, 0, false);
    }

    /** Penny Pincher (Nemesis): on entering the shop, gain $1 per {@code denom} your Nemesis spent last ante. */
    public static RunMod pvpShopSpendShare(int denom) {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, false, denom, false);
    }

    /** Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis. */
    public static RunMod skipBonus() {
        return capability(false, 0, BlindSelectConsume.NONE, OnSell.NONE, false, 0, false, 0, true);
    }

    /** Build from a single {@link OnSell} reaction (Luchador / Diet Cola / Invisible). */
    public static RunMod onSell(OnSell sell) {
        return capability(false, 0, BlindSelectConsume.NONE, sell, false, 0, false, 0, false);
    }

    /** Luchador: sold during a boss blind, disable that boss's ability for the rest of the blind. */
    public static RunMod disablesBossOnSell() {
        return onSell(new OnSell(true, -1, null));
    }

    /** Invisible Joker: sold after {@code rounds}+ rounds owned, duplicate a random remaining joker. */
    public static RunMod duplicatesJokerOnSell(int rounds) {
        return onSell(new OnSell(false, rounds, null));
    }

    /** Diet Cola: sold, create a free {@code tag}. */
    public static RunMod createsTagOnSell(String tag) {
        return onSell(new OnSell(false, -1, tag));
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return handsDelta == 0 && discardsDelta == 0 && handSizeDelta == 0 && !noDiscards
                && !disablesBoss && survivesLostBlindFraction == 0
                && blindSelectConsume == BlindSelectConsume.NONE && onSell.isNone()
                && !doublesProbability && handSizeDecayStart == 0
                && !duplicatesConsumableOnShopExit && pvpShopSpendDenominator == 0 && !pvpSkipBonus;
    }
}
