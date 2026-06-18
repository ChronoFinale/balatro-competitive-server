package com.balatro.engine.game;

import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The effective end-of-round economy, <b>resolved as a pure function of what a run currently owns</b>
 * (its deck + owned vouchers + owned jokers). Nothing here mutates run state — there is no
 * {@code noInterest} flag to set; it is <i>derived</i>. Compose a new economy effect by extending
 * {@link #resolve}, never by mutating a field elsewhere. Faithful to Balatro's round-eval rows
 * (state_events.lua:1166–1202): per-remaining-hand + per-remaining-discard money, then interest
 * ($1 per $5 held, capped) unless the source set suppresses it.
 */
public record EconomyConfig(int moneyPerHand, int moneyPerDiscard, boolean noInterest,
                            int interestCap, boolean toTheMoon, int minMoney) {

    /** Earn $1 of interest per this many dollars held (Balatro's $1 / $5). */
    public static final int DOLLARS_PER_INTEREST = 5;
    /** The interest cap before any voucher upgrade (max $5; Seed Money/Money Tree raise it). */
    public static final int BASE_INTEREST_CAP = 5;

    /** Fold the currently-owned sources into the effective economy. Pure — no side effects. */
    public static EconomyConfig resolve(DeckCatalog.DeckType deck, Set<String> vouchers, List<Joker> jokers) {
        boolean green = deck.greenEconomy();                 // Green Deck: hand/discard money, no interest
        List<Modify> voucherMods = new ArrayList<>();        // interest cap is folded from voucher data
        for (String v : vouchers) {
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) voucherMods.addAll(def.mods());
        }
        int cap = (int) Modify.fold(BASE_INTEREST_CAP, Value.Var.INTEREST_CAP, voucherMods);
        boolean moon = jokers.stream().anyMatch(j -> "j_to_the_moon".equals(j.key())); // +$1/$5, uncapped
        boolean credit = jokers.stream().anyMatch(j -> "j_credit_card".equals(j.key())); // debt floor of -$20
        return new EconomyConfig(green ? 2 : 1, green ? 1 : 0, green, cap, moon, credit ? -20 : 0);
    }

    /** $ from remaining hands + remaining discards. */
    public int perCardMoney(int handsLeft, int discardsLeft) {
        return handsLeft * moneyPerHand + discardsLeft * moneyPerDiscard;
    }

    /** Interest ($1 per $5 held, capped) plus To the Moon's extra uncapped $1 per $5. */
    public int interest(int money) {
        if (noInterest) return 0;
        int perDollar = money / DOLLARS_PER_INTEREST;
        return Math.min(interestCap, perDollar) + (toTheMoon ? perDollar : 0);
    }
}
