package com.balatro.engine.joker.def;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.EvaluationContext;
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
    @JsonSubTypes.Type(value = Condition.ScoredAmongFirst.class, name = "scoredAmongFirst"),
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
    @JsonSubTypes.Type(value = Condition.Compare.class, name = "compare"),
    @JsonSubTypes.Type(value = Condition.HeldAllSuits.class, name = "heldAllSuits"),
    @JsonSubTypes.Type(value = Condition.BossDefeated.class, name = "bossDefeated"),
    @JsonSubTypes.Type(value = Condition.HandPlayedThisRound.class, name = "handPlayedThisRound"),
    @JsonSubTypes.Type(value = Condition.OtherJokerRarity.class, name = "otherJokerRarity"),
    @JsonSubTypes.Type(value = Condition.ScoredRankIsTarget.class, name = "scoredRankIsTarget"),
    @JsonSubTypes.Type(value = Condition.RunVarModulo.class, name = "runVarModulo"),
    @JsonSubTypes.Type(value = Condition.HandsSinceAcquire.class, name = "handsSinceAcquire"),
    @JsonSubTypes.Type(value = Condition.InPvpBlind.class, name = "inPvpBlind"),
    @JsonSubTypes.Type(value = Condition.ReachedPvpFirst.class, name = "reachedPvpFirst"),
    @JsonSubTypes.Type(value = Condition.ConsumableType.class, name = "consumableType"),
    @JsonSubTypes.Type(value = Condition.Chance.class, name = "chance"),
    @JsonSubTypes.Type(value = Condition.And.class, name = "and"),
    @JsonSubTypes.Type(value = Condition.Or.class, name = "or"),
    @JsonSubTypes.Type(value = Condition.Not.class, name = "not"),
    @JsonSubTypes.Type(value = Condition.BossBlindSelected.class, name = "bossBlindSelected"),
    @JsonSubTypes.Type(value = Condition.BossAbilityActive.class, name = "bossAbilityActive"),
    @JsonSubTypes.Type(value = Condition.RoundHandTypeConsistent.class, name = "roundHandTypeConsistent"),
    @JsonSubTypes.Type(value = Condition.ScoredPlayedThisAnte.class, name = "scoredPlayedThisAnte"),
    @JsonSubTypes.Type(value = Condition.PlayedHandIsMostPlayed.class, name = "playedHandIsMostPlayed"),
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

        boolean holds(double value, double target) {
            return switch (this) {
                case LTE -> value <= target;
                case GTE -> value >= target;
                case EQ -> value == target;
            };
        }

        static boolean holds(Cmp cmp, double value, double target) {
            return cmp.holds(value, target);
        }
    }

    /** Unconditional. */
    record Always() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return true;
        }
    }

    /**
     * The currently-scoring card is of a suit — either a fixed {@code suit}, or (when {@code targetKey}
     * is set) this round's rolled target suit under that key (Ancient/Castle/Idol-suit). One record, not
     * a literal/target pair. Stone cards never match; a Wild card matches any. */
    record ScoredSuit(Suit suit, String targetKey) implements Condition {
        public ScoredSuit(Suit suit) { this(suit, null); }
        public boolean test(EvaluationContext ctx) {
            Card c = ctx.scoredCard;
            if (c == null) return false;
            Suit want = (targetKey == null) ? suit
                    : (ctx.run != null && ctx.run.roundTargets.get(targetKey) instanceof Suit s ? s : null);
            return want != null && c.isSuit(want);
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

    /** The card has already been played at some point this ante (The Pillar's debuff). */
    record ScoredPlayedThisAnte() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.scoredCard != null && ctx.scoredCard.playedThisAnte;
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

    /**
     * The played hand is exactly a type — either a fixed {@code hand}, or (when {@code targetKey} is set)
     * this round's rolled target hand under that key (To Do List). One record, not a literal/target pair. */
    record HandIs(HandType hand, String targetKey) implements Condition {
        public HandIs(HandType hand) { this(hand, null); }
        public boolean test(EvaluationContext ctx) {
            if (ctx.handType == null) return false;
            HandType want = (targetKey == null) ? hand
                    : (ctx.run != null && ctx.run.roundTargets.get(targetKey) instanceof HandType h ? h : null);
            return want != null && ctx.handType == want;
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

    /** The scoring card is among the first {@code n} cards in scoring order (multiplayer Hanging Chad). */
    record ScoredAmongFirst(int n) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.scoredCard == null || ctx.scoringCards == null) return false;
            int idx = ctx.scoringCards.indexOf(ctx.scoredCard);
            return idx >= 0 && idx < n;
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
    /**
     * The one numeric-comparison primitive: resolve a {@link Value}, compare it to {@code threshold}
     * with a {@link Cmp}. Absorbs the old MoneyAtLeast / HandsLeft / DiscardsLeft / Ante / StateAtLeast
     * / ValueAtLeast — each was "read a quantity, compare to a number", differing only in which quantity.
     * The quantity reuses the full {@link Value} vocabulary, so a run-state var ({@link Value.RunVar}),
     * a per-joker counter ({@link Value.State}), a card {@link Value.Count}, etc. all compare the same way.
     * Convenience constructors cover the two common sources directly.
     */
    record Compare(Value value, Cmp cmp, double threshold) implements Condition {
        /** Compare a live run-state variable (Money/Hand.PLAYS/Ante/…). */
        public Compare(Property variable, Cmp cmp, double threshold) {
            this(new Value.RunVar(variable, 0, 1), cmp, threshold);
        }
        /** Compare a per-joker state counter (the old StateAtLeast, always GTE before). */
        public Compare(String selfStateVar, Cmp cmp, double threshold) {
            this(new Value.State(selfStateVar, 0, 1), cmp, threshold);
        }
        public boolean test(EvaluationContext ctx) {
            return cmp.holds(value.resolve(ctx), threshold);
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

    /** The scored/event card's rank is this round's target rank under {@code key} (Rebate, Idol-rank). */
    record ScoredRankIsTarget(String key) implements Condition {
        public boolean test(EvaluationContext ctx) {
            Card c = ctx.scoredCard;
            return c != null && !c.isStone() && ctx.run != null
                    && ctx.run.roundTargets.get(key) instanceof Integer r && c.id() == r;
        }
    }

    /** Currently in a PvP (Nemesis) boss blind — Pacifist (negated) / Conjoined. */
    record InPvpBlind() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && ctx.run.inPvpBlind;
        }
    }

    /** Entered the PvP blind before the Nemesis — Speedrun. The Match supplies the answer on the context
     *  (it alone knows the opponent's arrival), exactly as the server supplies RNG for a chance condition. */
    record ReachedPvpFirst() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.reachedPvpFirst;
        }
    }

    /** Fewer than {@code max} hands have been played since this joker was acquired (Seltzer). */
    record HandsSinceAcquire(int max) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.run == null) return false;
            int acq = ((Number) ctx.selfState().getOrDefault("acqHands", 0)).intValue();
            return ctx.run.handsPlayedTotal - acq < max;
        }
    }

    /** A run variable modulo {@code mod} equals {@code remainder} (Loyalty Card: every 6 hands). */
    record RunVarModulo(Property which, int mod, int remainder) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.run == null || mod == 0) return false;
            return Math.floorMod((long) Value.readVar(which, ctx), mod) == remainder;
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

    /** This hand type is consistent with the round's single allowed type — either nothing has been
     *  played yet this round, or it matches what was (The Mouth's legality). */
    record RoundHandTypeConsistent() implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.run == null || ctx.handType == null) return true;
            var played = ctx.run.handTypesThisRound;
            return played.isEmpty() || played.contains(ctx.handType);
        }
    }

    /** The played poker hand is (one of) the most-played hand type(s) this run — The Ox's trigger. A tie
     *  counts: every hand sharing the maximum play count qualifies (read after this hand was counted). */
    record PlayedHandIsMostPlayed() implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.run == null || ctx.handType == null) return false;
            int mine = ctx.run.handTypePlays.getOrDefault(ctx.handType, 0);
            if (mine == 0) return false;
            int max = ctx.run.handTypePlays.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            return mine == max;
        }
    }

    /** The round just won was a Boss blind (END_OF_ROUND; Rocket). */
    record BossDefeated() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.bossDefeated;
        }
    }

    /** The blind just selected is a Boss blind (BLIND_SELECTED; Madness skips bosses). */
    record BossBlindSelected() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.bossBlind;
        }
    }

    /** A non-disabled Boss Blind ability is currently in play (Matador's $8). */
    record BossAbilityActive() implements Condition {
        public boolean test(EvaluationContext ctx) {
            return ctx.run != null && ctx.run.bossHasActiveAbility;
        }
    }

    /** The consumable in play is of this category ("Tarot" | "Planet" | "Spectral"). */
    record ConsumableType(String consumable) implements Condition {
        public boolean test(EvaluationContext ctx) {
            return consumable.equals(ctx.consumableType);
        }
    }

    /**
     * Probabilistic gate: true with probability {@code numerator/denominator},
     * scaled by {@code probabilityNumerator} (Oops! All 6s). Each evaluation pops
     * a roll from a game-long queue keyed by {@code seedKey}, so both players get
     * identical procs. Compose with {@link And} for "this card AND a chance".
     */
    record Chance(Odds odds, String seedKey) implements Condition {
        public boolean test(EvaluationContext ctx) {
            if (ctx.preview) return false; // preview shows the guaranteed floor — a gate never procs
            int probMult = ctx.run != null ? ctx.run.probabilityNumerator : 1;
            // The roll comes off the game-long queue; PROBABILITY_MULTIPLIER (Oops!) scales the threshold.
            return ctx.nextProb(seedKey) < (double) (odds.numerator() * probMult) / odds.denominator();
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
