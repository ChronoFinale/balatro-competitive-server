package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Blinds;
import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.Stake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Balatro's 8 cumulative difficulty stakes, validated against the real game
 * (game.lua:2050-2059, misc_functions.lua:922-944). Covers the difficulty effects
 * wired into the engine — Red (Small Blind $0), Green/Purple (harder blind curve),
 * Blue (-1 discard). Sticker stakes (Black/Orange/Gold) are flags only until the
 * joker-sticker subsystem lands.
 */
class StakeTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    // --- the cumulative model: each tier inherits every lower tier ---

    @Test
    void stakesAreCumulative() {
        assertThat(Stake.WHITE.smallBlindNoReward()).isFalse();
        assertThat(Stake.WHITE.scaling()).isEqualTo(1);
        assertThat(Stake.WHITE.discardDelta()).isZero();

        // Gold sits at the top and inherits everything below it.
        assertThat(Stake.GOLD.smallBlindNoReward()).isTrue();   // from Red
        assertThat(Stake.GOLD.scaling()).isEqualTo(3);          // from Purple
        assertThat(Stake.GOLD.discardDelta()).isEqualTo(-1);    // from Blue
        assertThat(Stake.GOLD.eternalsInShop()).isTrue();       // from Black
        assertThat(Stake.GOLD.perishablesInShop()).isTrue();    // from Orange
        assertThat(Stake.GOLD.rentalsInShop()).isTrue();        // Gold itself
    }

    @Test
    void scalingTierThresholds() {
        assertThat(Stake.WHITE.scaling()).isEqualTo(1);
        assertThat(Stake.RED.scaling()).isEqualTo(1);
        assertThat(Stake.GREEN.scaling()).isEqualTo(2);
        assertThat(Stake.BLACK.scaling()).isEqualTo(2);
        assertThat(Stake.BLUE.scaling()).isEqualTo(2);   // Blue (5) sits between Green (3) and Purple (6)
        assertThat(Stake.PURPLE.scaling()).isEqualTo(3);
        assertThat(Stake.GOLD.scaling()).isEqualTo(3);
    }

    @Test
    void discardDeltaThreshold() {
        for (Stake s : new Stake[]{Stake.WHITE, Stake.RED, Stake.GREEN, Stake.BLACK}) {
            assertThat(s.discardDelta()).as(s.name()).isZero();
        }
        for (Stake s : new Stake[]{Stake.BLUE, Stake.PURPLE, Stake.ORANGE, Stake.GOLD}) {
            assertThat(s.discardDelta()).as(s.name()).isEqualTo(-1);
        }
    }

    @Test
    void parseIsLenient() {
        assertThat(Stake.parse("GOLD")).isEqualTo(Stake.GOLD);
        assertThat(Stake.parse("gold")).isEqualTo(Stake.GOLD);
        assertThat(Stake.parse("Gold Stake")).isEqualTo(Stake.GOLD);
        assertThat(Stake.parse("8")).isEqualTo(Stake.GOLD);
        assertThat(Stake.parse(null)).isEqualTo(Stake.WHITE);
        assertThat(Stake.parse("nonsense")).isEqualTo(Stake.WHITE);
    }

    // --- the blind-requirement curve is a TABLE SWAP, not a multiplier (misc_functions.lua:922-944) ---

    @Test
    void blindCurveScalesByTier() {
        // BMP "release" competitive curve (release.lua:748-754): tier 1 vanilla, tier 2 green, tier 3 purple.
        int[] tier1 = {300, 800, 2000, 5000, 11000, 20000, 35000, 50000};      // White/Red (vanilla base)
        int[] tier2 = {300, 1000, 3200, 9000, 18000, 32000, 56000, 90000};     // Green..Blue (BMP green)
        int[] tier3 = {300, 1200, 3600, 10000, 25000, 50000, 90000, 180000};   // Purple+ (BMP purple)
        for (int ante = 1; ante <= 8; ante++) {
            assertThat(Blinds.requirement(ante, BlindType.SMALL, STD, 1)).as("tier1 ante " + ante).isEqualTo(tier1[ante - 1]);
            assertThat(Blinds.requirement(ante, BlindType.SMALL, STD, 2)).as("tier2 ante " + ante).isEqualTo(tier2[ante - 1]);
            assertThat(Blinds.requirement(ante, BlindType.SMALL, STD, 3)).as("tier3 ante " + ante).isEqualTo(tier3[ante - 1]);
        }
    }

    @Test
    void endlessAntesUseTheSharedFormulaAnchoredOnTheBmpTable() {
        // Past ante 8 it's a formula, not a table (release.lua:758-760): floor(a*(1.6+(0.75c)^d)^c)
        // rounded to 2 sig figs, anchored on the tier's ante-8 value (BMP purple = 180000). Locks that
        // endless uses BMP's anchor, not vanilla's (which would give 460000 / 1.2e9).
        assertThat(Blinds.requirement(9, BlindType.SMALL, STD, 3)).isEqualTo(410_000L);
        assertThat(Blinds.requirement(12, BlindType.SMALL, STD, 3)).isEqualTo(1_000_000_000L);
    }

    @Test
    void bigAndBossMultipliersStackOnTopOfTheTier() {
        // ante 2, tier 2 (BMP green): base 1000 -> Big x1.5 = 1500, Boss x2 = 2000.
        assertThat(Blinds.requirement(2, BlindType.BIG, STD, 2)).isEqualTo(1500);
        assertThat(Blinds.requirement(2, BlindType.BOSS, STD, 2)).isEqualTo(2000);
    }

    // --- the difficulty effects actually take hold in a live Run ---

    @Test
    void blueStakeStartsWithOneFewerDiscard() {
        assertThat(new Run(STD, "S", Stake.WHITE).view().discardsLeft()).isEqualTo(STD.discards());      // 3
        assertThat(new Run(STD, "S", Stake.BLUE).view().discardsLeft()).isEqualTo(STD.discards() - 1);   // 2
    }

    @Test
    void redStakeSmallBlindPaysNoReward() {
        Run white = new Run(STD, "STK", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"), Stake.WHITE);
        white.play(FIVE); // clear the Small blind -> shop
        assertThat(((Number) white.view().counters().get("cashOutReward")).intValue())
                .isEqualTo(BlindType.SMALL.reward); // White: the usual $3

        Run red = new Run(STD, "STK", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"), Stake.RED);
        red.play(FIVE);
        assertThat(((Number) red.view().counters().get("cashOutReward")).intValue())
                .isZero(); // Red+: the Small blind pays nothing
    }

    @Test
    void clientViewExposesTheStake() {
        assertThat(new Run(STD, "S", Stake.PURPLE).view().stake()).isEqualTo("Purple Stake");
    }

    // --- the Balatro-Multiplayer (BMP) stakes that sit above Gold ---

    @Test
    void multiplayerStakesResolveToTheirRealModifiers() {
        // Planet: Black effects + Perishable + scaling 3, but NO -1 discard and NO rental.
        assertThat(Stake.PLANET.smallBlindNoReward()).isTrue();
        assertThat(Stake.PLANET.eternalsInShop()).isTrue();
        assertThat(Stake.PLANET.perishablesInShop()).isTrue();
        assertThat(Stake.PLANET.rentalsInShop()).isFalse();
        assertThat(Stake.PLANET.discardDelta()).isZero();   // BMP stakes drop Blue's penalty
        assertThat(Stake.PLANET.scaling()).isEqualTo(3);

        // Spectral: Planet + Rental + scaling 4.
        assertThat(Stake.SPECTRAL.rentalsInShop()).isTrue();
        assertThat(Stake.SPECTRAL.scaling()).isEqualTo(4);
        assertThat(Stake.SPECTRAL.discardDelta()).isZero();

        // Spectral+: Spectral + scaling 5.
        assertThat(Stake.SPECTRAL_PLUS.scaling()).isEqualTo(5);
        assertThat(Stake.SPECTRAL_PLUS.rentalsInShop()).isTrue();
    }

    @Test
    void parseHandlesMultiplayerNames() {
        assertThat(Stake.parse("SPECTRAL_PLUS")).isEqualTo(Stake.SPECTRAL_PLUS);
        assertThat(Stake.parse("Spectral+ Stake")).isEqualTo(Stake.SPECTRAL_PLUS);
        assertThat(Stake.parse("spectralplus")).isEqualTo(Stake.SPECTRAL_PLUS);
        assertThat(Stake.parse("11")).isEqualTo(Stake.SPECTRAL_PLUS);
        assertThat(Stake.parse("Planet")).isEqualTo(Stake.PLANET);
        assertThat(Stake.parse("9")).isEqualTo(Stake.PLANET);
    }

    @Test
    void scalingTiersAboveThreeClampToTierThreeForNow() {
        // No authoritative requirement table exists past tier 3 (vanilla get_blind_amount keys on
        // scaling == 2/3); the BMP "scales even faster" tiers clamp to tier 3 until a curve is chosen.
        for (int ante = 1; ante <= 8; ante++) {
            long tier3 = Blinds.requirement(ante, BlindType.SMALL, STD, 3);
            assertThat(Blinds.requirement(ante, BlindType.SMALL, STD, 4)).as("tier4 ante " + ante).isEqualTo(tier3);
            assertThat(Blinds.requirement(ante, BlindType.SMALL, STD, 5)).as("tier5 ante " + ante).isEqualTo(tier3);
        }
    }
}
