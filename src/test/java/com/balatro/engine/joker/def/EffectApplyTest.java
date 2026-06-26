package com.balatro.engine.joker.def;

import com.balatro.grammar.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balatro.engine.eval.EffectInterpreter;
import com.balatro.engine.exec.Command;
import com.balatro.engine.joker.Contribution;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The {@link EffectInterpreter} over the pure-data {@link Effect} grammar: each numeric op maps to the right
 * {@link Contribution} (op × term) cell, identity contributions (+0 / x1) are dropped, structural effects
 * become {@link Command}s, and a rule's effect list concatenates into one {@link JokerResult}.
 */
class EffectApplyTest {

    private static final EvaluationContext CTX = new EvaluationContext();
    private static Value.Const c(double v) { return new Value.Const(v); }

    private static JokerResult apply(Effect e) { return EffectInterpreter.apply(e, CTX); }

    private static Contribution only(JokerResult r) {
        assertThat(r.contributions()).hasSize(1);
        return r.contributions().get(0);
    }

    private static void assertCell(Effect e, Effect.Operation op, Effect.Term term, double amount) {
        Contribution c = only(apply(e));
        assertThat(c.op()).isEqualTo(op);
        assertThat(c.term()).isEqualTo(term);
        assertThat(c.amount()).isEqualTo(amount);
    }

    @Test
    void eachCellMapsToItsContribution() { // (operation, term) cells map to one Contribution
        assertCell(Effect.chips(c(50)), Effect.Operation.ADD, Effect.Term.CHIPS, 50);
        assertCell(Effect.mult(c(4)), Effect.Operation.ADD, Effect.Term.MULT, 4);
        assertCell(Effect.xMult(c(3)), Effect.Operation.MULTIPLY, Effect.Term.MULT, 3.0);
        assertCell(Effect.powMult(c(2)), Effect.Operation.POWER, Effect.Term.MULT, 2.0);
        assertCell(Effect.dollars(c(7)), Effect.Operation.ADD, Effect.Term.DOLLARS, 7);
        assertCell(Effect.retriggers(c(2)), Effect.Operation.ADD, Effect.Term.RETRIGGERS, 2);
        assertCell(Effect.heldMult(c(13)), Effect.Operation.ADD, Effect.Term.HELD_MULT, 13);
    }

    @Test
    void identityContributionsAreSkipped() {
        assertThat(apply(Effect.mult(c(0))).contributions()).isEmpty();   // +0 mult
        assertThat(apply(Effect.xMult(c(1))).contributions()).isEmpty();  // x1 mult
        assertThat(apply(Effect.chips(c(0))).contributions()).isEmpty();  // +0 chips
    }

    @Test
    void additiveOnlyTermsRejectMultiply() { // CHIPS has no x-accumulator slot — the empty grid cell fails loudly
        assertThatThrownBy(() -> apply(new Effect.Score(Effect.Operation.MULTIPLY, Effect.Term.CHIPS, c(2))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyAllConcatenatesTheEffectList() {
        // Scholar: +20 Chips and +4 Mult — one rule, two effects, two contributions in order.
        JokerResult r = EffectInterpreter.applyAll(List.of(Effect.chips(c(20)), Effect.mult(c(4))), CTX);
        assertThat(r.contributions()).hasSize(2);
        assertThat(r.contributions().get(0).term()).isEqualTo(Effect.Term.CHIPS);
        assertThat(r.contributions().get(0).amount()).isEqualTo(20.0);
        assertThat(r.contributions().get(1).term()).isEqualTo(Effect.Term.MULT);
        assertThat(r.contributions().get(1).amount()).isEqualTo(4.0);
    }

    @Test
    void identitySkipDropsTheNoOpFromAList() {
        JokerResult r = EffectInterpreter.applyAll(List.of(Effect.mult(c(0)), Effect.chips(c(50))), CTX);
        assertThat(r.contributions()).hasSize(1); // the +0 mult contributed nothing
        assertThat(r.contributions().get(0).term()).isEqualTo(Effect.Term.CHIPS);
        assertThat(r.contributions().get(0).amount()).isEqualTo(50.0);
    }

    @Test
    void structuralEffectsBecomeCommands() {
        assertThat(apply(new Effect.Destroy(new Selector.Focus())).commands().get(0)).isInstanceOf(Command.DestroyScored.class);
        assertThat(apply(new Effect.Destroy(new Selector.Discarded())).commands().get(0)).isInstanceOf(Command.DestroyEventCards.class);
        assertThat(apply(new Effect.Destroy(new Selector.Self())).commands().get(0)).isInstanceOf(Command.DestroySelf.class);
        assertThat(apply(new Effect.Copy(new Selector.Focus(), 1)).commands().get(0)).isInstanceOf(Command.CopyScored.class);
    }
}
