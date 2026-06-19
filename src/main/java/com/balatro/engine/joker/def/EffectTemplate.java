package com.balatro.engine.joker.def;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerEffect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A data effect: an operation plus the {@link Value} it contributes. {@link #apply}
 * resolves the value and builds the matching {@link JokerEffect}, or returns
 * {@code null} when the result is the identity (no-op): +0 additive, x1.0
 * multiplicative, 0 retriggers. That identity-skip is what lets a scaling joker
 * (streak 0, planets 0) contribute nothing until it has ramped — exactly the
 * "return null when zero" behaviour of the hand-coded jokers.
 */
public record EffectTemplate(Op op, Value value, EffectTemplate extra, CardMod cardMod, CreateSpec create) {

    public enum Op { CHIPS, MULT, XMULT, POW_MULT, DOLLARS, REPETITIONS, HELD_MULT, MUTATE_CARD,
        CREATE, DESTROY_SCORED, DESTROY_DISCARDED, LEVEL_UP_HAND, COPY_SCORED }

    /** Add a permanent copy of the scoring card to the deck (DNA). */
    public static EffectTemplate copyScored() {
        return new EffectTemplate(Op.COPY_SCORED, null, null, null, null);
    }

    /** Level up the current poker hand by {@code levels} (Space/Burnt). */
    public static EffectTemplate levelUpHand(int levels) {
        return new EffectTemplate(Op.LEVEL_UP_HAND, new Value.Const(levels), null, null, null);
    }

    /** A pure destroy effect (destroys the scoring card; no numeric contribution). */
    public static EffectTemplate destroyScored() {
        return new EffectTemplate(Op.DESTROY_SCORED, null, null, null, null);
    }

    /** Destroy the discarded cards (the PRE_DISCARD event set) — Trading Card. */
    public static EffectTemplate destroyDiscarded() {
        return new EffectTemplate(Op.DESTROY_DISCARDED, null, null, null, null);
    }

    // Explicit canonical creator so the convenience constructors don't confuse Jackson.
    @JsonCreator
    public EffectTemplate(@JsonProperty("op") Op op, @JsonProperty("value") Value value,
            @JsonProperty("extra") EffectTemplate extra, @JsonProperty("cardMod") CardMod cardMod,
            @JsonProperty("create") CreateSpec create) {
        this.op = op;
        this.value = value;
        this.extra = extra;
        this.cardMod = cardMod;
        this.create = create;
    }

    /** Single-op effect (no extra chain). */
    public EffectTemplate(Op op, Value value) {
        this(op, value, null, null, null);
    }

    /** Compound effect (op + chained extra). */
    public EffectTemplate(Op op, Value value, EffectTemplate extra) {
        this(op, value, extra, null, null);
    }

    /** Numeric + card-mutation effect. */
    public EffectTemplate(Op op, Value value, EffectTemplate extra, CardMod cardMod) {
        this(op, value, extra, cardMod, null);
    }

    /** A pure card mutation (no numeric contribution). */
    public static EffectTemplate mutate(CardMod mod) {
        return new EffectTemplate(Op.MUTATE_CARD, null, null, mod, null);
    }

    /** A pure create effect (no numeric contribution). */
    public static EffectTemplate create(CreateSpec spec) {
        return new EffectTemplate(Op.CREATE, null, null, null, spec);
    }

    /** Start a compound numeric chain: {@code of(CHIPS, 20).and(MULT, 4)} (Scholar). */
    public static EffectTemplate of(Op op, double amount) {
        return new EffectTemplate(op, new Value.Const(amount));
    }

    /** Append another numeric op to this effect's chain (+20 Chips <b>and</b> +4 Mult). */
    public EffectTemplate and(Op op, double amount) {
        return andThen(new EffectTemplate(op, new Value.Const(amount)));
    }

    /** Append any effect to the end of this one's chain (Sixth Sense: destroy <b>then</b> create). */
    public EffectTemplate andThen(EffectTemplate next) {
        return extra == null
                ? new EffectTemplate(op, value, next, cardMod, create)
                : new EffectTemplate(op, value, extra.andThen(next), cardMod, create);
    }

    /**
     * Apply by converting to the sealed {@link Effect} list and running the one interpreter — the
     * serializable {@code EffectTemplate} is now a thin shim over {@link Effect} (doc-42 stage 2; the
     * interpreter logic lives in {@code Effect}, no longer duplicated here).
     */
    public JokerEffect apply(EvaluationContext ctx) {
        return Effect.applyAll(toEffects(), ctx);
    }

    /** Decompose this template (op + payload + extra chain) into the equivalent ordered {@link Effect}s. */
    public java.util.List<Effect> toEffects() {
        java.util.List<Effect> out = new java.util.ArrayList<>();
        switch (op) {
            case CHIPS, MULT, XMULT, POW_MULT, DOLLARS, REPETITIONS, HELD_MULT ->
                    out.add(new Effect.Score(Effect.Op.valueOf(op.name()), value));
            case DESTROY_SCORED -> out.add(new Effect.DestroyScored());
            case DESTROY_DISCARDED -> out.add(new Effect.DestroyDiscarded());
            case LEVEL_UP_HAND -> out.add(new Effect.LevelUpHand(value));
            case COPY_SCORED -> out.add(new Effect.CopyScored());
            case MUTATE_CARD, CREATE -> { } // carried by cardMod / create below
        }
        if (cardMod != null) out.add(new Effect.MutateCard(cardMod));
        if (create != null) out.add(new Effect.Create(create));
        if (extra != null) out.addAll(extra.toEffects());
        return out;
    }
}
