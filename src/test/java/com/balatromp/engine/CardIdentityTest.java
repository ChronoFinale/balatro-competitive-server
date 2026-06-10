package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.net.CardView;
import org.junit.jupiter.api.Test;

/**
 * Every card carries a unique, stable id (Balatro's sort_id) so effects can target
 * a specific card across the wire and replay — the foundation for Tarots/Spectrals
 * that mutate or destroy chosen cards, and for CREATE.
 */
class CardIdentityTest {

    @Test
    void everyCardHasAUniqueId() {
        Card a = new Card(Rank.KING, Suit.HEARTS);
        Card b = new Card(Rank.KING, Suit.HEARTS); // same rank/suit, different card
        assertThat(a.uid).isNotEqualTo(b.uid);
    }

    @Test
    void aCopyIsADistinctCardWithItsOwnId() {
        Card a = new Card(Rank.QUEEN, Suit.SPADES);
        a.permaChips = 7;
        Card copy = a.copy();
        assertThat(copy.uid).isNotEqualTo(a.uid);  // distinct identity
        assertThat(copy.permaChips).isEqualTo(7);   // but carries the state
    }

    @Test
    void theIdIsSurfacedToTheClient() {
        Card a = new Card(Rank.TEN, Suit.CLUBS);
        assertThat(CardView.of(a).uid()).isEqualTo(a.uid);
    }
}
