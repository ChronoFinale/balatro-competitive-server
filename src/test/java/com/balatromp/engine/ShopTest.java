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
    void showmanAllowsOwnedAndDuplicateOfferings() {
        var queues = new com.balatromp.engine.rng.QueueSet(new com.balatromp.engine.rng.RandomStreams("SM"));
        var shop = com.balatromp.engine.game.Shop.generate(queues, 2,
                List.of("j_bull"), java.util.Set.of("j_bull"), true); // Showman: duplicates allowed
        assertThat(shop.items()).allMatch(it -> it.jokerKey().equals("j_bull"));
    }
}
