package com.balatro.engine.joker.def;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The sealed {@link Effect} interpreter: each numeric op writes the right {@link JokerEffect} field,
 * identity contributions (+0 / x1) are skipped, and a rule's effect list chains via the {@code extra}
 * field (the chain is now just an ordered list).
 */
class EffectApplyTest {

    private static final EvaluationContext CTX = new EvaluationContext();
    private static Effect.Score score(Effect.Op op, double v) { return new Effect.Score(op, new Value.Const(v)); }

    @Test
    void eachNumericOpWritesItsField() {
        assertThat(score(Effect.Op.CHIPS, 50).apply(CTX).chips).isEqualTo(50);
        assertThat(score(Effect.Op.MULT, 4).apply(CTX).mult).isEqualTo(4);
        assertThat(score(Effect.Op.XMULT, 3).apply(CTX).xMult).isEqualTo(3.0);
        assertThat(score(Effect.Op.POW_MULT, 2).apply(CTX).powMult).isEqualTo(2.0);
        assertThat(score(Effect.Op.DOLLARS, 7).apply(CTX).dollars).isEqualTo(7);
        assertThat(score(Effect.Op.REPETITIONS, 2).apply(CTX).repetitions).isEqualTo(2);
        assertThat(score(Effect.Op.HELD_MULT, 13).apply(CTX).hMult).isEqualTo(13);
    }

    @Test
    void identityContributionsAreSkipped() {
        assertThat(score(Effect.Op.MULT, 0).apply(CTX)).isNull();   // +0 mult
        assertThat(score(Effect.Op.XMULT, 1).apply(CTX)).isNull();  // x1 mult
        assertThat(score(Effect.Op.CHIPS, 0).apply(CTX)).isNull();  // +0 chips
    }

    @Test
    void applyAllChainsTheEffectList() {
        // Scholar: +20 Chips and +4 Mult — one rule, two effects, chained via extra.
        JokerEffect r = Effect.applyAll(List.of(score(Effect.Op.CHIPS, 20), score(Effect.Op.MULT, 4)), CTX);
        assertThat(r.chips).isEqualTo(20);
        assertThat(r.extra).isNotNull();
        assertThat(r.extra.mult).isEqualTo(4);
    }

    @Test
    void identitySkipDropsTheNoOpFromAChain() {
        JokerEffect r = Effect.applyAll(List.of(score(Effect.Op.MULT, 0), score(Effect.Op.CHIPS, 50)), CTX);
        assertThat(r.chips).isEqualTo(50);
        assertThat(r.extra).isNull(); // the +0 mult contributed nothing
    }

    @Test
    void structuralEffectsCarryTheirPayload() {
        assertThat(new Effect.DestroyScored().apply(CTX).destroyScored).isTrue();
        assertThat(new Effect.DestroyDiscarded().apply(CTX).destroyEventCards).isTrue();
        assertThat(new Effect.CopyScored().apply(CTX).copyScored).isTrue();
    }
}
