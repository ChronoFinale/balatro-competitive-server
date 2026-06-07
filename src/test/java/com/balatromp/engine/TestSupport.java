package com.balatromp.engine;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.RunState;
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
