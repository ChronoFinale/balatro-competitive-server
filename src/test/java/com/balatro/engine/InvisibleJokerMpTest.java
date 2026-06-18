package com.balatro.engine;

import static com.balatro.engine.TestSupport.heartsKings;
import static com.balatro.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Invisible Joker in multiplayer copies the RIGHTMOST remaining joker on sell
 * (deterministic, so it's comparable between players) rather than a random one.
 */
class InvisibleJokerMpTest {

    private static Ruleset mp() {
        return new Ruleset("MP", 4, 4, 3, 8, 1.0, 8, Ruleset.standard().blindBaseAmounts(),
                List.of("j_joker"), "multiplayer");
    }

    @Test
    void invisibleCopiesTheRightmostJokerInMultiplayer() {
        Run run = new Run(mp(), "INV", heartsKings(60), jokers("j_invisible", "j_bull", "j_banner"));
        run.state.jokerState(run.state.jokers().get(0)).put("rounds", 2); // owned long enough to duplicate

        assertThat(run.sellJoker(0)).isNull(); // sell Invisible -> remaining [j_bull, j_banner], copy rightmost
        // -> [j_bull, j_banner, j_banner]
        assertThat(run.state.jokers()).hasSize(3);
        assertThat(run.state.jokers().get(2).key()).isEqualTo("j_banner"); // the rightmost was copied
    }
}
