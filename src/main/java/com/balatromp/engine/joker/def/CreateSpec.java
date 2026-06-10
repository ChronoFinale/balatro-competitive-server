package com.balatromp.engine.joker.def;

/**
 * A "create a card" instruction a joker emits (8 Ball makes a Tarot, Cartomancer a
 * Tarot at blind select, ...). Applied server-side only and never during preview —
 * the client just sees the created item in the next view. Random selection within a
 * kind is drawn from a game-long queue so both players in a match create the same
 * sequence.
 */
public record CreateSpec(Kind kind, int count) {

    public enum Kind { TAROT, PLANET, SPECTRAL }

    public CreateSpec(Kind kind) {
        this(kind, 1);
    }
}
