package com.balatro.dsl;

import com.balatro.grammar.Condition;
import com.balatro.grammar.Trigger;
import java.util.EnumSet;

/**
 * Catches the class of authoring bug the null-safe evaluator otherwise hides: a {@link Condition} that reads
 * a context the {@link Trigger} never provides (e.g. a per-scored-card check on {@code END_OF_ROUND}). At
 * runtime that condition just returns {@code false} forever, so the rule is silently dead. Here it is a loud
 * build-time error instead — the single highest-value protection against broken (especially modded) content.
 *
 * <p>The model is deliberately COARSE and generous on the scoring side: every scoring-pipeline phase is
 * treated as providing the full scoring context (a scored card, the played hand, the held cards), because
 * during scoring all of those exist. It is strict only about the clear impossibilities — a scoring/discard/
 * consumable/other-joker condition raised on a lifecycle trigger (shop/sell/blind/round) that has none of it.
 */
public final class RuleValidator {

    private RuleValidator() {}

    /** A coarse bucket of evaluation context a trigger may or may not carry. */
    public enum Facet { SCORED_CARD, PLAYED_HAND, HELD_CARDS, DISCARD, OTHER_JOKER, CONSUMABLE }

    /** The scoring pipeline + per-hand boss triggers: a hand is being scored, so all card context exists. */
    private static final EnumSet<Facet> SCORING =
            EnumSet.of(Facet.SCORED_CARD, Facet.PLAYED_HAND, Facet.HELD_CARDS);

    /** What a trigger provides. Conservative: lifecycle/shop triggers provide nothing card-shaped. */
    public static EnumSet<Facet> provides(Trigger t) {
        return switch (t) {
            case MODIFY_SCORING_HAND, BEFORE, INITIAL_SCORING_STEP, ON_SCORED, ON_HELD,
                 REPETITION_PLAYED, REPETITION_HELD, JOKER_MAIN, FINAL_SCORING_STEP, AFTER,
                 DEBUFFED_HAND, DESTROYING_CARD, REMOVE_PLAYING_CARDS, ON_HAND_PLAYED, PRE_HAND ->
                    EnumSet.copyOf(SCORING);
            case ON_OTHER_JOKER -> { var s = EnumSet.copyOf(SCORING); s.add(Facet.OTHER_JOKER); yield s; }
            // The just-destroyed card is the focus (Canio reads "was it a face?", Glass Joker "was it glass?").
            case CARD_DESTROYED -> EnumSet.of(Facet.SCORED_CARD);
            case PRE_DISCARD, ON_DISCARD -> EnumSet.of(Facet.DISCARD);
            case USE_CONSUMABLE -> EnumSet.of(Facet.CONSUMABLE);
            // Pure lifecycle / shop / blind / round / PvP-lifecycle: only RUN-level conditions are valid.
            default -> EnumSet.noneOf(Facet.class);
        };
    }

    /** What a condition needs (recursing through And/Or/Not). RUN-level checks need nothing. */
    public static EnumSet<Facet> requires(Condition c) {
        EnumSet<Facet> need = EnumSet.noneOf(Facet.class);
        switch (c) {
            case Condition.ScoredSuit ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredParity ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredIsFace ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredPlayedThisAnte ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredRankBetween ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredFirst ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredAmongFirst ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredFirstFace ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredEnhancement ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredEdition ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredSeal ignored -> need.add(Facet.SCORED_CARD);
            case Condition.ScoredRankIsTarget ignored -> need.add(Facet.SCORED_CARD);
            case Condition.HandContainsPair ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.HandContains ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.HandIs ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.PlayedCount ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.PlayedHandIsMostPlayed ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.HandPlayedThisRound ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.RoundHandTypeConsistent ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.ScoringAnyFace ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.ScoringContainsSuit ignored -> need.add(Facet.PLAYED_HAND);
            case Condition.HeldAllSuits ignored -> need.add(Facet.HELD_CARDS);
            case Condition.DiscardedFaceCount ignored -> need.add(Facet.DISCARD);
            case Condition.OtherJokerRarity ignored -> need.add(Facet.OTHER_JOKER);
            case Condition.ConsumableType ignored -> need.add(Facet.CONSUMABLE);
            case Condition.And a -> { for (Condition x : a.all()) need.addAll(requires(x)); }
            case Condition.Or o -> { for (Condition x : o.any()) need.addAll(requires(x)); }
            case Condition.Not n -> need.addAll(requires(n.inner()));
            // RUN-level (Compare/RunVarModulo/Chance/InPvpBlind/Boss*/HandsSinceAcquire/Always/…): no facet.
            default -> { }
        }
        return need;
    }

    /** Throw if {@code condition} needs context the {@code trigger} cannot provide. */
    public static void validate(String contentKey, Trigger trigger, Condition condition) {
        if (condition == null) return;
        EnumSet<Facet> missing = requires(condition);
        missing.removeAll(provides(trigger));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("'" + contentKey + "': a condition needs " + missing
                    + " but trigger " + trigger + " provides none of it — the rule would silently never fire."
                    + " Use a trigger that carries that context, or drop the condition.");
        }
    }
}
