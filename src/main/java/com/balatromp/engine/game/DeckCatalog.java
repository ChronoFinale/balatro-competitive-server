package com.balatromp.engine.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deck variants — the starting configuration a run begins with (Balatro's deck
 * select). A deck shifts starting money, joker slots, and per-blind hands/discards;
 * a few have special economy (Green). The base deck has no modifiers. The match's
 * {@link com.balatromp.engine.state.Ruleset} names the deck; {@code Run} applies it.
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
                           int handsDelta, int discardsDelta, int jokerSlotsDelta,
                           int startMoneyDelta, int handSizeDelta, boolean greenEconomy,
                           Composition composition, int consumableSlotDelta,
                           List<String> startingVouchers, List<String> startingConsumables) {

        public DeckType {
            startingVouchers = startingVouchers == null ? List.of() : List.copyOf(startingVouchers);
            startingConsumables = startingConsumables == null ? List.of() : List.copyOf(startingConsumables);
        }

        /** Backward-compatible: a stat-only deck on the standard composition. */
        public DeckType(String key, String name, String description, int handsDelta, int discardsDelta,
                        int jokerSlotsDelta, int startMoneyDelta, boolean greenEconomy) {
            this(key, name, description, handsDelta, discardsDelta, jokerSlotsDelta, startMoneyDelta,
                    0, greenEconomy, Composition.STANDARD, 0, List.of(), List.of());
        }

        /** A composition / hand-size deck with no starting grants. */
        public DeckType(String key, String name, String description, int handsDelta, int discardsDelta,
                        int jokerSlotsDelta, int startMoneyDelta, int handSizeDelta, boolean greenEconomy,
                        Composition composition) {
            this(key, name, description, handsDelta, discardsDelta, jokerSlotsDelta, startMoneyDelta,
                    handSizeDelta, greenEconomy, composition, 0, List.of(), List.of());
        }
    }

    private static final Map<String, DeckType> BY_KEY = new LinkedHashMap<>();

    static {
        put(new DeckType("d_base", "Base Deck", "No modifiers", 0, 0, 0, 0, false));
        put(new DeckType("d_red", "Red Deck", "+1 discard each round", 0, 1, 0, 0, false));
        put(new DeckType("d_blue", "Blue Deck", "+1 hand each round", 1, 0, 0, 0, false));
        put(new DeckType("d_yellow", "Yellow Deck", "Start with an extra $10", 0, 0, 0, 10, false));
        put(new DeckType("d_black", "Black Deck", "+1 Joker slot, but -1 hand each round", -1, 0, 1, 0, false));
        put(new DeckType("d_green", "Green Deck", "No interest; $2 per remaining hand, $1 per discard",
                0, 0, 0, 0, true));
        // Composition / hand-size decks (game.lua:636-642). Stat-only fields per the b_* configs.
        put(new DeckType("d_painted", "Painted Deck", "+2 hand size, -1 Joker slot",
                0, 0, -1, 0, 2, false, Composition.STANDARD));
        put(new DeckType("d_abandoned", "Abandoned Deck", "No face cards (no Jacks/Queens/Kings)",
                0, 0, 0, 0, 0, false, Composition.NO_FACES));
        put(new DeckType("d_checkered", "Checkered Deck", "26 Spades + 26 Hearts",
                0, 0, 0, 0, 0, false, Composition.CHECKERED));
        put(new DeckType("d_erratic", "Erratic Deck", "All ranks and suits are randomized",
                0, 0, 0, 0, 0, false, Composition.ERRATIC));
        // Starting-grant decks (game.lua:633-638): begin with vouchers/consumables. Note the engine's
        // shop-slot voucher key is "v_overstock" (Balatro's b_zodiac names it v_overstock_norm).
        put(new DeckType("d_magic", "Magic Deck", "Start with the Crystal Ball voucher and 2 The Fool tarots",
                0, 0, 0, 0, 0, false, Composition.STANDARD, 0,
                List.of("v_crystal_ball"), List.of("c_fool", "c_fool")));
        put(new DeckType("d_nebula", "Nebula Deck", "Start with the Telescope voucher, -1 consumable slot",
                0, 0, 0, 0, 0, false, Composition.STANDARD, -1,
                List.of("v_telescope"), List.of()));
        put(new DeckType("d_zodiac", "Zodiac Deck",
                "Start with Tarot Merchant, Planet Merchant, and Overstock vouchers",
                0, 0, 0, 0, 0, false, Composition.STANDARD, 0,
                List.of("v_tarot_merchant", "v_planet_merchant", "v_overstock"), List.of()));
        // Special-behavior decks keyed by name in Run/ScoringEngine (game.lua:640-641, back.lua).
        put(new DeckType("d_anaglyph", "Anaglyph Deck", "Double Tag after defeating each Boss Blind",
                0, 0, 0, 0, false));
        put(new DeckType("d_plasma", "Plasma Deck", "Balance chips and mult; blinds are 2x larger",
                0, 0, 0, 0, false));
    }

    private static void put(DeckType d) {
        BY_KEY.put(d.key(), d);
    }

    public static DeckType get(String key) {
        DeckType d = BY_KEY.get(key);
        return d != null ? d : BY_KEY.get("d_base");
    }
}
