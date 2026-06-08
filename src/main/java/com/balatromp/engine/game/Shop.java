package com.balatromp.engine.game;

import com.balatromp.engine.joker.JokerInfo;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.GameQueue;
import com.balatromp.engine.rng.QueueSet;
import java.util.ArrayList;
import java.util.List;

/**
 * A between-blinds shop. Offerings are drawn from the run's game-long
 * {@link QueueSet} (BMP-style determinism): one persistent joker queue and one
 * planet queue per run, advanced as the shop reveals/rerolls. Both players on the
 * same seed walk the same sequence, each at their own cursor — so a reroll just
 * reveals the next items, never a fresh independent roll. Offered content is
 * reproducible and never client-influenced.
 *
 * Iteration slots: rarity-split joker sub-queues, consumable/voucher/booster
 * slots, the main-queue-of-tags topology, scaling reroll cost.
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

    /**
     * Reveal {@code slots} jokers + one planet by advancing the run's game-long
     * queues. The joker queue draws from {@code pool} (the active ruleset's
     * {@code jokerPool} — so a custom ruleset that names custom jokers offers
     * exactly those); the planet queue draws from {@link PlanetCatalog}. Each
     * call advances the cursors, so a new shop or a reroll simply continues the
     * same sequence — matching BMP's shared-queue behavior.
     */
    public static Shop generate(QueueSet queues, int slots, List<String> pool) {
        List<String> jokerKeys = pool.isEmpty() ? JokerLibrary.builtinKeys() : pool;
        GameQueue<String> jokerQ = queues.queue("jokers", r -> jokerKeys.get(r.nextInt(jokerKeys.size())));
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            items.add(new Item(JokerLibrary.create(jokerQ.next()).info()));
        }
        List<String> planetKeys = PlanetCatalog.keys();
        GameQueue<String> planetQ = queues.queue("planets", r -> planetKeys.get(r.nextInt(planetKeys.size())));
        List<PlanetCatalog.Planet> planets = new ArrayList<>();
        planets.add(PlanetCatalog.get(planetQ.next()));
        return new Shop(items, planets);
    }
}
