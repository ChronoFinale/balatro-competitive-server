package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.game.EconomyConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The interest cap is folded from voucher data — highest owned tier wins (Seed Money/Money Tree). */
class EconomyConfigTest {

    private static int cap(Set<String> vouchers) {
        return EconomyConfig.resolve(DeckCatalog.get("d_base"), vouchers, List.of()).interestCap();
    }

    @Test
    void interestCapFoldsByHighestOwnedTier() {
        assertThat(cap(Set.of())).isEqualTo(5);                                 // base
        assertThat(cap(Set.of("v_seed_money"))).isEqualTo(10);                  // Seed Money
        // Money Tree is the upgrade and you keep Seed Money — MAX means $20, not $30.
        assertThat(cap(Set.of("v_seed_money", "v_money_tree"))).isEqualTo(20);
        // Order-independent (MAX, not last-SET-wins).
        assertThat(cap(Set.of("v_money_tree", "v_seed_money"))).isEqualTo(20);
    }
}
