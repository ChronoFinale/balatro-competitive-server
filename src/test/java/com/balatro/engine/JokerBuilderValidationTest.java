package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.balatro.engine.joker.def.Jokers;
import com.balatro.engine.joker.def.Target;
import org.junit.jupiter.api.Test;

/**
 * The fluent joker builder fails LOUD and COMPLETE on missing required fields — the error names every
 * gap at once, so you never have to guess what a joker needs. The message is the documentation.
 */
class JokerBuilderValidationTest {

    @Test
    void buildListsEveryMissingRequiredFieldAtOnce() {
        // No description, no cost, no behavior — all three should be reported in one message.
        assertThatThrownBy(() -> Jokers.common("j_empty", "Empty").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("j_empty")
                .hasMessageContaining("description")
                .hasMessageContaining("cost")
                .hasMessageContaining("behavior");
    }

    @Test
    void aJokerThatDoesNothingIsRejected() {
        // Has identity + cost + desc, but no rule/mutation/copy — it does nothing, so it's invalid.
        assertThatThrownBy(() -> Jokers.common("j_inert", "Inert").cost(3).desc("does nothing").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("behavior");
    }

    @Test
    void aFullyDeclaredJokerBuildsCleanly() {
        assertThatCode(() -> Jokers.common("j_ok", "Okay").cost(3).desc("+4 Mult")
                .whenHand().add(Target.MULT, 4).build())
                .doesNotThrowAnyException();
    }
}
