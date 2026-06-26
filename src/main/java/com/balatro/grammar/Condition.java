package com.balatro.grammar;

import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * A data-expressible predicate — the "when does this joker's effect apply" half of a data-driven joker.
 * PURE DATA: a {@code Condition} is an AST node with no behaviour; {@code com.balatro.engine.eval.
 * ConditionEvaluator} tests it against an {@code EvaluationContext}. Closed set of building blocks the
 * builder UI exposes as dropdowns; serialized to JSON with a {@code "type"} discriminator. Every predicate
 * is null-safe in the evaluator: one reading a context field absent for the trigger returns false.
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

    /** Comparison for count-style conditions — pure data (no behavior); the test lives in
     *  {@code com.balatro.engine.eval.ConditionEvaluator}. */
    enum Cmp { LTE, GTE, EQ }

    /** Unconditional. */
    record Always() implements Condition {}

    /** The scoring card is of a suit — fixed {@code suit}, or (when {@code targetKey} is set) this round's
     *  rolled target suit under that key (Ancient/Castle/Idol-suit). Stone never matches; Wild matches any. */
    record ScoredSuit(Suit suit, String targetKey) implements Condition {
        public ScoredSuit(Suit suit) { this(suit, null); }
    }

    /** Even ({@code true}: 10/8/6/4/2) or odd ({@code false}: A/9/7/5/3) rank (Even Steven / Odd Todd) —
     *  Ace counts as odd, faces as neither (Stone never matches). */
    record ScoredParity(boolean even) implements Condition {}

    /** The scoring card is a face card (J/Q/K). */
    record ScoredIsFace() implements Condition {}

    /** The card has already been played this ante (The Pillar's debuff). */
    record ScoredPlayedThisAnte() implements Condition {}

    /** The scoring card's rank id is in {@code [min, max]} (Hack: 2..5). */
    record ScoredRankBetween(int min, int max) implements Condition {}

    /** The scoring card is the first card in scoring order (Photograph, Hanging Chad). */
    record ScoredFirst() implements Condition {}

    /** The scoring card has a given enhancement. */
    record ScoredEnhancement(Enhancement enhancement) implements Condition {}

    /** The scoring card has a given edition. */
    record ScoredEdition(Edition edition) implements Condition {}

    /** The scoring card has a given seal. */
    record ScoredSeal(Seal seal) implements Condition {}

    /** The played poker hand contains a pair. */
    record HandContainsPair() implements Condition {}

    /** The played hand "contains" a hand category (Balatro's exact-count containment). */
    record HandContains(HandType hand) implements Condition {}

    /** The played hand is exactly a type — fixed {@code hand}, or this round's rolled target (To Do List). */
    record HandIs(HandType hand, String targetKey) implements Condition {
        public HandIs(HandType hand) { this(hand, null); }
    }

    /** Number of cards played compares to {@code n}. */
    record PlayedCount(Cmp cmp, int n) implements Condition {}

    /** At least {@code min} face cards in the event set (e.g. a discard). */
    record DiscardedFaceCount(int min) implements Condition {}

    /** Any of the scoring cards is a face card. */
    record ScoringAnyFace() implements Condition {}

    /** The scoring card is among the first {@code n} cards in scoring order (MP Hanging Chad). */
    record ScoredAmongFirst(int n) implements Condition {}

    /** The scoring card is the first FACE card in scoring order (Photograph). */
    record ScoredFirstFace() implements Condition {}

    /**
     * The one numeric-comparison primitive: resolve a {@link Value}, compare it to {@code threshold} with a
     * {@link Cmp}. Absorbs the old MoneyAtLeast / HandsLeft / Ante / StateAtLeast — each was "read a quantity,
     * compare to a number". The quantity reuses the full {@link Value} vocabulary.
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
    }

    /** Every held card is one of {@code suits} (empty hand counts as true; Blackboard). */
    record HeldAllSuits(List<Suit> suits) implements Condition {}

    /** At least one scoring card is of the given suit (Flower Pot, Seeing Double). */
    record ScoringContainsSuit(Suit suit) implements Condition {}

    /** The scored card's rank is this round's target rank under {@code key} (Rebate, Idol-rank). */
    record ScoredRankIsTarget(String key) implements Condition {}

    /** Currently in a PvP (Nemesis) boss blind — Pacifist (negated) / Conjoined. */
    record InPvpBlind() implements Condition {}

    /** Entered the PvP blind before the Nemesis — Speedrun (the Match supplies the answer on the context). */
    record ReachedPvpFirst() implements Condition {}

    /** Fewer than {@code max} hands played since this joker was acquired (Seltzer). */
    record HandsSinceAcquire(int max) implements Condition {}

    /** A run variable modulo {@code mod} equals {@code remainder} (Loyalty Card: every 6 hands). */
    record RunVarModulo(Property which, int mod, int remainder) implements Condition {}

    /** The joker being reacted to (ON_OTHER_JOKER) is of the given rarity (Baseball Card). */
    record OtherJokerRarity(Rarity rarity) implements Condition {}

    /** The current poker hand has already been played earlier this round (Card Sharp). */
    record HandPlayedThisRound() implements Condition {}

    /** This hand type is consistent with the round's single allowed type (The Mouth's legality). */
    record RoundHandTypeConsistent() implements Condition {}

    /** The played hand is (one of) the most-played hand type(s) this run — The Ox (ties count). */
    record PlayedHandIsMostPlayed() implements Condition {}

    /** The round just won was a Boss blind (END_OF_ROUND; Rocket). */
    record BossDefeated() implements Condition {}

    /** The blind just selected is a Boss blind (BLIND_SELECTED; Madness skips bosses). */
    record BossBlindSelected() implements Condition {}

    /** A non-disabled Boss Blind ability is currently in play (Matador's $8). */
    record BossAbilityActive() implements Condition {}

    /** The consumable in play is of this category ("Tarot" | "Planet" | "Spectral"). */
    record ConsumableType(ConsumableKind consumable) implements Condition {}

    /**
     * A probabilistic gate at {@link Odds}, rolled off a game-long queue. {@code stream} names a DEDICATED
     * source ({@code lucky_mult}, {@code glass}) for the card modifiers whose rolls Balatro keeps on their
     * own stream; when null/blank the roll uses the shared {@code prob:seedKey} source. Scaled by Oops!.
     */
    record Chance(Odds odds, String seedKey, String stream) implements Condition {
        /** Shared-PROB chance (the common case): rolls on {@code prob:seedKey}. */
        public Chance(Odds odds, String seedKey) {
            this(odds, seedKey, null);
        }
    }

    /** All sub-conditions hold. */
    record And(List<Condition> all) implements Condition {}

    /** Any sub-condition holds. */
    record Or(List<Condition> any) implements Condition {}

    /** Negation. */
    record Not(Condition inner) implements Condition {}
}
