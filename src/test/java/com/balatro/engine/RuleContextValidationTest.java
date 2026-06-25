package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balatro.content.BossDefs;
import com.balatro.content.jokers.BuiltinJokerDefs;
import com.balatro.dsl.RuleValidator;
import com.balatro.engine.card.Suit;
import com.balatro.grammar.Condition;
import com.balatro.grammar.Rule;
import com.balatro.grammar.Trigger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The context-capability safety net (review plan #2): every authored rule's condition must read only context
 * its trigger actually provides — otherwise the null-safe evaluator would let it silently never fire. This
 * test pins that ALL shipped content is clean, and proves the validator catches the bug it exists for.
 */
class RuleContextValidationTest {

    @Test
    void everyJokerAndBossRuleReadsOnlyContextItsTriggerProvides() {
        List<String> bad = new ArrayList<>();
        var jokers = new ArrayList<>(BuiltinJokerDefs.all());
        jokers.addAll(BuiltinJokerDefs.mpAdditions());
        for (var def : jokers) {
            for (Rule r : def.rules()) {
                try {
                    RuleValidator.validate(def.key(), r.when(), r.condition());
                } catch (RuntimeException e) {
                    bad.add(e.getMessage());
                }
            }
        }
        for (var boss : BossDefs.authored()) {
            for (Rule r : boss.rules()) {
                try {
                    RuleValidator.validate(boss.key(), r.when(), r.condition());
                } catch (RuntimeException e) {
                    bad.add(e.getMessage());
                }
            }
        }
        assertThatCode(() -> {
            if (!bad.isEmpty()) throw new AssertionError("rules whose condition can never see its context:\n  "
                    + String.join("\n  ", bad));
        }).doesNotThrowAnyException();
    }

    @Test
    void theValidatorCatchesAScoredCheckOnALifecycleTrigger() {
        // A per-scored-card condition on END_OF_ROUND has no scored card — it would silently never fire.
        assertThatThrownBy(() ->
                RuleValidator.validate("j_test", Trigger.END_OF_ROUND, new Condition.ScoredSuit(Suit.HEARTS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCORED_CARD")
                .hasMessageContaining("never fire");
        // A discard check on the shop is equally dead.
        assertThatThrownBy(() ->
                RuleValidator.validate("j_test", Trigger.SHOP_ENTER, new Condition.DiscardedFaceCount(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runLevelConditionsAreFineOnAnyTrigger() {
        // Compare(money>=5) reads only run state — valid on a lifecycle trigger.
        assertThatCode(() ->
                RuleValidator.validate("j_test", Trigger.END_OF_ROUND,
                        new Condition.Compare(com.balatro.grammar.Value.Var.MONEY, Condition.Cmp.GTE, 5)))
                .doesNotThrowAnyException();
    }
}
