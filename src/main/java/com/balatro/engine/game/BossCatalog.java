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
            Bosses.of("bl_club", "The Club").desc("Clubs are debuffed (score nothing)").debuffs(card().suit(Suit.CLUBS)).build(),
            Bosses.of("bl_goad", "The Goad").desc("Spades are debuffed").debuffs(card().suit(Suit.SPADES)).build(),
            Bosses.of("bl_window", "The Window").desc("Diamonds are debuffed").debuffs(card().suit(Suit.DIAMONDS)).build(),
            Bosses.of("bl_head", "The Head").desc("Hearts are debuffed").debuffs(card().suit(Suit.HEARTS)).build(),
            Bosses.of("bl_manacle", "The Manacle").desc("-1 hand size").handSize(-1).build(),
            Bosses.of("bl_plant", "The Plant").desc("Face cards are debuffed").minAnte(4).debuffs(card().isFace()).build(),
            Bosses.of("bl_wall", "The Wall").desc("Very large blind (4x score)").minAnte(2).requirement(4).build(),
            Bosses.of("bl_needle", "The Needle").desc("Only one hand").minAnte(2).requirement(1).hands(1).build(),
            Bosses.of("bl_water", "The Water").desc("Start with 0 discards").minAnte(2).discards(0).build(),
            Bosses.of("bl_flint", "The Flint").desc("Base Chips and Mult are halved").minAnte(2).halvesBase().build(),
            // per-hand-effect bosses (onHandPlayed framework)
            Bosses.of("bl_tooth", "The Tooth").desc("Lose $1 per card played").minAnte(2).dollarsPerCard(-1).build(),
            Bosses.of("bl_ox", "The Ox").desc("Playing your most-played hand sets money to $0")
                    .minAnte(6).zeroMoneyOnMostPlayed().build(),
            Bosses.of("bl_arm", "The Arm").desc("Decrease level of played poker hand").minAnte(2).delevelsPlayedHand().build(),
            // hand-legality bosses (validated in Run.play before scoring) — same Cond language
            Bosses.of("bl_psychic", "The Psychic").desc("Must play 5 cards")
                    .requires(playedHand().sizeAtLeast(5)).build(),
            Bosses.of("bl_mouth", "The Mouth").desc("Play only one hand type this round")
                    .minAnte(2).requires(playedHand().matchesRoundType()).build(),
            Bosses.of("bl_eye", "The Eye").desc("No repeat hand types this round")
                    .minAnte(3).requires(playedHand().firstTimeThisRound()).build(),
            // draw-time face-down bosses (Run marks the cards, ClientView hides them)
            Bosses.of("bl_mark", "The Mark").desc("All face cards are drawn face down")
                    .minAnte(2).drawsFaceDown(card().isFace()).build(),
            Bosses.of("bl_house", "The House").desc("First hand is drawn face down")
                    .minAnte(2).drawsInitialHandFaceDown().build(),
            Bosses.of("bl_fish", "The Fish").desc("Cards drawn face down after each hand played")
                    .minAnte(2).drawsFaceDownAfterPlay().build(),
            Bosses.of("bl_wheel", "The Wheel").desc("1 in 7 cards drawn face down")
                    .minAnte(2).drawsFaceDownOneIn(7).build(),
            // per-card history debuff (same shared Cond vocabulary)
            Bosses.of("bl_pillar", "The Pillar").desc("Cards played this ante are debuffed")
                    .minAnte(2).debuffs(card().playedThisAnte()).build(),
            // draw-count override
            Bosses.of("bl_serpent", "The Serpent").desc("After each hand or discard, always draw 3 cards")
                    .minAnte(5).drawsExactly(3).build(),
            // post-play forced discard
            Bosses.of("bl_hook", "The Hook").desc("Discards 2 random cards per hand played")
                    .discardsRandomAfterPlay(2).build(),
            // finisher / showdown bosses (ante 8)
            Bosses.of("bl_final_vessel", "Violet Vessel").desc("Colossal blind (6x score)").finisher().requirement(6).build(),
            Bosses.of("bl_final_bell", "Cerulean Bell").desc("Forces 1 card to always be selected")
                    .finisher().forcesOneCardSelected().build(),
            Bosses.of("bl_final_heart", "Crimson Heart").desc("One random Joker is disabled each hand")
                    .finisher().disablesRandomJokerEachHand().build(),
            Bosses.of("bl_final_leaf", "Verdant Leaf").desc("All cards debuffed until you sell a Joker")
                    .finisher().debuffs(always()).disabledBySellingJoker().build(),
            Bosses.of("bl_final_acorn", "Amber Acorn").desc("Jokers are flipped face down and shuffled")
                    .finisher().flipsAndShufflesJokers().build()
    );

    public static boolean isFinisherAnte(int ante) {
        return ante % 8 == 0;
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
