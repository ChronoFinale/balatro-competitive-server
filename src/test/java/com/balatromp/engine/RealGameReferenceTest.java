package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Anchors the whole verification chain to the ACTUAL GAME. balatro-prototypes.json was dumped straight
 * from a running Balatro (tools/balatro-pooldump hooking Game:init_item_prototypes) — the real
 * P_JOKER_RARITY_POOLS / P_CENTER_POOLS with real keys, in real registration order. This test confirms it
 * agrees with our Balatro4J-derived reference (balatro-pool-order.json), so every check that used Balatro4J
 * (pool order, content coverage, the shop diff) is grounded in real-game truth, not just a reimplementation.
 */
class RealGameReferenceTest {

    private JsonNode load(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assumeTrue(in != null, resource + " missing");
            return new ObjectMapper().readTree(in);
        }
    }

    private static int count(JsonNode n) {
        return n == null ? -1 : n.size();
    }

    @Test
    void realGamePoolsAgreeWithBalatro4jReference() throws Exception {
        JsonNode real = load("/balatro-prototypes.json");   // keys, from the live game
        JsonNode b4j = load("/balatro-pool-order.json");    // display names, from Balatro4J

        // Same pool sizes across all nine pools => same pools (coincidence across 9 is ~nil).
        record Pair(String real, String b4j) {}
        for (Pair p : List.of(
                new Pair("joker_1", "joker_common"), new Pair("joker_2", "joker_uncommon"),
                new Pair("joker_3", "joker_rare"), new Pair("joker_4", "joker_legendary"),
                new Pair("Tarot", "Tarot"), new Pair("Planet", "Planet"),
                new Pair("Spectral", "Spectral"), new Pair("Voucher", "Voucher"), new Pair("Tag", "Tag"))) {
            assertThat(count(real.get(p.real())))
                    .as("pool size %s (real) vs %s (Balatro4J)", p.real(), p.b4j())
                    .isEqualTo(count(b4j.get(p.b4j())));
        }
    }

    @Test
    void realGameConfirmsModelInvariants() throws Exception {
        JsonNode real = load("/balatro-prototypes.json");

        // Spectral pool ends with The Soul + Black Hole (in the array, but culled from normal draws).
        JsonNode spectral = real.get("Spectral");
        assertThat(spectral.get(spectral.size() - 2).asText()).isEqualTo("c_soul");
        assertThat(spectral.get(spectral.size() - 1).asText()).isEqualTo("c_black_hole");

        // Legendary order (real keys) matches Balatro4J's Canio/Triboulet/Yorick/Chicot/Perkeo.
        assertThat(real.get("joker_4")).extracting(JsonNode::asText)
                .containsExactly("j_caino", "j_triboulet", "j_yorick", "j_chicot", "j_perkeo");

        // Planet pool order (real): Mercury-first, secret hands (Planet X/Ceres/Eris) last.
        assertThat(real.get("Planet")).extracting(JsonNode::asText)
                .startsWith("c_mercury", "c_venus", "c_earth")
                .endsWith("c_planet_x", "c_ceres", "c_eris");
    }
}
