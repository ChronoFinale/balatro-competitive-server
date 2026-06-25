package com.balatro.engine.joker.def;

import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
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
                         Seal seal, boolean randomSeal, JokerStream stream, boolean dedup) {

    public enum Kind { TAROT, PLANET, SPECTRAL, JOKER, PLAYING_CARD }

    /** Which game-long queue a {@code JOKER} create draws from. Distinct streams stay uncorrelated by
     *  design (BMP keeps tag-grants and consumable-grants on separate queues), so the stream is part of
     *  the spec, not baked into a per-source verb. {@code CREATE} = {@code create:joker:<rarity>} (The
     *  Soul/Wraith/Riff-Raff); {@code TOPUP} = the {@code tag:topup} queue (Top-Up tag). */
    public enum JokerStream { CREATE, TOPUP }

    /** Compact canonical: an omitted stream defaults to the consumable-grant queue. */
    public CreateSpec {
        if (stream == null) stream = JokerStream.CREATE;
    }

    /** JSON entry point: {@code dedup} is boxed so "omitted" (null) coerces to true — BMP's
     *  get_current_pool skips already-owned by default; only Top-Up opts out. Delegates to canonical. */
    @JsonCreator
    public static CreateSpec fromJson(@JsonProperty("kind") Kind kind, @JsonProperty("count") int count,
            @JsonProperty("rarity") String rarity, @JsonProperty("enhancement") Enhancement enhancement,
            @JsonProperty("seal") Seal seal, @JsonProperty("randomSeal") boolean randomSeal,
            @JsonProperty("stream") JokerStream stream, @JsonProperty("dedup") Boolean dedup) {
        return new CreateSpec(kind, count, rarity, enhancement, seal, randomSeal,
                stream, dedup == null || dedup);
    }

    public CreateSpec(Kind kind) {
        this(kind, 1, null, null, null, false, null, true);
    }

    public CreateSpec(Kind kind, int count) {
        this(kind, count, null, null, null, false, null, true);
    }

    public CreateSpec(Kind kind, int count, String rarity, Enhancement enhancement) {
        this(kind, count, rarity, enhancement, null, false, null, true);
    }

    public CreateSpec(Kind kind, int count, String rarity, Enhancement enhancement,
            Seal seal, boolean randomSeal) {
        this(kind, count, rarity, enhancement, seal, randomSeal, null, true);
    }

    /** A JOKER grant with explicit stream + dedup policy (the Top-Up tag: TOPUP queue, no dedup). */
    public static CreateSpec jokers(int count, String rarity, JokerStream stream, boolean dedup) {
        return new CreateSpec(Kind.JOKER, count, rarity, null, null, false, stream, dedup);
    }
}
