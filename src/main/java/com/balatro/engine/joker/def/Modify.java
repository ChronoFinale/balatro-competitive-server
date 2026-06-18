package com.balatro.engine.joker.def;

import java.util.List;

/**
 * A modifier on a game variable — the <i>write</i> half of the same vocabulary conditions <i>read</i>.
 * A condition reads {@code MONEY} ({@code money >= 5}); a modifier writes it ({@code add(MONEY, 4)}).
 * Same nouns, opposite verbs — so this targets {@link Value.Var}, the existing game-variable enum,
 * not a parallel "aspect". Juggler's "+1 hand size" is {@code add(HAND_SIZE, 1)}; the Manacle's "-1"
 * is {@code add(HAND_SIZE, -1)} — same sentence, different card. Jokers, bosses, decks and vouchers
 * all contribute these, and one {@link #fold} resolves them.
 */
public record Modify(Value.Var variable, Op op, double value) {

    public enum Op { ADD, SET, MULTIPLY, MAX, MIN }

    public static Modify add(Value.Var variable, double value) { return new Modify(variable, Op.ADD, value); }

    public static Modify set(Value.Var variable, double value) { return new Modify(variable, Op.SET, value); }

    public static Modify multiply(Value.Var variable, double value) { return new Modify(variable, Op.MULTIPLY, value); }

    /** Raise the value to at least {@code value} — the highest tier wins (Seed Money/Money Tree cap, Overstock slots). */
    public static Modify max(Value.Var variable, double value) { return new Modify(variable, Op.MAX, value); }

    /** Lower the value to at most {@code value} — the deepest wins (Clearance/Liquidation price; Green's no-interest cap 0). */
    public static Modify min(Value.Var variable, double value) { return new Modify(variable, Op.MIN, value); }

    /**
     * Resolve {@code base} through every modifier for one variable, op-priority order so it is
     * independent of source order: SET replaces the base (last one wins — a boss override beats the
     * ruleset default), then ADD accumulates, then MULTIPLY scales, then MAX raises, then MIN clamps.
     * (MAX-before-MIN means a "no interest" MIN 0 beats a Money Tree MAX 20: max(20) then min(0) = 0.)
     */
    public static double fold(double base, Value.Var variable, List<Modify> mods) {
        double v = base;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.SET) v = m.value;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.ADD) v += m.value;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MULTIPLY) v *= m.value;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MAX) v = Math.max(v, m.value);
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MIN) v = Math.min(v, m.value);
        return v;
    }
}
