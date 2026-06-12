package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.card.Rank.ACE;
import static com.balatromp.engine.card.Rank.EIGHT;
import static com.balatromp.engine.card.Rank.FIVE;
import static com.balatromp.engine.card.Rank.FOUR;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Rank.NINE;
import static com.balatromp.engine.card.Rank.QUEEN;
import static com.balatromp.engine.card.Rank.SEVEN;
import static com.balatromp.engine.card.Rank.SIX;
import static com.balatromp.engine.card.Rank.TEN;
import static com.balatromp.engine.card.Rank.TWO;
import static com.balatromp.engine.card.Suit.CLUBS;
import static com.balatromp.engine.card.Suit.DIAMONDS;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exact level-1 base score of every poker hand: (handChips + scored-card chips) × handMult. Confirms the
 * hand-type base table (HandType.java) end-to-end through scoring. Card chips: pip value; T/J/Q/K = 10,
 * A = 11. Each hand plays exactly its scoring cards.
 */
class HandTypeScoringTest {

    private static double score(List<Card> played) {
        return new ScoringEngine().score(played, List.of(), new RunState(), new RandomStreams("T")).score();
    }

    @Test
    void highCard() {
        assertThat(score(List.of(c(KING, SPADES)))).isEqualTo(15.0); // (5 + 10) * 1
    }

    @Test
    void pair() {
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS)))).isEqualTo(60.0); // (10 + 20) * 2
    }

    @Test
    void twoPair() {
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS), c(QUEEN, SPADES), c(QUEEN, HEARTS))))
                .isEqualTo(120.0); // (20 + 40) * 2
    }

    @Test
    void threeOfAKind() {
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS), c(KING, DIAMONDS))))
                .isEqualTo(180.0); // (30 + 30) * 3
    }

    @Test
    void straight() {
        assertThat(score(List.of(c(FIVE, SPADES), c(SIX, HEARTS), c(SEVEN, DIAMONDS), c(EIGHT, CLUBS), c(NINE, SPADES))))
                .isEqualTo(260.0); // (30 + 35) * 4
    }

    @Test
    void flush() {
        assertThat(score(List.of(c(TWO, HEARTS), c(FOUR, HEARTS), c(SIX, HEARTS), c(EIGHT, HEARTS), c(TEN, HEARTS))))
                .isEqualTo(260.0); // (35 + 30) * 4
    }

    @Test
    void fullHouse() {
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS), c(KING, DIAMONDS), c(QUEEN, SPADES), c(QUEEN, HEARTS))))
                .isEqualTo(360.0); // (40 + 50) * 4
    }

    @Test
    void fourOfAKind() {
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS), c(KING, DIAMONDS), c(KING, CLUBS))))
                .isEqualTo(700.0); // (60 + 40) * 7
    }

    @Test
    void straightFlush() {
        assertThat(score(List.of(c(FIVE, HEARTS), c(SIX, HEARTS), c(SEVEN, HEARTS), c(EIGHT, HEARTS), c(NINE, HEARTS))))
                .isEqualTo(1080.0); // (100 + 35) * 8
    }

    @Test
    void fiveOfAKind() {
        // Same rank, NOT all one suit (else it'd be Flush Five).
        assertThat(score(List.of(c(KING, SPADES), c(KING, HEARTS), c(KING, DIAMONDS), c(KING, CLUBS), c(KING, SPADES))))
                .isEqualTo(2040.0); // (120 + 50) * 12
    }

    @Test
    void flushHouse() {
        assertThat(score(List.of(c(KING, HEARTS), c(KING, HEARTS), c(KING, HEARTS), c(QUEEN, HEARTS), c(QUEEN, HEARTS))))
                .isEqualTo(2660.0); // (140 + 50) * 14
    }

    @Test
    void flushFive() {
        assertThat(score(List.of(c(KING, HEARTS), c(KING, HEARTS), c(KING, HEARTS), c(KING, HEARTS), c(KING, HEARTS))))
                .isEqualTo(3360.0); // (160 + 50) * 16
    }

    @Test
    void aceCountsElevenChips() {
        assertThat(score(List.of(c(ACE, SPADES)))).isEqualTo(16.0); // (5 + 11) * 1
    }
}
