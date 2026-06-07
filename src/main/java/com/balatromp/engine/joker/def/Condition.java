package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.EvaluationContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * A data-expressible predicate over an {@link EvaluationContext} — the "when does
 * this joker's effect apply" half of a data-driven joker (spec §5/§6). Closed set
 * of building blocks that the builder UI exposes as dropdowns; serialized to JSON
 * with a {@code "type"} discriminator. Every condition is null-safe: a predicate
 * that reads a context field absent for the current trigger simply returns false,
 * so a malformed pairing (e.g. a per-card check on a hand-level trigger) can't
 * crash the engine.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Condition.Always.class, name = "always"),
    @JsonSubTypes.Type(value = Condition.ScoredSuit.class, name = "scoredSuit"),
    @JsonSubTypes.Type(value = Condition.ScoredParity.class, name = "scoredParity"),
    @JsonSubTypes.Type(value = Condition.ScoredIsFace.class, name = "scoredIsFace"),
    @JsonSubTypes.Type(value = Condition.ScoredRankBetween.class, name = "scoredRankBetween"),
    @JsonSubTypes.Type(value = Condition.HandContainsPair.class, name = "handContainsPair"),
    @JsonSubTypes.Type(value = Condition.HandIs.class, name = "handIs"),
    @JsonSubTypes.Type(value = Condition.PlayedCount.class, name = "playedCount"),
    @JsonSubTypes.Type(value = Condition.DiscardedFaceCount.class, name = "discardedFaceCount"),
    @JsonSubTypes.Type(value = Condition.ScoringAnyFace.class, name = "scoringAnyFace"),
    @JsonSubTypes.Type(value = Condition.ConsumableType.class, name = "consumableType"),
    @JsonSubTypes.Type(value = Condition.StateAtLeast.class, name = "stateAtLeast"),
    @JsonSubTypes.Type(value = Condition.And.class, name = "and"),
    @JsonSubTypes.Type(value = Condition.Or.class, name = "or"),
    @JsonSubTypes.Type(value = Condition.Not.class, name = "not"),
})
public sealed interface Condition {

    boolean test(EvaluationContext ctx);

    /** Comparison for count-style conditions. */
    enum Cmp { LTE, GTE, EQ }

    /** Unconditional. */
    record Always() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return true;
        }
    }

    /** The currently-scoring card is of a given suit (Stone cards never match). */
    record ScoredSuit(Suit suit) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoredCard.isSuit(suit);
        }
    }

    /** The scoring card's rank is even ({@code even=true}) or odd. */
    record ScoredParity(boolean even) implements Condition {
        public boolean test(EvaluationContext ctx) {
            Card c = ctx.scoredCard;
            return c != null && !c.isStone() && even == c.rank.isEven();
        }
    }

    /** The scoring card is a face card (J/Q/K). */
    record ScoredIsFace() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoredCard.isFace();
        }
    }

    /** The scoring card's rank id is in {@code [min, max]} (e.g. Hack: 2..5). */
    record ScoredRankBetween(int min, int max) implements Condition {
        public boolean test(EvaluationContext ctx) {
            Card c = ctx.scoredCard;
            if (c == null || c.isStone()) return false;
            int id = c.id();
            return id >= min && id <= max;
        }
    }

    /** The played poker hand contains a pair (Pair, Two Pair, Full House, ...). */
    record HandContainsPair() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.handType != null && ctx.handType.containsPair();
        }
    }

    /** The played hand is exactly this type. */
    record HandIs(HandType hand) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.handType == hand;
        }
    }

    /** Number of cards played compares to {@code n}. */
    record PlayedCount(Cmp cmp, int n) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.playedCards == null) return false;
            int size = ctx.playedCards.size();
            return switch (cmp) {
                case LTE -> size <= n;
                case GTE -> size >= n;
                case EQ -> size == n;
            };
        }
    }

    /** At least {@code min} face cards in the event set (e.g. a discard). */
    record DiscardedFaceCount(int min) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.eventCards == null) return false;
            int faces = 0;
            for (Card c : ctx.eventCards) {
                if (c.isFace()) faces++;
            }
            return faces >= min;
        }
    }

    /** Any of the scoring cards is a face card (e.g. Ride the Bus streak break). */
    record ScoringAnyFace() implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.scoringCards == null) return false;
            for (Card c : ctx.scoringCards) {
                if (c.isFace()) return true;
            }
            return false;
        }
    }

    /** The consumable in play is of this category ("Tarot" | "Planet" | "Spectral"). */
    record ConsumableType(String consumable) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return consumable.equals(ctx.consumableType);
        }
    }

    /** A server-only state variable is at least {@code min} (e.g. planets gained > 0). */
    record StateAtLeast(String var, double min) implements Condition {
        public boolean test(EvaluationContext ctx) {
            Object v = ctx.selfState().getOrDefault(var, 0);
            double n = (v instanceof Number num) ? num.doubleValue() : 0;
            return n >= min;
        }
    }

    /** All sub-conditions hold. */
    record And(List<Condition> all) implements Condition {
        public boolean test(EvaluationContext ctx) {
            for (Condition c : all) {
                if (!c.test(ctx)) return false;
            }
            return true;
        }
    }

    /** Any sub-condition holds. */
    record Or(List<Condition> any) implements Condition {
        public boolean test(EvaluationContext ctx) {
            for (Condition c : any) {
                if (c.test(ctx)) return true;
            }
            return false;
        }
    }

    /** Negation. */
    record Not(Condition inner) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return !inner.test(ctx);
        }
    }
}
