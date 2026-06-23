package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.engine.game.PlanetCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins every Planet's target hand to the real game ({@code game.lua} {@code config.hand_type}, dumped to
 * {@code balatro-planet-hands.json}). A planet wired to the wrong hand silently levels the wrong poker
 * hand forever — and the per-level amounts are already pinned by {@link HandTableAuditTest}, so this is
 * the other half: that each planet points at the right hand.
 */
class PlanetHandAuditTest {

    private JsonNode realPlanets() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/balatro-planet-hands.json")) {
            assumeTrue(in != null, "balatro-planet-hands.json missing from test resources");
            return new ObjectMapper().readTree(in).path("planets");
        }
    }

    @Test
    void everyPlanetTargetsTheRealGameHand() throws Exception {
        JsonNode real = realPlanets();
        List<String> problems = new ArrayList<>();
        real.fieldNames().forEachRemaining(key -> {
            String wantHand = real.path(key).asText();
            PlanetCatalog.Planet p = PlanetCatalog.get(key);
            if (p == null) {
                problems.add(key + ": real game has it (levels '" + wantHand + "') but we model no such planet");
            } else if (!p.hand().display.equals(wantHand)) {
                problems.add(key + ": levels '" + p.hand().display + "' but real game levels '" + wantHand + "'");
            }
        });
        assertThat(problems)
                .as("planet -> hand mappings must match game.lua config.hand_type")
                .isEmpty();
    }
}
