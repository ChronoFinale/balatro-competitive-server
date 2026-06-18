package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.JokerInfo;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.GameQueue;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RngSources;
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
    public enum Kind { JOKER, TAROT, PLANET, SPECTRAL, PLAYING_CARD }

    /** A single shop slot — any {@link Kind}, with the display fields the client renders. {@code card}
     *  is set only for PLAYING_CARD slots (Magic Trick / Illusion), carrying the actual card to buy. */
    public record Item(Kind kind, String key, String name, String description,
                       int cost, String rarity, Edition edition,
                       boolean eternal, boolean perishable, boolean rental, Card card) {

        /** Backward-compatible: an item with no stake stickers and no playing card. */
        public Item(Kind kind, String key, String name, String description,
                    int cost, String rarity, Edition edition) {
            this(kind, key, name, description, cost, rarity, edition, false, false, false, null);
        }

        public static Item joker(JokerInfo info, Edition edition) {
            return new Item(Kind.JOKER, info.key(), info.name(), info.description(),
                    info.cost(), info.rarity(), edition);
        }

        /** Magic Trick / Illusion: a playing card for sale (Illusion may give it an Enhancement/Edition). */
        public static Item playingCard(Card card, int cost) {
            return new Item(Kind.PLAYING_CARD, card.toString(), card.toString(), "Playing card",
                    cost, null, card.edition, false, false, false, card);
        }

        /** Copy this item with stake stickers applied (rental jokers cost $1). */
        public Item withStickers(boolean eternal, boolean perishable, boolean rental) {
            return new Item(kind, key, name, description, rental ? 1 : cost, rarity, edition,
                    eternal, perishable, rental, card);
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
        return generate(queues, slots, pool, owned, showman, 1.0, 1.0, null, null, 0, 4, 4, 0, false);
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
            Set<HandType> playedHands, int spectralRate, int tarotWeight, int planetWeight,
            int playingCardWeight, boolean cardsEnhanced) {
        GameQueue<Edition> editionQ = queues.queue(RngSources.JOKER_EDITION,
                r -> rollEdition(r.nextDouble(), editionMult, polyMult));
        List<String> planetKeys = PlanetCatalog.keys();
        GameQueue<String> planetQ = queues.queue(RngSources.PLANETS, r -> planetKeys.get(r.nextInt(planetKeys.size())));
        List<String> tarotKeys = TarotCatalog.tarotKeys();
        GameQueue<String> tarotQ = queues.queue(RngSources.TAROT, r -> tarotKeys.get(r.nextInt(tarotKeys.size())));
        List<String> spectralKeys = TarotCatalog.spectralKeys();
        GameQueue<String> spectralQ = queues.queue(RngSources.SOUL, r -> spectralKeys.get(r.nextInt(spectralKeys.size())));
        // Master queue: the TYPE of each main slot (vanilla weights Joker 20 / Tarot 4 / Planet 4 / Spectral N).
        GameQueue<Kind> slotQ = queues.queue(RngSources.SHOP_SLOT,
                r -> rollSlotType(r.nextDouble(), spectralRate, tarotWeight, planetWeight, playingCardWeight));
        java.util.List<com.balatro.engine.card.Rank> ranks = java.util.List.of(com.balatro.engine.card.Rank.values());
        java.util.List<com.balatro.engine.card.Suit> suits = java.util.List.of(com.balatro.engine.card.Suit.values());
        GameQueue<Card> cardQ = queues.queue(RngSources.PACK_CARD, r -> {
            Card c = new Card(ranks.get(r.nextInt(ranks.size())), suits.get(r.nextInt(suits.size())));
            if (cardsEnhanced) c.edition = rollEdition(r.nextDouble(), editionMult, polyMult); // Illusion
            return c;
        });

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
                case SPECTRAL -> { // Ghost Deck: Spectral cards in the main shop.
                    Consumable c = TarotCatalog.get(spectralQ.next());
                    items.add(new Item(Kind.SPECTRAL, c.key(), c.name(), c.description(),
                            CONSUMABLE_COST, null, Edition.NONE));
                }
                case PLAYING_CARD -> items.add(Item.playingCard(cardQ.next(), CONSUMABLE_COST)); // Magic Trick / Illusion
            }
        }

        List<String> vouchers = new ArrayList<>();
        if (voucher != null) vouchers.add(voucher); // per-ante voucher; a Voucher Tag may add more
        return new Shop(items, vouchers);
    }

    /** The type of a main shop slot. Vanilla weights Joker 20 / Tarot 4 / Planet 4, plus a Spectral
     *  weight {@code spectralRate} (0 normally, 2 on the Ghost Deck). Tarot/Planet weights are raised
     *  by the Merchant/Tycoon vouchers (4 -> 8 = 2x, 16 = 4x). */
    public static Kind rollSlotType(double x, int spectralRate, int tarotWeight, int planetWeight,
            int playingCardWeight) {
        double total = 20.0 + tarotWeight + planetWeight + spectralRate + playingCardWeight;
        if (x < 20.0 / total) return Kind.JOKER;
        if (x < (20.0 + tarotWeight) / total) return Kind.TAROT;
        if (x < (20.0 + tarotWeight + planetWeight) / total) return Kind.PLANET;
        if (x < (20.0 + tarotWeight + planetWeight + spectralRate) / total) return Kind.SPECTRAL;
        return Kind.PLAYING_CARD; // Magic Trick / Illusion
    }

    public static Kind rollSlotType(double x, int spectralRate, int tarotWeight, int planetWeight) {
        return rollSlotType(x, spectralRate, tarotWeight, planetWeight, 0);
    }

    /** Slot roll at the base Tarot/Planet weights (4 each). */
    public static Kind rollSlotType(double x, int spectralRate) {
        return rollSlotType(x, spectralRate, 4, 4, 0);
    }

    /** No-spectral slot roll at base weights (the default deck). */
    public static Kind rollSlotType(double x) {
        return rollSlotType(x, 0, 4, 4, 0);
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
