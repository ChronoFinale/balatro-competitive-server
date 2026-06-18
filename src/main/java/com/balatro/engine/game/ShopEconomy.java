package com.balatro.engine.game;

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
                          double editionMultiplier, double polyMultiplier) {

    /** Fold the currently-owned vouchers into the effective shop economy. Pure — no side effects. */
    public static ShopEconomy resolve(Set<String> vouchers) {
        int slots = vouchers.contains("v_overstock_plus") ? 4
                : vouchers.contains("v_overstock") ? 3 : 2;
        double price = vouchers.contains("v_liquidation") ? 0.50
                : vouchers.contains("v_clearance_sale") ? 0.75 : 1.0;
        int reroll = (vouchers.contains("v_reroll_surplus") ? 2 : 0)
                + (vouchers.contains("v_reroll_glut") ? 2 : 0);
        double editionMult = vouchers.contains("v_glow_up") ? 4.0
                : vouchers.contains("v_hone") ? 2.0 : 1.0;
        double polyMult = vouchers.contains("v_glow_up") ? 7.0
                : vouchers.contains("v_hone") ? 3.0 : 1.0;
        return new ShopEconomy(slots, price, reroll, editionMult, polyMult);
    }
}
