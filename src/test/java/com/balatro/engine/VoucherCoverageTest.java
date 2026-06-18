package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.VoucherCatalog;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The safety net against silent gaps: every voucher in the catalog must be <i>classified</i>. A voucher
 * is wired if it carries {@link com.balatro.engine.joker.def.Modify} data (folded by ShopEconomy /
 * EconomyConfig / Run); the few that act through code are listed in {@link #HANDLED_IN_CODE}; the
 * deliberate no-ops in {@link #NO_OP}; and the not-yet-built ones in {@link #UNIMPLEMENTED}. A new
 * voucher that fits none of these FAILS this test, so nothing slips into the catalog without a decision,
 * and implementing a gap FAILS until its marker is removed.
 */
class VoucherCoverageTest {

    /** No Modify data, but a real effect wired in code. */
    private static final Set<String> HANDLED_IN_CODE = Set.of("v_antimatter"); // +1 joker slot (grantVoucher)

    /** Deliberately does nothing. */
    private static final Set<String> NO_OP = Set.of("v_blank");

    /** Catalog-only — effect not yet implemented (tracked so it can't be forgotten). */
    private static final Set<String> UNIMPLEMENTED = Set.of(
            "v_telescope", "v_observatory",     // Celestial pack content / Planet x1.5 mult
            "v_magic_trick", "v_illusion");     // buy playing cards in the shop

    @Test
    void everyVoucherIsClassified() {
        for (String key : VoucherCatalog.keys()) {
            boolean wired = !VoucherCatalog.get(key).mods().isEmpty()
                    || HANDLED_IN_CODE.contains(key) || NO_OP.contains(key) || UNIMPLEMENTED.contains(key);
            assertThat(wired)
                    .as("voucher '%s' is unclassified — give it Modify data, wire it, or list it as a gap", key)
                    .isTrue();
        }
    }

    @Test
    void unimplementedMarkersAreNotStale() {
        // If a gap got Modify data (or is handled), it must leave UNIMPLEMENTED — keeps the list honest.
        for (String key : UNIMPLEMENTED) {
            assertThat(VoucherCatalog.get(key).mods())
                    .as("voucher '%s' now has Modify data — remove it from UNIMPLEMENTED", key).isEmpty();
            assertThat(HANDLED_IN_CODE).doesNotContain(key);
        }
    }
}
