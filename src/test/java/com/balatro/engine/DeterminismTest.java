package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.game.Run;
import com.balatro.engine.state.Deck;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The fairness contract for a 1v1: two players on the SAME seed, taking the SAME
 * actions, must get byte-identical outcomes — the shared game-long queues are what
 * make a duel fair (neither player gets luckier RNG for the same line of play).
 *
 * This is the safety net guarding the queue system while we make it BMP-correct:
 * if a change makes the same seed + same actions diverge between two runs, it broke
 * determinism. (PvP-specific queue resets — e.g. Bloodstone's per-hand PvP queue —
 * are validated separately in the match tests as those mechanics land.)
 */
class DeterminismTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    /** A deck of identical Lucky aces — every scored card rolls the Lucky mult/money queues. */
    private static Deck luckyDeck(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(new Card(Rank.ACE, Suit.HEARTS, Enhancement.LUCKY, Edition.NONE, Seal.NONE));
        }
        return Deck.of(cards);
    }

    /** A deck of identical Glass kings — every scored card rolls the Glass break queue. */
    private static Deck glassDeck(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(new Card(Rank.KING, Suit.SPADES, Enhancement.GLASS, Edition.NONE, Seal.NONE));
        }
        return Deck.of(cards);
    }

    @Test
    void luckyOutcomesAreIdenticalForTheSameSeed() {
        // Same seed + same play -> the Lucky mult (1/5) and money (1/15) queues hit identically.
        Run a = new Run(STD, "FAIR", luckyDeck(60), jokers());
        Run b = new Run(STD, "FAIR", luckyDeck(60), jokers());
        var ra = a.submit(FIVE);
        var rb = b.submit(FIVE);
        assertThat(ra.accepted()).isTrue();
        assertThat(ra.view().roundScore()).isEqualTo(rb.view().roundScore()); // Lucky +20 mult queue
        assertThat(ra.view().money()).isEqualTo(rb.view().money());           // Lucky $20 queue
    }

    @Test
    void glassBreaksAreIdenticalForTheSameSeed() {
        // The Glass break queue decides which scored glass cards shatter — identical for both.
        Run a = new Run(STD, "FAIR", glassDeck(60), jokers());
        Run b = new Run(STD, "FAIR", glassDeck(60), jokers());
        var ra = a.submit(FIVE);
        var rb = b.submit(FIVE);
        assertThat(ra.view().roundScore()).isEqualTo(rb.view().roundScore());
        // Breaks remove cards from the deck composition — both runs lose the same count.
        assertThat(a.state.deckComposition.size()).isEqualTo(b.state.deckComposition.size());
    }

    @Test
    void differentSeedsCanDiverge() {
        // Sanity: the determinism is seed-driven, not constant — different seeds may differ.
        Run a = new Run(STD, "SEED-A", luckyDeck(60), jokers());
        Run b = new Run(STD, "SEED-B", luckyDeck(60), jokers());
        a.submit(FIVE);
        b.submit(FIVE);
        // Not asserting inequality (a collision is possible), just that both run cleanly.
        assertThat(a.phase).isNotNull();
        assertThat(b.phase).isNotNull();
    }
}
