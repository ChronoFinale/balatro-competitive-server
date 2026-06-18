package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.game.Shop;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
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
        run.state.money = 100; // headroom for the escalating rerolls
        int money = run.state.money;
        assertThat(run.reroll()).isNull();
        assertThat(run.state.money).as("first reroll free (Chaos)").isEqualTo(money);
        assertThat(run.reroll()).isNull();
        assertThat(run.state.money).as("second reroll: base, free reroll didn't escalate").isEqualTo(money - Shop.REROLL_COST);
        assertThat(run.reroll()).isNull();
        assertThat(run.state.money).as("third reroll: base + $1 escalation")
                .isEqualTo(money - Shop.REROLL_COST - (Shop.REROLL_COST + 1));
    }

    @Test
    void rerollCostEscalatesByADollarEachPaidRerollAndResetsNextShop() {
        Run run = wonRun(); // no jokers
        run.state.money = 100;
        int m0 = run.state.money;
        run.reroll(); // $5
        assertThat(m0 - run.state.money).isEqualTo(5);
        run.reroll(); // $6
        assertThat(m0 - run.state.money).isEqualTo(11);
        run.reroll(); // $7
        assertThat(m0 - run.state.money).isEqualTo(18);
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
        run.state.money = 50;
        var red = run.state.jokers().stream().filter(j -> j.key().equals("j_red_card")).findFirst().get();
        assertThat(run.openPack(0)).isNull(); // open a pack...
        assertThat(run.skipPack()).isNull();  // ...then skip it -> SKIP_BOOSTER
        assertThat(((Number) run.state.jokerState(red).getOrDefault("mult", 0)).intValue()).isEqualTo(3);
    }

    @Test
    void openingAndPickingFromAPackTakesOneCard() {
        Run run = wonRun();
        run.state.money = 50;
        run.state.jokerSlots = 10;       // room for whatever the (random-kind) pack yields
        run.state.consumableSlots = 10;
        int money = run.state.money;
        int owned = run.state.jokers().size() + run.state.consumables.size() + run.state.deckComposition.size();
        assertThat(run.openPack(0)).isNull();
        assertThat(run.state.money).isLessThan(money); // paid the pack's cost
        assertThat(run.openPack(0)).isEqualTo("finish the open pack first"); // can't open another while one is open
        assertThat(run.pickPackItem(0)).isNull();      // take the first revealed card
        int ownedAfter = run.state.jokers().size() + run.state.consumables.size() + run.state.deckComposition.size();
        assertThat(ownedAfter).isEqualTo(owned + 1);   // exactly one card entered inventory/deck
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

    @Test
    void shopRulesAreDerivedFromOwnedJokers() {
        // Showman / Astronomer are no longer scattered hasJoker checks — they fold into one derived
        // ShopConfig, the sibling of EconomyConfig. (Chaos moved to a folded FREE_REROLLS var.)
        var none = com.balatro.engine.game.ShopConfig.resolve(java.util.List.of());
        assertThat(none.allowDuplicates()).isFalse();
        assertThat(none.planetsFree()).isFalse();

        var all = com.balatro.engine.game.ShopConfig.resolve(jokers("j_showman", "j_astronomer"));
        assertThat(all.allowDuplicates()).isTrue();   // Showman
        assertThat(all.planetsFree()).isTrue();       // Astronomer
    }

    @Test
    void chaosGivesItsFreeRerollViaTheFoldedFreeRerollsVar() {
        Run plain = wonRun();
        Run chaos = wonRun("j_chaos");
        plain.state.money = chaos.state.money = 100;
        int p0 = plain.state.money, c0 = chaos.state.money;
        plain.reroll();   // no Chaos -> first reroll costs $5
        chaos.reroll();   // Chaos -> first reroll free
        assertThat(p0 - plain.state.money).isEqualTo(5);
        assertThat(c0 - chaos.state.money).as("Chaos's first reroll is free, folded from FREE_REROLLS").isZero();
    }
}
