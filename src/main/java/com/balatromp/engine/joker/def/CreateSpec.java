package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Enhancement;

/**
 * A "create" instruction a joker emits: a consumable (8 Ball, Cartomancer, Vagabond,
 * Superposition, Seance), a joker (Riff Raff), or a playing card added to the deck
 * (Marble). Applied server-side only and never during preview — the client just sees
 * the result in the next view. Random choices are drawn from a game-long queue so both
 * players in a match create the same sequence.
 *
 * @param rarity      for {@code JOKER}: the rarity to draw from (e.g. "Common"); null = any
 * @param enhancement for {@code PLAYING_CARD}: the enhancement to give it (e.g. STONE); null = none
 */
public record CreateSpec(Kind kind, int count, String rarity, Enhancement enhancement) {

    public enum Kind { TAROT, PLANET, SPECTRAL, JOKER, PLAYING_CARD }

    public CreateSpec(Kind kind) {
        this(kind, 1, null, null);
    }

    public CreateSpec(Kind kind, int count) {
        this(kind, count, null, null);
    }
}
