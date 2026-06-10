package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.CardMod;
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
public record EffectTemplate(Op op, Value value, EffectTemplate extra, CardMod cardMod) {

    public enum Op { CHIPS, MULT, XMULT, DOLLARS, REPETITIONS, HELD_MULT, MUTATE_CARD }

    /** Single-op effect (no extra chain). */
    public EffectTemplate(Op op, Value value) {
        this(op, value, null, null);
    }

    /** Compound effect (op + chained extra). */
    public EffectTemplate(Op op, Value value, EffectTemplate extra) {
        this(op, value, extra, null);
    }

    /** A pure card mutation (no numeric contribution). */
    public static EffectTemplate mutate(CardMod mod) {
        return new EffectTemplate(Op.MUTATE_CARD, null, null, mod);
    }

    /**
     * Build the effect: a numeric contribution (op + value) and/or a card mutation
     * ({@link #cardMod}), chaining {@link #extra} so one rule can emit a compound
     * effect (e.g. Scholar = +20 Chips and +4 Mult, or Vampire = xMult and strip
     * enhancement). Numeric and mutation rides {@link JokerEffect#extra}/{@code cardMod},
     * applied in order by the scoring engine.
     */
    public JokerEffect apply(EvaluationContext ctx) {
        JokerEffect head = (op == Op.MUTATE_CARD) ? null : build(value.resolve(ctx));
        if (cardMod != null) {
            if (head == null) head = new JokerEffect();
            head.cardMod = cardMod;
        }
        if (extra == null) {
            return head;
        }
        JokerEffect tail = extra.apply(ctx);
        if (head == null) {
            return tail;
        }
        head.extra = tail;
        return head;
    }

    private JokerEffect build(double v) {
        return switch (op) {
            case CHIPS -> v == 0 ? null : JokerEffect.chips(Math.round(v)).msg("+" + fmt(v) + " Chips");
            case MULT -> v == 0 ? null : JokerEffect.mult(v).msg("+" + fmt(v) + " Mult");
            case XMULT -> v == 1.0 ? null : JokerEffect.xMult(v).msg("x" + fmt(v) + " Mult");
            case DOLLARS -> v == 0 ? null : JokerEffect.dollars(Math.round(v)).msg("+$" + fmt(v));
            case REPETITIONS -> {
                int r = (int) Math.round(v);
                yield r == 0 ? null : JokerEffect.repetitions(r).msg("Retrigger");
            }
            case HELD_MULT -> {
                if (v == 0) yield null;
                JokerEffect e = new JokerEffect();
                e.hMult = v;
                yield e.msg("+" + fmt(v) + " Mult");
            }
            case MUTATE_CARD -> null; // handled in apply(); no numeric contribution
        };
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return Double.toString(v);
    }
}
