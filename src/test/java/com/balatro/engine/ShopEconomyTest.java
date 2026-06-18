package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.ShopEconomy;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The shop economy is a pure function of owned vouchers (sibling of EconomyConfig/ShopConfig). */
class ShopEconomyTest {

    @Test
    void noVouchersIsThePlainBaseline() {
        ShopEconomy e = ShopEconomy.resolve(Set.of());
        assertThat(e.slots()).isEqualTo(2);
        assertThat(e.priceMultiplier()).isEqualTo(1.0);
        assertThat(e.rerollDiscount()).isZero();
        assertThat(e.editionMultiplier()).isEqualTo(1.0);
    }

    @Test
    void vouchersDeriveSlotsPriceRerollAndEditionOdds() {
        assertThat(ShopEconomy.resolve(Set.of("v_overstock")).slots()).isEqualTo(3);
        assertThat(ShopEconomy.resolve(Set.of("v_overstock_plus")).slots()).isEqualTo(4);
        assertThat(ShopEconomy.resolve(Set.of("v_liquidation")).priceMultiplier()).isEqualTo(0.50);
        assertThat(ShopEconomy.resolve(Set.of("v_clearance_sale")).priceMultiplier()).isEqualTo(0.75);
        assertThat(ShopEconomy.resolve(Set.of("v_reroll_surplus", "v_reroll_glut")).rerollDiscount()).isEqualTo(4);
        assertThat(ShopEconomy.resolve(Set.of("v_glow_up")).polyMultiplier()).isEqualTo(7.0);
    }
}
