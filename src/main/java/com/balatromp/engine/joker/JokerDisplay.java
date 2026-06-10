package com.balatromp.engine.joker;

import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.state.RunState;
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

        JokerEffect e = jokers.get(index).calculate(ctx);
        StringBuilder sb = new StringBuilder();
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.message != null && !cur.message.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(cur.message);
            }
        }
        return sb.toString();
    }
}
