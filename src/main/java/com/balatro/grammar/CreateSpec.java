package com.balatro.grammar;

import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A "create" instruction — a SEALED hierarchy with one variant per kind of thing created, so a spec carries
 * only the fields that actually apply to it (no more JOKER-only rarity/stream/dedup sitting null on a
 * playing-card create — the kind-conditional-fields smell). {@link Consumable} = a tarot/planet/spectral;
 * {@link Joker} = a joker (player slots or the next shop); {@link Card} = a playing card added to the deck.
 * Applied server-side only, never in preview; random choices come from game-long queues so both players in a
 * match create the same sequence.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateSpec.Consumable.class, name = "consumable"),
    @JsonSubTypes.Type(value = CreateSpec.Joker.class, name = "joker"),
    @JsonSubTypes.Type(value = CreateSpec.Card.class, name = "card"),
})
public sealed interface CreateSpec permits CreateSpec.Consumable, CreateSpec.Joker, CreateSpec.Card {

    /** How many to create. */
    int count();

    /** Which consumable a {@link Consumable} create makes. */
    enum Kind { TAROT, PLANET, SPECTRAL }

    /** Where a {@link Joker} create lands: the player's slots, or a free item in the next shop. */
    enum Destination { PLAYER, SHOP }

    /** Which game-long queue a {@link Joker} create draws from — distinct streams stay uncorrelated (BMP
     *  keeps tag-grants and consumable-grants on separate queues). CREATE = create:joker:&lt;rarity&gt;
     *  (Soul/Wraith/Riff-Raff); TOPUP = tag:topup (Top-Up); TAG_SHOP = tag:joker:&lt;rarity&gt; (free-joker tags). */
    enum JokerStream { CREATE, TOPUP, TAG_SHOP }

    /** Which seal a created {@link Card} gets: none, or a random one (Certificate). */
    enum SealStrategy { NONE, RANDOM }

    /** A tarot / planet / spectral (8 Ball, Cartomancer, Seance, Vagabond, Superposition). */
    record Consumable(Kind kind, int count) implements CreateSpec {
        public Consumable(Kind kind) { this(kind, 1); }
    }

    /** A joker — into the player's slots (Riff-Raff, Top-Up) or the next shop (Rare/Uncommon/editioned tags). */
    record Joker(int count, Rarity rarity, JokerStream stream, boolean dedup,
                 Destination destination, Edition edition) implements CreateSpec {
        public Joker {
            if (stream == null) stream = JokerStream.CREATE;
            if (destination == null) destination = Destination.PLAYER;
            if (edition == null) edition = Edition.NONE;
        }

        /** JSON: {@code dedup} omitted -> true (BMP get_current_pool skips already-owned; Top-Up opts out). */
        @JsonCreator
        static Joker fromJson(@JsonProperty("count") int count, @JsonProperty("rarity") Rarity rarity,
                @JsonProperty("stream") JokerStream stream, @JsonProperty("dedup") Boolean dedup,
                @JsonProperty("destination") Destination destination, @JsonProperty("edition") Edition edition) {
            return new Joker(count, rarity, stream, dedup == null || dedup, destination, edition);
        }

        /** A joker straight to the player (Riff-Raff: by rarity, dedup; Top-Up: TOPUP stream, no dedup). */
        public static Joker forPlayer(int count, Rarity rarity, JokerStream stream, boolean dedup) {
            return new Joker(count, rarity, stream, dedup, Destination.PLAYER, Edition.NONE);
        }

        /** A free joker in the next shop — by {@code rarity} (Rare/Uncommon tags), or a forced {@code edition}
         *  (Foil/Holo/Poly/Negative tags; rarity null). */
        public static Joker forShop(Rarity rarity, Edition edition) {
            return new Joker(1, rarity, JokerStream.TAG_SHOP, false, Destination.SHOP, edition);
        }
    }

    /** A playing card added to the deck (Marble: a Stone card; Certificate: a card with a random seal). */
    record Card(int count, Enhancement enhancement, SealStrategy sealStrategy) implements CreateSpec {
        public Card {
            if (sealStrategy == null) sealStrategy = SealStrategy.NONE;
        }
    }
}
