package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.ACE;
import static com.balatro.engine.card.Rank.EIGHT;
import static com.balatro.engine.card.Rank.FIVE;
import static com.balatro.engine.card.Rank.FOUR;
import static com.balatro.engine.card.Rank.JACK;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.NINE;
import static com.balatro.engine.card.Rank.QUEEN;
import static com.balatro.engine.card.Rank.SEVEN;
import static com.balatro.engine.card.Rank.SIX;
import static com.balatro.engine.card.Rank.TEN;
import static com.balatro.engine.card.Rank.THREE;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.DIAMONDS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static com.balatro.engine.hand.HandType.FLUSH;
import static com.balatro.engine.hand.HandType.FOUR_OF_A_KIND;
import static com.balatro.engine.hand.HandType.FULL_HOUSE;
import static com.balatro.engine.hand.HandType.HIGH_CARD;
import static com.balatro.engine.hand.HandType.PAIR;
import static com.balatro.engine.hand.HandType.STRAIGHT;
import static com.balatro.engine.hand.HandType.STRAIGHT_FLUSH;
import static com.balatro.engine.hand.HandType.THREE_OF_A_KIND;
import static com.balatro.engine.hand.HandType.TWO_PAIR;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.hand.HandEvaluator;
import com.balatro.engine.hand.HandType;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandEvaluatorTest {

    private static HandType type(com.balatro.engine.card.Card... cards) {
        return HandEvaluator.evaluate(List.of(cards)).type();
    }

    private static HandType type(com.balatro.engine.hand.HandMods mods,
            com.balatro.engine.card.Card... cards) {
        return HandEvaluator.evaluate(List.of(cards), mods).type();
    }

    @Test
    void pair() {
        assertThat(type(c(KING, HEARTS), c(KING, SPADES), c(TWO, CLUBS))).isEqualTo(PAIR);
    }

    @Test
    void twoPair() {
        assertThat(type(c(KING, HEARTS), c(KING, SPADES), c(QUEEN, CLUBS), c(QUEEN, DIAMONDS), c(TWO, CLUBS)))
                .isEqualTo(TWO_PAIR);
    }

    @Test
    void threeOfAKindIsNotMistakenForFullHouse() {
        assertThat(type(c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS))).isEqualTo(THREE_OF_A_KIND);
    }

    @Test
    void fullHouse() {
        assertThat(type(c(KING, HEARTS), c(KING, SPADES), c(KING, CLUBS), c(QUEEN, HEARTS), c(QUEEN, SPADES)))
                .isEqualTo(FULL_HOUSE);
    }

    @Test
    void fourOfAKind() {
        assertThat(type(c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS), c(NINE, DIAMONDS), c(TWO, CLUBS)))
                .isEqualTo(FOUR_OF_A_KIND);
    }

    @Test
    void flush() {
        assertThat(type(c(ACE, HEARTS), c(KING, HEARTS), c(QUEEN, HEARTS), c(JACK, HEARTS), c(NINE, HEARTS)))
                .isEqualTo(FLUSH);
    }

    @Test
    void lowAceStraight() {
        assertThat(type(c(ACE, HEARTS), c(TWO, SPADES), c(THREE, CLUBS), c(FOUR, DIAMONDS), c(FIVE, HEARTS)))
                .isEqualTo(STRAIGHT);
    }

    @Test
    void highAceStraight() {
        assertThat(type(c(TEN, HEARTS), c(JACK, SPADES), c(QUEEN, CLUBS), c(KING, DIAMONDS), c(ACE, HEARTS)))
                .isEqualTo(STRAIGHT);
    }

    @Test
    void straightFlush() {
        assertThat(type(c(FIVE, HEARTS), c(SIX, HEARTS), c(SEVEN, HEARTS), c(EIGHT, HEARTS), c(NINE, HEARTS)))
                .isEqualTo(STRAIGHT_FLUSH);
    }

    @Test
    void highCard() {
        assertThat(type(c(ACE, HEARTS), c(NINE, SPADES), c(TWO, CLUBS))).isEqualTo(HIGH_CARD);
    }

    // --- global hand modifiers (HandMods) ---

    @Test
    void fourFingersMakesAFourCardFlush() {
        var cards = new com.balatro.engine.card.Card[] {
                c(TWO, HEARTS), c(FIVE, HEARTS), c(EIGHT, HEARTS), c(JACK, HEARTS) };
        assertThat(type(cards)).isEqualTo(HIGH_CARD); // vanilla: 4 cards is not a flush
        assertThat(type(new com.balatro.engine.hand.HandMods(true, false, false, false, false), cards))
                .isEqualTo(FLUSH);
    }

    @Test
    void shortcutAllowsGappedStraight() {
        var cards = new com.balatro.engine.card.Card[] {
                c(THREE, SPADES), c(FIVE, HEARTS), c(SIX, CLUBS), c(SEVEN, DIAMONDS), c(NINE, SPADES) };
        assertThat(type(cards)).isEqualTo(HIGH_CARD);
        assertThat(type(new com.balatro.engine.hand.HandMods(false, true, false, false, false), cards))
                .isEqualTo(STRAIGHT);
    }

    @Test
    void smearedMergesSuitsForFlush() {
        var cards = new com.balatro.engine.card.Card[] { // 3 Hearts + 2 Diamonds
                c(TWO, HEARTS), c(FOUR, DIAMONDS), c(SIX, HEARTS), c(EIGHT, DIAMONDS), c(TEN, HEARTS) };
        assertThat(type(cards)).isEqualTo(HIGH_CARD);
        assertThat(type(new com.balatro.engine.hand.HandMods(false, false, true, false, false), cards))
                .isEqualTo(FLUSH);
    }
}
