package com.balatromp.engine.rng;

/**
 * The catalog of every game-randomness source, declared once. Each constant says
 * <i>what kind</i> of randomness a mechanic draws — its scope, whether it has a
 * per-hand PvP variant, and how it selects — so the rules live here as data
 * instead of as hand-built key strings sprinkled through the engine. Adding a
 * mechanic with a quirky RNG requirement is a one-line declaration; the engine
 * needs no new branch.
 *
 * <p>Sources are grouped by the property that matters:
 * <ul>
 *   <li><b>PvP-per-hand</b> — scoring/probability that must be fair hand-for-hand
 *       in a Nemesis blind (the Run rewinds the "pvp:" queues each hand).</li>
 *   <li><b>Composition</b> — the deck shuffle and "random joker/card" picks,
 *       chosen by identity so equal states behave identically (variance reduction).</li>
 *   <li><b>Plain game-long</b> — shop pools, packs, vouchers, tags: one shared
 *       sequence per game, drawn sequentially.</li>
 * </ul>
 */
public final class RngSources {

    private RngSources() {}

    // --- PvP-per-hand probability / scoring -----------------------------------
    /** Lucky card: 1-in-5 for +20 Mult. */
    public static final RngSource LUCKY_MULT = RngSource.of("lucky_mult").pvpPerHand();
    /** Lucky card: 1-in-15 for +$20. */
    public static final RngSource LUCKY_MONEY = RngSource.of("lucky_money").pvpPerHand();
    /** Glass card: 1-in-4 break after scoring. */
    public static final RngSource GLASS = RngSource.of("glass").pvpPerHand();
    /** Base for every Chance joker (Bloodstone, Business Card, …): {@code PROB.sub(seedKey)}. */
    public static final RngSource PROB = RngSource.of("prob").pvpPerHand();

    // --- Composition (identity-ordered) ---------------------------------------
    /** The deck deal: re-shuffled each blind, composition-ordered for low variance. */
    public static final RngSource DEAL = RngSource.of("deal").perBlind().composition();
    /** Madness: pick a random other joker to destroy. */
    public static final RngSource MADNESS_DESTROY = RngSource.of("madness:destroy").composition();
    /** Invisible Joker: duplicate a random remaining joker on sell. */
    public static final RngSource INVISIBLE_DUP = RngSource.of("invisible:dup").composition();
    /** Perkeo: duplicate a random held consumable. */
    public static final RngSource PERKEO_DUP = RngSource.of("perkeo:dup").composition();

    // --- Plain game-long sequential -------------------------------------------
    public static final RngSource JOKER_EDITION = RngSource.of("joker_edition");
    public static final RngSource JOKER_RARITY = RngSource.of("joker_rarity");
    public static final RngSource SHOP_SLOT = RngSource.of("shop_slot");
    public static final RngSource PLANETS = RngSource.of("planets");
    public static final RngSource TAROT = RngSource.of("tarot");
    public static final RngSource VOUCHERS = RngSource.of("vouchers");
    public static final RngSource PACKS = RngSource.of("packs");
    public static final RngSource PACK_CARD = RngSource.of("pack:card");
    public static final RngSource SOUL = RngSource.of("soul");
    public static final RngSource TAGS = RngSource.of("tags");
    public static final RngSource TAG_TOPUP = RngSource.of("tag:topup");
    /** Boss-blind targets (Idol/Ancient/Castle/To-Do/Rebate): {@code TARGET.sub("idol:suit")}. */
    public static final RngSource TARGET = RngSource.of("target");
    /** Procedural card-generation rolls (Familiar/Grim/Certificate): {@code CREATE_CARD.sub("rank")}. */
    public static final RngSource CREATE_CARD = RngSource.of("create:card");

    /** The created-joker pool for a generative consumable's rarity. */
    public static RngSource createJoker(String rarity) {
        return RngSource.of("create:joker:" + rarity);
    }

    // --- Dynamic-key factories ------------------------------------------------
    /** A rarity's joker sub-queue ("joker_common" / "joker_uncommon" / "joker_rare"). */
    public static RngSource jokerPool(String rarity) {
        return RngSource.of("joker_" + rarity.toLowerCase());
    }

    /** The free-joker queue for a Rare/Uncommon skip tag. */
    public static RngSource tagJoker(String rarity) {
        return RngSource.of("tag:joker:" + rarity);
    }

    /** A booster pack's content queue ("pack:tarot" / "pack:planet" / "pack:spectral"). */
    public static RngSource packContent(String key) {
        return RngSource.of(key);
    }

    /** Base for a consumable's own rolls: {@code consumable(key).sub("rank")},
     *  {@code consumable(key).sub("joker").composition()}, etc. */
    public static RngSource consumable(String key) {
        return RngSource.of("consumable:" + key);
    }
}
