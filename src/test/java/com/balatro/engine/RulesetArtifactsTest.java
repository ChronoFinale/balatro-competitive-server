package com.balatro.engine;

import com.balatro.engine.joker.JokerLibrary;
import com.balatro.content.jokers.BuiltinJokerDefs;
import com.balatro.engine.joker.def.JokerDef;
import com.balatro.engine.joker.def.JokerOverlays;
import com.balatro.engine.joker.def.RulesetDiff;
import com.balatro.engine.joker.def.RulesetOverlay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // the manifest hashes the other artifacts -> runs last
class RulesetArtifactsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final boolean REGEN = Boolean.getBoolean("regen");

    private static final Path VANILLA = Path.of("src/main/resources/rulesets/vanilla.json");
    private static final Path BMP = Path.of("src/main/resources/rulesets/bmp-0.4.2-ranked.json");
    private static final Path BMP_DOC = Path.of("docs/rulesets/bmp-0.4.2-ranked.md");
    private static final Path BUNDLES = Path.of("src/main/resources/rulesets/bundles");

    private static final Path CLIENT_TYPES = Path.of("client/src/generated/content-types.ts");

    @Test void bundlesCompileToJson() throws Exception {
        for (var b : com.balatro.content.Bundles.all()) {
            gate(BUNDLES.resolve(b.name() + ".json"), JokerOverlays.writePretty(b));
        }
    }

    @Test void clientTypesAreGenerated() throws Exception {
        gate(CLIENT_TYPES, com.balatro.engine.codegen.ClientCodegen.generate());
    }

    private static final Path MANIFEST = Path.of("src/main/resources/content/manifest.json");

    @Test @Order(Integer.MAX_VALUE) void contentManifestIsVersionedAndHashed() throws Exception {
        // A delta-sync manifest: every shipped content file with its sha256 + size, and a version that is
        // the hash of all of them. An auto-updater fetches this, diffs vs its cache, downloads only deltas.
        var files = new java.util.ArrayList<com.balatro.engine.codegen.ContentManifest.FileEntry>();
        for (String rel : com.balatro.engine.codegen.ContentManifest.FILES) {
            byte[] bytes = Files.readAllBytes(Path.of("src/main/resources").resolve(rel));
            files.add(new com.balatro.engine.codegen.ContentManifest.FileEntry(
                    rel, com.balatro.engine.codegen.ContentManifest.sha256(bytes), bytes.length));
        }
        var manifest = new com.balatro.engine.codegen.ContentManifest(
                com.balatro.engine.codegen.ContentManifest.versionOf(files), files);
        gate(MANIFEST, JokerOverlays.writePretty(manifest));
    }

    private static final Path DECKS = Path.of("src/main/resources/content/decks.json");

    @Test void decksCompileToJsonAndRoundTrip() throws Exception {
        var decks = com.balatro.content.DeckDefs.authored();  // DSL source (runtime loads from JSON)
        String json = JokerOverlays.writePretty(decks);
        gate(DECKS, json);
        var back = List.of(M.readValue(json, com.balatro.engine.game.DeckCatalog.DeckType[].class));
        assertThat(back).isEqualTo(decks);
    }

    private static final Path BOSSES = Path.of("src/main/resources/content/bosses.json");
    private static final Path TAGS = Path.of("src/main/resources/content/tags.json");

    @Test void bossesCompileToJsonAndRoundTrip() throws Exception {
        // The client renders each boss's display `effect` + the rule shape (debuff/requires Conditions, mods).
        var bosses = com.balatro.content.BossDefs.authored();  // DSL source (runtime loads from JSON)
        String json = JokerOverlays.writePretty(bosses);
        gate(BOSSES, json);
        assertThat(List.of(M.readValue(json, com.balatro.engine.game.BossBlind[].class))).isEqualTo(bosses);
    }

    @Test void tagsCompileToJsonAndRoundTrip() throws Exception {
        var tags = com.balatro.content.TagDefs.authored();  // DSL source (runtime loads from JSON)
        String json = JokerOverlays.writePretty(tags);
        gate(TAGS, json);
        assertThat(List.of(M.readValue(json, com.balatro.engine.game.TagCatalog.Tag[].class))).isEqualTo(tags);
    }

    private static final Path VOUCHERS = Path.of("src/main/resources/content/vouchers.json");
    private static final Path CONSUMABLES = Path.of("src/main/resources/content/consumables.json");

    @Test void vouchersCompileToJsonAndRoundTrip() throws Exception {
        var vouchers = com.balatro.engine.game.VoucherCatalog.authored();  // DSL source (runtime loads from JSON)
        String json = JokerOverlays.writePretty(vouchers);
        gate(VOUCHERS, json);
        assertThat(List.of(M.readValue(json, com.balatro.engine.game.VoucherCatalog.Voucher[].class)))
                .isEqualTo(vouchers);
    }

    @Test void consumablesCompileToJsonAndRoundTrip() throws Exception {
        // Tarots/planets/spectrals — name/description/type + their Effect-union shape for the client.
        var cs = com.balatro.content.ConsumableDefs.authored();  // DSL source (runtime loads from JSON)
        String json = JokerOverlays.writePretty(cs);
        gate(CONSUMABLES, json);
        assertThat(List.of(M.readValue(json, com.balatro.engine.consumable.Consumable[].class))).isEqualTo(cs);
    }

    private static final Path PLANETS = Path.of("src/main/resources/content/planets.json");
    private static final Path HAND_SCORES = Path.of("src/main/resources/content/hand-scores.json");

    @Test void planetsCompileToJsonAndRoundTrip() throws Exception {
        var planets = com.balatro.content.PlanetDefs.authored();  // DSL source (runtime loads from JSON)
        String json = JokerOverlays.writePretty(planets);
        gate(PLANETS, json);
        assertThat(List.of(M.readValue(json, com.balatro.engine.game.PlanetCatalog.Planet[].class)))
                .isEqualTo(planets);
    }

    @Test void handScoreTableCompiles() throws Exception {
        gate(HAND_SCORES, JokerOverlays.writePretty(handScoreTable()));
    }

    private static final Path UI_SCREENS = Path.of("src/main/resources/content/ui-screens.json");

    @Test void uiScreensCompileToJsonAndRoundTrip() throws Exception {
        var screens = com.balatro.content.Screens.all();
        String json = JokerOverlays.writePretty(screens);
        gate(UI_SCREENS, json);
        assertThat(List.of(M.readValue(json, com.balatro.engine.ui.UiScreen[].class))).isEqualTo(screens);
    }

    private static java.util.List<java.util.Map<String, Object>> handScoreTable() {
        var table = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var h : com.balatro.engine.hand.HandType.values()) {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("hand", h.name());
            m.put("display", h.display);
            m.put("baseChips", h.baseChips);
            m.put("baseMult", h.baseMult);
            m.put("chipsPerLevel", h.lChips);
            m.put("multPerLevel", h.lMult);
            table.add(m);
        }
        return table;
    }

    private static final Path CLIENT_CONTENT = Path.of("client/src/generated/content.ts");

    @Test void clientContentDataModuleGenerated() throws Exception {
        // Bundle the compiled content into the client as a typed TS module — no runtime fetch; the data is
        // checked against the generated interfaces at build. Single source stays the DSL.
        StringBuilder sb = new StringBuilder();
        sb.append("// GENERATED by com.balatro.engine.RulesetArtifactsTest — do not edit by hand.\n");
        sb.append("// Regenerate: ./gradlew generateContent\n");
        sb.append("import type { JokerDef, DeckType, BossBlind, Tag, Voucher, Consumable, Planet, HandScore, "
                + "RulesetBundle, UiScreen } from \"./content-types\";\n\n");
        emit(sb, "SCREENS", "UiScreen", com.balatro.content.Screens.all());
        emit(sb, "JOKERS", "JokerDef", com.balatro.content.jokers.BuiltinJokerDefs.all());
        emit(sb, "DECKS", "DeckType", com.balatro.engine.game.DeckCatalog.keys().stream()
                .map(com.balatro.engine.game.DeckCatalog::get).toList());
        emit(sb, "BOSSES", "BossBlind", com.balatro.engine.game.BossCatalog.all());
        emit(sb, "TAGS", "Tag", com.balatro.engine.game.TagCatalog.keys().stream()
                .map(com.balatro.engine.game.TagCatalog::get).toList());
        emit(sb, "VOUCHERS", "Voucher", com.balatro.engine.game.VoucherCatalog.keys().stream()
                .map(com.balatro.engine.game.VoucherCatalog::get).toList());
        emit(sb, "CONSUMABLES", "Consumable", com.balatro.engine.consumable.TarotCatalog.keys().stream()
                .map(com.balatro.engine.consumable.TarotCatalog::get).toList());
        emit(sb, "PLANETS", "Planet", com.balatro.engine.game.PlanetCatalog.keys().stream()
                .map(com.balatro.engine.game.PlanetCatalog::get).toList());
        emit(sb, "HAND_SCORES", "HandScore", handScoreTable());
        emit(sb, "BUNDLES", "RulesetBundle", com.balatro.content.Bundles.all());
        gate(CLIENT_CONTENT, sb.toString());
    }

    private static void emit(StringBuilder sb, String name, String type, Object data) {
        sb.append("export const ").append(name).append(": readonly ").append(type).append("[] = ")
                .append(JokerOverlays.writePrettyNonNull(data).stripTrailing()).append(";\n\n");
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
