package com.balatromp.engine.game;

import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.RandomStreams;
import java.util.ArrayList;
import java.util.List;

/**
 * A between-blinds shop. Minimal first cut: a few joker offerings at a flat cost,
 * plus reroll. Generated deterministically from a keyed RNG stream (server-only
 * seed), so the offered cards are reproducible and never client-influenced.
 *
 * Iteration slots: rarity-based cost/weighting, consumable/voucher/booster slots,
 * scaling reroll cost.
 */
public final class Shop {

    public static final int JOKER_COST = 4;
    public static final int REROLL_COST = 5;
    public static final int JOKER_SLOT_LIMIT = 5;

    public record Item(String jokerKey, String name, int cost) {}

    private final List<Item> items;

    private Shop(List<Item> items) {
        this.items = items;
    }

    public List<Item> items() {
        return items;
    }

    public static Shop generate(RandomStreams rng, String streamKey, int slots) {
        List<String> keys = new ArrayList<>(JokerLibrary.registry().keySet());
        var r = rng.stream(streamKey);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            String key = keys.get(r.nextInt(keys.size()));
            items.add(new Item(key, JokerLibrary.create(key).name(), JOKER_COST));
        }
        return new Shop(items);
    }
}
