package com.balatro.engine.joker;

import com.balatro.grammar.Effect;

/**
 * One typed scoring contribution from a joker or card — the replacement for the old {@code JokerEffect} bag's
 * scoring fields. Models the scoring axes ONCE, reusing the grammar's own {@link Effect.Operation} ×
 * {@link Effect.Term} (no parallel enum): those eight scoring fields are just eight (op, term) cells —
 * {@code chips=(ADD,CHIPS)}, {@code mult=(ADD,MULT)}, {@code hMult=(ADD,HELD_MULT)},
 * {@code dollars=(ADD,DOLLARS)}, {@code repetitions=(ADD,RETRIGGERS)}, {@code xMult=(MULTIPLY,MULT)},
 * {@code xChips=(MULTIPLY,CHIPS)}, {@code powMult=(POWER,MULT)}.
 *
 * <p>The {@code ScoringEngine} folds a list of these in canonical order (ADD chips/mult/held, then MULTIPLY,
 * then POWER — what the old {@code extra} chain encoded). {@code source} is the replay attribution.
 */
public record Contribution(Effect.Operation op, Effect.Term term, double amount, String source) {

    /** An additive contribution (the common case: +chips / +mult / +$ / +retriggers / +held-mult). */
    public static Contribution add(Effect.Term term, double amount, String source) {
        return new Contribution(Effect.Operation.ADD, term, amount, source);
    }
}
