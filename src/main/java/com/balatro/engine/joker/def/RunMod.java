package com.balatro.engine.joker.def;

/**
 * The static, non-scoring capabilities a joker has — its "while owned" / "when sold" behaviour, as
 * data. Most are per-blind stat deltas applied when a blind starts (Juggler +1 hand size, Drunkard
 * +1 discard, Burglar +3 hands / no discards). The rest are capabilities the run checks at the right
 * moment: {@code disablesBoss} (Chicot), {@code survivesLostBlindFraction} (Mr Bones),
 * {@code consumesRandomJokerOnBlindSelect} (Madness), and the {@link OnSell} group (Luchador / Diet
 * Cola / Invisible). None affect the per-hand score, so none are part of the client preview.
 */
public record RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards,
                     boolean disablesBoss, double survivesLostBlindFraction,
                     boolean consumesRandomJokerOnBlindSelect, OnSell onSell) {

    public static final RunMod NONE = new RunMod(0, 0, 0, false, false, 0, false, OnSell.NONE);

    /** What happens when THIS joker is sold (a SELL_SELF reaction, expressed as data). */
    public record OnSell(boolean disablesBoss, int duplicatesJokerAfterRounds, String createsTag) {
        public static final OnSell NONE = new OnSell(false, -1, null);

        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isNone() {
            return !disablesBoss && duplicatesJokerAfterRounds < 0 && createsTag == null;
        }
    }

    /** Canonical constructor: defaults the {@link OnSell} group to NONE so existing call-sites stand. */
    public RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards,
                  boolean disablesBoss, double survivesLostBlindFraction,
                  boolean consumesRandomJokerOnBlindSelect, OnSell onSell) {
        this.handsDelta = handsDelta;
        this.discardsDelta = discardsDelta;
        this.handSizeDelta = handSizeDelta;
        this.noDiscards = noDiscards;
        this.disablesBoss = disablesBoss;
        this.survivesLostBlindFraction = survivesLostBlindFraction;
        this.consumesRandomJokerOnBlindSelect = consumesRandomJokerOnBlindSelect;
        this.onSell = onSell == null ? OnSell.NONE : onSell;
    }

    /** A pure stat modifier (the common case) — no special capability. */
    public RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards) {
        this(handsDelta, discardsDelta, handSizeDelta, noDiscards, false, 0, false, OnSell.NONE);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return new RunMod(0, 0, 0, false, true, 0, false, OnSell.NONE);
    }

    /** Mr Bones: survive a lost blind (and be consumed) if you scored at least {@code fraction} of it. */
    public static RunMod survivesLostBlind(double fraction) {
        return new RunMod(0, 0, 0, false, false, fraction, false, OnSell.NONE);
    }

    /** Madness: on selecting a Small/Big blind, destroy a random other (non-eternal) Joker. */
    public static RunMod jokerEater() {
        return new RunMod(0, 0, 0, false, false, 0, true, OnSell.NONE);
    }

    /** Build from a single {@link OnSell} reaction (Luchador / Diet Cola / Invisible). */
    public static RunMod onSell(OnSell sell) {
        return new RunMod(0, 0, 0, false, false, 0, false, sell);
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
                && !disablesBoss && survivesLostBlindFraction == 0 && !consumesRandomJokerOnBlindSelect
                && onSell.isNone();
    }
}
