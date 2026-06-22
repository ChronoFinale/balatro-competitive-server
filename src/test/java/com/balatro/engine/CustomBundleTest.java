package com.balatro.engine;

import com.balatro.engine.game.Run;
import com.balatro.engine.state.BundleCatalog;
import com.balatro.engine.state.Capabilities;
import com.balatro.engine.state.RulesetBundle;
import com.balatro.engine.state.RulesetStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extensibility payoff: a new competitive mode is authored as pure JSON — content overlays + capabilities
 * + mode — and the server picks it up with no code change. It then flows through the SAME path as the
 * first-party bundles: selectable by name, resolvable as a Ruleset, and runnable by the engine.
 */
class CustomBundleTest {

    @Test void customBundleAuthoredAsJsonIsSelectableAndRunnable(@TempDir Path dir) throws Exception {
        // A modder drops in a bundle: vanilla base + the bmp overlay, multiplayer caps, head-to-head.
        Files.writeString(dir.resolve("my-ranked.json"), """
            {
              "name": "my-custom-ranked",
              "base": "vanilla",
              "overlays": ["bmp-0.4.2-ranked"],
              "variant": "multiplayer",
              "mode": "PVP",
              "decks": [],
              "startingMoney": 4, "hands": 4, "discards": 3, "handSize": 8,
              "anteScaling": 1.0, "winAnte": 8,
              "blindBaseAmounts": [300, 800, 2000, 5000, 11000, 20000, 35000, 50000],
              "deckType": "d_base"
            }
            """);
        BundleCatalog.loadDir(dir);

        // selectable by name (joins the offered rulesets)
        assertThat(BundleCatalog.names()).contains("my-custom-ranked");

        // its composition applied: the overlay's adds present, its bans removed — no code wrote this
        RulesetBundle b = BundleCatalog.get("my-custom-ranked");
        assertThat(b.content()).containsKey("j_pizza").doesNotContainKey("j_chicot");

        // resolvable as a Ruleset by name through the store the lobby uses
        RulesetStore store = new RulesetStore(dir.resolve("rulesets"));
        assertThat(store.get("my-custom-ranked")).isNotNull();

        // and the engine runs it, with the bundle's capabilities
        Run run = new Run(b.resolve(), "SEED", stoneDeck(400), jokers("j_joker"));
        assertThat(run.state.capabilities).isEqualTo(Capabilities.MULTIPLAYER);
    }

    @Test void invalidCustomBundleIsRejectedAtLoadNotAtRuntime(@TempDir Path dir) throws Exception {
        // references an overlay that doesn't exist — must be skipped at load, never offered
        Files.writeString(dir.resolve("broken.json"), """
            { "name": "broken-mode", "base": "vanilla", "overlays": ["does-not-exist"],
              "variant": "default", "mode": "SOLO", "decks": [],
              "startingMoney": 4, "hands": 4, "discards": 3, "handSize": 8, "anteScaling": 1.0,
              "winAnte": 8, "blindBaseAmounts": [300, 800, 2000, 5000, 11000, 20000, 35000, 50000],
              "deckType": "d_base" }
            """);
        BundleCatalog.loadDir(dir);
        assertThat(BundleCatalog.names()).doesNotContain("broken-mode");
        assertThat(BundleCatalog.get("broken-mode")).isNull();
    }
}
