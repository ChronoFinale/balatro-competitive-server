package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.grammar.JokerInfo;
import com.balatro.engine.joker.JokerLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The ground-truth guard that should have existed all along. Our other tests pin <i>internal</i>
 * consistency (pair of kings = 60, every joker does something, preview matches server) — none of them know
 * the <i>real</i> numbers, so a wrong constant (Acrobat's cost, Merry Andy's discards) compiles and passes
 * everything. This diffs every vanilla joker's cost and rarity against the authoritative dump from Balatro's
 * {@code game.lua} ({@code /balatro-joker-stats.json}). A mismatch fails the build: it's either a content bug
 * (fix the def) or an intentional BMP deviation (record it in {@link #INTENTIONAL_DEVIATIONS} with the why).
 *
 * <p>Jokers not in the dump are BMP/Nemesis additions (Pizza, Speedrun, Penny Pincher, …) — they have no
 * vanilla row, so they're exempt by construction.
 */
class JokerStatsAuditTest {

    /** game.lua rarity ints -> our rarity names. */
    private static final Map<Integer, com.balatro.grammar.Rarity> RARITY = Map.of(
            1, com.balatro.grammar.Rarity.COMMON, 2, com.balatro.grammar.Rarity.UNCOMMON,
            3, com.balatro.grammar.Rarity.RARE, 4, com.balatro.grammar.Rarity.LEGENDARY);

    /** Vanilla values we deliberately diverge from (BMP balance changes). Each entry must ACTUALLY differ
     *  from vanilla or the staleness check below fails. Empty = we match vanilla everywhere. */
    private static final Set<String> INTENTIONAL_DEVIATIONS = Set.of();

    private JsonNode vanillaStats() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/balatro-joker-stats.json")) {
            assumeTrue(in != null, "balatro-joker-stats.json missing from test resources");
            return new ObjectMapper().readTree(in).path("jokers");
        }
    }

    @Test
    void everyVanillaJokerMatchesGameLuaCostAndRarity() throws Exception {
        JsonNode stats = vanillaStats();
        List<String> mismatches = new ArrayList<>();
        for (String key : JokerLibrary.builtinKeys()) {
            JsonNode v = stats.get(key);
            if (v == null) continue;                       // BMP/Nemesis joker — no vanilla row
            if (INTENTIONAL_DEVIATIONS.contains(key)) continue;
            JokerInfo info = JokerLibrary.create(key).info();
            int wantCost = v.path("cost").asInt();
            com.balatro.grammar.Rarity wantRarity = RARITY.get(v.path("rarity").asInt());
            if (info.cost() != wantCost) {
                mismatches.add(key + ": cost " + info.cost() + " but vanilla is " + wantCost);
            }
            if (wantRarity != info.rarity()) {
                mismatches.add(key + ": rarity " + info.rarity().wire() + " but vanilla is " + wantRarity.wire());
            }
            if (v.has("x") && (info.atlasX() != v.path("x").asInt() || info.atlasY() != v.path("y").asInt())) {
                mismatches.add(key + ": atlas (" + info.atlasX() + "," + info.atlasY()
                        + ") but vanilla sprite is (" + v.path("x").asInt() + "," + v.path("y").asInt() + ")");
            }
        }
        assertThat(mismatches)
                .as("joker cost/rarity/atlas must match vanilla game.lua — fix the def or record a BMP deviation")
                .isEmpty();
    }

    @Test
    void intentionalDeviationsActuallyDeviate() throws Exception {
        JsonNode stats = vanillaStats();
        for (String key : INTENTIONAL_DEVIATIONS) {
            JsonNode v = stats.get(key);
            if (v == null) continue;
            JokerInfo info = JokerLibrary.create(key).info();
            boolean differs = info.cost() != v.path("cost").asInt()
                    || RARITY.get(v.path("rarity").asInt()) != info.rarity();
            assertThat(differs)
                    .as("'%s' is listed as an intentional deviation but now matches vanilla — drop it", key)
                    .isTrue();
        }
    }
}
