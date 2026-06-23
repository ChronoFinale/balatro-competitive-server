package com.balatro.content;

import static com.balatro.dsl.Cond.always;
import static com.balatro.dsl.Cond.card;
import static com.balatro.dsl.Cond.playedHand;

import com.balatro.engine.card.Suit;
import com.balatro.engine.game.BossBlind;
import com.balatro.engine.game.Bosses;
import java.util.List;

/** The boss CONTENT — each boss declares only the effects it has (see the {@link Bosses} builder). Compiled
 *  to {@code content/bosses.json}; descriptions come from localization (see en.json). */
public final class BossDefs {

    private BossDefs() {}

    public static List<BossBlind> authored() {
        return List.of(
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
                Bosses.of("bl_tooth", "The Tooth").minAnte(3).dollarsPerCard(-1).build(),
                Bosses.of("bl_ox", "The Ox").minAnte(6).zeroMoneyOnMostPlayed().build(),
                Bosses.of("bl_arm", "The Arm").minAnte(2).delevelsPlayedHand().build(),
                Bosses.of("bl_psychic", "The Psychic").requires(playedHand().sizeAtLeast(5)).build(),
                Bosses.of("bl_mouth", "The Mouth").minAnte(2).requires(playedHand().matchesRoundType()).build(),
                Bosses.of("bl_eye", "The Eye").minAnte(3).requires(playedHand().firstTimeThisRound()).build(),
                Bosses.of("bl_mark", "The Mark").minAnte(2).drawsFaceDown(card().isFace()).build(),
                Bosses.of("bl_house", "The House").minAnte(2).drawsInitialHandFaceDown().build(),
                Bosses.of("bl_fish", "The Fish").minAnte(2).drawsFaceDownAfterPlay().build(),
                Bosses.of("bl_wheel", "The Wheel").minAnte(2).drawsFaceDownOneIn(7).build(),
                Bosses.of("bl_pillar", "The Pillar").minAnte(1).debuffs(card().playedThisAnte()).build(),
                Bosses.of("bl_serpent", "The Serpent").minAnte(5).drawsExactly(3).build(),
                Bosses.of("bl_hook", "The Hook").discardsRandomAfterPlay(2).build(),
                Bosses.of("bl_final_vessel", "Violet Vessel").finisher().requirement(6).build(),
                Bosses.of("bl_final_bell", "Cerulean Bell").finisher().forcesOneCardSelected().build(),
                Bosses.of("bl_final_heart", "Crimson Heart").finisher().disablesRandomJokerEachHand().build(),
                Bosses.of("bl_final_leaf", "Verdant Leaf").finisher().debuffs(always()).disabledBySellingJoker().build(),
                Bosses.of("bl_final_acorn", "Amber Acorn").finisher().flipsAndShufflesJokers().build());
    }
}
