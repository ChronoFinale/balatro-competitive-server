package com.balatro.engine.game;

import com.balatro.engine.joker.def.Value;
import java.util.List;

/**
 * A modifier on a game variable — the <i>write</i> half of the same vocabulary conditions <i>read</i>.
 * A condition reads {@code MONEY} ({@code money >= 5}); a modifier writes it ({@code add(MONEY, 4)}).
 * Same nouns, opposite verbs — so this targets the existing {@link Value.Var}, not a parallel "aspect"
 * enum. Juggler's "+1 hand size" is {@code add(HAND_SIZE, 1)}; the Manacle's "-1" is {@code add(
 * HAND_SIZE, -1)} — same sentence, different card. Jokers, bosses, decks and vouchers all contribute
 * these, and one {@link #fold} resolves them.
 */
public record Modify(Value.Var variable, Op op, double value) {

    public enum Op { ADD, SET, MULTIPLY }

    public static Modify add(Value.Var variable, double value) { return new Modify(variable, Op.ADD, value); }

    public static Modify set(Value.Var variable, double value) { return new Modify(variable, Op.SET, value); }

    public static Modify multiply(Value.Var variable, double value) { return new Modify(variable, Op.MULTIPLY, value); }

    /**
     * Resolve {@code base} through every modifier for one variable, op-priority order so it is
     * independent of source order: a SET replaces the base (last one wins — a boss override beats
     * the ruleset default), then ADDs accumulate, then MULTIPLYs scale.
     */
    public static double fold(double base, Value.Var variable, List<Modify> mods) {
        double v = base;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.SET) v = m.value;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.ADD) v += m.value;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MULTIPLY) v *= m.value;
        return v;
    }
}
