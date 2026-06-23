package com.balatro.dsl;

import com.balatro.engine.joker.def.Subject;
import com.balatro.engine.joker.def.*;


import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import java.util.List;

/**
 * Fluent authoring sugar for {@link Condition}s — predicates that read like English and give IDE
 * autocomplete, producing the same JSON-serializable {@link Condition} records (the source of truth).
 *
 * <p>Two card-ish subjects, named precisely:
 * <ul>
 *   <li>{@link #card()} — the single current card (the moment — forEachScored / forEachHeld — decides
 *       which card that is; the predicate "is it a Diamond" is the same in every context),</li>
 *   <li>{@link #playedHand()} — the poker hand you played: its type, its size, its faces/suits.</li>
 * </ul>
 * plus {@link #held()}, {@link #discard()}, {@link #using()}, {@link #state(String)}.
 */
public final class Cond {

    private Cond() {}

    public static Condition always() { return new Condition.Always(); }

    public static Condition all(Condition... cs) { return new Condition.And(List.of(cs)); }

    public static Condition any(Condition... cs) { return new Condition.Or(List.of(cs)); }

    public static Condition not(Condition c) { return new Condition.Not(c); }

    /** The blind just selected is a Boss blind (use {@code not(bossBlind())} for "Small/Big only"). */
    public static Condition bossBlind() { return new Condition.BossBlindSelected(); }

    /** A non-disabled Boss Blind ability is in play right now (Matador). */
    public static Condition bossAbilityActive() { return new Condition.BossAbilityActive(); }

    /** The round just won was a Boss blind (END_OF_ROUND; Rocket, Campfire reset). */
    public static Condition bossDefeated() { return new Condition.BossDefeated(); }

    /** Currently in a PvP (Nemesis) boss blind (Conjoined; {@code not(inPvpBlind())} = Pacifist). */
    public static Condition inPvpBlind() { return new Condition.InPvpBlind(); }

    /** Entered the PvP blind before the Nemesis (Speedrun) — the Match supplies the answer on the context. */
    public static Condition reachedPvpFirst() { return new Condition.ReachedPvpFirst(); }

    /** A {@code numerator}-in-{@code denominator} probabilistic gate, popped from a queue keyed by {@code seed}. */
    public static Condition chance(int numerator, int denominator, String seed) {
        return new Condition.Chance(numerator, denominator, seed);
    }

    /** Fewer than {@code max} hands have been played since this joker was acquired (Seltzer). */
    public static Condition handsSinceAcquired(int max) { return new Condition.HandsSinceAcquire(max); }

    /** The reacted-to joker (ON_OTHER_JOKER) is of {@code rarity} (Baseball Card). */
    public static Condition otherJokerRarity(String rarity) { return new Condition.OtherJokerRarity(rarity); }

    /** A run variable modulo {@code mod} equals {@code remainder} (Loyalty Card: every 6 hands). */
    public static Condition runVarModulo(Subject which, int mod, int remainder) {
        return new Condition.RunVarModulo(which, mod, remainder);
    }

    /** The single current card (which card depends on the moment: scored / held / discarded). */
    public static CardC card() { return CardC.I; }

    /** The poker hand you played (its type, size, and the faces/suits among its scoring cards). */
    public static HandC playedHand() { return HandC.I; }

    /** The cards still held in your hand. */
    public static HeldC held() { return HeldC.I; }

    /** The discarded set (PRE_DISCARD). */
    public static DiscardC discard() { return DiscardC.I; }

    /** The consumable being used (USE_CONSUMABLE). */
    public static UsingC using() { return UsingC.I; }

    /** This joker's persistent state counter. */
    public static StateRef state(String var) { return new StateRef(var); }

    /** A live run-state quantity (money, hands left, ante, …) to compare against. */
    public static Compare runVar(Subject v) { return new Compare(new Value.RunVar(v, 0, 1)); }

    /** Any {@link Value} (a card count, a derived quantity) to compare against. */
    public static Compare value(Value v) { return new Compare(v); }

    public static final class CardC {
        static final CardC I = new CardC();
        public Condition suit(Suit s) { return new Condition.ScoredSuit(s); }
        public Condition even() { return new Condition.ScoredParity(true); }
        public Condition odd() { return new Condition.ScoredParity(false); }
        public Condition rankBetween(int lo, int hi) { return new Condition.ScoredRankBetween(lo, hi); }
        public Condition isFace() { return new Condition.ScoredIsFace(); }
        /** This card is first in scoring order (Photograph, Hanging Chad). */
        public Condition isFirst() { return new Condition.ScoredFirst(); }
        /** This card is among the first {@code n} scored (MP Hanging Chad). */
        public Condition amongFirst(int n) { return new Condition.ScoredAmongFirst(n); }
        /** This card is the first scored FACE card (Photograph). */
        public Condition firstFace() { return new Condition.ScoredFirstFace(); }
        public Condition enhancement(Enhancement e) { return new Condition.ScoredEnhancement(e); }
        /** This card has already been played at some point this ante (The Pillar). */
        public Condition playedThisAnte() { return new Condition.ScoredPlayedThisAnte(); }
        /** Suit matches this round's rolled target under {@code key} (Ancient/Castle/Idol-suit). */
        public Condition suitIsTarget(String key) { return new Condition.ScoredSuit(null, key); }
        /** Rank matches this round's rolled target under {@code key} (Rebate/Idol-rank). */
        public Condition rankIsTarget(String key) { return new Condition.ScoredRankIsTarget(key); }
    }

    public static final class HandC {
        static final HandC I = new HandC();
        public Condition contains(HandType t) { return new Condition.HandContains(t); }
        public Condition containsPair() { return new Condition.HandContainsPair(); }
        public Condition is(HandType t) { return new Condition.HandIs(t); }
        public Condition isTarget(String key) { return new Condition.HandIs(null, key); }
        public Condition sizeAtMost(int n) { return new Condition.PlayedCount(Condition.Cmp.LTE, n); }
        public Condition sizeAtLeast(int n) { return new Condition.PlayedCount(Condition.Cmp.GTE, n); }
        public Condition sizeExactly(int n) { return new Condition.PlayedCount(Condition.Cmp.EQ, n); }
        public Condition hasFace() { return new Condition.ScoringAnyFace(); }
        public Condition hasNoFace() { return new Condition.Not(new Condition.ScoringAnyFace()); }
        public Condition hasSuit(Suit s) { return new Condition.ScoringContainsSuit(s); }
        /** This poker hand has not yet been played this round (The Eye's legality). */
        public Condition firstTimeThisRound() { return new Condition.Not(new Condition.HandPlayedThisRound()); }
        /** This poker hand matches the round's single established type, or none played yet (The Mouth's legality). */
        public Condition matchesRoundType() { return new Condition.RoundHandTypeConsistent(); }
        /** This poker hand has already been played earlier this round (Card Sharp). */
        public Condition repeatedThisRound() { return new Condition.HandPlayedThisRound(); }
    }

    public static final class HeldC {
        static final HeldC I = new HeldC();
        /** Every card held in hand is one of {@code suits} (empty hand counts true; Blackboard). */
        public Condition allSuits(Suit... suits) { return new Condition.HeldAllSuits(List.of(suits)); }
    }

    public static final class DiscardC {
        static final DiscardC I = new DiscardC();
        public Condition faces(int min) { return new Condition.DiscardedFaceCount(min); }
    }

    public static final class UsingC {
        static final UsingC I = new UsingC();
        public Condition is(String type) { return new Condition.ConsumableType(type); }
    }

    public static final class StateRef {
        private final String var;
        StateRef(String var) { this.var = var; }
        public Condition atLeast(double min) { return new Condition.Compare(var, Condition.Cmp.GTE, min); }
        public Condition atMost(double max) { return new Condition.Compare(var, Condition.Cmp.LTE, max); }
        public Condition exactly(double n) { return new Condition.Compare(var, Condition.Cmp.EQ, n); }
    }

    /** Fluent tail for a value comparison: {@code runVar(MONEY).atLeast(5)}, {@code state("x").atMost(2)}. */
    public static final class Compare {
        private final Value value;
        Compare(Value value) { this.value = value; }
        public Condition atLeast(double n) { return new Condition.Compare(value, Condition.Cmp.GTE, n); }
        public Condition atMost(double n) { return new Condition.Compare(value, Condition.Cmp.LTE, n); }
        public Condition exactly(double n) { return new Condition.Compare(value, Condition.Cmp.EQ, n); }
    }
}
