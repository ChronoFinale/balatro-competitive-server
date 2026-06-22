package com.balatro.engine;

import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.BuiltinJokerDefs;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.joker.def.JokerDef;
import com.balatro.engine.joker.def.JokerOverlays;
import com.balatro.engine.joker.def.RulesetDiff;
import com.balatro.engine.joker.def.RulesetOverlay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private static final Path BMP = Path.of("src/main/resources/rulesets/bmp-0.4.2.json");
    private static final Path BMP_DOC = Path.of("docs/rulesets/bmp-0.4.2.md");

    @Test void baseCompilesToVanillaJson() throws Exception {
        String json = JokerOverlays.toJson(BuiltinJokerDefs.all());
        gate(VANILLA, json);
        // round-trips back to the same defs
        List<JokerDef> reloaded = List.of(M.readValue(json, JokerDef[].class));
        assertThat(reloaded).isEqualTo(BuiltinJokerDefs.all());
    }

    @Test void bmpOverlayAndDoc() throws Exception {
        RulesetOverlay overlay = REGEN ? deriveBmpOverlay() : loadOverlay(BMP);
        if (REGEN) gate(BMP, M.writerWithDefaultPrettyPrinter().writeValueAsString(overlay) + "\n");

        // It applies cleanly onto the base, and the doc reflects exactly what it does.
        var eff = JokerOverlays.apply(BuiltinJokerDefs.all(), overlay);
        assertThat(eff).doesNotContainKeys(JokerLibrary.MP_BANNED.toArray(String[]::new));
        RulesetDiff diff = JokerOverlays.diff(BuiltinJokerDefs.all(), overlay);
        gate(BMP_DOC, JokerOverlays.toMarkdown(diff));
    }

    /** One-time migration: build the BMP overlay from the existing MP variants + bans, with authored reasons. */
    private RulesetOverlay deriveBmpOverlay() {
        List<RulesetOverlay.Remove> removes = new ArrayList<>();
        // MP_BANNED: boss-blind interactions that are degenerate in head-to-head play.
        var banReasons = java.util.Map.of(
                "j_chicot", "Disables boss blinds — removes the core PvP variable",
                "j_matador", "Boss-triggered economy snowballs uncontested in PvP",
                "j_mr_bones", "Prevents loss — distorts the race",
                "j_luchador", "On-sell boss disable trivializes boss antes");
        JokerLibrary.MP_BANNED.stream().sorted()
                .forEach(k -> removes.add(new RulesetOverlay.Remove(k, banReasons.getOrDefault(k, "Banned in BMP standard"))));

        // The MP reworks, as patches derived from the legacy variant defs (only the changed fields).
        var reworkReasons = java.util.Map.of(
                "j_hanging_chad", "MP rework: retriggers first AND second played card",
                "j_seltzer", "MP rework: 8-hand acquire window",
                "j_golden_ticket", "MP rework: Uncommon, $3 per Gold card");
        List<RulesetOverlay.Override> overrides = new ArrayList<>();
        BuiltinJokerDefs.variants().getOrDefault("multiplayer", List.of()).stream()
                .sorted(java.util.Comparator.comparing(JokerDef::key))
                .forEach(v -> {
                    JokerDef base = ((DataJoker) JokerLibrary.create(v.key())).def();
                    ObjectNode patch = M.createObjectNode();
                    ObjectNode nb = M.valueToTree(base), nv = M.valueToTree(v);
                    nv.fieldNames().forEachRemaining(f -> {
                        if (!nv.get(f).equals(nb.get(f))) patch.set(f, nv.get(f));
                    });
                    overrides.add(new RulesetOverlay.Override(v.key(),
                            reworkReasons.getOrDefault(v.key(), "MP rework"), patch));
                });

        return new RulesetOverlay("bmp-0.4.2-ranked", "vanilla", removes, overrides, List.of());
    }

    private RulesetOverlay loadOverlay(Path p) throws Exception {
        return M.readValue(Files.readAllBytes(p), RulesetOverlay.class);
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
