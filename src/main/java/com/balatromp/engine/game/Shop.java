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
        return generate(queues, slots, pool, owned, showman, 1.0, 1.0);
    }

    /**
     * Fill {@code slots} mixed main slots + one voucher. Each slot's type comes from
     * the master "shop_slot" queue (jokers most common, then tarots, then planets);
     * the chosen type's sub-queue supplies the item. {@code editionMult}/{@code polyMult}
     * are the Hone/Glow-Up edition odds.
     */
    public static Shop generate(QueueSet queues, int slots, List<String> pool,
            Set<String> owned, boolean showman, double editionMult, double polyMult) {
        List<String> jokerKeys = pool.isEmpty() ? JokerLibrary.builtinKeys() : pool;
        GameQueue<String> jokerQ = queues.queue("jokers", r -> jokerKeys.get(r.nextInt(jokerKeys.size())));
        GameQueue<Edition> editionQ = queues.queue("joker_edition",
                r -> rollEdition(r.nextDouble(), editionMult, polyMult));
        List<String> planetKeys = PlanetCatalog.keys();
        GameQueue<String> planetQ = queues.queue("planets", r -> planetKeys.get(r.nextInt(planetKeys.size())));
        List<String> tarotKeys = TarotCatalog.tarotKeys();
        GameQueue<String> tarotQ = queues.queue("tarot", r -> tarotKeys.get(r.nextInt(tarotKeys.size())));
        // Master queue: the TYPE of each main slot. Jokers dominate, with occasional
        // tarots/planets — so a shop is a mix, not a fixed joker row + planet + tarot.
        GameQueue<Kind> slotQ = queues.queue("shop_slot", r -> {
            double x = r.nextDouble();
            return x < 0.70 ? Kind.JOKER : (x < 0.86 ? Kind.TAROT : Kind.PLANET);
        });

        List<Item> items = new ArrayList<>();
        Set<String> offeredJokers = new HashSet<>();
        for (int i = 0; i < slots; i++) {
            switch (slotQ.next()) {
                case JOKER -> {
                    boolean canSkip = !showman && jokerKeys.stream()
                            .anyMatch(k -> !owned.contains(k) && !offeredJokers.contains(k));
                    String key = canSkip
                            ? jokerQ.nextWhere(k -> !owned.contains(k) && !offeredJokers.contains(k))
                            : jokerQ.next();
                    offeredJokers.add(key);
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

        // One voucher offering, skipping any already owned (none acceptable -> no voucher).
        List<String> voucherKeys = VoucherCatalog.keys();
        String voucher = null;
        if (voucherKeys.stream().anyMatch(k -> !owned.contains(k))) {
            GameQueue<String> vQ = queues.queue("vouchers", r -> voucherKeys.get(r.nextInt(voucherKeys.size())));
            voucher = vQ.nextWhere(k -> !owned.contains(k));
        }

        return new Shop(items, voucher);
    }
}
