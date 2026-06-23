package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.engine.hand.HandType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The most fundamental scoring data — every poker hand's base chips/mult and per-level increments —
 * pinned against the real game's {@code G.GAME.hands} table (dumped to {@code balatro-hand-levels.json}
 * from game.lua). A wrong value here silently mis-scores EVERY hand of that type, and nothing else
 * catches it: the "pair of Kings = 60" baseline only exercises Pair's base chips, and the preview-mirror
 * only proves preview == server (both would be wrong together). This is the oracle the rest leans on.
 */
class HandTableAuditTest {

    private JsonNode realHands() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/balatro-hand-levels.json")) {
            assumeTrue(in != null, "balatro-hand-levels.json missing from test resources");
            return new ObjectMapper().readTree(in).path("hands");
        }
    }

    @Test
    void everyHandMatchesTheRealGameBaseAndLevelValues() throws Exception {
        JsonNode real = realHands();
        List<String> mismatches = new ArrayList<>();
        for (HandType h : HandType.values()) {
            JsonNode v = real.get(h.display);
            if (v == null) {
                mismatches.add(h.display + ": absent from the real-game table (display-name mismatch?)");
                continue;
            }
            check(mismatches, h.display, "base chips", h.baseChips, v.path("baseChips").asInt());
            check(mismatches, h.display, "base mult", h.baseMult, v.path("baseMult").asInt());
            check(mismatches, h.display, "level chips", h.lChips, v.path("levelChips").asInt());
            check(mismatches, h.display, "level mult", h.lMult, v.path("levelMult").asInt());
        }
        assertThat(mismatches)
                .as("hand table must match the real game's G.GAME.hands — fix HandType")
                .isEmpty();
    }

    @Test
    void everyRealHandIsModeled() throws Exception {
        JsonNode real = realHands();
        List<String> displays = new ArrayList<>();
        for (HandType h : HandType.values()) displays.add(h.display);
        List<String> missing = new ArrayList<>();
        real.fieldNames().forEachRemaining(name -> {
            if (!displays.contains(name)) missing.add(name);
        });
        assertThat(missing).as("real-game hands with no HandType — add them").isEmpty();
    }

    private static void check(List<String> out, String hand, String field, int ours, int real) {
        if (ours != real) out.add(hand + ": " + field + " = " + ours + " but real game is " + real);
    }
}
