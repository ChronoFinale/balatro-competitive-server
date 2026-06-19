package com.balatro.engine.joker.def;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stage-1 guard for the doc-42 migration: the new sealed {@link Effect} interpreter must produce the
 * <b>byte-identical</b> {@link JokerEffect} the old {@link EffectTemplate} does, for every numeric op and
 * for the chain. If these ever diverge, scoring changed — so this is the safety net the re-pointing leans on.
 */
class EffectApplyTest {

    private static final EvaluationContext CTX = new EvaluationContext();
    private static final Value FOUR = new Value.Const(4);

    private static void assertSame(JokerEffect a, JokerEffect b) {
        if (a == null || b == null) {
            assertThat(a).isNull();
            assertThat(b).isNull();
            return;
        }
        assertThat(a.chips).isEqualTo(b.chips);
        assertThat(a.mult).isEqualTo(b.mult);
        assertThat(a.xMult).isEqualTo(b.xMult);
        assertThat(a.powMult).isEqualTo(b.powMult);
        assertThat(a.dollars).isEqualTo(b.dollars);
        assertThat(a.repetitions).isEqualTo(b.repetitions);
        assertThat(a.hMult).isEqualTo(b.hMult);
        assertThat(a.message).isEqualTo(b.message);
    }

    @Test
    void everyNumericOpMatchesEffectTemplate() {
        for (Effect.Op op : Effect.Op.values()) {
            EffectTemplate.Op legacy = EffectTemplate.Op.valueOf(op.name());
            assertSame(new Effect.Score(op, FOUR).apply(CTX), new EffectTemplate(legacy, FOUR).apply(CTX));
        }
    }

    @Test
    void identityContributionsAreSkipped() {
        assertThat(new Effect.Score(Effect.Op.MULT, new Value.Const(0)).apply(CTX)).isNull();   // +0 mult
        assertThat(new Effect.Score(Effect.Op.XMULT, new Value.Const(1)).apply(CTX)).isNull();  // x1 mult
        assertThat(new Effect.Score(Effect.Op.CHIPS, new Value.Const(0)).apply(CTX)).isNull();  // +0 chips
    }

    @Test
    void applyAllChainsLikeTheExtraChain() {
        // Scholar: +20 Chips and +4 Mult — one rule, two effects.
        JokerEffect viaList = Effect.applyAll(
                List.of(new Effect.Score(Effect.Op.CHIPS, new Value.Const(20)),
                        new Effect.Score(Effect.Op.MULT, FOUR)),
                CTX);
        JokerEffect viaTemplate = new EffectTemplate(EffectTemplate.Op.CHIPS, new Value.Const(20),
                new EffectTemplate(EffectTemplate.Op.MULT, FOUR)).apply(CTX);

        assertThat(viaList.chips).isEqualTo(20);
        assertThat(viaList.extra).isNotNull();
        assertThat(viaList.extra.mult).isEqualTo(4);
        assertSame(viaList, viaTemplate);
        assertSame(viaList.extra, viaTemplate.extra);
    }

    @Test
    void identitySkipDropsTheNoOpFromAChain() {
        JokerEffect r = Effect.applyAll(
                List.of(new Effect.Score(Effect.Op.MULT, new Value.Const(0)),  // skipped
                        new Effect.Score(Effect.Op.CHIPS, new Value.Const(50))),
                CTX);
        assertThat(r.chips).isEqualTo(50);
        assertThat(r.extra).isNull(); // the +0 mult contributed nothing
    }
}
