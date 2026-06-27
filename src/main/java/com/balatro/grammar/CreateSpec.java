package com.balatro.grammar;

import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A "create" instruction: a consumable (8 Ball, Cartomancer, Vagabond, Superposition, Seance), a joker
 * (Riff Raff, the Top-Up / Rare / editioned tags), or a playing card added to the deck (Marble,
 * Certificate). One spec for every creator — the distinctions that used to be baked into per-source verb
 * names (GrantJokers / CreateShopJoker) are explicit fields here: where it goes ({@code destination}),
 * which RNG queue it draws from ({@code stream}), whether it skips already-owned ({@code dedup}), and any
 * forced {@code edition}. Applied server-side only and never during preview; random choices come from
 * game-long queues so both players in a match create the same sequence.
 *
 * @param rarity       for {@code JOKER}: the rarity to draw from (e.g. "Common"); null = any
 * @param enhancement  for {@code PLAYING_CARD}: the enhancement to give it (e.g. STONE); null = none
 * @param sealStrategy for {@code PLAYING_CARD}: whether the card gets no seal or a random one (Certificate)
 * @param stream       for {@code JOKER}: which game-long queue to draw from
 * @param dedup        for {@code JOKER}: skip already-owned (BMP get_current_pool); Top-Up opts out
 * @param destination  where the created thing lands: the PLAYER's slots, or the next SHOP (free-joker tags)
 * @param edition      for a SHOP JOKER: a forced edition (Foil/Holo/Poly/Negative tags); NONE = by rarity
 */
public record CreateSpec(Kind kind, int count, Rarity rarity, Enhancement enhancement,
                         SealStrategy sealStrategy, JokerStream stream, boolean dedup,
                         Destination destination, Edition edition) {

    public enum Kind { TAROT, PLANET, SPECTRAL, JOKER, PLAYING_CARD }

    /** Where a created thing lands. PLAYER = straight into the run's slots (consumable grants, Top-Up,
     *  Riff-Raff); SHOP = a free item in the next shop (Rare/Uncommon/editioned skip tags). */
    public enum Destination { PLAYER, SHOP }

    /** Which game-long queue a {@code JOKER} create draws from. Distinct streams stay uncorrelated by
     *  design (BMP keeps tag-grants and consumable-grants on separate queues), so the stream is part of
     *  the spec, not baked into a per-source verb. {@code CREATE} = {@code create:joker:<rarity>} (The
     *  Soul/Wraith/Riff-Raff); {@code TOPUP} = the {@code tag:topup} queue (Top-Up tag); {@code TAG_SHOP}
     *  = the {@code tag:joker:<rarity>} queue (Rare/Uncommon free-joker tags). */
    public enum JokerStream { CREATE, TOPUP, TAG_SHOP }

    /** Which seal a created PLAYING_CARD gets: none, or a random one (Certificate). (A fixed-seal create
     *  would add a {@code Fixed(Seal)} case — no content needs one today, so it isn't modeled.) */
    public enum SealStrategy { NONE, RANDOM }

    /** Compact canonical: omitted stream/destination/edition/sealStrategy fall back to their defaults. */
    public CreateSpec {
        if (stream == null) stream = JokerStream.CREATE;
        if (destination == null) destination = Destination.PLAYER;
        if (edition == null) edition = Edition.NONE;
        if (sealStrategy == null) sealStrategy = SealStrategy.NONE;
    }

    /** JSON entry point: {@code dedup} is boxed so "omitted" (null) coerces to true — BMP's
     *  get_current_pool skips already-owned by default; only Top-Up opts out. Delegates to canonical. */
    @JsonCreator
    public static CreateSpec fromJson(@JsonProperty("kind") Kind kind, @JsonProperty("count") int count,
            @JsonProperty("rarity") Rarity rarity, @JsonProperty("enhancement") Enhancement enhancement,
            @JsonProperty("sealStrategy") SealStrategy sealStrategy,
            @JsonProperty("stream") JokerStream stream, @JsonProperty("dedup") Boolean dedup,
            @JsonProperty("destination") Destination destination, @JsonProperty("edition") Edition edition) {
        return new CreateSpec(kind, count, rarity, enhancement, sealStrategy,
                stream, dedup == null || dedup, destination, edition);
    }

    public CreateSpec(Kind kind) {
        this(kind, 1, null, null, SealStrategy.NONE, null, true, null, null);
    }

    public CreateSpec(Kind kind, int count) {
        this(kind, count, null, null, SealStrategy.NONE, null, true, null, null);
    }

    public CreateSpec(Kind kind, int count, Rarity rarity, Enhancement enhancement) {
        this(kind, count, rarity, enhancement, SealStrategy.NONE, null, true, null, null);
    }

    public CreateSpec(Kind kind, int count, Rarity rarity, Enhancement enhancement, SealStrategy sealStrategy) {
        this(kind, count, rarity, enhancement, sealStrategy, null, true, null, null);
    }

    /** A JOKER grant straight to the player with explicit stream + dedup (the Top-Up tag: TOPUP, no dedup). */
    public static CreateSpec jokers(int count, Rarity rarity, JokerStream stream, boolean dedup) {
        return new CreateSpec(Kind.JOKER, count, rarity, null, SealStrategy.NONE, stream, dedup,
                Destination.PLAYER, Edition.NONE);
    }

    /** A free JOKER in the next shop — by {@code rarity} from the tag:joker queue (Rare/Uncommon tags), or
     *  a random one with a forced {@code edition} (Foil/Holo/Poly/Negative tags; rarity null). */
    public static CreateSpec shopJoker(Rarity rarity, Edition edition) {
        return new CreateSpec(Kind.JOKER, 1, rarity, null, SealStrategy.NONE,
                JokerStream.TAG_SHOP, false, Destination.SHOP, edition);
    }
}
