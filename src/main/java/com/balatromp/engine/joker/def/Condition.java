package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
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
    @JsonSubTypes.Type(value = Condition.ScoredFirst.class, name = "scoredFirst"),
    @JsonSubTypes.Type(value = Condition.ScoredEnhancement.class, name = "scoredEnhancement"),
    @JsonSubTypes.Type(value = Condition.ScoredEdition.class, name = "scoredEdition"),
    @JsonSubTypes.Type(value = Condition.ScoredSeal.class, name = "scoredSeal"),
    @JsonSubTypes.Type(value = Condition.HandContainsPair.class, name = "handContainsPair"),
    @JsonSubTypes.Type(value = Condition.HandContains.class, name = "handContains"),
    @JsonSubTypes.Type(value = Condition.HandIs.class, name = "handIs"),
    @JsonSubTypes.Type(value = Condition.PlayedCount.class, name = "playedCount"),
    @JsonSubTypes.Type(value = Condition.DiscardedFaceCount.class, name = "discardedFaceCount"),
    @JsonSubTypes.Type(value = Condition.ScoringAnyFace.class, name = "scoringAnyFace"),
    @JsonSubTypes.Type(value = Condition.ScoringContainsSuit.class, name = "scoringContainsSuit"),
    @JsonSubTypes.Type(value = Condition.ScoredFirstFace.class, name = "scoredFirstFace"),
    @JsonSubTypes.Type(value = Condition.ValueAtLeast.class, name = "valueAtLeast"),
    @JsonSubTypes.Type(value = Condition.HeldAllSuits.class, name = "heldAllSuits"),
    @JsonSubTypes.Type(value = Condition.BossDefeated.class, name = "bossDefeated"),
    @JsonSubTypes.Type(value = Condition.HandPlayedThisRound.class, name = "handPlayedThisRound"),
    @JsonSubTypes.Type(value = Condition.OtherJokerRarity.class, name = "otherJokerRarity"),
    @JsonSubTypes.Type(value = Condition.ScoredIsIdol.class, name = "scoredIsIdol"),
    @JsonSubTypes.Type(value = Condition.ScoredSuitIsAncient.class, name = "scoredSuitIsAncient"),
    @JsonSubTypes.Type(value = Condition.ScoredSuitIsCastle.class, name = "scoredSuitIsCastle"),
    @JsonSubTypes.Type(value = Condition.HandIsTodo.class, name = "handIsTodo"),
    @JsonSubTypes.Type(value = Condition.ConsumableType.class, name = "consumableType"),
    @JsonSubTypes.Type(value = Condition.StateAtLeast.class, name = "stateAtLeast"),
    @JsonSubTypes.Type(value = Condition.Chance.class, name = "chance"),
    @JsonSubTypes.Type(value = Condition.MoneyAtLeast.class, name = "moneyAtLeast"),
    @JsonSubTypes.Type(value = Condition.HandsLeft.class, name = "handsLeft"),
    @JsonSubTypes.Type(value = Condition.DiscardsLeft.class, name = "discardsLeft"),
    @JsonSubTypes.Type(value = Condition.Ante.class, name = "ante"),
    @JsonSubTypes.Type(value = Condition.And.class, name = "and"),
    @JsonSubTypes.Type(value = Condition.Or.class, name = "or"),
    @JsonSubTypes.Type(value = Condition.Not.class, name = "not"),
})
public sealed interface Condition {

    boolean test(EvaluationContext ctx);

    /** Face-ness honouring Pareidolia ({@code ctx.allFaces}); Stone cards never count. */
    static boolean isFace(EvaluationContext ctx, Card c) {
        return c != null && !c.isStone() && (c.isFace() || ctx.allFaces);
    }

    /** Comparison for count-style conditions. */
    enum Cmp {
        LTE, GTE, EQ;

        boolean holds(int value, int target) {
            return switch (this) {
                case LTE -> value <= target;
                case GTE -> value >= target;
                case EQ -> value == target;
            };
        }

        static boolean holds(Cmp cmp, int value, int target) {
            return cmp.holds(value, target);
        }
    }

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

    /**
     * The scoring card has an even ({@code even=true}: 10/8/6/4/2) or odd
     * ({@code even=false}: A/9/7/5/3) rank, per Balatro's Even Steven / Odd Todd —
     * Ace counts as <b>odd</b>, and face cards count as neither (Stone never matches).
     */
    record ScoredParity(boolean even) implements Condition {
        public boolean test(EvaluationContext ctx) {
            Card c = ctx.scoredCard;
            if (c == null || c.isStone()) return false;
            int id = c.id();
            boolean isEven = id == 2 || id == 4 || id == 6 || id == 8 || id == 10;
            boolean isOdd = id == 3 || id == 5 || id == 7 || id == 9 || id == 14; // Ace = odd
            return even ? isEven : isOdd;
        }
    }

    /** The scoring card is a face card (J/Q/K). */
    record ScoredIsFace() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return isFace(ctx, ctx.scoredCard);
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

    /** The scoring card is the first card in scoring order (Photograph, Hanging Chad). */
    record ScoredFirst() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoringCards != null
                    && !ctx.scoringCards.isEmpty() && ctx.scoringCards.get(0) == ctx.scoredCard;
        }
    }

    /** The scoring card has a given enhancement (Bonus, Mult, Glass, Steel, Stone, ...). */
    record ScoredEnhancement(Enhancement enhancement) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoredCard.enhancement == enhancement;
        }
    }

    /** The scoring card has a given edition (Foil, Holographic, Polychrome). */
    record ScoredEdition(Edition edition) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoredCard.edition == edition;
        }
    }

    /** The scoring card has a given seal (Gold, Red, Blue, Purple). */
    record ScoredSeal(Seal seal) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoredCard.seal == seal;
        }
    }

    /** The played poker hand contains a pair (Pair, Two Pair, Full House, ...). */
    record HandContainsPair() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.handType != null && ctx.handType.containsPair();
        }
    }

    /**
     * The played hand "contains" a hand category (Balatro's exact-count containment —
     * e.g. {@code THREE_OF_A_KIND} matches Three of a Kind / Full House / Flush House,
     * but NOT Four of a Kind). Powers the type-mult / type-chips jokers.
     */
    record HandContains(HandType hand) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.handType != null && ctx.handType.contains(hand);
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
            return ctx.playedCards != null && cmp.holds(ctx.playedCards.size(), n);
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
                if (isFace(ctx, c)) return true;
            }
            return false;
        }
    }

    /** The scoring card is the first FACE card in scoring order (Photograph). */
    record ScoredFirstFace() implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (!isFace(ctx, ctx.scoredCard) || ctx.scoringCards == null) return false;
            for (Card c : ctx.scoringCards) {
                if (isFace(ctx, c)) return c == ctx.scoredCard; // first face must be this one
            }
            return false;
        }
    }

    /** A resolved {@link Value} is at least {@code min} (Drivers License: >=16 enhanced). */
    record ValueAtLeast(Value value, double min) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return value.resolve(ctx) >= min;
        }
    }

    /** Every card held in hand is of one of {@code suits} (empty hand counts as true; Blackboard). */
    record HeldAllSuits(List<Suit> suits) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.heldCards == null) return true;
            for (Card c : ctx.heldCards) {
                boolean ok = false;
                for (Suit s : suits) if (c.isSuit(s)) { ok = true; break; }
                if (!ok) return false;
            }
            return true;
        }
    }

    /** At least one scoring card is of the given suit (Flower Pot, Seeing Double). */
    record ScoringContainsSuit(Suit suit) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.scoringCards == null) return false;
            for (Card c : ctx.scoringCards) {
                if (c.isSuit(suit)) return true;
            }
            return false;
        }
    }

    /** The scoring card matches this round's Idol target (rank + suit). */
    record ScoredIsIdol() implements Condition {
        public boolean test(EvaluationContext ctx) {
            Card c = ctx.scoredCard;
            return c != null && !c.isStone() && ctx.run != null
                    && c.id() == ctx.run.idolRankId && c.isSuit(ctx.run.idolSuit);
        }
    }

    /** The scoring card is of this round's Ancient suit. */
    record ScoredSuitIsAncient() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.run != null && ctx.scoredCard.isSuit(ctx.run.ancientSuit);
        }
    }

    /** The (scored/event) card is of this round's Castle suit. */
    record ScoredSuitIsCastle() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.run != null && ctx.scoredCard.isSuit(ctx.run.castleSuit);
        }
    }

    /** The played poker hand is this round's To Do List hand. */
    record HandIsTodo() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && ctx.handType == ctx.run.todoHandType;
        }
    }

    /** The joker being reacted to (ON_OTHER_JOKER) is of the given rarity (Baseball Card). */
    record OtherJokerRarity(String rarity) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.otherJoker != null && rarity.equals(ctx.otherJoker.info().rarity());
        }
    }

    /** The current poker hand has already been played earlier this round (Card Sharp). */
    record HandPlayedThisRound() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && ctx.handType != null
                    && ctx.run.handTypesThisRound.contains(ctx.handType);
        }
    }

    /** The round just won was a Boss blind (END_OF_ROUND; Rocket). */
    record BossDefeated() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.bossDefeated;
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

    /**
     * Probabilistic gate: true with probability {@code numerator/denominator},
     * scaled by {@code probabilityNumerator} (Oops! All 6s). Each evaluation pops
     * a roll from a game-long queue keyed by {@code seedKey}, so both players get
     * identical procs. Compose with {@link And} for "this card AND a chance".
     */
    record Chance(int numerator, int denominator, String seedKey) implements Condition {
        public boolean test(EvaluationContext ctx) {
            int probNum = ctx.run != null ? ctx.run.probabilityNumerator : 1;
            return ctx.nextProb(seedKey) < (double) (numerator * probNum) / denominator;
        }
    }

    /** The run has at least {@code min} money. */
    record MoneyAtLeast(int min) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && ctx.run.money >= min;
        }
    }

    /** Hands remaining this round compares to {@code n}. */
    record HandsLeft(Cmp cmp, int n) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && Cmp.holds(cmp, ctx.run.handsLeft, n);
        }
    }

    /** Discards remaining this round compares to {@code n}. */
    record DiscardsLeft(Cmp cmp, int n) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && Cmp.holds(cmp, ctx.run.discardsLeft, n);
        }
    }

    /** The current ante compares to {@code n}. */
    record Ante(Cmp cmp, int n) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && Cmp.holds(cmp, ctx.run.ante, n);
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
