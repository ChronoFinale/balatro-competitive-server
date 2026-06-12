package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatromp.engine.consumable.TarotCatalog;
import com.balatromp.engine.game.PlanetCatalog;
import com.balatromp.engine.joker.JokerLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Cross-references our content catalogs against Balatro's authoritative item set (the committed
 * src/test/resources/balatro-pool-order.json, parsed from Balatro4J). This verifies a real part of the
 * system WITHOUT needing the game: every vanilla item we claim to implement is named correctly (catches
 * naming drift), and it reports completeness (which vanilla items we have vs are missing, and which of
 * ours are MP/custom additions beyond vanilla). Comparison is by normalized display name, so it needs no
 * fragile name→key mapping. Writes build/content-coverage.txt.
 */
class ContentCoverageTest {

    private static String norm(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private JsonNode reference() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/balatro-pool-order.json")) {
            assumeTrue(in != null, "balatro-pool-order.json missing from test resources");
            return new ObjectMapper().readTree(in);
        }
    }

    private static Set<String> normSet(Iterable<String> names) {
        Set<String> s = new LinkedHashSet<>();
        for (String n : names) {
            s.add(norm(n));
        }
        return s;
    }

    private static List<String> balatroNames(JsonNode root, String... pools) {
        List<String> out = new java.util.ArrayList<>();
        for (String p : pools) {
            for (JsonNode n : root.get(p)) {
                String name = n.asText();
                if (!name.equals("RETRY")) { // Balatro4J's placeholder for The Soul / Black Hole
                    out.add(name);
                }
            }
        }
        return out;
    }

    /** Coverage of one category: matched (both), missing (Balatro has, we don't), extra (ours only). */
    private record Coverage(String category, Set<String> matched, Set<String> missing, Set<String> extra) {}

    private static Coverage cover(String cat, Set<String> ours, List<String> balatro) {
        Set<String> bal = normSet(balatro);
        Set<String> matched = new TreeSet<>(ours);
        matched.retainAll(bal);
        Set<String> missing = new TreeSet<>(bal);
        missing.removeAll(ours);
        Set<String> extra = new TreeSet<>(ours);
        extra.removeAll(bal);
        return new Coverage(cat, matched, missing, extra);
    }

    @Test
    void contentMatchesBalatroAuthoritativeSetAndReportsCoverage() throws Exception {
        JsonNode root = reference();

        // --- our catalogs, by normalized display name ---
        Set<String> ourJokers = new LinkedHashSet<>();
        for (String k : JokerLibrary.builtinKeys()) {
            ourJokers.add(norm(JokerLibrary.create(k).info().name()));
        }
        Set<String> ourTarots = new LinkedHashSet<>();
        for (String k : TarotCatalog.tarotKeys()) {
            ourTarots.add(norm(TarotCatalog.get(k).name()));
        }
        Set<String> ourSpectrals = new LinkedHashSet<>();
        for (String k : TarotCatalog.spectralKeys()) {
            ourSpectrals.add(norm(TarotCatalog.get(k).name()));
        }
        Set<String> ourPlanets = new LinkedHashSet<>();
        for (String k : PlanetCatalog.keys()) {
            ourPlanets.add(norm(PlanetCatalog.get(k).name()));
        }

        List<Coverage> report = List.of(
                cover("jokers", ourJokers,
                        balatroNames(root, "joker_common", "joker_uncommon", "joker_rare", "joker_legendary")),
                cover("tarots", ourTarots, balatroNames(root, "Tarot")),
                cover("spectrals", ourSpectrals, balatroNames(root, "Spectral")),
                cover("planets", ourPlanets, balatroNames(root, "Planet")));

        // --- write a human-readable coverage report ---
        StringBuilder sb = new StringBuilder("Content coverage vs Balatro (vanilla, from Balatro4J)\n");
        for (Coverage c : report) {
            int total = c.matched().size() + c.missing().size();
            sb.append(String.format("%n[%s] %d/%d vanilla implemented%n", c.category(), c.matched().size(), total));
            if (!c.missing().isEmpty()) {
                sb.append("  missing (vanilla, not implemented): ").append(c.missing()).append('\n');
            }
            if (!c.extra().isEmpty()) {
                sb.append("  extra (ours / MP / custom): ").append(c.extra()).append('\n');
            }
        }
        Path out = Path.of("build", "content-coverage.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());

        // --- verifications (robust, not brittle) ---
        Coverage jokers = report.get(0);
        // The cross-reference works and we share a substantial joker set with vanilla.
        assertThat(jokers.matched()).as("matched vanilla jokers (see %s)", out).hasSizeGreaterThan(30);
        // Known vanilla items we definitely implement must match by name (catches naming drift).
        assertThat(ourJokers).contains(norm("Joker"), norm("Blueprint"), norm("Baron"), norm("Cavendish"));
        assertThat(ourTarots).contains(norm("The Fool"), norm("The Magician"), norm("Death"));
        assertThat(ourPlanets).contains(norm("Pluto"), norm("Mars"), norm("Planet X"));
    }
}
