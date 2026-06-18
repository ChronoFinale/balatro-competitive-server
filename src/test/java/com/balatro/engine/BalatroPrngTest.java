package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.engine.rng.vanilla.BalatroOrder;
import com.balatro.engine.rng.vanilla.BalatroPool;
import com.balatro.engine.rng.vanilla.BalatroPrng;
import com.balatro.engine.rng.vanilla.BalatroShop;
import com.balatro.engine.rng.vanilla.LuaJitRandom;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * Bit-exact check of the float layers of Balatro's PRNG (Stage 3, the bit-exact milestone) against
 * golden vectors LuaJIT 2.1 produced via {@code tools/balatro_prng_ref.lua}. We compare the raw IEEE-754
 * doubles exactly ({@code ==}) — the vectors are emitted with %.17g so they round-trip to the exact bits.
 * If absent (no LuaJIT on this machine / vectors not regenerated), the test self-skips rather than fails.
 *
 * <p>Only pseudohash + pseudoseed are checked here; pseudorandom waits on the lj_prng port
 * (see {@link com.balatro.engine.rng.vanilla.BalatroPrng}).
 */
class BalatroPrngTest {

    /** Golden LuaJIT-2.1 vectors, committed at src/test/resources (regenerate with
     *  {@code luajit tools/balatro_prng_ref.lua src/test/resources/prng-vectors.json}). */
    private JsonNode load() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/prng-vectors.json")) {
            assumeTrue(in != null, "prng-vectors.json missing from test resources");
            return new ObjectMapper().readTree(in);
        }
    }

    private static double d(JsonNode n) {
        return Double.parseDouble(n.asText());
    }

    @Test
    void pseudohashMatchesLuaJitBitForBit() throws Exception {
        JsonNode root = load();
        for (JsonNode e : root.get("pseudohash")) {
            String s = e.get("s").asText();
            assertThat(BalatroPrng.pseudohash(s))
                    .as("pseudohash(%s)", s)
                    .isEqualTo(d(e.get("h")));
        }
    }

    @Test
    void pseudoseedSequenceMatchesLuaJitBitForBit() throws Exception {
        JsonNode root = load();
        for (JsonNode run : root.get("runs")) {
            String seed = run.get("seed").asText();
            // hashed_seed = pseudohash(seed)
            assertThat(BalatroPrng.pseudohash(seed))
                    .as("hashed_seed for %s", seed)
                    .isEqualTo(d(run.get("hashed_seed")));
            // Each key's first-N pseudoseed advances must match in order (state is stateful per key).
            for (JsonNode keyNode : run.get("keys")) {
                BalatroPrng prng = new BalatroPrng(seed); // fresh state per key column, mirroring the generator
                String key = keyNode.get("key").asText();
                JsonNode seeds = keyNode.get("pseudoseed");
                // Burn other keys' state? No — the generator advanced only this key in its own loop,
                // but pseudoseed state is per-key and independent, so a fresh prng reproduces this column.
                for (int i = 0; i < seeds.size(); i++) {
                    assertThat(prng.pseudoseed(key))
                            .as("pseudoseed(%s)[%d] for seed %s", key, i, seed)
                            .isEqualTo(d(seeds.get(i)));
                }
            }
        }
    }

    @Test
    void rawMathRandomMatchesLuaJitBitForBit() throws Exception {
        JsonNode root = load();
        for (JsonNode e : root.get("raw_random")) {
            double seed = d(e.get("seed"));
            LuaJitRandom rng = new LuaJitRandom();
            rng.randomseed(seed);
            JsonNode draws = e.get("draws");
            for (int i = 0; i < draws.size(); i++) {
                assertThat(rng.random())
                        .as("math.random()[%d] after randomseed(%s)", i, e.get("seed").asText())
                        .isEqualTo(d(draws.get(i)));
            }
        }
    }

    @Test
    void pseudorandomFullStackMatchesLuaJitBitForBit() throws Exception {
        JsonNode root = load();
        for (JsonNode run : root.get("runs")) {
            String seed = run.get("seed").asText();
            for (JsonNode keyNode : run.get("keys")) {
                BalatroPrng prng = new BalatroPrng(seed);
                String key = keyNode.get("key").asText();
                JsonNode rands = keyNode.get("pseudorandom");
                for (int i = 0; i < rands.size(); i++) {
                    assertThat(prng.pseudorandom(key))
                            .as("pseudorandom(%s)[%d] for seed %s", key, i, seed)
                            .isEqualTo(d(rands.get(i)));
                }
            }
        }
    }

    @Test
    void poolSelectionMatchesLuaJitCreateCard() throws Exception {
        JsonNode root = load();
        JsonNode ps = root.get("pool_select");
        List<String> pool = new ArrayList<>();
        for (JsonNode k : ps.get("pool")) {
            pool.add(k.asText());
        }
        for (JsonNode c : ps.get("cases")) {
            String seed = c.get("seed").asText();
            String poolKey = c.get("pool_key").asText();
            BalatroPrng prng = new BalatroPrng(seed);
            JsonNode picks = c.get("picks");
            for (int i = 0; i < picks.size(); i++) {
                assertThat(BalatroPool.draw(prng, poolKey, pool))
                        .as("create_card pick[%d] from %s with seed %s", i, poolKey, seed)
                        .isEqualTo(picks.get(i).asText());
            }
        }
    }

    @Test
    void ratePollsMatchLuaJit() throws Exception {
        JsonNode root = load();
        for (JsonNode c : root.get("rate_polls")) {
            String seed = c.get("seed").asText();
            BalatroPrng edPrng = new BalatroPrng(seed);
            JsonNode eds = c.get("editions");
            for (int i = 0; i < eds.size(); i++) {
                assertThat(BalatroShop.pollEdition(edPrng, "edi_test"))
                        .as("poll_edition[%d] for seed %s", i, seed).isEqualTo(eds.get(i).asText());
            }
            BalatroPrng rPrng = new BalatroPrng(seed);
            JsonNode rars = c.get("rarities");
            for (int i = 0; i < rars.size(); i++) {
                assertThat(BalatroShop.rarityTier(rPrng, "sho"))
                        .as("rarity tier[%d] for seed %s", i, seed).isEqualTo(rars.get(i).asInt());
            }
        }
    }

    @Test
    void editionThresholdsCoverEveryBand() {
        // mod 1: 1 - {0.04, 0.02, 0.006, 0.003} = {0.96, 0.98, 0.994, 0.997}
        assertThat(BalatroShop.editionFor(0.95)).isEqualTo("none");
        assertThat(BalatroShop.editionFor(0.965)).isEqualTo("foil");
        assertThat(BalatroShop.editionFor(0.985)).isEqualTo("holo");
        assertThat(BalatroShop.editionFor(0.995)).isEqualTo("polychrome");
        assertThat(BalatroShop.editionFor(0.999)).isEqualTo("negative");
        // mod 2, no_neg (standard cards): bands double, negatives suppressed -> top band is polychrome
        assertThat(BalatroShop.editionFor(0.999, 2, true)).isEqualTo("polychrome"); // > 1-0.012
        assertThat(BalatroShop.editionFor(0.95, 2, true)).isEqualTo("foil");        // > 1-0.08
        assertThat(BalatroShop.editionFor(0.90, 2, true)).isEqualTo("none");
    }

    @Test
    void rankedSeedPrefixShiftsTheWholeSequence() {
        assertThat(BalatroOrder.rankedSeed("TESTSEED")).isEqualTo("*TESTSEED");
        assertThat(BalatroOrder.rankedSeed("*TESTSEED")).isEqualTo("*TESTSEED"); // idempotent
        assertThat(BalatroOrder.anteFreeKey("Joker3sho")).isEqualTo("Joker3sho0");
        // The "*" marker feeds pseudohash, so ranked's hashed_seed (and thus every draw) differs from vanilla.
        assertThat(BalatroOrder.rankedPrng("TESTSEED").hashedSeed())
                .isEqualTo(new BalatroPrng("*TESTSEED").hashedSeed())
                .isNotEqualTo(new BalatroPrng("TESTSEED").hashedSeed());
    }

    @Test
    void cardPollsMatchLuaJit() throws Exception {
        JsonNode root = load();
        for (JsonNode c : root.get("card_polls")) {
            String seed = c.get("seed").asText();
            BalatroPrng sPrng = new BalatroPrng(seed);
            JsonNode slots = c.get("slot_types");
            for (int i = 0; i < slots.size(); i++) {
                assertThat(BalatroShop.slotType(sPrng, "0"))
                        .as("slot type[%d] for seed %s", i, seed).isEqualTo(slots.get(i).asText());
            }
            BalatroPrng sealPrng = new BalatroPrng(seed);
            JsonNode seals = c.get("seals");
            for (int i = 0; i < seals.size(); i++) {
                assertThat(BalatroShop.standardSeal(sealPrng, "0"))
                        .as("standard seal[%d] for seed %s", i, seed).isEqualTo(seals.get(i).asText());
            }
            BalatroPrng edPrng = new BalatroPrng(seed);
            JsonNode sed = c.get("std_editions");
            for (int i = 0; i < sed.size(); i++) {
                assertThat(BalatroShop.pollStandardEdition(edPrng, "0"))
                        .as("std edition[%d] for seed %s", i, seed).isEqualTo(sed.get(i).asText());
            }
        }
    }
}
