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

    @Test
    void merchantAndTycoonRaiseTheConsumableSlotWeights() {
        assertThat(ShopEconomy.resolve(Set.of()).tarotWeight()).isEqualTo(4);                    // base
        assertThat(ShopEconomy.resolve(Set.of("v_tarot_merchant")).tarotWeight()).isEqualTo(8);  // 2x
        assertThat(ShopEconomy.resolve(Set.of("v_tarot_tycoon")).tarotWeight()).isEqualTo(16);   // 4x
        assertThat(ShopEconomy.resolve(Set.of("v_planet_merchant")).planetWeight()).isEqualTo(8);
        // A higher Tarot weight makes Tarots more likely: 0.65 is a Joker at weight 4 (< 20/28),
        // a Tarot at weight 8 (> 20/32).
        assertThat(com.balatro.engine.game.Shop.rollSlotType(0.65, 0))
                .isEqualTo(com.balatro.engine.game.Shop.Kind.JOKER);
        assertThat(com.balatro.engine.game.Shop.rollSlotType(0.65, 0, 8, 4))
                .isEqualTo(com.balatro.engine.game.Shop.Kind.TAROT);
    }

    @Test
    void magicTrickAddsPlayingCardSlotsIllusionEnhancesThem() {
        assertThat(ShopEconomy.resolve(Set.of()).playingCardWeight()).isZero();
        assertThat(ShopEconomy.resolve(Set.of("v_magic_trick")).playingCardWeight()).isEqualTo(4);
        assertThat(ShopEconomy.resolve(Set.of("v_magic_trick")).playingCardsEnhanced()).isFalse();
        assertThat(ShopEconomy.resolve(Set.of("v_illusion")).playingCardsEnhanced()).isTrue();
        // With a playing-card weight, a high roll lands on a PLAYING_CARD slot; without it, it doesn't.
        assertThat(com.balatro.engine.game.Shop.rollSlotType(0.99, 0, 4, 4, 4))
                .isEqualTo(com.balatro.engine.game.Shop.Kind.PLAYING_CARD);
        assertThat(com.balatro.engine.game.Shop.rollSlotType(0.99, 0, 4, 4, 0))
                .isNotEqualTo(com.balatro.engine.game.Shop.Kind.PLAYING_CARD);
    }
}
