package com.balatromp.engine.joker.def;

import com.balatromp.engine.joker.EvaluationContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A number resolved at evaluation time — the magnitude half of a data effect. A
 * constant covers flat jokers (+4 Mult); {@link State} covers scaling jokers that
 * read a server-only per-joker counter (Ride the Bus's streak, Constellation's
 * planet count) as {@code base + scale * counter}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Value.Const.class, name = "const"),
    @JsonSubTypes.Type(value = Value.State.class, name = "state"),
})
public sealed interface Value {

    double resolve(EvaluationContext ctx);

    /** A fixed amount. */
    record Const(double amount) implements Value {
        public double resolve(EvaluationContext ctx) {
            return amount;
        }
    }

    /** {@code base + scale * (server-only state variable)}. */
    record State(String var, double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            Object v = ctx.selfState().getOrDefault(var, 0);
            double n = (v instanceof Number num) ? num.doubleValue() : 0;
            return base + scale * n;
        }
    }
}
