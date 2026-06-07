package com.balatromp.engine.game;

import com.balatromp.engine.card.Suit;
import com.balatromp.engine.rng.RandomStreams;
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

    private static final List<BossBlind> ALL = List.of(
            // key, name, effect, minAnte, finisher, reqMult, reward, hands, discards, handSizeDelta, debuffSuit, debuffFaces
            b("bl_club", "The Club", "Clubs are debuffed (score nothing)", 1, false, 2, 5, -1, -1, 0, Suit.CLUBS, false),
            b("bl_goad", "The Goad", "Spades are debuffed", 1, false, 2, 5, -1, -1, 0, Suit.SPADES, false),
            b("bl_window", "The Window", "Diamonds are debuffed", 1, false, 2, 5, -1, -1, 0, Suit.DIAMONDS, false),
            b("bl_head", "The Head", "Hearts are debuffed", 1, false, 2, 5, -1, -1, 0, Suit.HEARTS, false),
            b("bl_manacle", "The Manacle", "-1 hand size", 1, false, 2, 5, -1, -1, -1, null, false),
            b("bl_plant", "The Plant", "Face cards are debuffed", 4, false, 2, 5, -1, -1, 0, null, true),
            b("bl_wall", "The Wall", "Very large blind (4x score)", 2, false, 4, 5, -1, -1, 0, null, false),
            b("bl_needle", "The Needle", "Only one hand", 2, false, 1, 5, 1, -1, 0, null, false),
            b("bl_water", "The Water", "Start with 0 discards", 2, false, 2, 5, -1, 0, 0, null, false),
            // finisher / showdown bosses (ante 8)
            b("bl_final_vessel", "Violet Vessel", "Colossal blind (6x score)", 8, true, 6, 8, -1, -1, 0, null, false),
            b("bl_final_bell", "Cerulean Bell", "Finisher blind", 8, true, 2, 8, -1, -1, 0, null, false),
            b("bl_final_heart", "Crimson Heart", "Finisher blind", 8, true, 2, 8, -1, -1, 0, null, false),
            b("bl_final_leaf", "Verdant Leaf", "Finisher blind", 8, true, 2, 8, -1, -1, 0, null, false),
            b("bl_final_acorn", "Amber Acorn", "Finisher blind", 8, true, 2, 8, -1, -1, 0, null, false)
    );

    public static boolean isFinisherAnte(int ante) {
        return ante % 8 == 0;
    }

    /** Deterministically pick a boss for the given ante. */
    public static BossBlind pick(int ante, RandomStreams rng) {
        boolean finisher = isFinisherAnte(ante);
        List<BossBlind> pool = new ArrayList<>();
        for (BossBlind b : ALL) {
            if (b.finisher() == finisher && b.minAnte() <= ante) pool.add(b);
        }
        if (pool.isEmpty()) pool.add(ALL.get(0));
        return pool.get(rng.stream("boss:" + ante).nextInt(pool.size()));
    }

    private static BossBlind b(String key, String name, String effect, int minAnte, boolean finisher,
                              double reqMult, int reward, int hands, int discards, int handSizeDelta,
                              Suit debuffSuit, boolean debuffFaces) {
        return new BossBlind(key, name, effect, minAnte, finisher, reqMult, reward,
                hands, discards, handSizeDelta, debuffSuit, debuffFaces);
    }
}
