package com.balatro.engine.state;

/**
 * The behavioural knobs of a game mode, as <b>data</b> rather than a label the engine sniffs. The engine
 * never asks "am I multiplayer?"; it asks for the capability it actually needs (a Glass multiplier, an
 * Idol-roll mode, a duplicate-pick mode). A {@link Ruleset} resolves to one of these, and a mode name
 * maps to a capability set in exactly <b>one</b> place ({@link #of}).
 *
 * <p>This is the seam for "game modes as config": today the sets are presets, but the same value object
 * is what a loaded ruleset would carry, so new modes become data, not code.
 *
 * @param glassMult          Glass card x-mult (vanilla 2.0; the ranked-MP nerf is 1.5)
 * @param idolDeckPosition   The Idol's target is a deck-position roll (MP) instead of a free rank+suit roll
 * @param duplicateRightmost Invisible Joker / Ankh copy the rightmost joker (MP, comparable) vs a random one
 * @param ouijaRework        Ouija destroys 3 cards and keeps hand size (MP) vs vanilla convert-whole-hand
 * @param restrictedPools    Use the MP pool/ban variants for tags, vouchers, jokers, and creation
 */
public record Capabilities(
        double glassMult,
        boolean idolDeckPosition,
        boolean duplicateRightmost,
        boolean ouijaRework,
        boolean restrictedPools) {

    public static final Capabilities VANILLA = new Capabilities(2.0, false, false, false, false);
    public static final Capabilities MULTIPLAYER = new Capabilities(1.5, true, true, true, true);

    /** The only place a mode name maps to a behaviour set. New modes plug in here (or, later, load as data). */
    public static Capabilities of(String variant) {
        return "multiplayer".equals(variant) ? MULTIPLAYER : VANILLA;
    }
}
