package com.balatro.engine.game;

import com.balatro.engine.joker.Joker;
import java.util.List;

/**
 * The effective shop rules, <b>resolved as a pure function of what a run currently owns</b> — the
 * sibling of {@link EconomyConfig}. Nothing here mutates run state; the rules are <i>derived</i> from
 * the owned jokers, so a shop-rule joker is expressed as data rather than a scatter of {@code hasJoker}
 * checks at each shop call-site. Compose a new shop rule by extending {@link #resolve}.
 *
 * <ul>
 *   <li>{@code allowDuplicates} — Showman: cards already owned may reappear in shop pools.</li>
 *   <li>{@code planetsFree} — Astronomer: Planet cards in the shop cost $0.</li>
 *   <li>{@code firstRerollFree} — Chaos the Clown: the first reroll each shop visit is free
 *       (whether that free reroll has been spent yet is transient run state, not a rule).</li>
 * </ul>
 */
public record ShopConfig(boolean allowDuplicates, boolean planetsFree, boolean firstRerollFree) {

    /** Fold the currently-owned jokers into the effective shop rules. Pure — no side effects. */
    public static ShopConfig resolve(List<Joker> jokers) {
        return new ShopConfig(owns(jokers, "j_showman"), owns(jokers, "j_astronomer"), owns(jokers, "j_chaos"));
    }

    private static boolean owns(List<Joker> jokers, String key) {
        return jokers.stream().anyMatch(j -> key.equals(j.key()));
    }
}
