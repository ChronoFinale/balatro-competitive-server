package com.balatromp.engine.game;

import java.util.LinkedHashMap;
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

    public record DeckType(String key, String name, String description,
                           int handsDelta, int discardsDelta, int jokerSlotsDelta,
                           int startMoneyDelta, boolean greenEconomy) {}

    private static final Map<String, DeckType> BY_KEY = new LinkedHashMap<>();

    static {
        put(new DeckType("d_base", "Base Deck", "No modifiers", 0, 0, 0, 0, false));
        put(new DeckType("d_red", "Red Deck", "+1 discard each round", 0, 1, 0, 0, false));
        put(new DeckType("d_blue", "Blue Deck", "+1 hand each round", 1, 0, 0, 0, false));
        put(new DeckType("d_yellow", "Yellow Deck", "Start with an extra $10", 0, 0, 0, 10, false));
        put(new DeckType("d_black", "Black Deck", "+1 Joker slot, but -1 hand each round", -1, 0, 1, 0, false));
        put(new DeckType("d_green", "Green Deck", "No interest; $2 per remaining hand, $1 per discard",
                0, 0, 0, 0, true));
    }

    private static void put(DeckType d) {
        BY_KEY.put(d.key(), d);
    }

    public static DeckType get(String key) {
        DeckType d = BY_KEY.get(key);
        return d != null ? d : BY_KEY.get("d_base");
    }
}
