package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Shop;
import com.balatro.engine.game.Shop.Kind;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The main shop's topology: each slot's TYPE comes from vanilla weights (Joker 20 /
 * Tarot 4 / Planet 4), and joker slots roll a RARITY (Common 70 / Uncommon 25 /
 * Rare 5) then draw from that rarity's own sub-queue. Buffoon packs share those
 * sub-queues, so jokers appear at the right rarities everywhere.
 */
class ShopTopologyTest {

    private static final Set<String> RARITIES = Set.of("Common", "Uncommon", "Rare");

    @Test
    void slotTypeBands() {
        assertThat(Shop.rollSlotType(0.0)).isEqualTo(Kind.JOKER);
        assertThat(Shop.rollSlotType(0.70)).isEqualTo(Kind.JOKER);   // < 20/28
        assertThat(Shop.rollSlotType(0.75)).isEqualTo(Kind.TAROT);   // 20/28 .. 24/28
        assertThat(Shop.rollSlotType(0.90)).isEqualTo(Kind.PLANET);
    }

    @Test
    void rarityBands() {
        assertThat(Shop.rollRarity(0.0)).isEqualTo("Common");
        assertThat(Shop.rollRarity(0.69)).isEqualTo("Common");
        assertThat(Shop.rollRarity(0.70)).isEqualTo("Uncommon");
        assertThat(Shop.rollRarity(0.94)).isEqualTo("Uncommon");
        assertThat(Shop.rollRarity(0.95)).isEqualTo("Rare");
    }

    @Test
    void rarityDistributionIs70_25_5() {
        // Uniform sweep over [0,1): exact counts at the 70/25/5 boundaries.
        int n = 10_000, common = 0, uncommon = 0, rare = 0;
        for (int i = 0; i < n; i++) {
            switch (Shop.rollRarity(i / (double) n)) {
                case "Common" -> common++;
                case "Uncommon" -> uncommon++;
                default -> rare++;
            }
        }
        assertThat(common).isEqualTo(7000);
        assertThat(uncommon).isEqualTo(2500);
        assertThat(rare).isEqualTo(500);
    }

    @Test
    void shopJokersAlwaysHaveAValidRarity() {
        var shop = Shop.generate(new QueueSet(new RandomStreams("TOPO")), 24, List.of(), Set.of(), false);
        assertThat(shop.items().stream().filter(it -> it.kind() == Kind.JOKER))
                .isNotEmpty()
                .allMatch(it -> RARITIES.contains(it.rarity()));
    }

    @Test
    void aRarityRestrictedPoolOnlyOffersThatRarity() {
        List<String> rares = JokerLibrary.keysByRarity("Rare");
        assertThat(rares).isNotEmpty();
        // Common/Uncommon rarity rolls fall back to the (Rare-only) pool, so every joker is Rare.
        var shop = Shop.generate(new QueueSet(new RandomStreams("RARE")), 24, rares, Set.of(), false);
        assertThat(shop.items().stream().filter(it -> it.kind() == Kind.JOKER))
                .allMatch(it -> "Rare".equals(it.rarity()));
    }
}
