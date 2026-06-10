package com.balatromp.engine.consumable;

import com.balatromp.engine.game.PlanetCatalog;
import com.balatromp.engine.joker.def.CreateSpec;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.Rng;
import com.balatromp.engine.state.RunState;
import java.util.List;

/**
 * Applies a {@link CreateSpec} to a run: adds randomly-chosen consumable cards (up
 * to the free consumable slots), drawing the choice from a game-long queue so both
 * players in a match create the same sequence. Server-side only — never in preview.
 */
public final class ConsumableCreation {

    private ConsumableCreation() {}

    public static void apply(RunState run, CreateSpec spec, QueueSet queues) {
        if (spec == null || run == null) return;
        List<String> pool = poolFor(spec.kind());
        if (pool.isEmpty()) return;
        for (int i = 0; i < spec.count(); i++) {
            if (run.consumables.size() >= run.consumableSlots) return; // no free slot
            int idx = (int) Math.floor(roll(queues, "create:" + spec.kind()) * pool.size());
            if (idx >= pool.size()) idx = pool.size() - 1;
            run.consumables.add(pool.get(idx));
        }
    }

    private static List<String> poolFor(CreateSpec.Kind kind) {
        return switch (kind) {
            case TAROT -> TarotCatalog.tarotKeys();
            case SPECTRAL -> TarotCatalog.spectralKeys();
            case PLANET -> PlanetCatalog.keys();
        };
    }

    private static double roll(QueueSet queues, String key) {
        return queues.queue(key, Rng::nextDouble).next();
    }
}
