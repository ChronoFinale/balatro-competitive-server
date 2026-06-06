package com.balatromp.engine.hand;

/**
 * Poker hand categories with their level-1 base chips and mult (vanilla Balatro
 * values). Ordinal order is ascending strength, used to pick the "top" hand and
 * for "hand contains a pair/..." style joker checks.
 */
public enum HandType {
    HIGH_CARD("High Card", 5, 1),
    PAIR("Pair", 10, 2),
    TWO_PAIR("Two Pair", 20, 2),
    THREE_OF_A_KIND("Three of a Kind", 30, 3),
    STRAIGHT("Straight", 30, 4),
    FLUSH("Flush", 35, 4),
    FULL_HOUSE("Full House", 40, 4),
    FOUR_OF_A_KIND("Four of a Kind", 60, 7),
    STRAIGHT_FLUSH("Straight Flush", 100, 8),
    FIVE_OF_A_KIND("Five of a Kind", 120, 12),
    FLUSH_HOUSE("Flush House", 140, 14),
    FLUSH_FIVE("Flush Five", 160, 16);

    public final String display;
    public final int baseChips;
    public final int baseMult;

    HandType(String display, int baseChips, int baseMult) {
        this.display = display;
        this.baseChips = baseChips;
        this.baseMult = baseMult;
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
