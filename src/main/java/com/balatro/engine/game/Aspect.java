package com.balatro.engine.game;

/**
 * The aspects of the game — its nouns / resources / state — that a card can modify. Hand size,
 * discards, money and the rest are things the player <i>has</i>; a joker, a boss, a deck and a voucher
 * don't do different <i>kinds</i> of things, they all {@link Modify} these same aspects. This is the
 * sibling of {@link com.balatro.engine.hand.HandMod} (which modifies the one "how a hand is evaluated"
 * aspect): the same shape, generalised to every resource of the run.
 */
public enum Aspect {
    HANDS,             // hands per round
    DISCARDS,          // discards per round
    HAND_SIZE,         // cards held
    MONEY,             // dollars
    JOKER_SLOTS,
    CONSUMABLE_SLOTS,
    BLIND_REQUIREMENT  // the score needed to clear the blind
}
