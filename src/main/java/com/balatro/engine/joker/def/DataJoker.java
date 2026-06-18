package com.balatro.engine.joker.def;

import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerEffect;
import com.balatro.engine.joker.JokerInfo;

/**
 * Interprets a {@link JokerDef} as a live {@link Joker}. All logic stays
 * server-side — the def is data, the interpretation is here — so the client still
 * only ever sees {@link JokerInfo}; a custom joker is exactly as cheat-proof as a
 * hand-coded one. One instance per joker in a run, so per-joker state is keyed by
 * identity just like the hand-coded jokers.
 *
 * <p>Dispatch per call: apply any matching state {@link Mutation}s first (only at
 * {@code blueprintDepth == 0}), then return the effect of the first matching
 * {@link Rule} for the current trigger.
 */
public final class DataJoker implements Joker {

    private final JokerDef def;

    public DataJoker(JokerDef def) {
        this.def = def;
    }

    public JokerDef def() {
        return def;
    }

    @Override
    public JokerInfo info() {
        return def.info();
    }

    @Override
    public boolean blueprintCompatible() {
        return def.blueprintCompatible();
    }

    @Override
    public Object prop(String name) {
        return def.props().get(name);
    }

    @Override
    public JokerEffect calculate(EvaluationContext ctx) {
        // Copiers (Blueprint/Brainstorm) are the higher-order case: re-run the selected joker's whole
        // calculation in this context for the current phase, and relabel the source. No own rules/state.
        if (def.copy() != null) {
            if (ctx.blueprintDepth > ctx.jokers.size()) return null; // recursion guard
            int t = def.copy().targetIndex(ctx);
            if (t < 0) return null;
            Joker target = ctx.jokers.get(t);
            if (!target.blueprintCompatible()) return null;
            JokerEffect e = target.calculate(ctx.forCopy(t));
            if (e != null) e.source = name() + " -> " + target.name();
            return e;
        }
        // Skip state mutations during a preview dry-run: previewing a hand must not
        // advance scaling counters (Ride the Bus's streak, etc.).
        if (ctx.blueprintDepth == 0 && !ctx.preview) {
            for (Mutation m : def.mutations()) {
                if (m.when() == ctx.phase && m.condition().test(ctx)) {
                    m.apply(ctx);
                }
            }
        }
        for (Rule r : def.rules()) {
            if (r.when() == ctx.phase && r.condition().test(ctx)) {
                JokerEffect e = r.effect().apply(ctx);
                if (e != null) return e;
            }
        }
        return null;
    }
}
