package com.balatro.engine.game;

import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vouchers: permanent, once-per-run shop upgrades. There are 16 base vouchers, each
 * with a Tier-2 upgrade (32 total). The shop's per-ante voucher is chosen from a
 * game-long queue over the 16 bases, resolving by tier: show Tier 1 until bought,
 * then Tier 2, then skip the position once both are owned (see {@code Run}). Effects
 * live in {@code Run} (which owns the state they touch); this catalog is metadata +
 * the base/upgrade links.
 */
public final class VoucherCatalog {

    private VoucherCatalog() {}

    /**
     * {@code upgradeKey} is the Tier-2 key for a base voucher, or null for a Tier-2 voucher.
     * {@code mods} are the voucher's resource effects as data — a Grabber is {@code add(HANDS_LEFT, 1)},
     * not a key-string check in {@code Run}.
     */
    public record Voucher(String key, String name, String description, int cost, String upgradeKey,
                          List<Modify> mods) {
        public Voucher(String key, String name, String description, int cost, String upgradeKey) {
            this(key, name, description, cost, upgradeKey, List.of());
        }

        Voucher withMods(Modify... mods) {
            return new Voucher(key, name, description, cost, upgradeKey, List.of(mods));
        }
    }

    private static final Map<String, Voucher> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, Voucher> AUTHORED = new LinkedHashMap<>();  // DSL build (pair + addMods)
    private static final List<String> BASES = new ArrayList<>();

    /** Banned in Standard Ranked multiplayer (affect ante/boss in ways that break PvP pacing). */
    public static final Set<String> MP_BANNED =
            Set.of("v_hieroglyph", "v_petroglyph", "v_directors_cut", "v_retcon");

    static {
        pair("v_overstock", "Overstock", "v_overstock_plus", "Overstock Plus");
        pair("v_clearance_sale", "Clearance Sale", "v_liquidation", "Liquidation");
        pair("v_hone", "Hone", "v_glow_up", "Glow Up");
        pair("v_reroll_surplus", "Reroll Surplus", "v_reroll_glut", "Reroll Glut");
        pair("v_crystal_ball", "Crystal Ball", "v_omen_globe", "Omen Globe");
        pair("v_telescope", "Telescope", "v_observatory", "Observatory");
        pair("v_grabber", "Grabber", "v_nacho_tong", "Nacho Tong");
        pair("v_wasteful", "Wasteful", "v_recyclomancy", "Recyclomancy");
        pair("v_tarot_merchant", "Tarot Merchant", "v_tarot_tycoon", "Tarot Tycoon");
        pair("v_planet_merchant", "Planet Merchant", "v_planet_tycoon", "Planet Tycoon");
        pair("v_seed_money", "Seed Money", "v_money_tree", "Money Tree");
        pair("v_blank", "Blank", "v_antimatter", "Antimatter");
        pair("v_magic_trick", "Magic Trick", "v_illusion", "Illusion");
        pair("v_hieroglyph", "Hieroglyph", "v_petroglyph", "Petroglyph");
        pair("v_directors_cut", "Director's Cut", "v_retcon", "Retcon");
        pair("v_paint_brush", "Paint Brush", "v_palette", "Palette");

        // Resource vouchers carry their effect as data (a Modify on a game variable), folded by Run
        // alongside the joker/boss/deck modifiers — no key-string checks.
        addMods("v_grabber", Modify.add(Value.Var.HANDS_LEFT, 1));        // Permanently +1 hand
        addMods("v_nacho_tong", Modify.add(Value.Var.HANDS_LEFT, 1));
        addMods("v_wasteful", Modify.add(Value.Var.DISCARDS_LEFT, 1));    // Permanently +1 discard
        addMods("v_recyclomancy", Modify.add(Value.Var.DISCARDS_LEFT, 1));
        addMods("v_paint_brush", Modify.add(Value.Var.HAND_SIZE, 1));     // +1 hand size
        addMods("v_palette", Modify.add(Value.Var.HAND_SIZE, 1));
        addMods("v_antimatter", Modify.add(Value.Var.JOKER_SLOTS, 1));    // +1 Joker slot, folded like everyone else
        addMods("v_crystal_ball", Modify.add(Value.Var.CONSUMABLE_SLOTS, 1)); // +1 consumable slot
        addMods("v_omen_globe", Modify.add(Value.Var.CONSUMABLE_SLOTS, 1));

        // Shop-economy vouchers (folded by ShopEconomy). Tiered ones use max/min — the highest/deepest
        // owned tier wins, so owning Seed-Money-style base + upgrade resolves to the upgrade, in any order.
        addMods("v_overstock", Modify.max(Value.Var.SHOP_SLOTS, 3));         // base 2 -> 3 shop slots
        addMods("v_overstock_plus", Modify.max(Value.Var.SHOP_SLOTS, 4));    // -> 4
        addMods("v_clearance_sale", Modify.min(Value.Var.PRICE_MULTIPLIER, 0.75)); // 25% off
        addMods("v_liquidation", Modify.min(Value.Var.PRICE_MULTIPLIER, 0.50));    // 50% off
        addMods("v_reroll_surplus", Modify.add(Value.Var.REROLL_DISCOUNT, 2));     // -$2 reroll (cumulative)
        addMods("v_reroll_glut", Modify.add(Value.Var.REROLL_DISCOUNT, 2));        // -$2 more
        addMods("v_hone", Modify.max(Value.Var.EDITION_MULTIPLIER, 2), Modify.max(Value.Var.POLY_MULTIPLIER, 3));
        addMods("v_glow_up", Modify.max(Value.Var.EDITION_MULTIPLIER, 4), Modify.max(Value.Var.POLY_MULTIPLIER, 7));
        // Interest cap (folded by EconomyConfig): Seed Money $10, Money Tree $20 — highest tier wins.
        addMods("v_seed_money", Modify.max(Value.Var.INTEREST_CAP, 10));
        addMods("v_money_tree", Modify.max(Value.Var.INTEREST_CAP, 20));
        // Merchant/Tycoon: Tarots/Planets appear more often (slot weight; base 4 -> 8 = 2x, 16 = 4x).
        addMods("v_tarot_merchant", Modify.max(Value.Var.TAROT_RATE, 8));
        addMods("v_tarot_tycoon", Modify.max(Value.Var.TAROT_RATE, 16));
        addMods("v_planet_merchant", Modify.max(Value.Var.PLANET_RATE, 8));
        addMods("v_planet_tycoon", Modify.max(Value.Var.PLANET_RATE, 16));
        // Hieroglyph/Petroglyph: -1 Ante to win (folded into the win condition) + a per-round resource cost.
        addMods("v_hieroglyph", Modify.add(Value.Var.WIN_ANTE, -1), Modify.add(Value.Var.HANDS_LEFT, -1));
        addMods("v_petroglyph", Modify.add(Value.Var.WIN_ANTE, -1), Modify.add(Value.Var.DISCARDS_LEFT, -1));
        // Director's Cut / Retcon: reroll the Boss Blind — once per ante, then unlimited.
        addMods("v_directors_cut", Modify.max(Value.Var.BOSS_REROLLS_PER_ANTE, 1));
        addMods("v_retcon", Modify.max(Value.Var.BOSS_REROLLS_PER_ANTE, 9999));
        // Observatory: a held Planet gives x1.5 Mult when you play its hand (read by the scorer).
        addMods("v_observatory", Modify.max(Value.Var.HELD_PLANET_MULT, 1.5));
        // Telescope: a Celestial Pack always contains your most-played hand's Planet (flag = 1).
        addMods("v_telescope", Modify.max(Value.Var.CELESTIAL_MOST_PLAYED, 1));
        // Magic Trick: playing cards appear in the shop (slot weight). Illusion: + they may be enhanced.
        addMods("v_magic_trick", Modify.max(Value.Var.SHOP_PLAYING_CARD_RATE, 4));
        addMods("v_illusion", Modify.max(Value.Var.SHOP_PLAYING_CARD_RATE, 4),
                Modify.max(Value.Var.SHOP_CARDS_ENHANCED, 1));

        // Runtime loads from /content/vouchers.json; the pair()/addMods() above are the DSL authoring (+ fallback).
        List<Voucher> vouchers;
        try {
            vouchers = com.balatro.engine.content.ContentStore.vouchers();
        } catch (RuntimeException e) {
            vouchers = new ArrayList<>(AUTHORED.values());
        }
        for (Voucher v : vouchers) BY_KEY.put(v.key(), v);
    }

    /** The DSL authoring source for {@code content/vouchers.json} (also the fallback). */
    public static List<Voucher> authored() {
        return List.copyOf(AUTHORED.values());
    }

    private static void addMods(String key, Modify... mods) {
        AUTHORED.put(key, AUTHORED.get(key).withMods(mods));
    }

    private static void pair(String baseKey, String baseName, String upKey, String upName) {
        // Descriptions are localization data, keyed by voucher key (see /localization/en.json via Loc).
        AUTHORED.put(baseKey, new Voucher(baseKey, baseName, com.balatro.engine.i18n.Loc.text(baseKey), 10, upKey));
        AUTHORED.put(upKey, new Voucher(upKey, upName, com.balatro.engine.i18n.Loc.text(upKey), 10, null));
        BASES.add(baseKey);
    }

    public static Voucher get(String key) {
        return BY_KEY.get(key);
    }

    /** The Tier-2 key for a base voucher (null if {@code baseKey} is itself a Tier-2 or unknown). */
    public static String upgradeKey(String baseKey) {
        Voucher v = BY_KEY.get(baseKey);
        return v != null ? v.upgradeKey() : null;
    }

    /** The 16 base voucher keys the queue draws from; in multiplayer the banned ones are excluded. */
    public static List<String> baseKeys(boolean multiplayer) {
        if (!multiplayer) return new ArrayList<>(BASES);
        return BASES.stream().filter(k -> !MP_BANNED.contains(k)).toList();
    }

    /** All voucher keys (base + upgrade). */
    public static List<String> keys() {
        return new ArrayList<>(BY_KEY.keySet());
    }
}
