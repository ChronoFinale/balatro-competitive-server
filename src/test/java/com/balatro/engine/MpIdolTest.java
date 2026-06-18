package com.balatro.engine;

import static com.balatro.engine.TestSupport.heartsKings;
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
import com.balatro.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Multiplayer Idol picks a card by a deck-position roll (1–1000) over the deck sorted
 * most-duplicated → fewest. The roll is shared between players (fair), but the card it
 * lands on depends on each player's own deck — a better-stacked deck reliably hits its
 * most-common card.
 */
class MpIdolTest {

    private static Ruleset mp() {
        return new Ruleset("MP", 4, 4, 3, 8, 1.0, 8, Ruleset.standard().blindBaseAmounts(),
                List.of("j_joker"), "multiplayer");
    }

    private static Deck mixed() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 5; i++) cards.add(card(Rank.KING, Suit.HEARTS));
        for (int i = 0; i < 3; i++) cards.add(card(Rank.QUEEN, Suit.SPADES));
        for (int i = 0; i < 2; i++) cards.add(card(Rank.ACE, Suit.CLUBS));
        cards.add(card(Rank.TWO, Suit.DIAMONDS));
        return Deck.of(cards);
    }

    private static Card card(Rank r, Suit s) {
        return new Card(r, s, Enhancement.NONE, Edition.NONE, Seal.NONE);
    }

    @Test
    void allOneCardDeckAlwaysTargetsThatCard() {
        // Every deck position is the King of Hearts, so any 1–1000 roll lands on it.
        Run run = new Run(mp(), "IDOL", heartsKings(52), jokers("j_joker"));
        assertThat(run.state.roundTargets.get("idolRankId")).isEqualTo(Rank.KING.id);
        assertThat(run.state.roundTargets.get("idolSuit")).isEqualTo(Suit.HEARTS);
    }

    @Test
    void mpIdolIsDeterministicForTheSameSeedAndDeck() {
        Run a = new Run(mp(), "IDOLSEED", mixed(), jokers("j_joker"));
        Run b = new Run(mp(), "IDOLSEED", mixed(), jokers("j_joker"));
        assertThat(a.state.roundTargets.get("idolRankId")).isEqualTo(b.state.roundTargets.get("idolRankId"));
        assertThat(a.state.roundTargets.get("idolSuit")).isEqualTo(b.state.roundTargets.get("idolSuit"));
    }

    @Test
    void mpIdolTargetIsACardActuallyInTheDeck() {
        Run run = new Run(mp(), "IDOL2", mixed(), jokers("j_joker"));
        int idolRank = (int) run.state.roundTargets.get("idolRankId");
        Object idolSuit = run.state.roundTargets.get("idolSuit");
        boolean present = run.state.deckComposition.stream()
                .anyMatch(c -> c.rank.id == idolRank && c.suit == idolSuit);
        assertThat(present).isTrue();
    }
}
