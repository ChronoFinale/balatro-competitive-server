package com.balatro.engine.joker;

import com.balatro.grammar.JokerInfo;

/**
 * A joker. All effect logic is server-side (spec §5/§6): the client never holds
 * this code, only generated display metadata ({@link JokerInfo}). A joker reacts
 * to a moment by branching on {@link EvaluationContext#phase} and returning a
 * {@link JokerResult} (typed contributions + resolved commands; {@link JokerResult#EMPTY} for "no effect").
 */
public interface Joker {

    /** Display + shop metadata (name, description, rarity, cost, sprite). */
    JokerInfo info();

    default String key() {
        return info().key();
    }

    default String name() {
        return info().name();
    }

    /** Whether this joker can be copied by Blueprint/Brainstorm. */
    default boolean blueprintCompatible() {
        return true;
    }

    /** A declared constant property of this joker (its named parameters), or null if none. */
    default Object prop(String name) {
        return null;
    }

    default JokerResult calculate(EvaluationContext ctx) {
        return JokerResult.EMPTY;
    }
}
