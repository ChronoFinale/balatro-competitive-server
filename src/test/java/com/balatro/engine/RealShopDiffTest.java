package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.balatro.engine.rng.vanilla.BalatroPool;
import com.balatro.engine.rng.vanilla.BalatroPrng;
import com.balatro.engine.rng.vanilla.BalatroShop;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * THE real-game seeded-shop diff. real-shop.json is the actual ante-1 shop the LIVE game generated for
 * seed ABCD1234, dumped by tools/balatro-pooldump/shopdump.lua calling the game's own create_card_for_shop
 * — PLUS the exact CULLED joker pools (get_current_pool output, with UNAVAILABLE holes for locked jokers,
 * and reflecting whatever mods were loaded). This composes our verified PRNG + draw mechanism over those
 * REAL pools and asserts it reproduces the live shop byte-for-byte. Fully independent: the reference is the
 * game itself. Uses VANILLA resample (the_order=false in the dump): floor index + `_resample{n}` keys.
 */
class RealShopDiffTest {

    private JsonNode load(String res) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(res)) {
            assumeTrue(in != null, res + " missing (dump it from the live game)");
            return new ObjectMapper().readTree(in);
        }
    }

    private List<String> list(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            out.add(n.asText());
        }
        return out;
    }

    @Test
    void ourGeneratorReproducesTheLiveGamesAnte1Shop() throws Exception {
        JsonNode real = load("/real-shop.json");
        JsonNode pools = load("/balatro-prototypes.json");
        assumeTrue(real.has("culled"), "real-shop.json predates the culled-pool dump; re-dump from the game");
        int ante = real.get("ante").asInt();
        JsonNode realShop = real.get("shop");

        // Joker pools = the REAL culled pools the game drew from (locks/mods baked in), mutable for dedup.
        List<String> common = list(real.get("culled").get("common"));
        List<String> uncommon = list(real.get("culled").get("uncommon"));
        List<String> rare = list(real.get("culled").get("rare"));
        List<String> tarots = list(pools.get("Tarot"));   // not lock-gated; full pool, single draw
        List<String> planets = list(pools.get("Planet"));

        BalatroPrng prng = new BalatroPrng(real.get("seed").asText()); // ABCD1234, vanilla
        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();

        for (int i = 0; i < realShop.size(); i++) {
            JsonNode it = realShop.get(i);
            expected.add(it.get("key").asText() + "/" + it.get("edition").asText());

            String type = BalatroShop.slotType(prng, String.valueOf(ante)); // cdt{ante}
            switch (type) {
                case "Joker" -> {
                    int tier = BalatroShop.rarityTier(prng, ante + "sho"); // rarity{ante}sho
                    List<String> p = tier == 3 ? rare : tier == 2 ? uncommon : common;
                    String edition = BalatroShop.pollEdition(prng, "edisho" + ante);
                    int idx = BalatroPool.pickIndexVanilla(prng, "Joker" + tier + "sho" + ante, p);
                    String key = p.get(idx);
                    p.set(idx, BalatroPool.UNAVAILABLE); // within-shop dedup (used_jokers)
                    actual.add(key + "/" + edition);
                }
                case "Tarot" -> actual.add(BalatroPool.draw(prng, "Tarotsho" + ante, tarots) + "/none");
                case "Planet" -> actual.add(BalatroPool.draw(prng, "Planetsho" + ante, planets) + "/none");
                default -> throw new IllegalStateException(type);
            }
        }

        assertThat(actual)
                .as("our composed shop vs the LIVE game's ante-1 shop for seed %s", real.get("seed").asText())
                .isEqualTo(expected);
    }
}
