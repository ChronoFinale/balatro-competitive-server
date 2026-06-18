package com.balatro.engine.card;

/**
 * Card ranks. {@code id} matches Balatro's get_id (2..14, Ace high = 14;
 * Ace also acts as 1 for low straights — handled in HandEvaluator).
 * {@code baseChips} is the chips a card contributes when scored.
 */
public enum Rank {
    TWO(2, 2), THREE(3, 3), FOUR(4, 4), FIVE(5, 5), SIX(6, 6), SEVEN(7, 7),
    EIGHT(8, 8), NINE(9, 9), TEN(10, 10),
    JACK(11, 10), QUEEN(12, 10), KING(13, 10), ACE(14, 11);

    public final int id;
    public final int baseChips;

    Rank(int id, int baseChips) {
        this.id = id;
        this.baseChips = baseChips;
    }

    public boolean isFace() {
        return this == JACK || this == QUEEN || this == KING;
    }

    /** Even by rank value: 2,4,6,8,10 (Ace counts as odd per Balatro). */
    public boolean isEven() {
        return this == TWO || this == FOUR || this == SIX || this == EIGHT || this == TEN;
    }

    public boolean isOdd() {
        return this == THREE || this == FIVE || this == SEVEN || this == NINE || this == ACE;
    }

    /** The rank {@code n} steps higher, wrapping Ace → Two (Balatro's Strength up_rank). */
    public Rank shifted(int n) {
        Rank[] order = values(); // TWO..ACE, 13 ranks in id order
        int idx = Math.floorMod(ordinal() + n, order.length);
        return order[idx];
    }
}
