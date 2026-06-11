package com.balatromp.engine.game;

import com.balatromp.engine.card.Edition;
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
 * A between-blinds shop, modelled on Balatro's real shape: the main area is a set
 * of <b>mixed</b> card slots — each independently a Joker, Tarot, or Planet drawn
 * from the master queue — plus one Voucher. (Booster packs are a follow-up.)
 *
 * Offerings come from the run's game-long {@link QueueSet} (BMP-style determinism):
 * a master "shop_slot" queue decides each slot's TYPE, then the next item of that
 * type is pulled from its own sub-queue (jokers/tarot/planets, jokers skipping
 * owned). Both players on a seed walk identical sequences; a reroll just reveals
 * the next slots. Content is reproducible and never client-influenced.
 */
public final class Shop {

    public static final int REROLL_COST = 5;
    public static final int JOKER_SLOT_LIMIT = 5;
    public static final int CONSUMABLE_COST = 3;

    /** What a shop slot offers. (Spectrals come from packs/Ghost Deck, not the main shop.) */
    public enum Kind { JOKER, TAROT, PLANET }

    /** A single shop slot — any {@link Kind}, with the display fields the client renders. */
    public record Item(Kind kind, String key, String name, String description,
                       int cost, String rarity, Edition edition) {
        public static Item joker(JokerInfo info, Edition edition) {
            return new Item(Kind.JOKER, info.key(), info.name(), info.description(),
                    info.cost(), info.rarity(), edition);
        }
    }

    /**
     * Roll a shop joker's edition from a uniform draw, using the base appearance
     * rates (Foil 2%, Holo 1.4%, Poly 0.3%, Negative 0.3%). {@code mult} scales
     * Foil/Holo (Hone 2×, Glow Up 4×) and {@code polyMult} scales Poly (3×/7×);
     * Negative is never voucher-scaled.
     */
    public static Edition rollEdition(double roll, double mult, double polyMult) {
        double foil = 0.02 * mult, holo = 0.014 * mult, poly = 0.003 * polyMult, neg = 0.003;
        if (roll < foil) return Edition.FOIL;
        if (roll < foil + holo) return Edition.HOLOGRAPHIC;
        if (roll < foil + holo + poly) return Edition.POLYCHROME;
        if (roll < foil + holo + poly + neg) return Edition.NEGATIVE;
        return Edition.NONE;
    }

    private final List<Item> items;
    private String voucher; // the offered voucher key this shop, or null (one per shop)

    private Shop(List<Item> items, String voucher) {
        this.items = items;
        this.voucher = voucher;
    }

    public List<Item> items() {
        return items;
    }

    public String voucher() {
        return voucher;
    }

    public void clearVoucher() {
        voucher = null;
    }

    public static Shop generate(QueueSet queues, int slots, List<String> pool) {
        return generate(queues, slots, pool, Set.of(), false);
    }

    public static Shop generate(QueueSet queues, int slots, List<String> pool,
            Set<String> owned, boolean showman) {
        return generate(queues, slots, pool, owned, showman, 1.0, 1.0, null);
    }

    /**
     * Fill {@code slots} mixed main slots. Each slot's type comes from the master
     * "shop_slot" queue (jokers most common, then tarots, then planets); the chosen
     * type's sub-queue supplies the item. {@code editionMult}/{@code polyMult} are the
     * Hone/Glow-Up edition odds. {@code voucher} is the run's per-ante voucher (decided
     * once per ante by {@link Run}, not re-rolled each shop), or null if none/bought.
     */
    public static Shop generate(QueueSet queues, int slots, List<String> pool,
            Set<String> owned, boolean showman, double editionMult, double polyMult, String voucher) {
        GameQueue<Edition> editionQ = queues.queue("joker_edition",
                r -> rollEdition(r.nextDouble(), editionMult, polyMult));
        List<String> planetKeys = PlanetCatalog.keys();
        GameQueue<String> planetQ = queues.queue("planets", r -> planetKeys.get(r.nextInt(planetKeys.size())));
        List<String> tarotKeys = TarotCatalog.tarotKeys();
        GameQueue<String> tarotQ = queues.queue("tarot", r -> tarotKeys.get(r.nextInt(tarotKeys.size())));
        // Master queue: the TYPE of each main slot (vanilla weights Joker 20 / Tarot 4 / Planet 4).
        GameQueue<Kind> slotQ = queues.queue("shop_slot", r -> rollSlotType(r.nextDouble()));

        List<Item> items = new ArrayList<>();
        Set<String> offeredJokers = new HashSet<>();
        for (int i = 0; i < slots; i++) {
            switch (slotQ.next()) {
                case JOKER -> {
                    String key = drawJoker(queues, pool, owned, offeredJokers, showman);
                    items.add(Item.joker(JokerLibrary.create(key).info(), editionQ.next()));
                }
                case TAROT -> {
                    Consumable c = TarotCatalog.get(tarotQ.next());
                    items.add(new Item(Kind.TAROT, c.key(), c.name(), c.description(),
                            CONSUMABLE_COST, null, Edition.NONE));
                }
                case PLANET -> {
                    PlanetCatalog.Planet p = PlanetCatalog.get(planetQ.next());
                    items.add(new Item(Kind.PLANET, p.key(), p.name(), p.description(),
                            PlanetCatalog.COST, null, Edition.NONE));
                }
            }
        }

        return new Shop(items, voucher); // voucher decided per-ante by the Run, passed in
    }

    /** The type of a main shop slot, vanilla weights: Joker 20 / Tarot 4 / Planet 4 (of 28). */
    public static Kind rollSlotType(double x) {
        if (x < 20.0 / 28.0) return Kind.JOKER;
        if (x < 24.0 / 28.0) return Kind.TAROT;
        return Kind.PLANET;
    }

    /** A joker's rarity, vanilla shop weights: Common 70% / Uncommon 25% / Rare 5% (no Legendary). */
    public static String rollRarity(double x) {
        if (x < 0.70) return "Common";
        if (x < 0.95) return "Uncommon";
        return "Rare";
    }

    private static List<String> byRarity(List<String> pool, String rarity) {
        return pool.stream().filter(k -> rarity.equals(JokerLibrary.create(k).info().rarity())).toList();
    }

    /**
     * Draw the next shop joker: roll a rarity (Common/Uncommon/Rare) from the shared
     * rarity queue, then pull from that rarity's own sub-queue, skipping owned/already-
     * offered keys (unless Showman). Used by both the shop and Buffoon packs, so they
     * share the rarity sub-queues exactly as BMP specifies. Falls back to the full pool
     * when a custom pool lacks a rolled rarity.
     */
    public static String drawJoker(QueueSet queues, List<String> pool,
            Set<String> owned, Set<String> offered, boolean showman) {
        List<String> all = pool.isEmpty() ? JokerLibrary.builtinKeys() : pool;
        String rarity = queues.queue("joker_rarity", r -> rollRarity(r.nextDouble())).next();
        List<String> byR = byRarity(all, rarity);
        final List<String> draw = byR.isEmpty() ? all : byR; // custom pool may lack this rarity
        GameQueue<String> q = queues.queue("joker_" + rarity.toLowerCase(),
                r -> draw.get(r.nextInt(draw.size())));
        boolean canSkip = !showman && draw.stream().anyMatch(k -> !owned.contains(k) && !offered.contains(k));
        String key = canSkip
                ? q.nextWhere(k -> !owned.contains(k) && !offered.contains(k))
                : q.next();
        offered.add(key);
        return key;
    }
}
