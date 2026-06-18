package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Bosses;
import com.balatro.engine.game.Run;
import com.balatro.engine.game.VoucherCatalog;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The 16-voucher queue + Tier-1/Tier-2 resolution: one voucher per ante from a
 * game-long queue, deterministic across players, never repeating back-to-back
 * (dup-skip), and once a base voucher is bought it isn't re-offered (its upgrade
 * shows instead). Plus a couple of the wired effects.
 */
class VoucherTierTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    /** A run strong enough (5-of-a-kind stones + 5 jokers) to clear blinds through ante ~4. The boss is
     *  pinned to a trivially-clearable stub so ante progression never hinges on which boss the seed picks
     *  — this suite tests the voucher queue, not boss-clear difficulty (legality bosses can otherwise
     *  reject a needed second hand and stall the run). */
    private Run strongRun(String seed) {
        Run run = new Run(STD, seed, stoneDeck(400),
                jokers("j_joker", "j_joker", "j_joker", "j_joker", "j_joker"));
        run.forcedBoss = Bosses.of("bl_void", "Test Boss").desc("no ability").requirement(0.0).build();
        return run;
    }

    /** The voucher offered at each ante's first shop, for {@code antes} antes. */
    private List<String> anteVouchers(String seed, int antes) {
        Run run = strongRun(seed);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < antes; i++) {
            run.play(FIVE);                          // clear Small -> shop (this ante's voucher set)
            out.add(run.shop.voucher());
            run.proceed(); run.play(FIVE);           // Big -> shop
            run.proceed(); run.play(FIVE);           // Boss -> shop
            run.proceed();                           // -> next ante's Small (BLIND_ACTIVE)
        }
        return out;
    }

    @Test
    void catalogHas16BasesEachWithAnUpgrade() {
        List<String> bases = VoucherCatalog.baseKeys(false);
        assertThat(bases).hasSize(16);
        for (String base : bases) {
            String up = VoucherCatalog.upgradeKey(base);
            assertThat(up).as("upgrade of " + base).isNotNull();
            assertThat(VoucherCatalog.upgradeKey(up)).as(up + " is a Tier 2 (no further upgrade)").isNull();
        }
    }

    @Test
    void multiplayerExcludesTheBannedVouchers() {
        List<String> mp = VoucherCatalog.baseKeys(true);
        assertThat(mp).hasSize(14); // 16 - Hieroglyph - Director's Cut
        assertThat(mp).doesNotContain("v_hieroglyph", "v_directors_cut");
    }

    @Test
    void voucherSequenceIsDeterministicAndNeverRepeatsBackToBack() {
        List<String> a = anteVouchers("VSEQ", 4);
        List<String> b = anteVouchers("VSEQ", 4);
        assertThat(a).isEqualTo(b);                  // same seed -> same per-ante vouchers (fair)
        assertThat(a).doesNotContainNull();
        for (int i = 1; i < a.size(); i++) {
            assertThat(a.get(i)).as("dup-skip: ante " + i + " differs from prior").isNotEqualTo(a.get(i - 1));
        }
    }

    @Test
    void aBoughtBaseVoucherIsNotReoffered() {
        Run run = strongRun("VBUY");
        run.play(FIVE);                              // ante-1 Small -> shop
        run.state.money = 500;
        String bought = run.shop.voucher();          // a Tier-1 base
        assertThat(run.buyVoucher()).isNull();
        for (int ante = 1; ante <= 3; ante++) {      // advance a few antes
            run.proceed(); run.play(FIVE);
            run.proceed(); run.play(FIVE);
            run.proceed(); run.play(FIVE);
            run.state.money = 500;
            assertThat(run.shop.voucher())
                    .as("a bought Tier-1 is never re-offered (its upgrade shows instead)")
                    .isNotEqualTo(bought);
        }
    }

    @Test
    void overstockPlusGivesFourShopSlots() {
        Run run = strongRun("OSP");
        run.play(FIVE);
        run.state.money = 100;
        run.state.vouchers.add("v_overstock_plus");
        assertThat(run.reroll()).isNull();           // regenerate the shop with the voucher owned
        assertThat(run.shop.items()).hasSize(4);
    }

    @Test
    void rerollVouchersReduceRerollCost() {
        Run run = strongRun("RR");
        run.play(FIVE);
        assertThat(run.view().rerollCost()).isEqualTo(5);
        run.state.vouchers.add("v_reroll_surplus");
        assertThat(run.view().rerollCost()).isEqualTo(3);
        run.state.vouchers.add("v_reroll_glut");
        assertThat(run.view().rerollCost()).isEqualTo(1);
    }
}
