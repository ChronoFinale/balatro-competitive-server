package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.game.Shop;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Shop/economy joker hooks: Chaos (free reroll), Credit Card (debt). */
class ShopHooksTest {

    private final Ruleset std = Ruleset.standard();
    private final Intent all = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private Run wonRun(String... jokerKeys) {
        // Three plain Jokers supply enough mult for a 5-stone hand to clear the small blind.
        List<String> keys = new ArrayList<>(List.of("j_joker", "j_joker", "j_joker"));
        keys.addAll(Arrays.asList(jokerKeys));
        Run run = new Run(std, "SHOP", stoneDeck(400), jokers(keys.toArray(String[]::new)));
        run.play(all);
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        return run;
    }

    @Test
    void chaosGivesOneFreeRerollPerShop() {
        Run run = wonRun("j_chaos");
        int money = run.state.money;
        assertThat(run.reroll()).isNull();
        assertThat(run.state.money).as("first reroll free").isEqualTo(money);
        assertThat(run.reroll()).isNull();
        assertThat(run.state.money).as("second reroll costs").isEqualTo(money - Shop.REROLL_COST);
    }

    @Test
    void creditCardAllowsDebtDownToMinus20() {
        Run run = wonRun("j_credit_card");
        // Reroll until it refuses; Credit Card lets money go negative (to -$20).
        while (run.reroll() == null) { /* spend down */ }
        assertThat(run.state.money).isLessThan(0);
        assertThat(run.state.money).isGreaterThanOrEqualTo(-20);
    }

    @Test
    void withoutCreditCardRerollStopsAtZero() {
        Run run = wonRun();
        while (run.reroll() == null) { /* spend down */ }
        assertThat(run.state.money).isGreaterThanOrEqualTo(0);
    }

    @Test
    void eggSellsForMoreAfterARound() {
        Run run = wonRun("j_egg"); // one round won -> Egg gained +$3 sell value
        int money = run.state.money;
        assertThat(run.sellJoker(3)).isNull(); // the Egg (cost 4 -> base sell $2, +$3 = $5)
        assertThat(run.state.money).isEqualTo(money + 5);
    }

    @Test
    void tradingCardDestroysASingleFirstDiscardForMoney() {
        Run run = new Run(std, "TC", stoneDeck(400), jokers("j_trading"));
        int money = run.state.money;
        int deck = run.state.deckComposition.size();
        run.play(new Intent.Discard(List.of(0))); // first discard, single card
        assertThat(run.state.money).isEqualTo(money + 3);
        assertThat(run.state.deckComposition).hasSize(deck - 1); // the card was destroyed
    }

    @Test
    void redCardGainsMultWhenABoosterIsSkipped() {
        Run run = wonRun("j_red_card");
        var red = run.state.jokers().stream().filter(j -> j.key().equals("j_red_card")).findFirst().get();
        assertThat(run.skipBooster()).isNull();
        assertThat(((Number) run.state.jokerState(red).getOrDefault("mult", 0)).intValue()).isEqualTo(3);
    }

    @Test
    void openingABoosterCostsAndYieldsAConsumable() {
        Run run = wonRun();
        run.state.money = 10;
        int before = run.state.consumables.size();
        assertThat(run.openBooster()).isNull();
        assertThat(run.state.money).isEqualTo(6); // $10 - $4
        assertThat(run.state.consumables.size()).isGreaterThanOrEqualTo(before + 1);
        assertThat(run.openBooster()).isEqualTo("no booster available"); // one per shop
    }

    @Test
    void perkeoDuplicatesAConsumableOnShopExit() {
        Run run = wonRun("j_perkeo");
        run.state.consumables.add("c_pluto");
        int before = run.state.consumables.size();
        run.proceed(); // leaving the shop -> Perkeo copies a held consumable
        assertThat(run.state.consumables.size()).isEqualTo(before + 1);
    }

    @Test
    void invisibleJokerDuplicatesAJokerWhenSoldAfterTwoRounds() {
        Run run = wonRun("j_invisible");
        var inv = run.state.jokers().stream().filter(j -> j.key().equals("j_invisible")).findFirst().get();
        run.state.jokerState(inv).put("rounds", 2); // pretend two rounds passed
        int before = run.state.jokers().size();
        assertThat(run.sellJoker(run.state.jokers().indexOf(inv))).isNull();
        assertThat(run.state.jokers()).noneMatch(j -> j.key().equals("j_invisible"));
        assertThat(run.state.jokers()).hasSize(before); // sold one, duplicated one
    }

    @Test
    void giftCardBoostsEveryJokersSellValue() {
        Run run = wonRun("j_gift_card"); // end of round -> +$1 sell value to every joker
        var plainJoker = run.state.jokers().get(0);
        assertThat(((Number) run.state.jokerState(plainJoker).getOrDefault("sellBonus", 0)).intValue())
                .isEqualTo(1);
    }
}
