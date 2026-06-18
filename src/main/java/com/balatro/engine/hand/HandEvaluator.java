package com.balatro.engine.hand;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Suit;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects the best poker hand from 1–5 played cards. Faithful to Balatro's
 * {@code evaluate_poker_hand} / {@code get_flush} / {@code get_straight}
 * (misc_functions.lua), including low- and high-ace straights and the
 * three-vs-full-house distinction (a triple is NOT counted as a pair —
 * Balatro's {@code get_X_same} matches groups of exactly n).
 *
 * <p>Global modifiers ({@link HandMods}) are honoured: Four Fingers (4-card
 * flush/straight), Shortcut (gapped straights) and Smeared (merged suits for
 * flushes). Splash and Wild suits remain out of scope for this slice.
 */
public final class HandEvaluator {

    private HandEvaluator() {}

    /** Vanilla evaluation, no global modifiers. */
    public static HandResult evaluate(List<Card> played) {
        return evaluate(played, HandMods.NONE);
    }

    public static HandResult evaluate(List<Card> played, HandMods mods) {
        List<Card> ranked = nonStone(played);

        List<Card> flushCards = flushCards(ranked, mods);
        List<Card> straightCards = straightCards(ranked, mods);
        boolean flush = !flushCards.isEmpty();
        boolean straight = !straightCards.isEmpty();

        int[] count = new int[15]; // ids 2..14
        for (Card c : ranked) count[c.id()]++;

        int highestFive = -1, highestFour = -1, highestThree = -1;
        int tripleCount = 0;
        List<Integer> pairRanks = new ArrayList<>(); // count>=2, high -> low
        for (int id = 14; id >= 2; id--) {
            if (count[id] >= 5 && highestFive < 0) highestFive = id;
            if (count[id] >= 4 && highestFour < 0) highestFour = id;
            if (count[id] >= 3 && highestThree < 0) highestThree = id;
            if (count[id] >= 3) tripleCount++;
            if (count[id] >= 2) pairRanks.add(id);
        }

        boolean five = highestFive >= 0;
        boolean four = highestFour >= 0;
        boolean three = highestThree >= 0;
        // Full house: a triple + at least one OTHER rank with a pair.
        boolean fullHouse = tripleCount >= 1 && pairRanks.size() >= 2;
        boolean twoPair = pairRanks.size() >= 2; // only reached when not a full house
        boolean pair = !pairRanks.isEmpty();

        List<Card> all = new ArrayList<>(played);

        // Priority high -> low (mirrors evaluate_poker_hand).
        if (five && flush) return new HandResult(HandType.FLUSH_FIVE, all);
        if (fullHouse && flush) return new HandResult(HandType.FLUSH_HOUSE, all);
        if (five) return new HandResult(HandType.FIVE_OF_A_KIND, cardsOf(played, highestFive));
        // Straight flush scores the cards in the run (which are necessarily one suit).
        if (straight && flush) return new HandResult(HandType.STRAIGHT_FLUSH, straightCards);
        if (four) return new HandResult(HandType.FOUR_OF_A_KIND, cardsOf(played, highestFour));
        if (fullHouse) return new HandResult(HandType.FULL_HOUSE, all);
        // Only the cards that actually form the flush/straight score (matters under
        // Four Fingers: a 4-card flush in a 5-card hand leaves the odd card out).
        if (flush) return new HandResult(HandType.FLUSH, flushCards);
        if (straight) return new HandResult(HandType.STRAIGHT, straightCards);
        if (three) return new HandResult(HandType.THREE_OF_A_KIND, cardsOf(played, highestThree));
        if (twoPair) {
            List<Card> tp = cardsOf(played, pairRanks.get(0));
            tp.addAll(cardsOf(played, pairRanks.get(1)));
            return new HandResult(HandType.TWO_PAIR, tp);
        }
        if (pair) return new HandResult(HandType.PAIR, cardsOf(played, pairRanks.get(0)));
        return new HandResult(HandType.HIGH_CARD, highest(ranked));
    }

    private static List<Card> nonStone(List<Card> played) {
        List<Card> r = new ArrayList<>();
        for (Card c : played) if (!c.isStone()) r.add(c);
        return r;
    }

    /** All (non-stone) cards in {@code played} with the given rank id. */
    private static List<Card> cardsOf(List<Card> played, int id) {
        List<Card> r = new ArrayList<>();
        for (Card c : played) if (!c.isStone() && c.id() == id) r.add(c);
        return r;
    }

    /**
     * Flush cards = the cards of the dominant suit-group when it reaches
     * {@code runLength}, else empty. With Smeared, Hearts/Diamonds collapse to one
     * group and Spades/Clubs to another.
     */
    private static List<Card> flushCards(List<Card> cards, HandMods mods) {
        int need = mods.runLength();
        if (cards.size() < need) return List.of();
        int groups = mods.smeared() ? 2 : Suit.values().length;
        int[] counts = new int[groups];
        for (Card c : cards) counts[group(c.suit, mods)]++;
        for (int g = 0; g < groups; g++) {
            if (counts[g] >= need) {
                List<Card> r = new ArrayList<>();
                for (Card c : cards) if (group(c.suit, mods) == g) r.add(c);
                return r;
            }
        }
        return List.of();
    }

    private static int group(Suit s, HandMods mods) {
        if (!mods.smeared()) return s.ordinal();
        return (s == Suit.HEARTS || s == Suit.DIAMONDS) ? 0 : 1;
    }

    /**
     * Straight cards = one card per rank in the qualifying run, where each
     * consecutive present rank is within {@code maxGap} (Shortcut allows a gap of
     * one) and the run reaches {@code runLength}. Ace counts high (14) or low (1).
     * Returns empty if no straight.
     */
    private static List<Card> straightCards(List<Card> cards, HandMods mods) {
        int need = mods.runLength();
        if (cards.size() < need) return List.of();
        boolean[] present = new boolean[16]; // indices 1..14 (+ace-low at 1)
        boolean hasAce = false;
        for (Card c : cards) {
            present[c.id()] = true;
            if (c.id() == 14) hasAce = true;
        }
        if (hasAce) present[1] = true; // ace can be low
        int gap = mods.maxGap();
        for (int start = 1; start <= 14; start++) {
            if (!present[start]) continue;
            List<Integer> runRanks = new ArrayList<>();
            runRanks.add(start);
            int last = start;
            for (int next = start + 1; next <= 14 && runRanks.size() < need; next++) {
                if (!present[next]) continue;
                if (next - last > gap) break;
                runRanks.add(next);
                last = next;
            }
            if (runRanks.size() >= need) return cardsForRanks(cards, runRanks);
        }
        return List.of();
    }

    /** One card per rank id in the run (ace-low rank 1 maps to the Ace, id 14). */
    private static List<Card> cardsForRanks(List<Card> cards, List<Integer> ranks) {
        List<Card> r = new ArrayList<>();
        for (int rank : ranks) {
            int wanted = rank == 1 ? 14 : rank;
            for (Card c : cards) {
                if (c.id() == wanted && !r.contains(c)) { r.add(c); break; }
            }
        }
        return r;
    }

    private static List<Card> highest(List<Card> cards) {
        Card hi = null;
        for (Card c : cards) {
            if (hi == null || c.id() > hi.id()) hi = c;
        }
        List<Card> r = new ArrayList<>();
        if (hi != null) r.add(hi);
        return r;
    }
}
