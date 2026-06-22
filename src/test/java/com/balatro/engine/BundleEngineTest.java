package com.balatro.engine;

import com.balatro.engine.game.Run;
import com.balatro.engine.state.Bundles;
import com.balatro.engine.state.Capabilities;
import com.balatro.engine.state.RulesetBundle;
import org.junit.jupiter.api.Test;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end of the vertical pipe: the engine runs a bundle <b>loaded from its JSON artifact</b>. Proves
 * DSL → JSON → engine is closed — the JSON-loaded bundle drives a real Run with the composed content +
 * capabilities, identical to the in-memory DSL form.
 */
class BundleEngineTest {

    @Test void jsonLoadedBundleEqualsDslForm() {
        assertThat(Bundles.load("vanilla-pvp").content().keySet())
                .isEqualTo(Bundles.vanillaPvp().content().keySet());
        assertThat(Bundles.load("bmp-0.4.2-ranked").content().keySet())
                .isEqualTo(Bundles.bmp042Ranked().content().keySet());
    }

    @Test void engineRunsFromJsonLoadedBundle() {
        RulesetBundle vp = Bundles.load("vanilla-pvp");
        Run solo = new Run(vp.resolve(), "SEED", stoneDeck(400), jokers("j_joker"));
        assertThat(solo.state.capabilities).isEqualTo(Capabilities.VANILLA);
        assertThat(solo.state.jokers()).singleElement().satisfies(j -> assertThat(j.key()).isEqualTo("j_joker"));

        RulesetBundle bmp = Bundles.load("bmp-0.4.2-ranked");
        Run mp = new Run(bmp.resolve(), "SEED", stoneDeck(400), jokers("j_pizza"));
        assertThat(mp.state.capabilities).isEqualTo(Capabilities.MULTIPLAYER);  // MP knobs flow through
        assertThat(mp.state.jokers()).singleElement().satisfies(j -> assertThat(j.key()).isEqualTo("j_pizza"));
    }
}
