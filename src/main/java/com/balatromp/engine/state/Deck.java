package com.balatromp.engine.state;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.rng.RandomStreams;
import java.util.ArrayList;
import java.util.List;

/**
 * The draw pile. Authoritative and server-only: the client never sees the deck
 * order (spec §8). Cards are drawn from the end after a shuffle on the "shuffle"
 * stream so the order is reproducible from the seed.
 */
public final class Deck {

    private final List<Card> drawPile = new ArrayList<>();

    public static Deck standard() {
        Deck d = new Deck();
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                d.drawPile.add(new Card(r, s));
            }
        }
        return d;
    }

    /** Build a deck from an explicit card list (custom/alt decks, tests). */
    public static Deck of(List<Card> cards) {
        Deck d = new Deck();
        d.drawPile.addAll(cards);
        return d;
    }

    public void add(Card c) {
        drawPile.add(c);
    }

    public void shuffle(RandomStreams rng) {
        rng.shuffle(drawPile, "shuffle");
    }

    /** Shuffle with a specific keyed stream (deterministic per blind). */
    public void shuffle(RandomStreams rng, String key) {
        rng.shuffle(drawPile, key);
    }

    /** The current cards (used to capture a run's deck composition). */
    public List<Card> cards() {
        return drawPile;
    }

    public int remaining() {
        return drawPile.size();
    }

    public Card draw() {
        return drawPile.isEmpty() ? null : drawPile.remove(drawPile.size() - 1);
    }

    /** Draw until {@code hand} reaches {@code target} size or the pile is empty. */
    public void drawTo(List<Card> hand, int target) {
        while (hand.size() < target && !drawPile.isEmpty()) {
            hand.add(draw());
        }
    }
}
