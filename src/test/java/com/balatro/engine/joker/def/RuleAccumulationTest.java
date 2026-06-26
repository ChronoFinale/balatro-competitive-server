package com.balatro.engine.joker.def;

import com.balatro.grammar.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerEffect;
import com.balatro.grammar.Trigger;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code DataJoker} accumulates: every rule whose trigger and condition match contributes, in authoring
 * order — there is no "one scoring rule per trigger" limit and no ordering constraint between scoring and
 * state-write rules. These tests pin that behaviour: two scoring rules on one trigger both apply, and a
 * state write still happens even when a scoring rule on the same trigger precedes it (the pass never stops
 * early). State writes self-gate out of preview/blueprint, verified elsewhere.
 */
class RuleAccumulationTest {

    private static final Condition ALWAYS = new Condition.Always();

    @Test
    void twoScoringRulesOnOneTriggerBothContribute() {
        DataJoker j = joker(
                new Rule(Trigger.JOKER_MAIN, ALWAYS, Effect.chips(new Value.Const(10))),
                new Rule(Trigger.JOKER_MAIN, ALWAYS, Effect.mult(new Value.Const(5))));

        JokerEffect e = j.calculate(ctx(j, Trigger.JOKER_MAIN));

        assertThat(e).isNotNull();
        assertThat(e.chips).isEqualTo(10);
        assertThat(e.extra).isNotNull();
        assertThat(e.extra.mult).isEqualTo(5); // the second rule chained on, not dropped
    }

    @Test
    void aStateWriteAfterAScoringRuleStillApplies() {
        DataJoker j = joker(
                new Rule(Trigger.JOKER_MAIN, ALWAYS, Effect.mult(new Value.Const(4))),
                new Rule(Trigger.JOKER_MAIN, ALWAYS,
                        new Effect.MutateState("count", Effect.Operation.ADD, 1, null, null)));

        EvaluationContext c = ctx(j, Trigger.JOKER_MAIN);
        JokerEffect e = j.calculate(c);

        assertThat(e.mult).isEqualTo(4);                  // scoring rule applied
        assertThat(c.selfState().get("count")).isEqualTo(1); // and the later write still ran
    }

    private static DataJoker joker(Rule... rules) {
        return new DataJoker(new JokerDef("j_accum", "Accum", "test", com.balatro.grammar.Rarity.COMMON, 1, 0, 0, null, null, true,
                List.of(rules)));
    }

    private static EvaluationContext ctx(Joker self, Trigger phase) {
        EvaluationContext c = new EvaluationContext();
        c.jokers = List.of(self);
        c.selfIndex = 0;
        c.blueprintDepth = 0;
        c.run = new RunState();
        c.phase = phase;
        return c;
    }
}
