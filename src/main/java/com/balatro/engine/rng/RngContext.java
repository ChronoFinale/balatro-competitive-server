package com.balatro.engine.rng;

/**
 * The live state a {@link RngSource} is resolved against — the other half of the
 * keying decision. A source says "I am per-blind, pvp-per-hand"; the context
 * supplies the concrete ante/blind and whether we are currently in a PvP blind
 * and whether "The Order" is active. {@link QueueSet#resolve} combines the two.
 *
 * <p>{@code orderOn} is the single switch behind BMP's ranked variance reduction:
 * when on, {@link RngSource.Scope#GAME_LONG} sources omit the ante from their key
 * (one sequence for the whole game) and {@link RngSource.Selection#COMPOSITION}
 * sources order by identity rather than position. A future casual ruleset can
 * flip it off to get vanilla per-ante, position-sensitive behavior — without
 * touching any call-site.
 *
 * @param ante        the current ante
 * @param blind       the current blind's key/name (only consulted for PER_BLIND sources)
 * @param inPvpBlind  whether the active blind is a PvP (Nemesis) blind
 * @param orderOn     whether "The Order" variance reduction is active
 */
public record RngContext(int ante, String blind, boolean inPvpBlind, boolean orderOn) {

    /** Convenience for game-long / scoring sources that never need the blind name. */
    public static RngContext of(int ante, boolean inPvpBlind, boolean orderOn) {
        return new RngContext(ante, "", inPvpBlind, orderOn);
    }
}
