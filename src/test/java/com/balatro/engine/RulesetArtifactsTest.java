package com.balatro.engine;

import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.BuiltinJokerDefs;
import com.balatro.engine.joker.def.JokerDef;
import com.balatro.engine.joker.def.JokerOverlays;
import com.balatro.engine.joker.def.RulesetDiff;
import com.balatro.engine.joker.def.RulesetOverlay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gates the generated ruleset artifacts so they can never drift from the authoring source:
 * <ul>
 *   <li>{@code resources/rulesets/vanilla.json} — the base joker set, <b>compiled</b> from the typed
 *       {@link BuiltinJokerDefs} DSL via {@link JokerOverlays#toJson}.</li>
 *   <li>{@code resources/rulesets/bmp-0.4.2.json} — the BMP overlay (remove/override/add), derived once from
 *       the legacy {@code variants()} + {@code MP_BANNED} so the data faithfully reproduces current behaviour.</li>
 *   <li>{@code docs/rulesets/bmp-0.4.2.md} — the auto-generated, reasoned changelog of that overlay.</li>
 * </ul>
 * Run with {@code -Dregen=true} to (re)write all three from source; the plain test fails if the committed
 * files differ, pointing you at the regen command.
 */
class RulesetArtifactsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final boolean REGEN = Boolean.getBoolean("regen");

    private static final Path VANILLA = Path.of("src/main/resources/rulesets/vanilla.json");
    private static final Path BMP = Path.of("src/main/resources/rulesets/bmp-0.4.2-ranked.json");
    private static final Path BMP_DOC = Path.of("docs/rulesets/bmp-0.4.2-ranked.md");
    private static final Path BUNDLES = Path.of("src/main/resources/rulesets/bundles");

    @Test void bundlesCompileToJson() throws Exception {
        for (var b : com.balatro.engine.state.Bundles.all()) {
            gate(BUNDLES.resolve(b.name() + ".json"), JokerOverlays.writePretty(b));
        }
    }

    @Test void baseCompilesToVanillaJson() throws Exception {
        String json = JokerOverlays.toJson(BuiltinJokerDefs.all());
        gate(VANILLA, json);
        // round-trips back to the same defs
        List<JokerDef> reloaded = List.of(M.readValue(json, JokerDef[].class));
        assertThat(reloaded).isEqualTo(BuiltinJokerDefs.all());
    }

    @Test void bmpOverlayAndDoc() throws Exception {
        // remove + override are the committed hand-data; the add list is compiled from the typed
        // BuiltinJokerDefs.mpAdditions() DSL (so complex Nemesis rules aren't hand-written as JSON).
        RulesetOverlay committed = M.readValue(Files.readAllBytes(BMP), RulesetOverlay.class);
        RulesetOverlay overlay = REGEN ? withCompiledAdds(committed) : committed;
        if (REGEN) gate(BMP, JokerOverlays.writePretty(overlay));

        var eff = JokerOverlays.apply(BuiltinJokerDefs.all(), overlay);
        assertThat(eff).doesNotContainKeys(JokerLibrary.MP_BANNED.toArray(String[]::new));
        assertThat(eff).containsKey("j_pizza"); // an MP-only add lands in the effective set
        RulesetDiff diff = JokerOverlays.diff(BuiltinJokerDefs.all(), overlay);
        gate(BMP_DOC, JokerOverlays.toMarkdown(diff));
    }

    /** Rebuild the overlay's {@code add} list from the DSL-authored Nemesis defs, keeping remove + override. */
    private RulesetOverlay withCompiledAdds(RulesetOverlay committed) {
        var reasons = java.util.Map.ofEntries(
                java.util.Map.entry("j_pizza", "Nemesis: spends at PvP end for temporary discards on both sides"),
                java.util.Map.entry("j_speedrun", "Nemesis: rewards reaching the PvP blind first"),
                java.util.Map.entry("j_penny_pincher", "Nemesis: taxes the opponent's shop spend"),
                java.util.Map.entry("j_skip_off", "Nemesis: rewards skipping blinds"),
                java.util.Map.entry("j_lets_go_gambling", "Nemesis: high-variance gamble payout"),
                java.util.Map.entry("j_pacifist", "Nemesis: pays off outside PvP blinds"),
                java.util.Map.entry("j_defensive_joker", "Nemesis: scales with the opponent's life deficit"),
                java.util.Map.entry("j_conjoined", "Nemesis: scales with the opponent's remaining hands"),
                java.util.Map.entry("j_taxes", "Nemesis: scales with the opponent's cards sold"));
        List<RulesetOverlay.Add> adds = BuiltinJokerDefs.mpAdditions().stream()
                .map(d -> new RulesetOverlay.Add(reasons.getOrDefault(d.key(), "Multiplayer-exclusive Nemesis joker"), d))
                .toList();
        return new RulesetOverlay(committed.name(), committed.base(), committed.remove(), committed.override(), adds);
    }

    private static void gate(Path path, String expected) throws Exception {
        if (REGEN) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, expected);
            return;
        }
        assertThat(Files.exists(path)).as("missing %s — run: ./gradlew test --tests "
                + "com.balatro.engine.RulesetArtifactsTest -Dregen=true", path).isTrue();
        assertThat(Files.readString(path)).as("%s is stale — regenerate with -Dregen=true", path)
                .isEqualTo(expected);
    }
}
