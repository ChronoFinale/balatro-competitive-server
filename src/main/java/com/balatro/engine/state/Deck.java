package com.balatro.engine.state;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Suit;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RngContext;
import com.balatro.engine.rng.RngSources;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

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

    /**
     * A card's <b>identity group</b> for composition shuffling: its suit+rank (Stone cards, which
     * have no rank/suit, share one group). Cards in the same group are interchangeable for draw
     * order, so two players holding the same composition shuffle identically regardless of the order
     * in which they acquired the cards.
     */
    public static final Function<Card, String> CARD_GROUP =
            c -> c.isStone() ? "Stone" : c.suit + "_" + c.rank.id;

    /**
     * Tiebreak between same-group cards (highest "quality" first), so duplicates that differ only by
     * enhancement/edition/seal/permanent-bonus get a stable cross-player order. Uses only shared,
     * deterministic attributes — never the per-instance uid or list position.
     */
    public static final Comparator<Card> CARD_QUALITY = Comparator
            .comparingInt((Card c) -> c.enhancement.ordinal())
            .thenComparingInt(c -> c.edition.ordinal())
            .thenComparingInt(c -> c.seal.ordinal())
            .thenComparingInt(c -> c.permaChips)
            .thenComparingDouble(c -> c.permaMult)
            .reversed();

    /**
     * Shuffle the draw pile for a blind via the composition-ordered {@link RngSources#DEAL} source:
     * each card's order is derived from its identity and the seed, not its position — so adding or
     * removing one card perturbs only that card's slot, keeping variance low between similar boards.
     */
    public void shuffle(QueueSet queues, RngContext ctx) {
        queues.shuffle(drawPile, RngSources.DEAL, ctx, CARD_GROUP, CARD_QUALITY);
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

    /** Draw exactly {@code n} cards (or until the pile empties) into {@code hand} — The Serpent always
     *  draws a fixed count instead of refilling to hand size, so the hand can shrink as the blind runs. */
    public void drawCount(List<Card> hand, int n) {
        for (int i = 0; i < n; i++) {
            Card c = draw();
            if (c == null) break;
            hand.add(c);
        }
    }
}
