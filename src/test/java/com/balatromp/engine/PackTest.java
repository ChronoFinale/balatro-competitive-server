package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.PackCatalog;
import com.balatromp.engine.game.PackCatalog.Kind;
import com.balatromp.engine.game.PackCatalog.Pack;
import com.balatromp.engine.game.PackCatalog.Size;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Booster packs: sizes/costs/pick-counts from the spec, the game-long packs queue
 * (deterministic across players, kept across rerolls), and the open -> reveal ->
 * pick/skip flow drawing from each pack kind's queue.
 */
class PackTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private Run wonRun(String seed) {
        Run run = new Run(STD, seed, stoneDeck(400), jokers("j_joker", "j_joker", "j_joker"));
        run.play(FIVE); // clear ante-1 Small -> shop
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        return run;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> packs(Run run) {
        return (List<Map<String, Object>>) (List<?>) run.view().packs();
    }

    @Test
    void packSizesCostsAndPickCounts() {
        assertThat(new Pack(Kind.ARCANA, Size.MEGA).shown()).isEqualTo(5);
        assertThat(new Pack(Kind.ARCANA, Size.MEGA).choose()).isEqualTo(2);
        assertThat(new Pack(Kind.ARCANA, Size.NORMAL).shown()).isEqualTo(3);
        assertThat(new Pack(Kind.BUFFOON, Size.NORMAL).shown()).isEqualTo(2); // buffoon/spectral show fewer
        assertThat(new Pack(Kind.SPECTRAL, Size.JUMBO).shown()).isEqualTo(4);
        assertThat(new Pack(Kind.STANDARD, Size.JUMBO).cost()).isEqualTo(6);
        assertThat(new Pack(Kind.CELESTIAL, Size.MEGA).cost()).isEqualTo(8);
        // The rate table covers the whole [0,1) range.
        assertThat(PackCatalog.roll(0.0)).isNotNull();
        assertThat(PackCatalog.roll(0.999999)).isNotNull();
    }

    @Test
    void everyShopOffersTwoPacks() {
        Run run = wonRun("PACKS");
        assertThat(packs(run)).hasSize(2);
    }

    @Test
    void bothPlayersSeeTheSamePacksOnTheSameSeed() {
        List<Object> a = packs(wonRun("DUEL")).stream().map(m -> m.get("name")).toList();
        List<Object> b = packs(wonRun("DUEL")).stream().map(m -> m.get("name")).toList();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void packsDoNotRefreshOnReroll() {
        Run run = wonRun("DUEL");
        run.state.money = 100;
        List<Object> before = packs(run).stream().map(m -> m.get("name")).toList();
        assertThat(run.reroll()).isNull();
        assertThat(packs(run).stream().map(m -> m.get("name")).toList()).isEqualTo(before);
    }

    @Test
    @SuppressWarnings("unchecked")
    void openingAPackRevealsContentsMatchingItsKind() {
        Run run = wonRun("DUEL");
        run.state.money = 50;
        run.state.jokerSlots = 10;
        run.state.consumableSlots = 10;
        Map<String, Object> pack0 = packs(run).get(0);
        String kind = (String) pack0.get("kind");
        int shown = ((Number) pack0.get("shown")).intValue();
        int choose = ((Number) pack0.get("choose")).intValue();

        assertThat(run.openPack(0)).isNull();
        Map<String, Object> open = run.view().openPack();
        assertThat(open).isNotNull();
        var items = (List<Map<String, Object>>) open.get("items");
        assertThat(items).hasSize(shown);
        String expectedType = switch (kind) {
            case "BUFFOON" -> "JOKER";
            case "STANDARD" -> "CARD";
            default -> "CONSUMABLE"; // Arcana / Celestial / Spectral
        };
        assertThat(items).allMatch(m -> expectedType.equals(m.get("type")));

        // Picking 'choose' cards consumes the pack.
        for (int i = 0; i < choose; i++) assertThat(run.pickPackItem(0)).isNull();
        assertThat(run.view().openPack()).isNull(); // closed after all picks used
    }

    @Test
    void skippingAnOpenPackClosesIt() {
        Run run = wonRun("DUEL");
        run.state.money = 50;
        assertThat(run.openPack(0)).isNull();
        assertThat(run.view().openPack()).isNotNull();
        assertThat(run.skipPack()).isNull();
        assertThat(run.view().openPack()).isNull();
    }
}
