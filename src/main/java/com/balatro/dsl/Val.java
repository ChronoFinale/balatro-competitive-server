package com.balatro.dsl;

import com.balatro.grammar.Property;
import com.balatro.engine.joker.def.*;
import com.balatro.grammar.*;


import com.balatro.engine.card.Enhancement;

/**
 * Fluent authoring sugar for {@link Value}s — the magnitude half of an effect, read like a small
 * expression over the joker's declared bindings and the game context. Produces the same serializable
 * {@link Value} records (the source of truth); this just reads nicely in code, with autocomplete.
 *
 * <p>The point of {@link #prop(String)}: a joker's numbers are declared, named constants, so effects are
 * functions over those names ({@code mult(Val.prop("mult"))}) rather than anonymous magic literals.
 */
public final class Val {

    private Val() {}

    /** A fixed amount. */
    public static Value of(double amount) { return new Value.Const(amount); }

    /** A declared constant property of this joker (its named parameter). */
    public static Value prop(String name) { return new Value.Prop(name); }

    /** The raw value of a state counter (e.g. {@code add(MULT, state("streak"))} = +streak Mult). */
    public static Value state(String var) { return new Value.State(var, 0, 1); }

    /** {@code base + scale * (state counter)} — the general form (Rocket: {@code state("bosses", 1, 2)}). */
    public static Value state(String var, double base, double scale) { return new Value.State(var, base, scale); }

    /** {@code each} per unit of the counter — additive scaling (+{@code each} per unit). */
    public static Value perState(String var, double each) { return new Value.State(var, 0, each); }

    /** {@code 1 + each} per unit — the x-Mult convention (Constellation: {@code x(1 + 0.1·planets)}). */
    public static Value xPerState(String var, double each) { return new Value.State(var, 1, each); }

    /** {@code base + scale * floor(state / per)} — stepwise state scaling (Yorick: x1 per 23 discarded). */
    public static Value stateStep(String var, double base, double scale, double per) {
        return new Value.StateStep(var, base, scale, per);
    }

    // --- live run-state quantities ---

    /** The raw value of a run variable (Money/Ante/Hand.PLAYS/…). */
    public static Value runVar(Property v) { return new Value.RunVar(v, 0, 1); }

    /** {@code base + scale * (run variable)} — the general form. */
    public static Value runVar(Property v, double base, double scale) { return new Value.RunVar(v, base, scale); }

    /** {@code scale} per unit of a run variable (Banner: +30 Chips per remaining discard). */
    public static Value perRunVar(Property v, double scale) { return new Value.RunVar(v, 0, scale); }

    /** {@code 1 + scale} per unit — the x-Mult convention over a run variable (Obelisk, Throwback). */
    public static Value xPerRunVar(Property v, double scale) { return new Value.RunVar(v, 1, scale); }

    /** {@code base + scale * floor(runVar / per)} — stepwise run-var scaling (Bootstraps: +2 per $5). */
    public static Value runVarStep(Property v, double base, double scale, double per) {
        return new Value.RunVarStep(v, base, scale, per);
    }

    // --- deck/joker statistics ---

    /** {@code base + scale * (a deck/joker statistic)} — Blue Joker, Abstract, Joker Stencil, ... */
    public static Value stat(Value.Which which, double base, double scale) {
        return new Value.Stat(which, base, scale, null);
    }

    /** A statistic counting cards of one {@code enhancement} (Stone/Steel Joker). */
    public static Value stat(Value.Which which, double base, double scale, Enhancement enhancement) {
        return new Value.Stat(which, base, scale, enhancement);
    }

    /** {@code base + scale * (cards in {@code source} matching {@code match})} — composes per-card predicates. */
    public static Value count(Value.Source source, Condition match, double base, double scale) {
        return new Value.Count(source, match, base, scale);
    }

    /** {@code base + scale * (number of N-rank cards in the full deck)} (Cloud 9 = 9s). */
    public static Value deckRankCount(int rankId, double base, double scale) {
        return new Value.DeckRankCount(rankId, base, scale);
    }

    /** {@code base + scale * (times the current poker hand has been played this run)} — Supernova. */
    public static Value handTypePlays(double base, double scale) { return new Value.HandTypePlays(base, scale); }

    /** {@code base + scale * (sum of OTHER owned jokers' sell value)} — Swashbuckler. */
    public static Value otherJokersSellSum(double base, double scale) {
        return new Value.OtherJokersSellSum(base, scale);
    }

    /** {@code base + scale * (rank of the lowest / highest card held in hand)} — Raised Fist. */
    public static Value lowestHeld(double base, double scale) { return new Value.HeldExtreme(true, base, scale); }
    public static Value highestHeld(double base, double scale) { return new Value.HeldExtreme(false, base, scale); }

    // --- shaping ---

    /** Clamp an inner value to {@code [min, max]} (decay jokers floor at 0/1: Ice Cream, Ramen). */
    public static Value clamp(Value inner, double min, double max) { return new Value.Clamp(inner, min, max); }

    /** Clamp an inner value to a floor (no upper bound) — the common decay case. */
    public static Value floorAt(double min, Value inner) { return new Value.Clamp(inner, min, 1e9); }

    /** The difference {@code left − right} — one quantity defined relative to another (Skip-Off's
     *  blinds-skipped-beyond-Nemesis, Turtle Bean's decay). Compose with {@link #floorAt} to clamp. */
    public static Value diff(Value left, Value right) { return new Value.Diff(left, right); }

    /** A uniform random amount in {@code [min, max]} keyed by {@code seed} (Misprint). */
    public static Value random(double min, double max, String seed) { return new Value.Random(min, max, seed); }
}
