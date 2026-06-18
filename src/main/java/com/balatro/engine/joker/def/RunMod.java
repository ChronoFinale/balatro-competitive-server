package com.balatro.engine.joker.def;

/**
 * Passive capabilities a joker grants simply by being owned — not scoring effects. Most are per-blind
 * stat deltas applied when a blind starts (Juggler +1 hand size, Drunkard +1 discard, Burglar +3 hands
 * / no discards). Two are checked elsewhere in the run: {@code disablesBoss} (Chicot — the boss's
 * abilities are switched off) and {@code survivesLostBlindFraction} (Mr Bones — survive a lost blind,
 * and be consumed, if you reached this fraction of the requirement). None affect the per-hand score,
 * so they are not part of the client preview.
 */
public record RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards,
                     boolean disablesBoss, double survivesLostBlindFraction,
                     boolean consumesRandomJokerOnBlindSelect) {

    public static final RunMod NONE = new RunMod(0, 0, 0, false, false, 0, false);

    /** A pure stat modifier (the common case) — no special capability. */
    public RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards) {
        this(handsDelta, discardsDelta, handSizeDelta, noDiscards, false, 0, false);
    }

    /** Chicot: while owned, every Boss Blind's ability is disabled. */
    public static RunMod bossDisabler() {
        return new RunMod(0, 0, 0, false, true, 0, false);
    }

    /** Mr Bones: survive a lost blind (and be consumed) if you scored at least {@code fraction} of it. */
    public static RunMod survivesLostBlind(double fraction) {
        return new RunMod(0, 0, 0, false, false, fraction, false);
    }

    /** Madness: on selecting a Small/Big blind, destroy a random other (non-eternal) Joker. */
    public static RunMod jokerEater() {
        return new RunMod(0, 0, 0, false, false, 0, true);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return handsDelta == 0 && discardsDelta == 0 && handSizeDelta == 0 && !noDiscards
                && !disablesBoss && survivesLostBlindFraction == 0 && !consumesRandomJokerOnBlindSelect;
    }
}
