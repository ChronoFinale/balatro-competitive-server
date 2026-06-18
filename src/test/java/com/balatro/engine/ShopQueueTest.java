package com.balatro.engine;

import static com.balatro.engine.TestSupport.heartsKings;
import static com.balatro.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.game.Shop;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Shop offerings now come from the run's game-long queue, so they have BMP's
 * fairness shape: two players on the same seed see the same shop, and a reroll
 * deterministically advances the same shared sequence (never an independent
 * roll). This is the behavior that makes a 1v1 fair.
 */
class ShopQueueTest {

    private Run winToShop(String seed) {
        Run run = new Run(Ruleset.standard(), seed, heartsKings(200),
                jokers("j_joker", "j_joker", "j_joker"));
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        return run;
    }

    private static List<String> keys(Shop shop) {
        return shop.items().stream().map(Shop.Item::key).toList();
    }

    @Test
    void bothPlayersSeeTheSameShopOnTheSameSeed() {
        assertThat(keys(winToShop("DUEL").shop)).isEqualTo(keys(winToShop("DUEL").shop));
    }

    @Test
    void rerollDeterministicallyAdvancesTheSameQueue() {
        Run a = winToShop("DUEL");
        Run b = winToShop("DUEL");
        List<String> initial = keys(a.shop);

        a.state.money = 100;
        b.state.money = 100;
        assertThat(a.reroll()).isNull();
        assertThat(b.reroll()).isNull();

        // Reroll is identical for both players (same shared queue, same cursor)...
        assertThat(keys(a.shop)).isEqualTo(keys(b.shop));
        // ...and it advanced the queue, so it reveals new offerings, not the same two.
        assertThat(keys(a.shop)).isNotEqualTo(initial);
    }
}
