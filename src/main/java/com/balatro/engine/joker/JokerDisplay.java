package com.balatro.engine.joker;

import com.balatro.grammar.Trigger;

import com.balatro.engine.rng.RandomStreams;
import com.balatro.grammar.Effect;
import com.balatro.engine.state.RunState;
import java.util.List;

/**
 * Built-in joker display: the live value a joker would contribute right now —
 * what the JokerDisplay mod shows, but computed server-side so any client just
 * reads it (no mod needed). Because the server is authoritative and jokers are
 * data, we get this by dry-running the joker's main effect against current state
 * and reading the formatted message(s) the effect already carries
 * (e.g. "x1.4 Mult", "+12 Mult", "+50 Chips" for a scaling joker).
 *
 * <p>The dry-run is side-effect-free: a {@code preview} context so probabilistic
 * jokers don't consume the real RNG queues.
 */
public final class JokerDisplay {

    private static final RandomStreams PREVIEW_RNG = new RandomStreams("display");

    private JokerDisplay() {}

    /** The current display value for the joker at {@code index}, or "" if it has none. */
    public static String currentValue(List<Joker> jokers, int index, RunState run) {
        EvaluationContext ctx = new EvaluationContext();
        ctx.phase = Trigger.JOKER_MAIN;
        ctx.jokers = jokers;
        ctx.selfIndex = index;
        ctx.blueprintDepth = 0;
        ctx.run = run;
        ctx.rng = PREVIEW_RNG;
        ctx.preview = true;

        JokerResult e = jokers.get(index).calculate(ctx);
        StringBuilder sb = new StringBuilder();
        for (Contribution c : e.contributions()) {
            String label = label(c);
            if (!label.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(label);
            }
        }
        return sb.toString();
    }

    /** The joker's live value, DERIVED from a {@link Contribution}'s (op, term, amount) — not a pre-built
     *  string. This one display label is server-computed BY DESIGN — its purpose is that a thin client reads it
     *  directly — so it is built here, from data, in one place, rather than smuggled through the scorer. */
    private static String label(Contribution c) {
        return switch (c.op()) {
            case MULTIPLY -> switch (c.term()) {
                case MULT -> "x" + fmt(c.amount()) + " Mult";
                case CHIPS -> "x" + fmt(c.amount()) + " Chips";
                default -> "";
            };
            case POWER -> "^" + fmt(c.amount()) + " Mult";
            case ADD -> switch (c.term()) {
                case MULT, HELD_MULT -> "+" + fmt(c.amount()) + " Mult";
                case CHIPS -> "+" + fmt(c.amount()) + " Chips";
                case DOLLARS -> (c.amount() > 0 ? "+$" : "-$") + Math.abs((long) c.amount());
                case RETRIGGERS -> "Retrigger";
            };
            default -> "";
        };
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return Double.toString(v);
    }
}
