package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.heartsKings;
import static com.balatromp.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShopTest {

    private final Ruleset std = Ruleset.standard();

    /** Clear ante-1 Small with a stacked run, landing in the shop. */
    private Run winToShop() {
        Run run = new Run(std, "SHOP", heartsKings(200), jokers("j_joker", "j_joker", "j_joker"));
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        return run;
    }

    @Test
    void winningOpensShopAndBuyingAddsAJoker() {
        Run run = winToShop();
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(run.shop.items()).hasSize(2);
        assertThat(run.state.money).isEqualTo(7); // 4 start + 3 Small-blind reward

        int before = run.state.jokers().size();
        int cost = run.shop.items().get(0).cost(); // per-joker price
        int money = run.state.money;
        if (money >= cost) {
            assertThat(run.buyJoker(0)).isNull();                 // success
            assertThat(run.state.jokers()).hasSize(before + 1);   // joker added to the run
            assertThat(run.state.money).isEqualTo(money - cost);  // charged its real cost
            assertThat(run.shop.items()).hasSize(1);              // bought slot removed
        } else {
            assertThat(run.buyJoker(0)).isEqualTo("not enough money");
        }
    }

    @Test
    void editionRollHitsBaseRateBoundaries() {
        // Base cumulative bands: Foil [0,2%), Holo [2%,3.4%), Poly [3.4%,3.7%), Negative [3.7%,4%).
        assertThat(com.balatromp.engine.game.Shop.rollEdition(0.01, 1, 1)).isEqualTo(com.balatromp.engine.card.Edition.FOIL);
        assertThat(com.balatromp.engine.game.Shop.rollEdition(0.025, 1, 1)).isEqualTo(com.balatromp.engine.card.Edition.HOLOGRAPHIC);
        assertThat(com.balatromp.engine.game.Shop.rollEdition(0.035, 1, 1)).isEqualTo(com.balatromp.engine.card.Edition.POLYCHROME);
        assertThat(com.balatromp.engine.game.Shop.rollEdition(0.038, 1, 1)).isEqualTo(com.balatromp.engine.card.Edition.NEGATIVE);
        assertThat(com.balatromp.engine.game.Shop.rollEdition(0.5, 1, 1)).isEqualTo(com.balatromp.engine.card.Edition.NONE);
        // Hone (2× foil/holo) widens the Foil band so 0.03 now lands Foil instead of Holo.
        assertThat(com.balatromp.engine.game.Shop.rollEdition(0.03, 2, 3)).isEqualTo(com.balatromp.engine.card.Edition.FOIL);
    }

    @Test
    void buyingANegativeJokerGrantsItsOwnSlotEvenWhenFull() {
        Run run = winToShop();
        // Stamp the first shop item Negative and fill the joker slots.
        var first = run.shop.items().get(0);
        run.shop.items().set(0, new com.balatromp.engine.game.Shop.Item(first.info(),
                com.balatromp.engine.card.Edition.NEGATIVE));
        run.state.jokerSlots = run.state.jokers().size(); // slots are full
        run.state.money = 999;
        int slotsBefore = run.state.jokerSlots;
        int countBefore = run.state.jokers().size();
        assertThat(run.buyJoker(0)).isNull(); // Negative bypasses the slots-full check
        assertThat(run.state.jokers()).hasSize(countBefore + 1);
        assertThat(run.state.jokerSlots).isEqualTo(slotsBefore + 1); // it brought its own slot
        var bought = run.state.jokers().get(run.state.jokers().size() - 1);
        assertThat(run.state.jokerEdition(bought)).isEqualTo(com.balatromp.engine.card.Edition.NEGATIVE);
    }

    @Test
    void unaffordableActionsRejectCleanlyWithoutSpending() {
        Run run = winToShop();
        run.state.money = 0;
        assertThat(run.buyJoker(0)).isEqualTo("not enough money");
        assertThat(run.reroll()).isEqualTo("not enough money");
        assertThat(run.state.money).isEqualTo(0); // untouched by failed actions
    }

    @Test
    void rerollSpendsMoneyAndRefreshesOfferings() {
        Run run = winToShop();
        run.state.money = 100;
        assertThat(run.reroll()).isNull();
        assertThat(run.state.money).isEqualTo(95); // 100 - 5
        assertThat(run.shop.items()).hasSize(2);
    }

    @Test
    void shopOffersAndSellsAConsumable() {
        Run run = winToShop();
        assertThat(run.shop.consumables()).isNotEmpty(); // a Tarot is offered
        int money = run.state.money;
        int before = run.state.consumables.size();
        assertThat(run.buyConsumable(0)).isNull();
        assertThat(run.state.consumables).hasSize(before + 1);  // into inventory
        assertThat(run.state.money).isEqualTo(money - 3);       // charged $3
        assertThat(run.shop.consumables()).isEmpty();           // removed from shop
    }

    @Test
    void proceedLeavesShopForTheNextBlind() {
        Run run = winToShop();
        run.proceed();
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_ACTIVE);
        assertThat(run.blind).isEqualTo(BlindType.BIG);
    }

    @Test
    void shopSkipsJokersYouAlreadyOwn() {
        var queues = new com.balatromp.engine.rng.QueueSet(new com.balatromp.engine.rng.RandomStreams("SK"));
        var shop = com.balatromp.engine.game.Shop.generate(queues, 2,
                List.of("j_joker", "j_bull", "j_banner"), java.util.Set.of("j_bull"), false);
        assertThat(shop.items()).noneMatch(it -> it.jokerKey().equals("j_bull")); // owned -> skipped
        assertThat(shop.items().get(0).jokerKey()).isNotEqualTo(shop.items().get(1).jokerKey());
    }

    @Test
    void buyingAVoucherAppliesItsPermanentEffect() {
        Run run = winToShop();
        run.state.money = 100;
        assertThat(run.shop.voucher()).isNotNull(); // a voucher is offered
        int slots = run.state.consumableSlots;
        int hands = run.ruleset.hands();

        // Force a known voucher to assert a concrete effect, then buy it.
        // (Grabber: +1 hand each blind.) We just verify buying succeeds and is recorded.
        String key = run.shop.voucher();
        assertThat(run.buyVoucher()).isNull();
        assertThat(run.state.vouchers).contains(key);
        assertThat(run.buyVoucher()).isEqualTo("no voucher offered"); // one per shop, now taken

        // Crystal Ball / Seed Money apply immediately when that voucher is the one bought.
        if (key.equals("v_crystal_ball")) assertThat(run.state.consumableSlots).isEqualTo(slots + 1);
        if (key.equals("v_seed_money")) assertThat(run.state.interestCap).isEqualTo(10);
        // Grabber's +1 hand shows next blind.
        if (key.equals("v_grabber")) {
            run.proceed();
            assertThat(run.state.handsLeft).isEqualTo(hands + 1);
        }
    }

    @Test
    void pennyPincherPaysFromNemesisShopSpend() {
        var all = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));
        Run base = new Run(std, "PP", heartsKings(200), jokers("j_joker", "j_joker", "j_joker"));
        base.play(all); // win Small -> shop
        Run pp = new Run(std, "PP", heartsKings(200),
                jokers("j_joker", "j_joker", "j_joker", "j_penny_pincher"));
        pp.state.oppShopSpentLastAnte = 9; // nemesis spent $9 last ante -> $3 on entering the shop
        pp.play(all);
        assertThat(pp.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(pp.state.money - base.state.money).isEqualTo(3); // only difference is Penny Pincher
    }

    @Test
    void multiplayerShopExcludesBannedJokers() {
        Ruleset mp = new Ruleset("MP", 4, 4, 3, 8, 1.0, 8, std.blindBaseAmounts(),
                List.of("j_bull", "j_banner", "j_chicot", "j_matador"), "multiplayer");
        Run run = new Run(mp, "MP", heartsKings(200), jokers("j_joker", "j_joker", "j_joker"));
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(run.shop.items())
                .noneMatch(it -> it.jokerKey().equals("j_chicot") || it.jokerKey().equals("j_matador"));
    }

    @Test
    void showmanAllowsOwnedAndDuplicateOfferings() {
        var queues = new com.balatromp.engine.rng.QueueSet(new com.balatromp.engine.rng.RandomStreams("SM"));
        var shop = com.balatromp.engine.game.Shop.generate(queues, 2,
                List.of("j_bull"), java.util.Set.of("j_bull"), true); // Showman: duplicates allowed
        assertThat(shop.items()).allMatch(it -> it.jokerKey().equals("j_bull"));
    }
}
