package com.balatro.engine.state;

import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The data-driven registry of <b>per-round targets</b>: a value rolled fresh each blind from a
 * seed-derived source and matched during scoring — the Idol's rank+suit, Ancient/Castle's suit,
 * To Do List's hand, Mail-In Rebate's rank.
 *
 * <p>Each {@link Spec} is pure data. Adding a new "matches a rolled X this round" card needs only a row
 * here plus a generic {@code Condition.ScoredSuitIsTarget}/{@code ScoredRankIsTarget}/{@code HandIsTarget}
 * referencing its {@code id} — no new {@code RunState} field, no roll code, no bespoke {@code Condition}.
 * The rolled values live generically in {@link RunState#roundTargets} keyed by {@code id}.
 */
public final class RoundTargets {

    private RoundTargets() {}

    /** What kind of value a target rolls. */
    public enum Domain { SUIT, RANK, HAND_TYPE }

    /**
     * @param id     the bag / {@code Condition} / client-counter key (stable, human-readable)
     * @param rngKey the RNG sub-key under {@code RngSources.TARGET} (kept stable for determinism)
     * @param domain what is rolled (a Suit, a rank id, or a HandType)
     */
    public record Spec(String id, String rngKey, Domain domain) {}

    public static final List<Spec> ALL = List.of(
            new Spec("idolRankId", "idol:rank", Domain.RANK),
            new Spec("idolSuit", "idol:suit", Domain.SUIT),
            new Spec("ancientSuit", "ancient:suit", Domain.SUIT),
            new Spec("castleSuit", "castle:suit", Domain.SUIT),
            new Spec("todoHand", "todo:hand", Domain.HAND_TYPE),
            new Spec("rebateRankId", "rebate:rank", Domain.RANK));

    /** Starting targets, used until the first blind rolls them (matches the historic field defaults). */
    public static Map<String, Object> defaults() {
        Map<String, Object> m = new HashMap<>();
        m.put("idolRankId", 14);
        m.put("idolSuit", Suit.HEARTS);
        m.put("ancientSuit", Suit.HEARTS);
        m.put("castleSuit", Suit.HEARTS);
        m.put("todoHand", HandType.PAIR);
        m.put("rebateRankId", 2);
        return m;
    }
}
