package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.TagCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The tag half of the safety net (sibling of {@link VoucherCoverageTest} / {@link JokerCoverageTest}).
 * Tags don't carry data — each resolves through a hand-wired branch in {@code Run} keyed by its
 * {@link TagCatalog.Timing}. So coverage here is an explicit ledger: every catalog tag must be listed
 * in exactly one handling bucket below. A new tag that nobody wires fails this test, so it can't slip
 * into the catalog as a silent no-op the way Boss Tag did.
 */
class TagCoverageTest {

    /** Resolves the instant it's claimed — money/level effects computed in {@code Run.claimTag}. */
    private static final Set<String> IMMEDIATE = Set.of(
            "tag_economy", "tag_skip", "tag_orbital", "tag_handy", "tag_garbage", "tag_top_up");

    /** Resolves when the next shop opens — free jokers/editions, packs, voucher, coupon, d6. */
    private static final Set<String> ON_SHOP = Set.of(
            "tag_uncommon", "tag_rare", "tag_negative", "tag_foil", "tag_holo", "tag_polychrome",
            "tag_voucher", "tag_standard", "tag_charm", "tag_meteor", "tag_buffoon", "tag_ethereal",
            "tag_coupon", "tag_d_six");

    /** Resolves at a specific later moment in the run loop. */
    private static final Set<String> DEFERRED = Set.of(
            "tag_investment", // ON_BOSS_DEFEAT: +$25 payout
            "tag_juggle",     // NEXT_BLIND: +3 hand size
            "tag_boss",       // HELD: free boss reroll on arrival at the boss blind
            "tag_double");    // HELD: duplicates the next claimed tag

    @Test
    void everyTagIsClassified() {
        List<String> unclassified = new ArrayList<>();
        for (String key : TagCatalog.keys()) {
            boolean wired = IMMEDIATE.contains(key) || ON_SHOP.contains(key) || DEFERRED.contains(key);
            if (!wired) unclassified.add(key);
        }
        assertThat(unclassified)
                .as("tags with no wired effect — implement them in Run and list them in a bucket")
                .isEmpty();
    }

    @Test
    void bucketsAreNotStale() {
        // Every listed key must still exist in the catalog (no rot when tags are renamed/removed).
        for (Set<String> bucket : List.of(IMMEDIATE, ON_SHOP, DEFERRED)) {
            for (String key : bucket) {
                assertThat(TagCatalog.get(key)).as("listed tag '%s' is gone from the catalog", key).isNotNull();
            }
        }
    }
}
