package com.balatro.engine.game;

import com.balatro.engine.joker.Joker;
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

    /** Fold the currently-owned sources into the effective economy. Pure — no side effects. */
    public static EconomyConfig resolve(DeckCatalog.DeckType deck, Set<String> vouchers, List<Joker> jokers) {
        boolean green = deck.greenEconomy();                 // Green Deck: hand/discard money, no interest
        int cap = vouchers.contains("v_money_tree") ? 20     // Money Tree raises the interest cap to $20
                : vouchers.contains("v_seed_money") ? 10     // Seed Money to $10
                : 5;                                         // base
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
        return Math.min(interestCap, money / 5) + (toTheMoon ? money / 5 : 0);
    }
}
