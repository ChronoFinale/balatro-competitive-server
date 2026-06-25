package com.balatro.engine.game;

import com.balatro.grammar.Modify;
import com.balatro.grammar.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The effective shop economy, <b>resolved as a pure function of the owned vouchers</b> — the sibling of
 * {@link EconomyConfig} (round money) and {@link ShopConfig} (shop rules from owned jokers). The
 * per-voucher effects that used to be scattered key-string checks in {@code Run} live here, derived,
 * in one place:
 *
 * <ul>
 *   <li>{@code slots} — Overstock (+1) / Overstock Plus (+2) shop card slots (base 2).</li>
 *   <li>{@code priceMultiplier} — Clearance Sale (0.75) / Liquidation (0.50) off shop prices.</li>
 *   <li>{@code rerollDiscount} — Reroll Surplus (-$2) + Reroll Glut (-$2 more), cumulative.</li>
 *   <li>{@code editionMultiplier} / {@code polyMultiplier} — Hone (2x/3x) / Glow Up (4x/7x) edition odds.</li>
 * </ul>
 */
public record ShopEconomy(int slots, double priceMultiplier, int rerollDiscount,
                          double editionMultiplier, double polyMultiplier,
                          int tarotWeight, int planetWeight, int spectralWeight,
                          int playingCardWeight, boolean playingCardsEnhanced) {

    /** Shop card slots before any voucher (Overstock/Overstock Plus raise it). */
    public static final int BASE_SHOP_SLOTS = 2;
    /** Base slot weight for a Tarot / Planet (Tarot/Planet Merchant & Tycoon raise it). */
    public static final int BASE_CONSUMABLE_WEIGHT = 4;

    /** Voucher-only resolve (Spectral rate stays 0 — only the Ghost Deck raises it). */
    public static ShopEconomy resolve(Set<String> vouchers) {
        return resolve(vouchers, List.of());
    }

    /** Fold the owned vouchers' {@link Modify} data <i>plus</i> any deck mods (Ghost's Spectral rate)
     *  into the effective shop economy — no key-strings. Slots/edition odds/rates use MAX (highest tier
     *  wins), price uses MIN (deepest discount), reroll ADD. */
    public static ShopEconomy resolve(Set<String> vouchers, List<Modify> deckMods) {
        List<Modify> mods = new ArrayList<>(deckMods);
        for (String v : vouchers) {
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) mods.addAll(def.mods());
        }
        return new ShopEconomy(
                (int) com.balatro.engine.eval.ModifyFolder.fold(BASE_SHOP_SLOTS, Value.Var.SHOP_SLOTS, mods),
                com.balatro.engine.eval.ModifyFolder.fold(1.0, Value.Var.PRICE_MULTIPLIER, mods),
                (int) com.balatro.engine.eval.ModifyFolder.fold(0, Value.Var.REROLL_DISCOUNT, mods),
                com.balatro.engine.eval.ModifyFolder.fold(1.0, Value.Var.EDITION_MULTIPLIER, mods),
                com.balatro.engine.eval.ModifyFolder.fold(1.0, Value.Var.POLY_MULTIPLIER, mods),
                (int) com.balatro.engine.eval.ModifyFolder.fold(BASE_CONSUMABLE_WEIGHT, Value.Var.TAROT_RATE, mods),
                (int) com.balatro.engine.eval.ModifyFolder.fold(BASE_CONSUMABLE_WEIGHT, Value.Var.PLANET_RATE, mods),
                (int) com.balatro.engine.eval.ModifyFolder.fold(0, Value.Var.SPECTRAL_RATE, mods),
                (int) com.balatro.engine.eval.ModifyFolder.fold(0, Value.Var.SHOP_PLAYING_CARD_RATE, mods),
                com.balatro.engine.eval.ModifyFolder.fold(0, Value.Var.SHOP_CARDS_ENHANCED, mods) >= 1);
    }
}
