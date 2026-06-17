package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Locks the {@link ClientView} shape the real-Balatro bridge ({@code tools/balatro-bridge}) parses out of
 * the wire. The mod scrapes these exact fields, so a silent rename/removal here breaks the renderer with
 * no compile error on the Lua side — this test is that missing compile check.
 */
class ClientViewContractTest {

    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    /** Win the Small blind in one hand → the run is in the shop. */
    private static Run runInShop() {
        Run run = new Run(Ruleset.standard(), "VIEW", stoneDeck(300), jokers("j_joker"));
        run.play(FIVE);
        return run;
    }

    @Test
    void shopViewCarriesEveryFieldTheBridgeReads() {
        ClientView v = runInShop().view();
        assertThat(v.phase()).isEqualTo("SHOP");

        // Main slots: kind/key/name/cost/edition (set_shop_card_identity + edition rendering).
        assertThat(v.shop()).isNotNull().isNotEmpty();
        assertThat(v.shop().get(0)).containsKeys("kind", "key", "name", "cost", "edition");

        // Reroll cost (synced to the reroll button) + packs (kind/size/cost -> p_<kind>_<size>_1).
        assertThat(v.rerollCost()).isGreaterThan(0);
        assertThat(v.packs()).isNotNull().isNotEmpty();
        assertThat(v.packs().get(0)).containsKeys("kind", "size", "cost");

        // Consumables list is always present (index-by-key for useConsumable).
        assertThat(v.consumables()).isNotNull();
    }

    @Test
    void openPackViewExposesRevealedItemsForReconcile() {
        Run run = runInShop();
        run.state.money = 100; // afford any pack size
        assertThat(run.openPack(0)).isNull(); // success

        ClientView v = run.view();
        assertThat(v.openPack()).isNotNull();
        assertThat(v.openPack()).containsKeys("picksLeft", "items");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) v.openPack().get("items");
        assertThat(items).isNotEmpty();
        // Each revealed item is typed JOKER / CONSUMABLE / CARD (drives set_ability vs set_base).
        assertThat(items.get(0)).containsKey("type");

        // Picking removes one revealed item; when picks run out the pack closes (openPack -> null).
        int before = items.size();
        assertThat(run.pickPackItem(0)).isNull();
        ClientView after = run.view();
        if (after.openPack() != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> left = (List<Map<String, Object>>) after.openPack().get("items");
            assertThat(left).hasSize(before - 1);
        }
    }
}
