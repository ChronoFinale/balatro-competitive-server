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

/**
 * Exact scoring effect of each card enhancement, baselined on a Pair of Kings (10 base chips + 20 card
 * chips, ×2 mult = 60). Both kings score in a Pair, so enhancing one isolates its contribution.
 */
class EnhancementScoringTest {

    private static Card king(Enhancement e) {
        return new Card(KING, SPADES, e, Edition.NONE, Seal.NONE);
    }

    private static double score(List<Card> played, List<Card> held, RunState run) {
        return new ScoringEngine().score(played, held, run, new RandomStreams("T")).score();
    }

    private static double score(List<Card> played) {
        return score(played, List.of(), new RunState());
    }

    @Test
    void plainPairBaseline() {
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS)))).isEqualTo(60.0); // (10+20)*2
    }

    @Test
    void bonusAddsThirtyChips() {
        assertThat(score(List.of(king(Enhancement.BONUS), c(KING, HEARTS)))).isEqualTo(120.0); // (10+20+30)*2
    }

    @Test
    void multAddsFourMult() {
        assertThat(score(List.of(king(Enhancement.MULT), c(KING, HEARTS)))).isEqualTo(180.0); // (10+20)*(2+4)
    }

    @Test
    void glassDoublesMultSinglePlayer() {
        assertThat(score(List.of(king(Enhancement.GLASS), c(KING, HEARTS)))).isEqualTo(120.0); // (10+20)*(2*2)
    }

    @Test
    void glassIsNerfedToOneAndAHalfInMultiplayer() {
        RunState mp = new RunState();
        mp.capabilities = com.balatro.engine.state.Capabilities.MULTIPLAYER;
        assertThat(score(List.of(king(Enhancement.GLASS), c(KING, HEARTS)), List.of(), mp))
                .isEqualTo(90.0); // (10+20)*(2*1.5)
    }

    @Test
    void steelGivesX1_5WhileHeldInHand() {
        // Steel must be HELD (not played) to apply; it scales the whole hand's mult.
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS)), List.of(king(Enhancement.STEEL)), new RunState()))
                .isEqualTo(90.0); // 60 * 1.5
    }

    @Test
    void luckyGivesTwentyMultWhenTheRollIsGuaranteed() {
        RunState run = new RunState();
        run.probabilityNumerator = 5; // 5/5 = 1.0 -> the 1-in-5 mult roll always hits
        assertThat(score(List.of(king(Enhancement.LUCKY), c(KING, HEARTS)), List.of(), run))
                .isEqualTo(660.0); // (10+20)*(2+20); the +$20 money roll doesn't affect score
    }
}
