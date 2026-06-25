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
 * <p>Dispatch per call: every rule whose trigger and condition match contributes, in authoring order —
 * their effects are concatenated and chained into one result. A state-write effect ({@link
 * Effect.MutateState}) self-gates to {@code blueprintDepth == 0 && !preview} and contributes nothing
 * (so it neither scores nor blocks later rules); two scoring rules on one trigger both apply.
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

    /**
     * True if the owned jokers collectively enable a boolean shop policy — fold their standing {@code mods}
     * for {@code var} and test {@code >= 1}. Lets a policy joker (Showman / Astronomer) declare its rule as
     * data ({@code mods(Modify.max(var, 1))}) instead of being matched by key-string at the shop call-site.
     */
    public static boolean policyEnabled(java.util.List<com.balatro.engine.joker.Joker> jokers, Value.Var var) {
        java.util.List<Modify> mods = new java.util.ArrayList<>();
        for (var j : jokers) if (j instanceof DataJoker dj) mods.addAll(dj.def().mods());
        return com.balatro.engine.eval.ModifyFolder.fold(0, var, mods) >= 1;
    }

    @Override
    public JokerEffect calculate(EvaluationContext ctx) {
        // Copiers (Blueprint/Brainstorm) are the higher-order case: re-run the selected joker's whole
        // calculation in this context for the current phase, and relabel the source. No own rules/state.
        if (def.copy() != null) {
            if (ctx.blueprintDepth > ctx.jokers.size()) return null; // recursion guard
            int t = copyTargetIndex(def.copy(), ctx);
            if (t < 0) return null;
            Joker target = ctx.jokers.get(t);
            if (!target.blueprintCompatible()) return null;
            JokerEffect e = target.calculate(ctx.forCopy(t));
            if (e != null) e.source = name() + " -> " + target.name();
            return e;
        }
        // Accumulate, rule by rule in authoring order: every rule matching this trigger contributes, and its
        // contributions chain onto the result. Each rule is fully applied before the next rule's condition is
        // evaluated — so a state write (Burnt's discard counter) is visible to a later rule's condition (its
        // "first discard?" check), and a condition is evaluated exactly once (no double chance/RNG draw). A
        // state write contributes null and is simply skipped; two scoring rules on one trigger both chain on.
        JokerEffect head = null;
        JokerEffect tail = null;
        for (Rule r : def.rules()) {
            if (r.when() != ctx.phase || !com.balatro.engine.eval.ConditionEvaluator.test(r.condition(), ctx)) continue;
            JokerEffect e = com.balatro.engine.eval.EffectInterpreter.applyAll(r.effects(), ctx);
            if (e == null) continue;
            if (head == null) head = e; else tail.extra = e;
            tail = e;
            while (tail.extra != null) tail = tail.extra; // a contribution may already carry its own chain
        }
        return head;
    }

    /** Index of the joker a {@link CopySpec} targets in {@code ctx}, or -1 if none / it would be self. */
    private static int copyTargetIndex(CopySpec spec, EvaluationContext ctx) {
        int idx = switch (spec.selector()) {
            case RIGHT_NEIGHBOR -> ctx.selfIndex + 1;
            case LEFTMOST -> 0;
        };
        if (idx < 0 || idx >= ctx.jokers.size() || idx == ctx.selfIndex) return -1;
        return idx;
    }
}
