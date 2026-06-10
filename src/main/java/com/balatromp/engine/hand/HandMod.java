package com.balatromp.engine.hand;

/**
 * A global hand-evaluation modifier a joker can grant — it changes what hands are
 * even possible, not the score math. These are collected from all owned jokers and
 * fed to {@link HandEvaluator}. The same set is mirrored by the client preview
 * (preview.js evaluateHand) so the instant preview detects the same hands.
 */
public enum HandMod {
    /** Four Fingers: Flushes and Straights can be made with 4 cards. */
    FOUR_FINGERS,
    /** Shortcut: Straights can be made with gaps of one rank (e.g. 3 5 6 7 9). */
    SHORTCUT,
    /** Smeared Joker: Hearts and Diamonds count as one suit, Spades and Clubs as one. */
    SMEARED
}
