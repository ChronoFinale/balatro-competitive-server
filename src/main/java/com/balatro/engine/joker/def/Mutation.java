package com.balatro.engine.joker.def;

import com.balatro.engine.card.Card;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Trigger;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A persistent, server-only state change for a scaling joker (Ride the Bus's
 * streak, Constellation's planet count). When {@code when} is raised and
 * {@code condition} holds, the named variable is updated. Mutations are applied
 * only at {@code blueprintDepth == 0}, so a Blueprint copy re-reads the counter
 * without advancing it twice.
 *
 * <p>If {@code perCard} is set, an {@code ADD} adds {@code by} times the number of
 * {@code eventCards} matching that per-card condition (Hit the Road: +0.5 per Jack
 * discarded). Otherwise {@code by} is added once.
 */
public record Mutation(Trigger when, Condition condition, String var, Op op, double by, Condition perCard,
                       Scope scope) {

    public enum Op { ADD, SET, RESET }

    /** Whose state bag the mutation writes to: the joker itself (Egg), or every owned joker (Gift Card). */
    public enum Scope { SELF, ALL_JOKERS }

    @JsonCreator
    public Mutation(@JsonProperty("when") Trigger when, @JsonProperty("condition") Condition condition,
            @JsonProperty("var") String var, @JsonProperty("op") Op op, @JsonProperty("by") double by,
            @JsonProperty("perCard") Condition perCard, @JsonProperty("scope") Scope scope) {
        this.when = when;
        this.condition = condition;
        this.var = var;
        this.op = op;
        this.by = by;
        this.perCard = perCard;
        this.scope = scope == null ? Scope.SELF : scope; // omitted in JSON ⇒ writes to self
    }

    /** A scalar mutation (adds/sets {@code by} once) on the joker's own state. */
    public Mutation(Trigger when, Condition condition, String var, Op op, double by) {
        this(when, condition, var, op, by, null, Scope.SELF);
    }

    /** A scalar mutation with an explicit target scope (e.g. Gift Card writes to every joker). */
    public Mutation(Trigger when, Condition condition, String var, Op op, double by, Scope scope) {
        this(when, condition, var, op, by, null, scope);
    }

    /** A per-card mutation on the joker's own state. */
    public Mutation(Trigger when, Condition condition, String var, Op op, double by, Condition perCard) {
        this(when, condition, var, op, by, perCard, Scope.SELF);
    }

    public void apply(EvaluationContext ctx) {
        double amount = by;
        if (perCard != null && ctx.eventCards != null) {
            int count = 0;
            Card prev = ctx.scoredCard;
            for (Card c : ctx.eventCards) {
                ctx.scoredCard = c;
                if (perCard.test(ctx)) count++;
            }
            ctx.scoredCard = prev;
            amount = by * count;
        }
        if (scope == Scope.ALL_JOKERS && ctx.run != null) {
            for (var j : ctx.run.jokers()) applyTo(ctx.run.jokerState(j), amount);
        } else {
            applyTo(ctx.selfState(), amount);
        }
    }

    /** Write the {@code op} of {@code amount} into one joker's state bag, keeping whole numbers as ints. */
    private void applyTo(java.util.Map<String, Object> state, double amount) {
        Object cur = state.getOrDefault(var, 0);
        double n = (cur instanceof Number num) ? num.doubleValue() : 0;
        double next = switch (op) {
            case ADD -> n + amount;
            case SET -> amount;
            case RESET -> 0;
        };
        if (next == Math.rint(next) && !Double.isInfinite(next)) {
            state.put(var, (int) next);
        } else {
            state.put(var, next);
        }
    }
}
