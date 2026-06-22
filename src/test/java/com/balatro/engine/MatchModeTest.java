package com.balatro.engine;

import com.balatro.engine.game.Match;
import com.balatro.engine.state.BundleCatalog;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.RulesetStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The mode axis: bundles are proposable rulesets, and a match refuses a SOLO bundle. */
class MatchModeTest {

    @Test void bundlesResolveAsRulesetsAndCarryMode(@TempDir Path dir) {
        RulesetStore store = new RulesetStore(dir);
        assertThat(store.get("vanilla-pvp")).isNotNull();         // bundle resolvable by name
        assertThat(store.get("vanilla-solo")).isNotNull();
        assertThat(store.names()).contains("vanilla-pvp", "vanilla-solo", "bmp-0.4.2-ranked");
        assertThat(BundleCatalog.isPvp("vanilla-pvp")).isTrue();
        assertThat(BundleCatalog.isPvp("vanilla-solo")).isFalse();
        assertThat(BundleCatalog.isPvp("Standard")).isTrue();     // unknown/curated -> allowed
    }

    @Test void matchAcceptsPvpBundleRejectsSoloBundle(@TempDir Path dir) {
        List<Object> sent = new ArrayList<>();
        Match match = new Match("CODE", "seed", Ruleset.standard(), new RulesetStore(dir),
                (sid, payload) -> sent.add(payload));
        match.setHost("h", "ph");
        match.setGuestAndStart("g", "pg");

        match.propose("h", "vanilla-solo");      // SOLO bundle -> rejected, never proposed
        match.respond("g", true);
        assertThat(match.phase()).isEqualTo(Match.Phase.AGREEING);
        assertThat(match.runOf("h")).isNull();
        assertThat(sent.toString()).contains("single-player only");

        match.propose("h", "vanilla-pvp");       // PvP bundle -> accepted
        match.respond("g", true);
        assertThat(match.phase()).isEqualTo(Match.Phase.PLAYING);
        assertThat(match.runOf("h")).isNotNull();
    }
}
