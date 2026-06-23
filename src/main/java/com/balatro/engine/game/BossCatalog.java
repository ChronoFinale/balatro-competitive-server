package com.balatro.engine.game;

import static com.balatro.dsl.Cond.always;
import static com.balatro.dsl.Cond.card;
import static com.balatro.dsl.Cond.playedHand;

import com.balatro.engine.card.Suit;
import com.balatro.engine.rng.RandomStreams;
import java.util.ArrayList;
import java.util.List;

/**
 * The boss blind pool (data, mirroring Balatro's blinds). Selection is
 * deterministic per ante from the seed. Finisher ("showdown") bosses appear only
 * on finisher antes (every 8th); regular bosses are gated by {@code minAnte}.
 * Add a boss by adding a row.
 */
public final class BossCatalog {

    private BossCatalog() {}

    // Runtime loads bosses from /content/bosses.json; authored() (the DSL) is the codegen source. Identical
    // by the artifact gate; falls back to the DSL if the artifact is absent.
    private static final List<BossBlind> ALL = load();

    private static List<BossBlind> load() {
        try {
            return com.balatro.engine.content.ContentStore.bosses();
        } catch (RuntimeException e) {
            return com.balatro.content.BossDefs.authored(); // content authored in com.balatro.content.BossDefs
        }
    }

    public static boolean isFinisherAnte(int ante) {
        return ante % 8 == 0;
    }

    /** Every boss in the catalog — the surface the coverage net enumerates. */
    public static List<BossBlind> all() {
        return ALL;
    }

    /** Deterministically pick a boss for the given ante. */
    public static BossBlind pick(int ante, RandomStreams rng) {
        return pick(ante, rng, 0);
    }

    /** Pick the boss for {@code ante}; {@code reroll} (Director's Cut/Retcon) varies the stream so a
     *  reroll yields a different draw, deterministically. */
    public static BossBlind pick(int ante, RandomStreams rng, int reroll) {
        boolean finisher = isFinisherAnte(ante);
        List<BossBlind> pool = new ArrayList<>();
        for (BossBlind b : ALL) {
            if (b.finisher() == finisher && b.minAnte() <= ante) pool.add(b);
        }
        if (pool.isEmpty()) pool.add(ALL.get(0));
        String key = "boss:" + ante + (reroll > 0 ? ":r" + reroll : "");
        return pool.get(rng.stream(key).nextInt(pool.size()));
    }
}
