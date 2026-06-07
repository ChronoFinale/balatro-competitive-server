package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.poly;
import static com.balatromp.engine.TestSupport.score;
import static com.balatromp.engine.card.Rank.ACE;
import static com.balatromp.engine.card.Rank.JACK;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Rank.NINE;
import static com.balatromp.engine.card.Rank.QUEEN;
import static com.balatromp.engine.card.Rank.TEN;
import static com.balatromp.engine.card.Rank.THREE;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringEngineTest {

    @Test
    void pairOfKingsPlusJoker() { // 30 chips x (2+4) = 180
        assertThat(score(jokers("j_joker"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(180.0);
    }

    @Test
    void heartFlush() { // (35+50) x 4 = 340
        assertThat(score(jokers(), List.of(c(ACE, HEARTS), c(KING, HEARTS), c(QUEEN, HEARTS),
                c(JACK, HEARTS), c(NINE, HEARTS))).score()).isEqualTo(340.0);
    }

    @Test
    void polychromeAppliesBeforeJokerMult() { // chips 30; (2 x1.5)=3, +4 = 7 -> 210
        assertThat(score(jokers("j_joker"), List.of(poly(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(210.0);
    }

    @Test
    void slyJokerAddsChipsOnPair() { // (30+50) x 2 = 160
        assertThat(score(jokers("j_sly_joker"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(160.0);
    }

    @Test
    void halfJokerOnSmallHand() { // 30 x (2+20) = 660
        assertThat(score(jokers("j_half"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(660.0);
    }

    @Test
    void hackRetriggersLowCards() { // pair of 3s scored twice: (10 + 12) x 2 = 44
        assertThat(score(jokers("j_hack"), List.of(c(THREE, HEARTS), c(THREE, SPADES))).score())
                .isEqualTo(44.0);
    }

    @Test
    void blueprintCopiesRightNeighbour() { // Blueprint + Joker: +4 +4 -> 30 x 10 = 300
        assertThat(score(jokers("j_blueprint", "j_joker"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(300.0);
    }

    @Test
    void rideTheBusScalesAcrossConsecutiveHands() {
        RunState run = new RunState();
        run.addJoker(com.balatromp.engine.joker.JokerLibrary.create("j_ride_the_bus"));
        RandomStreams rng = new RandomStreams("RTB");
        List<Card> tens = List.of(c(TEN, HEARTS), c(TEN, SPADES));
        ScoringEngine eng = new ScoringEngine();
        assertThat(eng.score(tens, List.of(), run, rng).score()).isEqualTo(90.0);  // streak 1
        assertThat(eng.score(tens, List.of(), run, rng).score()).isEqualTo(120.0); // streak 2
    }
}
