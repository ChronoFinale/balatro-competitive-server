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
    private static final List<String> BASES = new ArrayList<>();

    /** Banned in Standard Ranked multiplayer (affect ante/boss in ways that break PvP pacing). */
    public static final Set<String> MP_BANNED =
            Set.of("v_hieroglyph", "v_petroglyph", "v_directors_cut", "v_retcon");

    static {
        pair("v_overstock", "Overstock", "+1 card slot in the shop",
                "v_overstock_plus", "Overstock Plus", "+1 more shop card slot (4 total)");
        pair("v_clearance_sale", "Clearance Sale", "All shop cards & packs 25% off",
                "v_liquidation", "Liquidation", "All shop cards & packs 50% off");
        pair("v_hone", "Hone", "Foil/Holo/Poly Jokers appear 2x more often",
                "v_glow_up", "Glow Up", "Foil/Holo/Poly Jokers appear 4x more often");
        pair("v_reroll_surplus", "Reroll Surplus", "Rerolls cost $2 less",
                "v_reroll_glut", "Reroll Glut", "Rerolls cost an additional $2 less");
        pair("v_crystal_ball", "Crystal Ball", "+1 consumable slot",
                "v_omen_globe", "Omen Globe", "Spectral cards may appear in Arcana Packs");
        pair("v_telescope", "Telescope", "Celestial Packs always contain your most-played hand's Planet",
                "v_observatory", "Observatory", "Planet cards in your consumables give X1.5 Mult for their hand");
        pair("v_grabber", "Grabber", "Permanently +1 hand per round",
                "v_nacho_tong", "Nacho Tong", "Permanently +1 additional hand per round");
        pair("v_wasteful", "Wasteful", "Permanently +1 discard per round",
                "v_recyclomancy", "Recyclomancy", "Permanently +1 additional discard per round");
        pair("v_tarot_merchant", "Tarot Merchant", "Tarot cards appear 2x more in the shop",
                "v_tarot_tycoon", "Tarot Tycoon", "Tarot cards appear 4x more in the shop");
        pair("v_planet_merchant", "Planet Merchant", "Planet cards appear 2x more in the shop",
                "v_planet_tycoon", "Planet Tycoon", "Planet cards appear 4x more in the shop");
        pair("v_seed_money", "Seed Money", "Raise the interest cap to $10 per round",
                "v_money_tree", "Money Tree", "Raise the interest cap to $20 per round");
        pair("v_blank", "Blank", "Does nothing",
                "v_antimatter", "Antimatter", "+1 Joker slot");
        pair("v_magic_trick", "Magic Trick", "Playing cards can be bought from the shop",
                "v_illusion", "Illusion", "Shop playing cards may have an Enhancement and/or Edition");
        pair("v_hieroglyph", "Hieroglyph", "-1 Ante; -1 hand each round",
                "v_petroglyph", "Petroglyph", "-1 Ante again; -1 discard each round");
        pair("v_directors_cut", "Director's Cut", "Reroll the Boss Blind once per ante ($10)",
                "v_retcon", "Retcon", "Reroll the Boss Blind unlimited times ($10)");
        pair("v_paint_brush", "Paint Brush", "+1 hand size",
                "v_palette", "Palette", "+1 hand size again");

        // Resource vouchers carry their effect as data (a Modify on a game variable), folded by Run
        // alongside the joker/boss/deck modifiers — no key-string checks.
        addMods("v_grabber", Modify.add(Value.Var.HANDS_LEFT, 1));        // Permanently +1 hand
        addMods("v_nacho_tong", Modify.add(Value.Var.HANDS_LEFT, 1));
        addMods("v_wasteful", Modify.add(Value.Var.DISCARDS_LEFT, 1));    // Permanently +1 discard
        addMods("v_recyclomancy", Modify.add(Value.Var.DISCARDS_LEFT, 1));
        addMods("v_paint_brush", Modify.add(Value.Var.HAND_SIZE, 1));     // +1 hand size
        addMods("v_palette", Modify.add(Value.Var.HAND_SIZE, 1));
    }

    private static void addMods(String key, Modify... mods) {
        BY_KEY.put(key, BY_KEY.get(key).withMods(mods));
    }

    private static void pair(String baseKey, String baseName, String baseDesc,
            String upKey, String upName, String upDesc) {
        BY_KEY.put(baseKey, new Voucher(baseKey, baseName, baseDesc, 10, upKey));
        BY_KEY.put(upKey, new Voucher(upKey, upName, upDesc, 10, null));
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
