package com.balatro.engine.joker.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balatro.engine.eval.EffectInterpreter;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The {@link EffectInterpreter} over the pure-data {@link Effect} grammar: each numeric op writes the right
 * {@link JokerEffect} field, identity contributions (+0 / x1) are skipped, and a rule's effect list chains
 * via the {@code extra} field (the chain is now just an ordered list).
 */
class EffectApplyTest {

    private static final EvaluationContext CTX = new EvaluationContext();
    private static Value.Const c(double v) { return new Value.Const(v); }

    private static JokerEffect apply(Effect e) { return EffectInterpreter.apply(e, CTX); }

    @Test
    void eachCellWritesItsField() { // (operation, subject) cells map to the right JokerEffect field
        assertThat(apply(Effect.chips(c(50))).chips).isEqualTo(50);
        assertThat(apply(Effect.mult(c(4))).mult).isEqualTo(4);
        assertThat(apply(Effect.xMult(c(3))).xMult).isEqualTo(3.0);   // (MULTIPLY, MULT)
        assertThat(apply(Effect.powMult(c(2))).powMult).isEqualTo(2.0); // (POWER, MULT)
        assertThat(apply(Effect.dollars(c(7))).dollars).isEqualTo(7);
        assertThat(apply(Effect.retriggers(c(2))).repetitions).isEqualTo(2);
        assertThat(apply(Effect.heldMult(c(13))).hMult).isEqualTo(13);
    }

    @Test
    void identityContributionsAreSkipped() {
        assertThat(apply(Effect.mult(c(0)))).isNull();   // +0 mult
        assertThat(apply(Effect.xMult(c(1)))).isNull();  // x1 mult
        assertThat(apply(Effect.chips(c(0)))).isNull();  // +0 chips
    }

    @Test
    void additiveOnlyTermsRejectMultiply() { // CHIPS has no x-accumulator slot — the empty grid cell fails loudly
        assertThatThrownBy(() -> apply(new Effect.Score(Effect.Operation.MULTIPLY, Effect.Term.CHIPS, c(2))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyAllChainsTheEffectList() {
        // Scholar: +20 Chips and +4 Mult — one rule, two effects, chained via extra.
        JokerEffect r = EffectInterpreter.applyAll(List.of(Effect.chips(c(20)), Effect.mult(c(4))), CTX);
        assertThat(r.chips).isEqualTo(20);
        assertThat(r.extra).isNotNull();
        assertThat(r.extra.mult).isEqualTo(4);
    }

    @Test
    void identitySkipDropsTheNoOpFromAChain() {
        JokerEffect r = EffectInterpreter.applyAll(List.of(Effect.mult(c(0)), Effect.chips(c(50))), CTX);
        assertThat(r.chips).isEqualTo(50);
        assertThat(r.extra).isNull(); // the +0 mult contributed nothing
    }

    @Test
    void structuralEffectsCarryTheirPayload() {
        assertThat(apply(new Effect.Destroy(new Selector.Focus())).destroyScored).isTrue();
        assertThat(apply(new Effect.Destroy(new Selector.Discarded())).destroyEventCards).isTrue();
        assertThat(apply(new Effect.Destroy(new Selector.Self())).destroySelf).isTrue();
        assertThat(apply(new Effect.Copy(new Selector.Focus(), 1)).copyScored).isTrue();
    }
}
