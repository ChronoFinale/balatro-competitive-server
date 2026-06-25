package com.balatro.engine.joker.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private static Value.Const c(double v) { return new Value.Const(v); }

    @Test
    void eachCellWritesItsField() { // (operation, subject) cells map to the right JokerEffect field
        assertThat(Effect.chips(c(50)).apply(CTX).chips).isEqualTo(50);
        assertThat(Effect.mult(c(4)).apply(CTX).mult).isEqualTo(4);
        assertThat(Effect.xMult(c(3)).apply(CTX).xMult).isEqualTo(3.0);   // (MULTIPLY, MULT)
        assertThat(Effect.powMult(c(2)).apply(CTX).powMult).isEqualTo(2.0); // (POWER, MULT)
        assertThat(Effect.dollars(c(7)).apply(CTX).dollars).isEqualTo(7);
        assertThat(Effect.retriggers(c(2)).apply(CTX).repetitions).isEqualTo(2);
        assertThat(Effect.heldMult(c(13)).apply(CTX).hMult).isEqualTo(13);
    }

    @Test
    void identityContributionsAreSkipped() {
        assertThat(Effect.mult(c(0)).apply(CTX)).isNull();   // +0 mult
        assertThat(Effect.xMult(c(1)).apply(CTX)).isNull();  // x1 mult
        assertThat(Effect.chips(c(0)).apply(CTX)).isNull();  // +0 chips
    }

    @Test
    void additiveOnlyTermsRejectMultiply() { // CHIPS has no x-accumulator slot — the empty grid cell fails loudly
        assertThatThrownBy(() -> new Effect.Score(Effect.Operation.MULTIPLY, Effect.Term.CHIPS, c(2)).apply(CTX))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyAllChainsTheEffectList() {
        // Scholar: +20 Chips and +4 Mult — one rule, two effects, chained via extra.
        JokerEffect r = Effect.applyAll(List.of(Effect.chips(c(20)), Effect.mult(c(4))), CTX);
        assertThat(r.chips).isEqualTo(20);
        assertThat(r.extra).isNotNull();
        assertThat(r.extra.mult).isEqualTo(4);
    }

    @Test
    void identitySkipDropsTheNoOpFromAChain() {
        JokerEffect r = Effect.applyAll(List.of(Effect.mult(c(0)), Effect.chips(c(50))), CTX);
        assertThat(r.chips).isEqualTo(50);
        assertThat(r.extra).isNull(); // the +0 mult contributed nothing
    }

    @Test
    void structuralEffectsCarryTheirPayload() {
        assertThat(new Effect.Destroy(new Selector.Focus()).apply(CTX).destroyScored).isTrue();
        assertThat(new Effect.Destroy(new Selector.Discarded()).apply(CTX).destroyEventCards).isTrue();
        assertThat(new Effect.Destroy(new Selector.Self()).apply(CTX).destroySelf).isTrue();
        assertThat(new Effect.CopyScored().apply(CTX).copyScored).isTrue();
    }
}
