package com.balatro.content;

import com.balatro.grammar.Hand;
import com.balatro.engine.game.VoucherCatalog.Voucher;
import com.balatro.engine.i18n.Loc;
import com.balatro.grammar.Modify;
import com.balatro.grammar.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The voucher CONTENT — base/upgrade pairs + their resource/economy {@link Modify}s, compiled to
 *  {@code content/vouchers.json}. Descriptions come from localization (en.json). */
public final class VoucherDefs {

    private VoucherDefs() {}

    private static final Map<String, Voucher> M = new LinkedHashMap<>();

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

        addMods("v_grabber", Modify.add(Hand.PLAYS, 1));
        addMods("v_nacho_tong", Modify.add(Hand.PLAYS, 1));
        addMods("v_wasteful", Modify.add(Hand.DISCARDS, 1));
        addMods("v_recyclomancy", Modify.add(Hand.DISCARDS, 1));
        addMods("v_paint_brush", Modify.add(Hand.SIZE, 1));
        addMods("v_palette", Modify.add(Hand.SIZE, 1));
        addMods("v_antimatter", Modify.add(Value.Var.JOKER_SLOTS, 1));
        addMods("v_crystal_ball", Modify.add(Value.Var.CONSUMABLE_SLOTS, 1));
        addMods("v_omen_globe", Modify.add(Value.Var.CONSUMABLE_SLOTS, 1));
        addMods("v_overstock", Modify.max(Value.Var.SHOP_SLOTS, 3));
        addMods("v_overstock_plus", Modify.max(Value.Var.SHOP_SLOTS, 4));
        addMods("v_clearance_sale", Modify.min(Value.Var.PRICE_MULTIPLIER, 0.75));
        addMods("v_liquidation", Modify.min(Value.Var.PRICE_MULTIPLIER, 0.50));
        addMods("v_reroll_surplus", Modify.add(Value.Var.REROLL_DISCOUNT, 2));
        addMods("v_reroll_glut", Modify.add(Value.Var.REROLL_DISCOUNT, 2));
        addMods("v_hone", Modify.max(Value.Var.EDITION_MULTIPLIER, 2), Modify.max(Value.Var.POLY_MULTIPLIER, 3));
        addMods("v_glow_up", Modify.max(Value.Var.EDITION_MULTIPLIER, 4), Modify.max(Value.Var.POLY_MULTIPLIER, 7));
        addMods("v_seed_money", Modify.max(Value.Var.INTEREST_CAP, 10));
        addMods("v_money_tree", Modify.max(Value.Var.INTEREST_CAP, 20));
        addMods("v_tarot_merchant", Modify.max(Value.Var.TAROT_RATE, 8));
        addMods("v_tarot_tycoon", Modify.max(Value.Var.TAROT_RATE, 16));
        addMods("v_planet_merchant", Modify.max(Value.Var.PLANET_RATE, 8));
        addMods("v_planet_tycoon", Modify.max(Value.Var.PLANET_RATE, 16));
        addMods("v_hieroglyph", Modify.add(Value.Var.WIN_ANTE, -1), Modify.add(Hand.PLAYS, -1));
        addMods("v_petroglyph", Modify.add(Value.Var.WIN_ANTE, -1), Modify.add(Hand.DISCARDS, -1));
        addMods("v_directors_cut", Modify.max(Value.Var.BOSS_REROLLS_PER_ANTE, 1));
        addMods("v_retcon", Modify.max(Value.Var.BOSS_REROLLS_PER_ANTE, 9999));
        addMods("v_observatory", Modify.max(Value.Var.HELD_PLANET_MULT, 1.5));
        addMods("v_telescope", Modify.max(Value.Var.CELESTIAL_MOST_PLAYED, 1));
        addMods("v_magic_trick", Modify.max(Value.Var.SHOP_PLAYING_CARD_RATE, 4));
        addMods("v_illusion", Modify.max(Value.Var.SHOP_PLAYING_CARD_RATE, 4),
                Modify.max(Value.Var.SHOP_CARDS_ENHANCED, 1));
    }

    public static List<Voucher> authored() {
        return List.copyOf(M.values());
    }

    private static void pair(String baseKey, String baseName, String upKey, String upName) {
        M.put(baseKey, new Voucher(baseKey, baseName, Loc.text(baseKey), 10, upKey));
        M.put(upKey, new Voucher(upKey, upName, Loc.text(upKey), 10, null));
    }

    private static void addMods(String key, Modify... mods) {
        Voucher v = M.get(key);
        M.put(key, new Voucher(v.key(), v.name(), v.description(), v.cost(), v.upgradeKey(), List.of(mods)));
    }
}
