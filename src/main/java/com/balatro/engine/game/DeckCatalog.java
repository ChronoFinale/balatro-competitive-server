package com.balatro.engine.game;

import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deck variants — the starting configuration a run begins with (Balatro's deck
 * select). A deck shifts starting money, joker slots, and per-blind hands/discards;
 * a few have special economy (Green). The base deck has no modifiers. The match's
 * {@link com.balatro.engine.state.Ruleset} names the deck; {@code Run} applies it.
 *
 * @param greenEconomy end-of-round economy of the Green Deck: no interest, but $2 per
 *                     remaining hand and $1 per remaining discard.
 */
public final class DeckCatalog {

    private DeckCatalog() {}

    /** How a deck builds its starting 52-card composition (game.lua b_* configs). */
    public enum Composition {
        STANDARD,    // the plain 52
        NO_FACES,    // Abandoned: remove every J/Q/K (40 cards)
        CHECKERED,   // Checkered: Clubs->Spades, Diamonds->Hearts (26 Spades + 26 Hearts)
        ERRATIC      // Erratic: each card gets a random rank AND suit
    }

    public record DeckType(String key, String name, String description,
                           // per-blind resource changes as data: Blue = add(HANDS_LEFT,1), Painted =
                           // add(HAND_SIZE,2), Black = add(HANDS_LEFT,-1) — folded by Run with everyone else.
                           List<Modify> resourceMods,
                           int jokerSlotsDelta, boolean greenEconomy,
                           Composition composition,
                           List<String> startingVouchers, List<String> startingConsumables,
                           // special-behaviour data (de-hardcoded from Run/ScoringEngine):
                           int spectralRate,            // Ghost: Spectral cards appear in the shop at this weight
                           boolean balanceChipsMult,    // Plasma: average chips & mult before final score
                           int blindSizeMult,           // Plasma: blind requirements x this (default 1)
                           List<String> onBossDefeatTags) { // Anaglyph: tags granted after each Boss defeat

        public DeckType {
            resourceMods = resourceMods == null ? List.of() : List.copyOf(resourceMods);
            startingVouchers = startingVouchers == null ? List.of() : List.copyOf(startingVouchers);
            startingConsumables = startingConsumables == null ? List.of() : List.copyOf(startingConsumables);
            onBossDefeatTags = onBossDefeatTags == null ? List.of() : List.copyOf(onBossDefeatTags);
            if (blindSizeMult == 0) blindSizeMult = 1;
        }
        // Authored via the fluent Decks builder (Decks.of(...).build()); this canonical ctor is the only one.

        /** This deck's per-blind resource changes as {@link Modify}s — folded by Run with everyone else's. */
        public List<Modify> mods() {
            return resourceMods;
        }

        /** Does this deck change anything vs the plain 52-card base? (Base Deck is the one legit no-op.) */
        public boolean hasEffect() {
            return !resourceMods.isEmpty() || jokerSlotsDelta != 0 || greenEconomy
                    || composition != Composition.STANDARD || !startingVouchers.isEmpty()
                    || !startingConsumables.isEmpty() || spectralRate != 0 || balanceChipsMult
                    || blindSizeMult != 1 || !onBossDefeatTags.isEmpty();
        }
    }

    private static final Map<String, DeckType> BY_KEY = new LinkedHashMap<>();

    static {
        put(Decks.of("d_base", "Base Deck").desc("No modifiers").build());
        put(Decks.of("d_red", "Red Deck").desc("+1 discard each round").discards(1).build());
        put(Decks.of("d_blue", "Blue Deck").desc("+1 hand each round").hands(1).build());
        put(Decks.of("d_yellow", "Yellow Deck").desc("Start with an extra $10").money(10).build());
        put(Decks.of("d_black", "Black Deck").desc("+1 Joker slot, but -1 hand each round").jokerSlots(1).hands(-1).build());
        put(Decks.of("d_green", "Green Deck").desc("No interest; $2 per remaining hand, $1 per discard").greenEconomy().build());
        put(Decks.of("d_painted", "Painted Deck").desc("+2 hand size, -1 Joker slot").handSize(2).jokerSlots(-1).build());
        put(Decks.of("d_abandoned", "Abandoned Deck").desc("No face cards (no Jacks/Queens/Kings)").noFaces().build());
        put(Decks.of("d_checkered", "Checkered Deck").desc("26 Spades + 26 Hearts").checkered().build());
        put(Decks.of("d_erratic", "Erratic Deck").desc("All ranks and suits are randomized").erratic().build());
        // Starting-grant decks: the engine's shop-slot voucher key is "v_overstock" (b_zodiac names it v_overstock_norm).
        put(Decks.of("d_magic", "Magic Deck").desc("Start with the Crystal Ball voucher and 2 The Fool tarots")
                .startsWithVouchers("v_crystal_ball").startsWith("c_fool", "c_fool").build());
        put(Decks.of("d_nebula", "Nebula Deck").desc("Start with the Telescope voucher, -1 consumable slot")
                .startsWithVouchers("v_telescope").consumableSlots(-1).build());
        put(Decks.of("d_zodiac", "Zodiac Deck").desc("Start with Tarot Merchant, Planet Merchant, and Overstock vouchers")
                .startsWithVouchers("v_tarot_merchant", "v_planet_merchant", "v_overstock").build());
        put(Decks.of("d_ghost", "Ghost Deck").desc("Spectral cards may appear in the shop; start with The Hex")
                .spectralRate(2).startsWith("c_hex").build());
        put(Decks.of("d_anaglyph", "Anaglyph Deck").desc("Double Tag after defeating each Boss Blind")
                .tagAfterBoss("tag_double").build());
        put(Decks.of("d_plasma", "Plasma Deck").desc("Balance chips and mult; blinds are 2x larger")
                .balancesChipsAndMult().blindSizeMult(2).build());
    }

    private static void put(DeckType d) {
        BY_KEY.put(d.key(), d);
    }

    public static DeckType get(String key) {
        DeckType d = BY_KEY.get(key);
        return d != null ? d : BY_KEY.get("d_base");
    }

    /** Every deck key in the catalog — the surface the coverage net enumerates. */
    public static java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(BY_KEY.keySet());
    }
}
