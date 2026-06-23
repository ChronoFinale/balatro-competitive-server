package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.BossBlind;
import com.balatro.engine.game.BossCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The boss half of the safety net (sibling of voucher/joker/tag/consumable coverage). Every boss is
 * built from the {@link com.balatro.dsl.Bosses} DSL; a boss that declares only {@code .desc(...)}
 * and forgets its actual ability is a baseline (×2) blind that does nothing special — the no-op trap
 * Boss Tag fell into. {@link BossBlind#hasAbility()} pins that down, and this rejects any boss without one.
 */
class BossCoverageTest {

    @Test
    void everyBossHasAnAbilityBeyondTheBaselineBlind() {
        List<String> inert = new ArrayList<>();
        for (BossBlind b : BossCatalog.all()) {
            if (!b.hasAbility()) inert.add(b.key());
        }
        assertThat(inert)
                .as("bosses that are just a baseline blind — wire their declared ability")
                .isEmpty();
    }
}
