package com.balatromp.engine.game;

import com.balatromp.engine.consumable.Consumable;
import com.balatromp.engine.consumable.TarotCatalog;
import com.balatromp.engine.joker.JokerInfo;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.GameQueue;
import com.balatromp.engine.rng.QueueSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public static final int CONSUMABLE_COST = 3;

    /** A shop offering carries the joker's full display/shop metadata. */
    public record Item(JokerInfo info) {
        public String jokerKey() { return info.key(); }
        public String name() { return info.name(); }
        public int cost() { return info.cost(); }
    }

    private final List<Item> items;
    private final List<PlanetCatalog.Planet> planets;
    private final List<Consumable> consumables;
    private String voucher; // the offered voucher key this shop, or null (one per shop)

    private Shop(List<Item> items, List<PlanetCatalog.Planet> planets, List<Consumable> consumables,
            String voucher) {
        this.items = items;
        this.planets = planets;
        this.consumables = consumables;
        this.voucher = voucher;
    }

    public String voucher() {
        return voucher;
    }

    public void clearVoucher() {
        voucher = null;
    }

    public List<Item> items() {
        return items;
    }

    public List<PlanetCatalog.Planet> planets() {
        return planets;
    }

    public List<Consumable> consumables() {
        return consumables;
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
        return generate(queues, slots, pool, Set.of(), false);
    }

    /**
     * As {@link #generate(QueueSet, int, List)}, but the joker queue skips any key in
     * {@code owned} (and already offered this shop) — the BMP "you already have it, skip
     * over it" rule — unless {@code showman} is set, which allows duplicate offerings.
     */
    public static Shop generate(QueueSet queues, int slots, List<String> pool,
            Set<String> owned, boolean showman) {
        List<String> jokerKeys = pool.isEmpty() ? JokerLibrary.builtinKeys() : pool;
        GameQueue<String> jokerQ = queues.queue("jokers", r -> jokerKeys.get(r.nextInt(jokerKeys.size())));
        List<Item> items = new ArrayList<>();
        Set<String> offered = new HashSet<>();
        for (int i = 0; i < slots; i++) {
            // Skip owned / already-offered keys, but only while an acceptable key remains
            // (else nextWhere would loop forever). Showman disables skipping entirely.
            boolean canSkip = !showman && jokerKeys.stream()
                    .anyMatch(k -> !owned.contains(k) && !offered.contains(k));
            String key = canSkip
                    ? jokerQ.nextWhere(k -> !owned.contains(k) && !offered.contains(k))
                    : jokerQ.next();
            offered.add(key);
            items.add(new Item(JokerLibrary.create(key).info()));
        }
        List<String> planetKeys = PlanetCatalog.keys();
        GameQueue<String> planetQ = queues.queue("planets", r -> planetKeys.get(r.nextInt(planetKeys.size())));
        List<PlanetCatalog.Planet> planets = new ArrayList<>();
        planets.add(PlanetCatalog.get(planetQ.next()));

        // One Tarot offering, from its own game-long queue.
        List<String> tarotKeys = TarotCatalog.tarotKeys();
        GameQueue<String> tarotQ = queues.queue("tarot", r -> tarotKeys.get(r.nextInt(tarotKeys.size())));
        List<Consumable> consumables = new ArrayList<>();
        consumables.add(TarotCatalog.get(tarotQ.next()));

        // One voucher offering, skipping any already owned (none acceptable -> no voucher).
        List<String> voucherKeys = VoucherCatalog.keys();
        String voucher = null;
        if (voucherKeys.stream().anyMatch(k -> !owned.contains(k))) {
            GameQueue<String> vQ = queues.queue("vouchers", r -> voucherKeys.get(r.nextInt(voucherKeys.size())));
            voucher = vQ.nextWhere(k -> !owned.contains(k));
        }

        return new Shop(items, planets, consumables, voucher);
    }
}
