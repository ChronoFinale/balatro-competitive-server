package com.balatro.engine.card;

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
    GOLD,    // +$3 at end of round if held (GameEvents.endOfRound)
    WILD,    // counts as every suit (Card.isSuit / HandEvaluator flushes)
    LUCKY;   // chance-based payout/mult when scored (ScoringEngine)
}
