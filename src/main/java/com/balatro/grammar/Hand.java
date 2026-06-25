package com.balatro.grammar;

/**
 * The hand as a first-class {@link Property} — the game noun, not four loose entries in a flat var bag. Its
 * {@code SIZE}, the {@code PLAYS} and {@code DISCARDS} you have this blind, and the per-blind {@code
 * DRAW_COUNT} override (-1 = refill to size; The Serpent sets a fixed count). A {@link Modify} targets one of
 * these exactly as it targets any other resource; {@code SIZE}/{@code PLAYS}/{@code DISCARDS} are also
 * readable by conditions, while {@code DRAW_COUNT} is write-only.
 */
public enum Hand implements Property {
    SIZE, PLAYS, DISCARDS, DRAW_COUNT
}
