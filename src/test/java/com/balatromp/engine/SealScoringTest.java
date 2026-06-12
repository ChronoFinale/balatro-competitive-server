package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Seal effects at scoring time (Gold = +$3, Red = retrigger). Baselined on a Pair of Kings (= 60). */
class SealScoringTest {

    private static ScoreResult run(List<Card> played, RunState run) {
        return new ScoringEngine().score(played, List.of(), run, new RandomStreams("T"));
    }

    @Test
    void goldSealPaysThreeDollarsForTheSealedCardOnly() {
        RunState run = new RunState();
        int before = run.money;
        Card gold = new Card(KING, SPADES, Enhancement.NONE, Edition.NONE, Seal.GOLD);
        ScoreResult r = run(List.of(gold, c(KING, HEARTS)), run);
        assertThat(r.score()).isEqualTo(60.0);          // Gold seal is money, not score
        assertThat(run.money).isEqualTo(before + 3);    // exactly +$3, once
    }

    @Test
    void redSealRetriggersTheCard() {
        // King with a Red seal scores twice: base 10 + A(10) + B(10) + B-retrigger(10) = 40, *2 = 80.
        Card red = new Card(KING, SPADES, Enhancement.NONE, Edition.NONE, Seal.RED);
        assertThat(run(List.of(red, c(KING, HEARTS)), new RunState()).score()).isEqualTo(80.0);
    }

    @Test
    void redSealRetriggerCompoundsTheEnhancement() {
        // Bonus(+30) + Red: the +30 chips and the card chips are credited on both triggers.
        // base 10 + A(10) + B[(10+30) x2 = 80] = 100 chips, *2 mult = 200.
        Card bonusRed = new Card(KING, SPADES, Enhancement.BONUS, Edition.NONE, Seal.RED);
        assertThat(run(List.of(bonusRed, c(KING, HEARTS)), new RunState()).score()).isEqualTo(200.0);
    }

    @Test
    void blueAndPurpleSealsDoNotChangePlayScore() {
        // Blue (planet at round end) and Purple (tarot on discard) are lifecycle effects, not play-score.
        Card blue = new Card(KING, SPADES, Enhancement.NONE, Edition.NONE, Seal.BLUE);
        Card purple = new Card(KING, HEARTS, Enhancement.NONE, Edition.NONE, Seal.PURPLE);
        assertThat(run(List.of(blue, purple), new RunState()).score()).isEqualTo(60.0);
    }
}
