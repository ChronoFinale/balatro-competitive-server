package com.balatro.engine;

import com.balatro.engine.state.Bundles;
import com.balatro.engine.state.RulesetBundle;
import com.balatro.engine.state.Ruleset;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A bundle composes content + capabilities + mode, round-trips through JSON, and resolves to a Ruleset. */
class BundleTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final java.util.List<String> NEMESIS = java.util.List.of(
            "j_pizza", "j_speedrun", "j_pacifist", "j_taxes", "j_conjoined",
            "j_defensive_joker", "j_penny_pincher", "j_skip_off", "j_lets_go_gambling");

    @Test void roundTripsThroughJson() throws Exception {
        for (RulesetBundle b : Bundles.all()) {
            RulesetBundle back = M.readValue(M.writeValueAsString(b), RulesetBundle.class);
            assertThat(back.name()).isEqualTo(b.name());
            assertThat(back.overlays()).isEqualTo(b.overlays());
            assertThat(back.content().keySet()).isEqualTo(b.content().keySet());
        }
    }

    @Test void vanillaPvpIsVanillaContentPvpStructure() {
        RulesetBundle b = Bundles.vanillaPvp();
        assertThat(b.pvp()).isTrue();                         // PvP match structure
        assertThat(b.variant()).isEqualTo("default");         // vanilla capabilities
        var content = b.content();
        assertThat(content).doesNotContainKeys(NEMESIS.toArray(String[]::new));  // no Nemesis
        assertThat(content).containsKeys("j_chicot", "j_seltzer");               // bans/reworks NOT applied
        // the rework didn't happen: Seltzer keeps its vanilla description
        assertThat(content.get("j_seltzer").description()).doesNotContain("multiplayer");
    }

    @Test void bmpBundleAppliesTheOverlay() {
        var content = Bundles.bmp042Ranked().content();
        assertThat(content).doesNotContainKeys("j_chicot", "j_matador");     // bans applied
        assertThat(content).containsKeys(NEMESIS.toArray(String[]::new));    // Nemesis added
        assertThat(content.get("j_seltzer").description()).contains("multiplayer"); // rework applied
    }

    @Test void resolvesToARunnableRuleset() {
        Ruleset r = Bundles.vanillaPvp().resolve();
        assertThat(r.name()).isEqualTo("vanilla-pvp");
        assertThat(r.jokerPool()).doesNotContainAnyElementsOf(NEMESIS);
        assertThat(r.capabilities()).isEqualTo(com.balatro.engine.state.Capabilities.VANILLA);
        assertThat(Bundles.bmp042Ranked().resolve().capabilities())
                .isEqualTo(com.balatro.engine.state.Capabilities.MULTIPLAYER);
    }
}
