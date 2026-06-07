package com.balatromp.engine.game;

import com.balatromp.engine.joker.JokerInfo;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.RandomStreams;
import java.util.ArrayList;
import java.util.List;

/**
 * A between-blinds shop. Minimal first cut: a few joker offerings priced by their
 * own metadata, plus reroll. Generated deterministically from a keyed RNG stream
 * (server-only seed), so the offered cards are reproducible and never
 * client-influenced.
 *
 * Iteration slots: rarity-weighted pools, consumable/voucher/booster slots,
 * scaling reroll cost.
 */
public final class Shop {

    public static final int REROLL_COST = 5;
    public static final int JOKER_SLOT_LIMIT = 5;

    /** A shop offering carries the joker's full display/shop metadata. */
    public record Item(JokerInfo info) {
        public String jokerKey() { return info.key(); }
        public String name() { return info.name(); }
        public int cost() { return info.cost(); }
    }

    private final List<Item> items;
    private final List<PlanetCatalog.Planet> planets;

    private Shop(List<Item> items, List<PlanetCatalog.Planet> planets) {
        this.items = items;
        this.planets = planets;
    }

    public List<Item> items() {
        return items;
    }

    public List<PlanetCatalog.Planet> planets() {
        return planets;
    }

    public static Shop generate(RandomStreams rng, String streamKey, int slots) {
        List<String> jokerKeys = new ArrayList<>(JokerLibrary.builtinKeys());
        var r = rng.stream(streamKey);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            String key = jokerKeys.get(r.nextInt(jokerKeys.size()));
            items.add(new Item(JokerLibrary.create(key).info()));
        }
        // One planet offering.
        List<String> planetKeys = PlanetCatalog.keys();
        var pr = rng.stream(streamKey + ":planet");
        List<PlanetCatalog.Planet> planets = new ArrayList<>();
        planets.add(PlanetCatalog.get(planetKeys.get(pr.nextInt(planetKeys.size()))));
        return new Shop(items, planets);
    }
}
