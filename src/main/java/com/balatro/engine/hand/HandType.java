package com.balatro.engine.hand;

/**
 * Poker hand categories with level-1 base chips/mult and the per-level increments
 * ({@code lChips}/{@code lMult}) applied by Planet cards — all vanilla Balatro
 * values. A hand at level L scores base + (L-1)×increment.
 */
public enum HandType {
    HIGH_CARD("High Card", 5, 1, 10, 1),
    PAIR("Pair", 10, 2, 15, 1),
    TWO_PAIR("Two Pair", 20, 2, 20, 1),
    THREE_OF_A_KIND("Three of a Kind", 30, 3, 20, 2),
    STRAIGHT("Straight", 30, 4, 30, 3),
    FLUSH("Flush", 35, 4, 15, 2),
    FULL_HOUSE("Full House", 40, 4, 25, 2),
    FOUR_OF_A_KIND("Four of a Kind", 60, 7, 30, 3),
    STRAIGHT_FLUSH("Straight Flush", 100, 8, 40, 4),
    FIVE_OF_A_KIND("Five of a Kind", 120, 12, 35, 3),
    FLUSH_HOUSE("Flush House", 140, 14, 40, 4),
    FLUSH_FIVE("Flush Five", 160, 16, 50, 3);

    public final String display;
    public final int baseChips;
    public final int baseMult;
    public final int lChips;
    public final int lMult;

    HandType(String display, int baseChips, int baseMult, int lChips, int lMult) {
        this.display = display;
        this.baseChips = baseChips;
        this.baseMult = baseMult;
        this.lChips = lChips;
        this.lMult = lMult;
    }

    /**
     * Whether a played hand of this (top) category "contains" {@code part}, matching
     * Balatro's {@code evaluate_poker_hand}: its parts come from {@code get_X_same}
     * which counts ranks with <b>exactly</b> N cards — so e.g. Four of a Kind contains
     * neither a Pair nor Three of a Kind, and Three of a Kind contains no Pair. Used by
     * the "contains X" jokers (Jolly/Sly/Zany/Mad/Crazy/Droll/Wily/Clever/Devious/Crafty).
     */
    public boolean contains(HandType part) {
        return switch (part) {
            case HIGH_CARD -> true; // every hand has a highest card
            case PAIR -> this == PAIR || this == TWO_PAIR || this == FULL_HOUSE || this == FLUSH_HOUSE;
            case TWO_PAIR -> this == TWO_PAIR || this == FULL_HOUSE || this == FLUSH_HOUSE;
            case THREE_OF_A_KIND -> this == THREE_OF_A_KIND || this == FULL_HOUSE || this == FLUSH_HOUSE;
            case STRAIGHT -> this == STRAIGHT || this == STRAIGHT_FLUSH;
            case FLUSH -> this == FLUSH || this == STRAIGHT_FLUSH || this == FLUSH_HOUSE || this == FLUSH_FIVE;
            case FULL_HOUSE -> this == FULL_HOUSE || this == FLUSH_HOUSE;
            case FOUR_OF_A_KIND -> this == FOUR_OF_A_KIND;
            case STRAIGHT_FLUSH -> this == STRAIGHT_FLUSH;
            case FIVE_OF_A_KIND -> this == FIVE_OF_A_KIND || this == FLUSH_FIVE;
            case FLUSH_HOUSE -> this == FLUSH_HOUSE;
            case FLUSH_FIVE -> this == FLUSH_FIVE;
        };
    }

    /** Does a hand of this category contain at least one pair? (for Sly/Jolly etc.) */
    public boolean containsPair() {
        return contains(PAIR);
    }
}
