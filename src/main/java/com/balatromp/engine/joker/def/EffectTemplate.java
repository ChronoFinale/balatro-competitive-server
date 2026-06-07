package com.balatromp.engine.joker.def;

import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.JokerEffect;

/**
 * A data effect: an operation plus the {@link Value} it contributes. {@link #apply}
 * resolves the value and builds the matching {@link JokerEffect}, or returns
 * {@code null} when the result is the identity (no-op): +0 additive, x1.0
 * multiplicative, 0 retriggers. That identity-skip is what lets a scaling joker
 * (streak 0, planets 0) contribute nothing until it has ramped — exactly the
 * "return null when zero" behaviour of the hand-coded jokers.
 */
public record EffectTemplate(Op op, Value value) {

    public enum Op { CHIPS, MULT, XMULT, DOLLARS, REPETITIONS }

    public JokerEffect apply(EvaluationContext ctx) {
        double v = value.resolve(ctx);
        return switch (op) {
            case CHIPS -> v == 0 ? null : JokerEffect.chips(Math.round(v)).msg("+" + fmt(v) + " Chips");
            case MULT -> v == 0 ? null : JokerEffect.mult(v).msg("+" + fmt(v) + " Mult");
            case XMULT -> v == 1.0 ? null : JokerEffect.xMult(v).msg("x" + fmt(v) + " Mult");
            case DOLLARS -> v == 0 ? null : JokerEffect.dollars(Math.round(v)).msg("+$" + fmt(v));
            case REPETITIONS -> {
                int r = (int) Math.round(v);
                yield r == 0 ? null : JokerEffect.repetitions(r).msg("Retrigger");
            }
        };
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return Double.toString(v);
    }
}
