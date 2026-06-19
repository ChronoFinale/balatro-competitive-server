package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.game.EconomyConfig;
import com.balatro.engine.joker.JokerLibrary;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The interest cap is folded from deck + voucher data — Seed Money/Money Tree raise it (MAX),
 *  Green Deck zeroes it (MIN, which no voucher can override). */
class EconomyConfigTest {

    private static int cap(String deck, Set<String> vouchers) {
        return EconomyConfig.resolve(DeckCatalog.get(deck), vouchers, List.of()).interestCap();
    }

    @Test
    void interestCapFoldsByHighestOwnedTier() {
        assertThat(cap("d_base", Set.of())).isEqualTo(5);                              // base
        assertThat(cap("d_base", Set.of("v_seed_money"))).isEqualTo(10);               // Seed Money
        // Money Tree is the upgrade and you keep Seed Money — MAX means $20, not $30.
        assertThat(cap("d_base", Set.of("v_seed_money", "v_money_tree"))).isEqualTo(20);
        // Order-independent (MAX, not last-SET-wins).
        assertThat(cap("d_base", Set.of("v_money_tree", "v_seed_money"))).isEqualTo(20);
    }

    @Test
    void toTheMoonAddsAnUncappedInterestTier() {
        // To the Moon is now data — its UNCAPPED_INTEREST policy folds from the owned joker's mods,
        // and the formula adds an extra $1/$5 with NO cap. At $100 held: base interest = min($5 cap, $20) =
        // $5; with To the Moon = $5 + $20 (uncapped) = $25.
        var base = EconomyConfig.resolve(DeckCatalog.get("d_base"), Set.of(), List.of());
        assertThat(base.interest(100)).isEqualTo(5); // capped at $5

        var moon = EconomyConfig.resolve(DeckCatalog.get("d_base"), Set.of(),
                List.of(JokerLibrary.create("j_to_the_moon")));
        assertThat(moon.interest(100)).isEqualTo(25); // $5 capped + $20 uncapped tier
        assertThat(moon.interest(40)).isEqualTo(13);  // min(5, 8) + 8 = 13
    }

    @Test
    void greenDeckZeroesInterestAndBeatsAnyVoucherOrJoker() {
        assertThat(cap("d_green", Set.of())).isZero();                                 // Green Deck: no interest
        assertThat(cap("d_green", Set.of("v_money_tree"))).isZero();                   // MIN(0) beats Money Tree's MAX(20)
        // To the Moon's uncapped interest is still gated by Green's no-interest.
        var econ = EconomyConfig.resolve(DeckCatalog.get("d_green"), Set.of(),
                List.of(JokerLibrary.create("j_to_the_moon")));
        assertThat(econ.noInterest()).isTrue();
        assertThat(econ.interest(100)).isZero();
    }
}
