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
}
