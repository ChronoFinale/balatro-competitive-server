package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exact scoring effect of each card edition, baselined on a Pair of Kings (= 60). */
class CardEditionScoringTest {

    private static Card king(Edition ed) {
        return new Card(KING, SPADES, Enhancement.NONE, ed, Seal.NONE);
    }

    private static double score(List<Card> played) {
        return new ScoringEngine().score(played, List.of(), new RunState(), new RandomStreams("T")).score();
    }

    @Test
    void foilAddsFiftyChips() {
        assertThat(score(List.of(king(Edition.FOIL), c(KING, HEARTS)))).isEqualTo(160.0); // (10+20+50)*2
    }

    @Test
    void holographicAddsTenMult() {
        assertThat(score(List.of(king(Edition.HOLOGRAPHIC), c(KING, HEARTS)))).isEqualTo(360.0); // (10+20)*(2+10)
    }

    @Test
    void polychromeGivesX1_5Mult() {
        assertThat(score(List.of(king(Edition.POLYCHROME), c(KING, HEARTS)))).isEqualTo(90.0); // (10+20)*(2*1.5)
    }

    @Test
    void negativeOnAPlayingCardHasNoScoreEffect() {
        // Negative is a joker/consumable slot modifier; on a playing card it scores like a plain card.
        assertThat(score(List.of(king(Edition.NEGATIVE), c(KING, HEARTS)))).isEqualTo(60.0);
    }

    @Test
    void editionsStackOnTopOfTheHandBaseAndCards() {
        // Foil (+50 chips) and Holo (+10 mult) on the two kings: (10+20+50)*(2+10) = 960.
        assertThat(score(List.of(king(Edition.FOIL),
                new Card(KING, HEARTS, Enhancement.NONE, Edition.HOLOGRAPHIC, Seal.NONE)))).isEqualTo(960.0);
    }
}
