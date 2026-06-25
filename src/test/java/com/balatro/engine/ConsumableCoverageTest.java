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
 * is a catalog ENTRY that carries no effect. So here: every Tarot/Spectral has a non-empty effect
 * list whose effects all do something, and every Planet targets a real hand. (Since the Generate
 * composite was eliminated, a generative consumable is an ordered List<Effect> of first-class effects
 * — each does something by construction — so the empty-list check is the whole net.)
 */
class ConsumableCoverageTest {

    private static boolean isDegenerate(Effect e) {
        return e == null; // every effect type does something by construction; only a null/empty list is a no-op
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
