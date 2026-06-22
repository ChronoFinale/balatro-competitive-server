package com.balatro.engine.game;

import static com.balatro.engine.joker.def.Cond.always;
import static com.balatro.engine.joker.def.Cond.card;
import static com.balatro.engine.joker.def.Cond.playedHand;

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

    private static final List<BossBlind> ALL = List.of(
            // each boss declares only the effects it has — see Bosses (the fluent builder).
            Bosses.of("bl_club", "The Club").debuffs(card().suit(Suit.CLUBS)).build(),
            Bosses.of("bl_goad", "The Goad").debuffs(card().suit(Suit.SPADES)).build(),
            Bosses.of("bl_window", "The Window").debuffs(card().suit(Suit.DIAMONDS)).build(),
            Bosses.of("bl_head", "The Head").debuffs(card().suit(Suit.HEARTS)).build(),
            Bosses.of("bl_manacle", "The Manacle").handSize(-1).build(),
            Bosses.of("bl_plant", "The Plant").minAnte(4).debuffs(card().isFace()).build(),
            Bosses.of("bl_wall", "The Wall").minAnte(2).requirement(4).build(),
            Bosses.of("bl_needle", "The Needle").minAnte(2).requirement(1).hands(1).build(),
            Bosses.of("bl_water", "The Water").minAnte(2).discards(0).build(),
            Bosses.of("bl_flint", "The Flint").minAnte(2).halvesBase().build(),
            // per-hand-effect bosses (onHandPlayed framework)
            Bosses.of("bl_tooth", "The Tooth").minAnte(3).dollarsPerCard(-1).build(),
            Bosses.of("bl_ox", "The Ox")
                    .minAnte(6).zeroMoneyOnMostPlayed().build(),
            Bosses.of("bl_arm", "The Arm").minAnte(2).delevelsPlayedHand().build(),
            // hand-legality bosses (validated in Run.play before scoring) — same Cond language
            Bosses.of("bl_psychic", "The Psychic")
                    .requires(playedHand().sizeAtLeast(5)).build(),
            Bosses.of("bl_mouth", "The Mouth")
                    .minAnte(2).requires(playedHand().matchesRoundType()).build(),
            Bosses.of("bl_eye", "The Eye")
                    .minAnte(3).requires(playedHand().firstTimeThisRound()).build(),
            // draw-time face-down bosses (Run marks the cards, ClientView hides them)
            Bosses.of("bl_mark", "The Mark")
                    .minAnte(2).drawsFaceDown(card().isFace()).build(),
            Bosses.of("bl_house", "The House")
                    .minAnte(2).drawsInitialHandFaceDown().build(),
            Bosses.of("bl_fish", "The Fish")
                    .minAnte(2).drawsFaceDownAfterPlay().build(),
            Bosses.of("bl_wheel", "The Wheel")
                    .minAnte(2).drawsFaceDownOneIn(7).build(),
            // per-card history debuff (same shared Cond vocabulary)
            Bosses.of("bl_pillar", "The Pillar")
                    .minAnte(1).debuffs(card().playedThisAnte()).build(),
            // draw-count override
            Bosses.of("bl_serpent", "The Serpent")
                    .minAnte(5).drawsExactly(3).build(),
            // post-play forced discard
            Bosses.of("bl_hook", "The Hook")
                    .discardsRandomAfterPlay(2).build(),
            // finisher / showdown bosses (ante 8)
            Bosses.of("bl_final_vessel", "Violet Vessel").finisher().requirement(6).build(),
            Bosses.of("bl_final_bell", "Cerulean Bell")
                    .finisher().forcesOneCardSelected().build(),
            Bosses.of("bl_final_heart", "Crimson Heart")
                    .finisher().disablesRandomJokerEachHand().build(),
            Bosses.of("bl_final_leaf", "Verdant Leaf")
                    .finisher().debuffs(always()).disabledBySellingJoker().build(),
            Bosses.of("bl_final_acorn", "Amber Acorn")
                    .finisher().flipsAndShufflesJokers().build()
    );

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
