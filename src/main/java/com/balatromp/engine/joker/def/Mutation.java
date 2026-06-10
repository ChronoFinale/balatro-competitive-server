package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Trigger;
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
public record Mutation(Trigger when, Condition condition, String var, Op op, double by, Condition perCard) {

    public enum Op { ADD, SET, RESET }

    @JsonCreator
    public Mutation(@JsonProperty("when") Trigger when, @JsonProperty("condition") Condition condition,
            @JsonProperty("var") String var, @JsonProperty("op") Op op, @JsonProperty("by") double by,
            @JsonProperty("perCard") Condition perCard) {
        this.when = when;
        this.condition = condition;
        this.var = var;
        this.op = op;
        this.by = by;
        this.perCard = perCard;
    }

    /** A scalar mutation (adds/sets {@code by} once). */
    public Mutation(Trigger when, Condition condition, String var, Op op, double by) {
        this(when, condition, var, op, by, null);
    }

    public void apply(EvaluationContext ctx) {
        Object cur = ctx.selfState().getOrDefault(var, 0);
        double n = (cur instanceof Number num) ? num.doubleValue() : 0;
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
        double next = switch (op) {
            case ADD -> n + amount;
            case SET -> amount;
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
