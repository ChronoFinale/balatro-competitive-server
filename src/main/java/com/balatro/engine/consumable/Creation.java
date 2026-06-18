package com.balatro.engine.consumable;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.game.PlanetCatalog;
import com.balatro.engine.game.Shop;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.CreateSpec;
import com.balatro.engine.rng.GameQueue;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.Rng;
import com.balatro.engine.rng.RngSource;
import com.balatro.engine.rng.RngSources;
import com.balatro.engine.state.RunState;
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
            run.consumables.add(pick(pool, queues, RngSource.of("create:" + spec.kind())));
        }
    }

    private static void addJokers(RunState run, CreateSpec spec, QueueSet queues) {
        // The Wraith (Rare) / The Soul (Legendary) draw from the FIXED rarity pool and skip jokers
        // you already hold — BMP's get_current_pool marks owned keys UNAVAILABLE (unless Showman) and
        // the draw advances past them. Mirrors Shop.drawJoker; same fixed-modulus + nextWhere shape.
        List<String> base = (spec.rarity() != null)
                ? JokerLibrary.keysByRarity(spec.rarity()) : JokerLibrary.builtinKeys();
        // In multiplayer the boss-interacting jokers are banned from every pool, creation included
        // (so The Soul can't hand you a banned Legendary like Chicot).
        List<String> pool = run.capabilities.restrictedPools()
                ? base.stream().filter(k -> !JokerLibrary.MP_BANNED.contains(k)).toList() : base;
        if (pool.isEmpty()) return;
        boolean showman = run.jokers().stream().anyMatch(j -> j.key().equals("j_showman"));
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Joker j : run.jokers()) used.add(j.key());
        GameQueue<String> q = queues.queue(RngSources.createJoker(spec.rarity()),
                r -> pool.get(r.nextInt(pool.size())));
        for (int i = 0; i < spec.count(); i++) {
            if (run.jokers().size() >= run.jokerSlots) return;
            boolean anyAcceptable = pool.stream().anyMatch(k -> !used.contains(k));
            String key;
            if (showman) {
                key = q.next();
            } else if (anyAcceptable) {
                key = q.nextWhere(k -> !used.contains(k));
            } else {
                key = "j_joker"; // rarity fully owned -> BMP empty-pool fallback
            }
            run.addJoker(JokerLibrary.create(key));
            used.add(key); // a multi-create won't dupe within itself (each created card is "used")
        }
    }

    // Seals a Certificate-style created card may roll (excludes NONE).
    private static final Seal[] RANDOM_SEALS = { Seal.GOLD, Seal.RED, Seal.BLUE, Seal.PURPLE };

    private static void addCards(RunState run, CreateSpec spec, QueueSet queues) {
        Rank[] ranks = Rank.values();
        Suit[] suits = Suit.values();
        for (int i = 0; i < spec.count(); i++) {
            Rank r = ranks[idx(roll(queues, RngSources.CREATE_CARD.sub("rank")), ranks.length)];
            Suit s = suits[idx(roll(queues, RngSources.CREATE_CARD.sub("suit")), suits.length)];
            Enhancement enh = spec.enhancement() != null ? spec.enhancement() : Enhancement.NONE;
            Seal seal = spec.randomSeal()
                    ? RANDOM_SEALS[idx(roll(queues, RngSources.CREATE_CARD.sub("seal")), RANDOM_SEALS.length)]
                    : (spec.seal() != null ? spec.seal() : Seal.NONE);
            run.deckComposition.add(new Card(r, s, enh, Edition.NONE, seal));
        }
    }

    private static int idx(double roll, int size) {
        return Math.min((int) Math.floor(roll * size), size - 1);
    }

    private static String pick(List<String> pool, QueueSet queues, RngSource src) {
        int idx = (int) Math.floor(roll(queues, src) * pool.size());
        return pool.get(Math.min(idx, pool.size() - 1));
    }

    private static double roll(QueueSet queues, RngSource src) {
        return queues.queue(src, Rng::nextDouble).next();
    }
}
