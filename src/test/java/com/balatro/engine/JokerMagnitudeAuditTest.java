package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.grammar.JokerDef;
import com.balatro.engine.joker.def.JokerDefLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins every vanilla joker's effect MAGNITUDES (the +mult / +chips / xMult / odds numbers) to the real
 * game's {@code config} block, dumped from game.lua to {@code balatro-joker-configs.json}. Sibling of
 * {@link JokerStatsAuditTest} (which pins cost/rarity/atlas) — together they pin a joker's whole numeric
 * surface. Nothing else catches a wrong magnitude: the preview-mirror only proves preview == server, so
 * a Jolly Joker authored as +4 instead of +8 would mis-score in BOTH and stay green.
 *
 * <p>The check is structure-agnostic: it serializes our {@link JokerDef} to a JSON tree and collects every
 * numeric constant in it (effect values, conditions, props, mods), then asserts each real-game config
 * magnitude appears among them. Compared on absolute value (a decay rate we author as {@code -0.01} still
 * matches a config {@code 0.01}); {@link #STRUCTURAL_OR_DERIVED} lists the few magnitudes we legitimately
 * represent differently (computed, or a vanilla default we override), each with a reason.
 */
class JokerMagnitudeAuditTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final double EPS = 1e-9;

    /** "key:magnitude" pairs the audit should skip — a real-game config number we legitimately don't carry
     *  as a literal (derived/computed, intentionally deviated, or vestigial in game.lua). Add with a reason. */
    private static final Set<String> STRUCTURAL_OR_DERIVED = Set.of(
            "j_vagabond:4",    // "create Tarot if money <= 4" authored as not(money >= 5) — boundary literal is 5
            "j_turtle_bean:5", // intentional BMP 0.4.2 deviation: +4 hand size (vanilla +5), see the def comment
            "j_turtle_bean:1", // h_mod: the -1/round decay is a dynamic Modify(SIZE, clamp(4−roundsPlayed)), not a literal
            "j_burnt:4");      // vanilla config extra=4 is vestigial — Burnt's code levels the first discard by 1

    @Test
    void everyVanillaJokerCarriesItsRealGameMagnitudes() throws Exception {
        JsonNode configs;
        try (InputStream in = getClass().getResourceAsStream("/balatro-joker-configs.json")) {
            assumeTrue(in != null, "balatro-joker-configs.json missing from test resources");
            configs = OM.readTree(in).path("jokers");
        }

        List<String> missing = new ArrayList<>();
        configs.fieldNames().forEachRemaining(key -> {
            JokerDef def = JokerDefLibrary.get(key);
            if (def == null) return; // not a built-in vanilla joker we model
            Set<Double> ours = new LinkedHashSet<>();
            collectNumbers(OM.valueToTree(def), ours);
            for (JsonNode mag : configs.path(key).path("magnitudes")) {
                double m = Math.abs(mag.asDouble());
                if (STRUCTURAL_OR_DERIVED.contains(key + ":" + mag.asText())) continue;
                boolean found = ours.stream().anyMatch(o -> Math.abs(Math.abs(o) - m) < EPS);
                if (!found) {
                    missing.add(key + ": real-game magnitude " + mag.asText() + " appears in no effect "
                            + "(config " + configs.path(key).path("config").asText() + "); our numbers=" + ours);
                }
            }
        });

        assertThat(missing)
                .as("jokers whose effect magnitude doesn't match game.lua — fix the def (or, if we derive "
                        + "it, add 'key:magnitude' to STRUCTURAL_OR_DERIVED with a reason)")
                .isEmpty();
    }

    private static void collectNumbers(JsonNode node, Set<Double> out) {
        if (node.isNumber()) {
            out.add(node.doubleValue());
        } else if (node.isArray() || node.isObject()) {
            node.forEach(child -> collectNumbers(child, out));
        }
    }
}
