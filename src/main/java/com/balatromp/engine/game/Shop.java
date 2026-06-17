package com.balatromp.engine.game;

import com.balatromp.engine.card.Edition;
import com.balatromp.engine.consumable.Consumable;
import com.balatromp.engine.consumable.TarotCatalog;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.JokerInfo;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.GameQueue;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RngSources;
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
                       int cost, String rarity, Edition edition,
                       boolean eternal, boolean perishable, boolean rental) {

        /** Backward-compatible: an item with no stake stickers. */
        public Item(Kind kind, String key, String name, String description,
                    int cost, String rarity, Edition edition) {
            this(kind, key, name, description, cost, rarity, edition, false, false, false);
        }

        public static Item joker(JokerInfo info, Edition edition) {
            return new Item(Kind.JOKER, info.key(), info.name(), info.description(),
                    info.cost(), info.rarity(), edition);
        }

        /** Copy this item with stake stickers applied (rental jokers cost $1). */
        public Item withStickers(boolean eternal, boolean perishable, boolean rental) {
            return new Item(kind, key, name, description, rental ? 1 : cost, rarity, edition,
                    eternal, perishable, rental);
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
    // Offered vouchers this shop. Normally the single per-ante voucher; a Voucher Tag adds an
    // extra slot (vanilla: card_limit + 1) drawn from the same voucher queue.
    private final List<String> vouchers;

    private Shop(List<Item> items, List<String> vouchers) {
        this.items = items;
        this.vouchers = vouchers;
    }

    public List<Item> items() {
        return items;
    }

    /** The vouchers offered this shop (0..n). */
    public List<String> vouchers() {
        return vouchers;
    }

    /** Convenience for the common single-voucher case: the first offered voucher, or null. */
    public String voucher() {
        return vouchers.isEmpty() ? null : vouchers.get(0);
    }

    /** Add an extra offered voucher (Voucher Tag). */
    public void addVoucher(String key) {
        if (key != null && !vouchers.contains(key)) vouchers.add(key);
    }

    /** Remove a voucher (bought). */
    public void removeVoucher(String key) {
        vouchers.remove(key);
    }

    public static Shop generate(QueueSet queues, int slots, List<String> pool) {
        return generate(queues, slots, pool, Set.of(), false);
    }

    public static Shop generate(QueueSet queues, int slots, List<String> pool,
            Set<String> owned, boolean showman) {
        return generate(queues, slots, pool, owned, showman, 1.0, 1.0, null, null);
    }

    /**
     * Fill {@code slots} mixed main slots. Each slot's type comes from the master
     * "shop_slot" queue (jokers most common, then tarots, then planets); the chosen
     * type's sub-queue supplies the item. {@code editionMult}/{@code polyMult} are the
     * Hone/Glow-Up edition odds. {@code voucher} is the run's per-ante voucher (decided
     * once per ante by {@link Run}, not re-rolled each shop), or null if none/bought.
     */
    public static Shop generate(QueueSet queues, int slots, List<String> pool,
            Set<String> owned, boolean showman, double editionMult, double polyMult, String voucher,
            Set<HandType> playedHands) {
        GameQueue<Edition> editionQ = queues.queue(RngSources.JOKER_EDITION,
                r -> rollEdition(r.nextDouble(), editionMult, polyMult));
        List<String> planetKeys = PlanetCatalog.keys();
        GameQueue<String> planetQ = queues.queue(RngSources.PLANETS, r -> planetKeys.get(r.nextInt(planetKeys.size())));
        List<String> tarotKeys = TarotCatalog.tarotKeys();
        GameQueue<String> tarotQ = queues.queue(RngSources.TAROT, r -> tarotKeys.get(r.nextInt(tarotKeys.size())));
        // Master queue: the TYPE of each main slot (vanilla weights Joker 20 / Tarot 4 / Planet 4).
        GameQueue<Kind> slotQ = queues.queue(RngSources.SHOP_SLOT, r -> rollSlotType(r.nextDouble()));

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
                    // Skip softlocked secret-hand planets (Planet X/Ceres/Eris) until their hand is played.
                    String pk = (playedHands == null) ? planetQ.next()
                            : planetQ.nextWhere(k -> PlanetCatalog.available(k, playedHands));
                    PlanetCatalog.Planet p = PlanetCatalog.get(pk);
                    items.add(new Item(Kind.PLANET, p.key(), p.name(), p.description(),
                            PlanetCatalog.COST, null, Edition.NONE));
                }
            }
        }

        List<String> vouchers = new ArrayList<>();
        if (voucher != null) vouchers.add(voucher); // per-ante voucher; a Voucher Tag may add more
        return new Shop(items, vouchers);
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
        String rarity = queues.queue(RngSources.JOKER_RARITY, r -> rollRarity(r.nextDouble())).next();
        List<String> byR = byRarity(all, rarity);
        final List<String> draw = byR.isEmpty() ? all : byR; // custom pool may lack this rarity
        GameQueue<String> q = queues.queue(RngSources.jokerPool(rarity),
                r -> draw.get(r.nextInt(draw.size())));
        boolean anyAcceptable = draw.stream().anyMatch(k -> !owned.contains(k) && !offered.contains(k));
        // Showman allows duplicates; otherwise skip owned/offered. If the whole rarity is exhausted,
        // BMP's culled pool is empty and falls back to j_joker (get_current_pool, common_events.lua:2044).
        String key;
        if (showman) {
            key = q.next();
        } else if (anyAcceptable) {
            key = q.nextWhere(k -> !owned.contains(k) && !offered.contains(k));
        } else {
            key = "j_joker";
        }
        offered.add(key);
        return key;
    }
}
