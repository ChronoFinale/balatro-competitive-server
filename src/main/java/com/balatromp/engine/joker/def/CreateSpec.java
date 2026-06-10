package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A "create" instruction a joker emits: a consumable (8 Ball, Cartomancer, Vagabond,
 * Superposition, Seance), a joker (Riff Raff), or a playing card added to the deck
 * (Marble, Certificate). Applied server-side only and never during preview — the
 * client just sees the result in the next view. Random choices are drawn from a
 * game-long queue so both players in a match create the same sequence.
 *
 * @param rarity      for {@code JOKER}: the rarity to draw from (e.g. "Common"); null = any
 * @param enhancement for {@code PLAYING_CARD}: the enhancement to give it (e.g. STONE); null = none
 * @param seal        for {@code PLAYING_CARD}: a fixed seal to apply; null = none (unless randomSeal)
 * @param randomSeal  for {@code PLAYING_CARD}: give the card a random seal (Certificate)
 */
public record CreateSpec(Kind kind, int count, String rarity, Enhancement enhancement,
                         Seal seal, boolean randomSeal) {

    public enum Kind { TAROT, PLANET, SPECTRAL, JOKER, PLAYING_CARD }

    @JsonCreator
    public CreateSpec(@JsonProperty("kind") Kind kind, @JsonProperty("count") int count,
            @JsonProperty("rarity") String rarity, @JsonProperty("enhancement") Enhancement enhancement,
            @JsonProperty("seal") Seal seal, @JsonProperty("randomSeal") boolean randomSeal) {
        this.kind = kind;
        this.count = count;
        this.rarity = rarity;
        this.enhancement = enhancement;
        this.seal = seal;
        this.randomSeal = randomSeal;
    }

    public CreateSpec(Kind kind) {
        this(kind, 1, null, null, null, false);
    }

    public CreateSpec(Kind kind, int count) {
        this(kind, count, null, null, null, false);
    }

    public CreateSpec(Kind kind, int count, String rarity, Enhancement enhancement) {
        this(kind, count, rarity, enhancement, null, false);
    }
}
