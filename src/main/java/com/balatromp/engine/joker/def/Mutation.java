package com.balatromp.engine.joker.def;

import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Trigger;

/**
 * A persistent, server-only state change for a scaling joker (Ride the Bus's
 * streak, Constellation's planet count). When {@code when} is raised and
 * {@code condition} holds, the named variable is updated. Mutations are applied
 * only at {@code blueprintDepth == 0}, so a Blueprint copy re-reads the counter
 * without advancing it twice.
 */
public record Mutation(Trigger when, Condition condition, String var, Op op, double by) {

    public enum Op { ADD, SET, RESET }

    public void apply(EvaluationContext ctx) {
        Object cur = ctx.selfState().getOrDefault(var, 0);
        double n = (cur instanceof Number num) ? num.doubleValue() : 0;
        double next = switch (op) {
            case ADD -> n + by;
            case SET -> by;
            case RESET -> 0;
        };
        // Keep whole numbers as ints, matching the hand-coded jokers' state bags.
        if (next == Math.rint(next) && !Double.isInfinite(next)) {
            ctx.selfState().put(var, (int) next);
        } else {
            ctx.selfState().put(var, next);
        }
    }
}
