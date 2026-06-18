package com.balatro.engine;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.Deck;
import com.balatro.engine.state.RunState;
import java.util.ArrayList;
import java.util.List;

/** Shared test fixtures/builders. */
final class TestSupport {

    private TestSupport() {}

    static Card c(Rank r, Suit s) {
        return new Card(r, s);
    }

    static Card card(Rank r, Suit s, Enhancement e, Seal seal) {
        return new Card(r, s, e, Edition.NONE, seal);
    }

    static Card poly(Rank r, Suit s) {
        Card x = new Card(r, s);
        x.edition = Edition.POLYCHROME;
        return x;
    }

    static List<Joker> jokers(String... keys) {
        List<Joker> l = new ArrayList<>();
        for (String k : keys) l.add(JokerLibrary.create(k));
        return l;
    }

    static Deck heartsKings(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) cards.add(new Card(Rank.KING, Suit.HEARTS));
        return Deck.of(cards);
    }

    /** Stone cards score 50 each and ignore suit/face debuffs — handy for boss tests. */
    static Deck stoneDeck(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(new Card(Rank.TWO, Suit.SPADES, Enhancement.STONE, Edition.NONE, Seal.NONE));
        }
        return Deck.of(cards);
    }

    /** A deck of identical King♥ cards — every draw is the same, so playing N cards yields a known
     *  poker hand (1 = High Card, 2 = Pair, 3 = Three of a Kind...). Handy for hand-type legality tests. */
    static Deck kingDeck(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) cards.add(new Card(Rank.KING, Suit.HEARTS));
        return Deck.of(cards);
    }

    static List<Integer> seq(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(i);
        return l;
    }

    /** Score a played hand with the given jokers (no held cards, fixed seed). */
    static ScoreResult score(List<Joker> jokers, List<Card> played) {
        RunState run = new RunState();
        for (Joker j : jokers) run.addJoker(j);
        return new ScoringEngine().score(played, List.of(), run, new RandomStreams("T"));
    }
}
