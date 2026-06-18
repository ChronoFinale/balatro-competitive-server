package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tag effects wired through the Run loop. Boss Tag: a free one-shot boss reroll on arrival. */
class TagEffectsTest {

    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private static Run toBoss(boolean holdBossTag) {
        Run run = new Run(Ruleset.standard(), "BT", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        if (holdBossTag) run.state.tags.add("tag_boss");
        run.play(FIVE); run.proceed(); // Small -> Big
        run.play(FIVE); run.proceed(); // Big -> Boss
        return run;
    }

    @Test
    void bossTagRerollsTheBossAndIsConsumed() {
        String baseline = toBoss(false).view().boss();
        Run withTag = toBoss(true);
        assertThat(withTag.blind).isEqualTo(BlindType.BOSS);
        // The held tag is spent reaching the boss...
        assertThat(withTag.state.tags).doesNotContain("tag_boss");
        // ...and the boss it rerolled into differs from the un-rerolled pick for this seed.
        assertThat(withTag.view().boss()).isNotEqualTo(baseline);
    }
}
