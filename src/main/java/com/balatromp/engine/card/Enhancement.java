package com.balatromp.engine.card;

/**
 * Card enhancements. Scoring effects are applied by the ScoringEngine, not here,
 * so the enum stays pure data (the values mirror vanilla Balatro).
 */
public enum Enhancement {
    NONE,
    BONUS,   // +30 chips when scored
    MULT,    // +4 mult when scored
    GLASS,   // x2 mult when scored; may shatter (1 in 4)
    STEEL,   // x1.5 mult while held in hand
    STONE,   // +50 chips, always scores, no rank/suit
    GOLD,    // +$3 at end of round if held (out of scope for the slice)
    WILD,    // counts as any suit (out of scope for the slice)
    LUCKY;   // chance-based (out of scope for the slice)
}
