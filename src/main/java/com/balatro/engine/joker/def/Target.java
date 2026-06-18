package com.balatro.engine.joker.def;

/**
 * What a scoring effect operates on — the targets of the scoring algebra's operations
 * ({@code add} / {@code times} / {@code lose}). These three are the score's running quantities;
 * a joker (or an enhanced card) contributes by operating on them.
 */
public enum Target {
    MULT,
    CHIPS,
    DOLLARS
}
