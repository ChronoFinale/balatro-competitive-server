package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.FIVE;
import static com.balatro.engine.card.Rank.FOUR;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.NINE;
import static com.balatro.engine.card.Rank.SEVEN;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static com.balatro.engine.hand.HandType.FLUSH;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandEvaluator;
import com.balatro.engine.hand.HandType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Wild cards count as every suit — for flush formation (HandEvaluator) and suit checks (Card.isSuit). */
class WildCardTest {

    private static Card wild(com.balatro.engine.card.Rank r, Suit s) {
        return new Card(r, s, Enhancement.WILD, Edition.NONE, Seal.NONE);
    }

    private static HandType type(Card... cards) {
        return HandEvaluator.evaluate(List.of(cards)).type();
    }

    @Test
    void aWildCardCompletesAFlushOfAnotherSuit() {
        // Four Hearts + one Wild (nominally Spades) = a Flush, because Wild joins the Hearts group.
        assertThat(type(c(TWO, HEARTS), c(FOUR, HEARTS), c(SEVEN, HEARTS), c(NINE, HEARTS), wild(KING, SPADES)))
                .isEqualTo(FLUSH);
    }

    @Test
    void withoutTheWildItIsNotAFlush() {
        assertThat(type(c(TWO, HEARTS), c(FOUR, HEARTS), c(SEVEN, HEARTS), c(NINE, HEARTS), c(KING, SPADES)))
                .isNotEqualTo(FLUSH);
    }

    @Test
    void wildMatchesEverySuitButStoneNeverDoes() {
        Card w = wild(FIVE, CLUBS);
        assertThat(w.isSuit(HEARTS)).isTrue();
        assertThat(w.isSuit(SPADES)).isTrue();
        assertThat(w.isSuit(CLUBS)).isTrue();

        Card stone = new Card(FIVE, CLUBS, Enhancement.STONE, Edition.NONE, Seal.NONE);
        assertThat(stone.isSuit(CLUBS)).as("stone has no suit").isFalse();
    }
}
