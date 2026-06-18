package com.balatro.engine.game;

import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;
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
                          double editionMultiplier, double polyMultiplier) {

    /** Fold the owned vouchers' {@link Modify} data into the effective shop economy — no key-strings.
     *  Slots/edition odds use MAX (highest tier wins), price uses MIN (deepest discount), reroll ADD. */
    public static ShopEconomy resolve(Set<String> vouchers) {
        List<Modify> mods = new ArrayList<>();
        for (String v : vouchers) {
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) mods.addAll(def.mods());
        }
        return new ShopEconomy(
                (int) Modify.fold(2, Value.Var.SHOP_SLOTS, mods),
                Modify.fold(1.0, Value.Var.PRICE_MULTIPLIER, mods),
                (int) Modify.fold(0, Value.Var.REROLL_DISCOUNT, mods),
                Modify.fold(1.0, Value.Var.EDITION_MULTIPLIER, mods),
                Modify.fold(1.0, Value.Var.POLY_MULTIPLIER, mods));
    }
}
