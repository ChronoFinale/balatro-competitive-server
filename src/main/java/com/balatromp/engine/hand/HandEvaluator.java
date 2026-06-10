package com.balatromp.engine.hand;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Suit;
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

        boolean flush = isFlush(ranked, mods);
        boolean straight = isStraight(ranked, mods);

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
        if (straight && flush) return new HandResult(HandType.STRAIGHT_FLUSH, all);
        if (four) return new HandResult(HandType.FOUR_OF_A_KIND, cardsOf(played, highestFour));
        if (fullHouse) return new HandResult(HandType.FULL_HOUSE, all);
        if (flush) return new HandResult(HandType.FLUSH, all);
        if (straight) return new HandResult(HandType.STRAIGHT, all);
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
     * Flush = {@code runLength} cards of one suit. With Smeared, Hearts/Diamonds
     * collapse to one suit-group and Spades/Clubs to another.
     */
    private static boolean isFlush(List<Card> cards, HandMods mods) {
        int need = mods.runLength();
        if (cards.size() < need) return false;
        // group by suit, or by smeared pair (Hearts+Diamonds=0, Spades+Clubs=1)
        int[] counts = new int[mods.smeared() ? 2 : Suit.values().length];
        for (Card c : cards) counts[mods.smeared() ? smearedGroup(c.suit) : c.suit.ordinal()]++;
        for (int n : counts) if (n >= need) return true;
        return false;
    }

    private static int smearedGroup(Suit s) {
        return (s == Suit.HEARTS || s == Suit.DIAMONDS) ? 0 : 1;
    }

    /**
     * Straight = {@code runLength} ranks where each consecutive present rank is
     * within {@code maxGap} (Shortcut allows a gap of one). Ace counts high (14)
     * or low (1).
     */
    private static boolean isStraight(List<Card> cards, HandMods mods) {
        int need = mods.runLength();
        if (cards.size() < need) return false;
        boolean[] present = new boolean[16]; // indices 1..14 (+ace-low at 1)
        boolean hasAce = false;
        for (Card c : cards) {
            present[c.id()] = true;
            if (c.id() == 14) hasAce = true;
        }
        if (hasAce) present[1] = true; // ace can be low
        int gap = mods.maxGap();
        // Walk upward collecting a run where each step to the next present rank is <= gap.
        for (int start = 1; start <= 14; start++) {
            if (!present[start]) continue;
            int runLen = 1, last = start;
            for (int next = start + 1; next <= 14 && runLen < need; next++) {
                if (!present[next]) continue;
                if (next - last > gap) break;
                runLen++;
                last = next;
            }
            if (runLen >= need) return true;
        }
        return false;
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
