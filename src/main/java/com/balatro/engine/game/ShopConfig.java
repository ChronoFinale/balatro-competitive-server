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
 * </ul>
 *
 * <p>(Chaos the Clown's free reroll is no longer a rule here — it's a {@code FREE_REROLLS}
 * {@link com.balatro.engine.joker.def.Value.Var} folded from ownership, like every other resource.)
 */
public record ShopConfig(boolean allowDuplicates, boolean planetsFree) {

    /** Fold the currently-owned jokers into the effective shop rules. Pure — no side effects. */
    public static ShopConfig resolve(List<Joker> jokers) {
        return new ShopConfig(owns(jokers, "j_showman"), owns(jokers, "j_astronomer"));
    }

    private static boolean owns(List<Joker> jokers, String key) {
        return jokers.stream().anyMatch(j -> key.equals(j.key()));
    }
}
