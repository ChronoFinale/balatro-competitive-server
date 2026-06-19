package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.game.PlanetCatalog;
import com.balatro.engine.joker.def.Effect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The consumable half of the safety net (sibling of voucher/joker/tag coverage). The effect
 * <i>dispatch</i> is already compiler-safe — {@code applyConsumableEffect} switches over the sealed
 * {@link Effect}, so a new effect TYPE can't be silently dropped. What that can't catch
 * is a catalog ENTRY that carries a degenerate effect — a {@link Effect.Generate} with every
 * part empty does nothing, exactly the no-op trap Boss Tag fell into. So here: every Tarot/Spectral
 * has a non-empty effect list whose effects all do something, and every Planet targets a real hand.
 */
class ConsumableCoverageTest {

    private static boolean isDegenerate(Effect e) {
        if (e == null) return true;
        // Generate is the one effect whose parts are all optional — all-empty = a no-op.
        if (e instanceof Effect.Generate g) {
            return g.create() == null && g.destroyRandomInHand() == 0 && g.add() == null && g.money() == null;
        }
        return false; // every other effect type does something by construction
    }

    @Test
    void everyTarotAndSpectralHasARealEffect() {
        List<String> noOps = new ArrayList<>();
        for (String key : TarotCatalog.keys()) {
            Consumable c = TarotCatalog.get(key);
            if (c.effects().isEmpty() || c.effects().stream().anyMatch(ConsumableCoverageTest::isDegenerate)) {
                noOps.add(key);
            }
        }
        assertThat(noOps).as("consumables whose effect does nothing — wire them").isEmpty();
    }

    @Test
    void everyPlanetTargetsARealHand() {
        for (String key : PlanetCatalog.keys()) {
            assertThat(PlanetCatalog.get(key).hand())
                    .as("planet '%s' has no target hand to level", key).isNotNull();
        }
    }
}
