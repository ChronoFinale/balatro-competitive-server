package com.balatro.engine.joker.def;

import com.balatro.engine.joker.EvaluationContext;
import java.util.List;

/**
 * A modifier on a game {@link Property} — the <i>write</i> half of the same vocabulary conditions <i>read</i>.
 * A condition reads {@code MONEY} ({@code money >= 5}); a modifier writes it ({@code add(MONEY, 4)}). Same
 * nouns, opposite verbs. Targets a {@link Property} (a {@link Hand} noun like {@code Hand.SIZE}, or a
 * run/shop/economy {@link Value.Var}).
 *
 * <p>The amount is a {@link Value}, not a constant — so a modifier can be DYNAMIC. Juggler's "+1 hand size"
 * is {@code add(Hand.SIZE, 1)} (a constant); Turtle Bean's decaying bonus and Skip-Off's cross-player diff
 * are {@code add(..., <a Value that reads run/joker state>)}. The {@code double} factories wrap the number in
 * a {@link Value.Const}; {@link #fold} resolves each modifier's Value against an {@link EvaluationContext}
 * (constant-only folds pass {@code null}).
 */
public record Modify(Property variable, Op op, Value value) {

    public enum Op { ADD, SET, MULTIPLY, MAX, MIN }

    public static Modify add(Property variable, double value) { return add(variable, new Value.Const(value)); }
    public static Modify set(Property variable, double value) { return set(variable, new Value.Const(value)); }
    public static Modify multiply(Property variable, double value) { return multiply(variable, new Value.Const(value)); }

    /** Raise the value to at least {@code value} — the highest tier wins (Seed Money/Money Tree cap, Overstock). */
    public static Modify max(Property variable, double value) { return max(variable, new Value.Const(value)); }

    /** Lower the value to at most {@code value} — the deepest wins (Clearance/Liquidation; Green's no-interest 0). */
    public static Modify min(Property variable, double value) { return min(variable, new Value.Const(value)); }

    // --- Value overloads: a DYNAMIC modifier whose amount is resolved per fold (Turtle Bean / Skip-Off) ---
    public static Modify add(Property variable, Value value) { return new Modify(variable, Op.ADD, value); }
    public static Modify set(Property variable, Value value) { return new Modify(variable, Op.SET, value); }
    public static Modify multiply(Property variable, Value value) { return new Modify(variable, Op.MULTIPLY, value); }
    public static Modify max(Property variable, Value value) { return new Modify(variable, Op.MAX, value); }
    public static Modify min(Property variable, Value value) { return new Modify(variable, Op.MIN, value); }

    /**
     * Resolve {@code base} through every modifier for one variable, op-priority order so it is independent of
     * source order: SET replaces the base (last wins), then ADD accumulates, then MULTIPLY scales, then MAX
     * raises, then MIN clamps. (MAX-before-MIN: a "no interest" MIN 0 beats a Money Tree MAX 20.) Each
     * modifier's amount is resolved via {@link Value#resolve} against {@code ctx} (null = constants only).
     */
    public static double fold(double base, Property variable, List<Modify> mods, EvaluationContext ctx) {
        double v = base;
        for (Modify m : mods) if (m.variable == variable && m.op == Op.SET) v = com.balatro.engine.eval.ValueResolver.resolve(m.value, ctx);
        for (Modify m : mods) if (m.variable == variable && m.op == Op.ADD) v += com.balatro.engine.eval.ValueResolver.resolve(m.value, ctx);
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MULTIPLY) v *= com.balatro.engine.eval.ValueResolver.resolve(m.value, ctx);
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MAX) v = Math.max(v, com.balatro.engine.eval.ValueResolver.resolve(m.value, ctx));
        for (Modify m : mods) if (m.variable == variable && m.op == Op.MIN) v = Math.min(v, com.balatro.engine.eval.ValueResolver.resolve(m.value, ctx));
        return v;
    }

    /** Constant-only fold (no run/joker state to read) — the common case for economy/slot/deck folds. */
    public static double fold(double base, Property variable, List<Modify> mods) {
        return fold(base, variable, mods, null);
    }
}
