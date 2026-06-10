package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import org.junit.jupiter.api.Test;

/** Consumable-creation jokers add cards to the run server-side. */
class CreationJokerTest {

    @Test
    void cartomancerCreatesATarotAtBlindSelect() {
        Run run = new Run(Ruleset.standard(), "C", stoneDeck(400), jokers("j_cartomancer"));
        // startBlind raised BLIND_SELECTED, so Cartomancer should have made one Tarot.
        assertThat(run.state.consumables).hasSize(1);
        assertThat(run.state.consumables.get(0)).startsWith("c_");
    }

    @Test
    void cartomancerRespectsConsumableSlots() {
        Run run = new Run(Ruleset.standard(), "C", stoneDeck(400),
                jokers("j_cartomancer", "j_cartomancer", "j_cartomancer"));
        // Two slots: three Cartomancers at one blind select can fill at most the slots.
        assertThat(run.state.consumables.size()).isLessThanOrEqualTo(run.state.consumableSlots);
    }
}
