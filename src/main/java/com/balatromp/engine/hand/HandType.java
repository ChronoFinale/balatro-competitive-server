package com.balatromp.engine.hand;

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

    /** Does a hand of this category contain at least one pair? (for Sly Joker etc.) */
    public boolean containsPair() {
        return switch (this) {
            case PAIR, TWO_PAIR, THREE_OF_A_KIND, FULL_HOUSE, FOUR_OF_A_KIND,
                 FIVE_OF_A_KIND, FLUSH_HOUSE, FLUSH_FIVE -> true;
            default -> false;
        };
    }
}
