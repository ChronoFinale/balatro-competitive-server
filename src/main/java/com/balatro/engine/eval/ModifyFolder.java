package com.balatro.engine.eval;

import com.balatro.engine.joker.EvaluationContext;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Modify;
import com.balatro.grammar.Property;
import java.util.List;

/**
 * The interpreter for the {@link Modify} grammar — folds a list of modifiers onto a base value. Lives in the
 * engine (each modifier's amount is a {@link com.balatro.grammar.Value} resolved via
 * {@link ValueResolver}); {@link Modify} itself stays pure data. Behaviour-identical to the old
 * {@code Modify.fold} — only the dispatch moved here.
 */
public final class ModifyFolder {

    private ModifyFolder() {}

    /**
     * Resolve {@code base} through every modifier for one variable in op-priority order (so it is independent
     * of source order): SET replaces (last wins), then ADD accumulates, then MULTIPLY scales, then MAX raises,
     * then MIN clamps. (MAX-before-MIN: a "no interest" MIN 0 beats a Money Tree MAX 20.)
     */
    public static double fold(double base, Property variable, List<Modify> mods, EvaluationContext ctx) {
        double v = base;
        for (Modify m : mods) if (m.variable() == variable && m.op() == Effect.Operation.SET) v = ValueResolver.resolve(m.value(), ctx);
        for (Modify m : mods) if (m.variable() == variable && m.op() == Effect.Operation.ADD) v += ValueResolver.resolve(m.value(), ctx);
        for (Modify m : mods) if (m.variable() == variable && m.op() == Effect.Operation.MULTIPLY) v *= ValueResolver.resolve(m.value(), ctx);
        for (Modify m : mods) if (m.variable() == variable && m.op() == Effect.Operation.MAX) v = Math.max(v, ValueResolver.resolve(m.value(), ctx));
        for (Modify m : mods) if (m.variable() == variable && m.op() == Effect.Operation.MIN) v = Math.min(v, ValueResolver.resolve(m.value(), ctx));
        return v;
    }

    /** Constant-only fold (no run/joker state to read) — the common case for economy/slot/deck folds. */
    public static double fold(double base, Property variable, List<Modify> mods) {
        return fold(base, variable, mods, null);
    }
}
