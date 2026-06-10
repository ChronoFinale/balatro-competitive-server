package com.balatromp.engine.joker;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Built-in joker display: the server computes each joker's current live value, so
 * clients read it directly (no JokerDisplay mod). It reflects scaling state, and
 * the dry-run is side-effect-free.
 */
class JokerDisplayTest {

    private static RunState runWith(String... keys) {
        RunState run = new RunState();
        for (String k : keys) run.addJoker(JokerLibrary.create(k));
        return run;
    }

    @Test
    void flatJokerShowsItsValue() {
        RunState run = runWith("j_joker"); // +4 Mult
        assertThat(JokerDisplay.currentValue(run.jokers(), 0, run)).isEqualTo("+4 Mult");
    }

    @Test
    void scalingJokerReflectsCurrentState() {
        // Abstract Joker: +3 Mult per Joker. With 3 jokers -> +9 Mult right now.
        RunState run = runWith("j_abstract", "j_joker", "j_joker");
        assertThat(JokerDisplay.currentValue(run.jokers(), 0, run)).isEqualTo("+9 Mult");
    }

    @Test
    void perCardJokerHasNoMainDisplay() {
        RunState run = runWith("j_greedy_joker"); // on-scored per Diamond -> no JOKER_MAIN value
        assertThat(JokerDisplay.currentValue(run.jokers(), 0, run)).isEmpty();
    }

    @Test
    void computingDisplayDoesNotConsumeRealQueues() {
        // Misprint reads a probability queue; computing its display must not advance
        // the run's real queue. We assert display is stable across repeated calls.
        RunState run = runWith("j_misprint");
        String first = JokerDisplay.currentValue(run.jokers(), 0, run);
        String second = JokerDisplay.currentValue(run.jokers(), 0, run);
        assertThat(first).isEqualTo(second); // preview queue is fresh each time, never the real one
    }
}
