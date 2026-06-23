package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.engine.game.Blinds;
import com.balatro.engine.game.BossBlind;
import com.balatro.engine.game.BossCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins every blind's score-requirement multiplier to the real game ({@code game.lua} {@code mult},
 * dumped to {@code balatro-blind-mults.json}). A wrong multiplier makes a blind unbeatable or trivial,
 * and nothing else catches it — the value only surfaces when you actually reach that boss. Covers the
 * Small/Big blinds ({@link Blinds.BlindType}) and every Boss ({@link BossBlind#reqMult()}).
 */
class BlindRequirementAuditTest {

    private JsonNode realMults() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/balatro-blind-mults.json")) {
            assumeTrue(in != null, "balatro-blind-mults.json missing from test resources");
            return new ObjectMapper().readTree(in).path("mults");
        }
    }

    /** Our multiplier for a real-game blind key (bl_small / bl_big -> the regular blinds; else a boss). */
    private static Double ourMult(String key, Map<String, BossBlind> bosses) {
        return switch (key) {
            case "bl_small" -> Blinds.BlindType.SMALL.mult;
            case "bl_big" -> Blinds.BlindType.BIG.mult;
            default -> bosses.containsKey(key) ? bosses.get(key).reqMult() : null;
        };
    }

    @Test
    void everyBlindMultiplierMatchesTheRealGame() throws Exception {
        JsonNode real = realMults();
        Map<String, BossBlind> bosses = new LinkedHashMap<>();
        for (BossBlind b : BossCatalog.all()) bosses.put(b.key(), b);

        List<String> problems = new ArrayList<>();
        real.fieldNames().forEachRemaining(key -> {
            double want = real.path(key).asDouble();
            Double ours = ourMult(key, bosses);
            if (ours == null) {
                problems.add(key + ": real game has it (mult " + want + ") but we model no such blind/boss");
            } else if (Math.abs(ours - want) > 1e-9) {
                problems.add(key + ": multiplier " + ours + " but real game is " + want);
            }
        });

        assertThat(problems)
                .as("blind/boss score-requirement multipliers must match game.lua")
                .isEmpty();
    }
}
