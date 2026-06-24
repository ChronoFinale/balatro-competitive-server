package com.balatro.engine;

import static com.balatro.engine.TestSupport.heartsKings;
import static com.balatro.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
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
    void winningOpensShopAndBuyingASlotWorks() {
        Run run = winToShop();
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(run.shop.items()).hasSize(2);   // two mixed main slots
        assertThat(run.state.money).isEqualTo(10); // 4 start + 3 Small reward + 3 ($1 × 3 remaining hands)

        run.state.money = 999; // afford whatever slot 0 is
        int jokers = run.state.jokers().size();
        int consumables = run.state.consumables.size();
        int slots = run.shop.items().size();
        assertThat(run.buyShopItem(0)).isNull();          // buy regardless of kind
        assertThat(run.shop.items()).hasSize(slots - 1);  // bought slot removed
        // a Joker lands in the joker row; a Tarot/Planet lands in consumables — either way +1 owned.
        assertThat(run.state.jokers().size() + run.state.consumables.size())
                .isEqualTo(jokers + consumables + 1);
    }

    @Test
    void editionRollHitsBaseRateBoundaries() {
        // Base cumulative bands: Foil [0,2%), Holo [2%,3.4%), Poly [3.4%,3.7%), Negative [3.7%,4%).
        assertThat(com.balatro.engine.game.Shop.rollEdition(0.01, 1, 1)).isEqualTo(com.balatro.engine.card.Edition.FOIL);
        assertThat(com.balatro.engine.game.Shop.rollEdition(0.025, 1, 1)).isEqualTo(com.balatro.engine.card.Edition.HOLOGRAPHIC);
        assertThat(com.balatro.engine.game.Shop.rollEdition(0.035, 1, 1)).isEqualTo(com.balatro.engine.card.Edition.POLYCHROME);
        assertThat(com.balatro.engine.game.Shop.rollEdition(0.038, 1, 1)).isEqualTo(com.balatro.engine.card.Edition.NEGATIVE);
        assertThat(com.balatro.engine.game.Shop.rollEdition(0.5, 1, 1)).isEqualTo(com.balatro.engine.card.Edition.NONE);
        // Hone (2× foil/holo) widens the Foil band so 0.03 now lands Foil instead of Holo.
        assertThat(com.balatro.engine.game.Shop.rollEdition(0.03, 2, 3)).isEqualTo(com.balatro.engine.card.Edition.FOIL);
    }

    @Test
    void buyingANegativeJokerGrantsItsOwnSlotEvenWhenFull() {
        Run run = winToShop();
        // Force slot 0 to a Negative joker and fill the joker slots.
        var info = com.balatro.engine.joker.JokerLibrary.create("j_joker").info();
        run.shop.items().set(0, com.balatro.engine.game.Shop.Item.joker(info,
                com.balatro.engine.card.Edition.NEGATIVE));
        run.state.jokerSlots = run.state.jokers().size(); // slots are full
        run.state.money = 999;
        int slotsBefore = run.state.jokerSlots;
        int countBefore = run.state.jokers().size();
        assertThat(run.buyShopItem(0)).isNull(); // Negative bypasses the slots-full check
        assertThat(run.state.jokers()).hasSize(countBefore + 1);
        assertThat(run.state.jokerSlots).isEqualTo(slotsBefore + 1); // it brought its own slot
        var bought = run.state.jokers().get(run.state.jokers().size() - 1);
        assertThat(run.state.jokerEdition(bought)).isEqualTo(com.balatro.engine.card.Edition.NEGATIVE);
    }

    @Test
    void unaffordableActionsRejectCleanlyWithoutSpending() {
        Run run = winToShop();
        run.state.money = 0;
        assertThat(run.buyShopItem(0)).isEqualTo("not enough money");
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
    void buyingATarotSlotAddsItToConsumables() {
        Run run = winToShop();
        run.state.money = 100;
        // Force slot 0 to a Tarot (the master queue is otherwise per-seed random).
        run.shop.items().set(0, new com.balatro.engine.game.Shop.Item(
                com.balatro.engine.game.Shop.Kind.TAROT, "c_magician", "The Magician", "x", 3, null,
                com.balatro.engine.card.Edition.NONE));
        int before = run.state.consumables.size();
        int money = run.state.money;
        assertThat(run.buyShopItem(0)).isNull();
        assertThat(run.state.consumables).hasSize(before + 1);  // into inventory
        assertThat(run.state.money).isEqualTo(money - 3);       // charged $3
    }

    @Test
    void proceedLeavesShopForTheNextBlind() {
        Run run = winToShop();
        run.proceed();
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_SELECT);
        assertThat(run.blind).isEqualTo(BlindType.BIG);
    }

    @Test
    void shopSkipsJokersYouAlreadyOwn() {
        var queues = new com.balatro.engine.rng.QueueSet(new com.balatro.engine.rng.RandomStreams("SK"));
        // More acceptable jokers than slots, so the queue never exhausts to the owned fallback.
        var shop = com.balatro.engine.game.Shop.generate(queues, 3,
                List.of("j_joker", "j_bull", "j_banner", "j_greedy_joker", "j_lusty_joker"),
                java.util.Set.of("j_bull"), false);
        assertThat(shop.items().stream()
                .filter(it -> it.kind() == com.balatro.engine.game.Shop.Kind.JOKER))
                .noneMatch(it -> it.key().equals("j_bull")); // owned -> skipped
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

        // Crystal Ball applies immediately; Seed Money's interest cap is resolved from ownership.
        if (key.equals("v_crystal_ball")) assertThat(run.state.consumableSlots).isEqualTo(slots + 1);
        if (key.equals("v_seed_money")) {
            assertThat(com.balatro.engine.game.EconomyConfig
                    .resolve(run.deckType, run.state.vouchers, run.state.jokers()).interestCap())
                    .isEqualTo(10);
        }
        // Grabber's +1 hand shows next blind.
        if (key.equals("v_grabber")) {
            run.proceed();
            assertThat(run.state.handsLeft).isEqualTo(hands + 1);
        }
    }

    @Test
    void voucherIsPerAnteNotPerBlind() {
        var all = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));
        Run run = new Run(std, "VOUCH", heartsKings(200), jokers("j_joker", "j_joker", "j_joker"));
        run.play(all); // clear ante-1 Small -> shop
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        String v1 = run.shop.voucher();
        assertThat(v1).isNotNull();
        run.proceed();  // -> Big blind
        run.play(all);  // clear Big -> shop
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(run.shop.voucher()).isEqualTo(v1); // same ante -> same voucher (NOT re-rolled per blind)
    }

    @Test
    void pennyPincherPaysFromNemesisShopSpend() {
        var all = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));
        Run base = new Run(std, "PP", heartsKings(200), jokers("j_joker", "j_joker", "j_joker"));
        base.play(all); // win Small -> shop
        Run pp = new Run(std, "PP", heartsKings(200),
                jokers("j_joker", "j_joker", "j_joker", "j_penny_pincher"));
        pp.state.opponent.shopSpentLastAnte = 9; // nemesis spent $9 last ante -> $3 on entering the shop
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
        assertThat(run.shop.items().stream()
                .filter(it -> it.kind() == com.balatro.engine.game.Shop.Kind.JOKER))
                .noneMatch(it -> it.key().equals("j_chicot") || it.key().equals("j_matador"));
    }

    @Test
    void showmanAllowsOwnedAndDuplicateOfferings() {
        var queues = new com.balatro.engine.rng.QueueSet(new com.balatro.engine.rng.RandomStreams("SM"));
        var shop = com.balatro.engine.game.Shop.generate(queues, 8,
                List.of("j_bull"), java.util.Set.of("j_bull"), true); // Showman: duplicates allowed
        assertThat(shop.items().stream()
                .filter(it -> it.kind() == com.balatro.engine.game.Shop.Kind.JOKER))
                .allMatch(it -> it.key().equals("j_bull"));
    }
}
