package com.balatromp.engine.consumable;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.game.PlanetCatalog;
import com.balatromp.engine.game.Shop;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.joker.def.CreateSpec;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.Rng;
import com.balatromp.engine.state.RunState;
import java.util.List;

/**
 * Applies a {@link CreateSpec} to a run server-side (never in preview): adds
 * consumables (up to free slots), jokers (up to the joker slot limit), or playing
 * cards (to the deck composition). Random choices come from game-long queues so both
 * players in a match create the same sequence.
 */
public final class Creation {

    private Creation() {}

    public static void apply(RunState run, CreateSpec spec, QueueSet queues) {
        if (spec == null || run == null) return;
        switch (spec.kind()) {
            case TAROT, PLANET, SPECTRAL -> addConsumables(run, spec, queues);
            case JOKER -> addJokers(run, spec, queues);
            case PLAYING_CARD -> addCards(run, spec, queues);
        }
    }

    private static void addConsumables(RunState run, CreateSpec spec, QueueSet queues) {
        List<String> pool = switch (spec.kind()) {
            case TAROT -> TarotCatalog.tarotKeys();
            case SPECTRAL -> TarotCatalog.spectralKeys();
            case PLANET -> PlanetCatalog.keys();
            default -> List.of();
        };
        if (pool.isEmpty()) return;
        for (int i = 0; i < spec.count(); i++) {
            if (run.consumables.size() >= run.consumableSlots) return;
            run.consumables.add(pick(pool, queues, "create:" + spec.kind()));
        }
    }

    private static void addJokers(RunState run, CreateSpec spec, QueueSet queues) {
        List<String> pool = (spec.rarity() != null)
                ? JokerLibrary.keysByRarity(spec.rarity()) : JokerLibrary.builtinKeys();
        if (pool.isEmpty()) return;
        for (int i = 0; i < spec.count(); i++) {
            if (run.jokers().size() >= Shop.JOKER_SLOT_LIMIT) return;
            run.addJoker(JokerLibrary.create(pick(pool, queues, "create:joker:" + spec.rarity())));
        }
    }

    private static void addCards(RunState run, CreateSpec spec, QueueSet queues) {
        for (int i = 0; i < spec.count(); i++) {
            Rank[] ranks = Rank.values();
            Suit[] suits = Suit.values();
            Rank r = ranks[(int) Math.floor(roll(queues, "create:card:rank") * ranks.length)];
            Suit s = suits[(int) Math.floor(roll(queues, "create:card:suit") * suits.length)];
            Enhancement enh = spec.enhancement() != null ? spec.enhancement() : Enhancement.NONE;
            run.deckComposition.add(new Card(r, s, enh,
                    com.balatromp.engine.card.Edition.NONE, com.balatromp.engine.card.Seal.NONE));
        }
    }

    private static String pick(List<String> pool, QueueSet queues, String key) {
        int idx = (int) Math.floor(roll(queues, key) * pool.size());
        return pool.get(Math.min(idx, pool.size() - 1));
    }

    private static double roll(QueueSet queues, String key) {
        return queues.queue(key, Rng::nextDouble).next();
    }
}
