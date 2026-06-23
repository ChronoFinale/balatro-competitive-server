package com.balatro.engine.consumable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A starter set of Tarot/Spectral consumables, as data — exercising the
 * consumable effect model: enhance selected cards, destroy selected cards, and
 * create new cards. Enhance/Destroy target cards the player selects (by id);
 * Create adds to the deck. (Rank/suit-converting Tarots — Strength, the suit
 * Tarots — land once Card rank/suit are mutable, a small follow-up.)
 */
public final class TarotCatalog {

    private TarotCatalog() {}

    private static final Map<String, Consumable> BY_KEY = new LinkedHashMap<>();

    static {
        // Runtime loads from /content/consumables.json; content authored in com.balatro.content.ConsumableDefs.
        List<Consumable> cs;
        try {
            cs = com.balatro.engine.content.ContentStore.consumables();
        } catch (RuntimeException e) {
            cs = com.balatro.content.ConsumableDefs.authored();
        }
        for (Consumable c : cs) BY_KEY.put(c.key(), c);
    }



    /** The DSL authoring source for {@code content/consumables.json} (also the fallback). */

    public static Consumable get(String key) {
        return BY_KEY.get(key);
    }

    /** Every Tarot/Spectral key in the catalog — the surface the coverage net enumerates. */
    public static java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(BY_KEY.keySet());
    }

    /** Tarot keys eligible to appear in the main shop (Spectrals come from packs). */
    public static java.util.List<String> tarotKeys() {
        return BY_KEY.values().stream()
                .filter(c -> c.type() == ConsumableType.TAROT)
                .map(Consumable::key)
                .toList();
    }

    /**
     * Spectral keys eligible for a normal draw (packs / Spectral-creating jokers). The Soul and Black
     * Hole are EXCLUDED — exactly as BMP's get_current_pool does (common_events.lua:2022-2024) — because
     * they only surface via the dedicated soul roll, never as ordinary pool content.
     */
    public static java.util.List<String> spectralKeys() {
        return BY_KEY.values().stream()
                .filter(c -> c.type() == ConsumableType.SPECTRAL)
                .filter(c -> !c.key().equals("c_the_soul") && !c.key().equals("c_black_hole"))
                .map(Consumable::key)
                .toList();
    }
}
