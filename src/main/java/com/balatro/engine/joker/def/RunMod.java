package com.balatro.engine.joker.def;

/**
 * Passive per-blind run modifiers a joker grants while owned — applied when a blind
 * starts (hands / discards / hand-size deltas), not during scoring. Juggler (+1 hand
 * size), Drunkard (+1 discard), Burglar (+3 hands, no discards), etc. Does not affect
 * the per-hand score, so it is not part of the client preview.
 */
public record RunMod(int handsDelta, int discardsDelta, int handSizeDelta, boolean noDiscards) {

    public static final RunMod NONE = new RunMod(0, 0, 0, false);

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return handsDelta == 0 && discardsDelta == 0 && handSizeDelta == 0 && !noDiscards;
    }
}
