package com.balatromp.engine.joker;

/**
 * A joker. All effect logic is server-side (spec §5/§6): the client never holds
 * this code, only generated display metadata ({@link JokerInfo}). A joker reacts
 * to a moment by branching on {@link EvaluationContext#phase} and returning a
 * {@link JokerEffect} (or {@code null} for "no effect this call").
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

    default JokerEffect calculate(EvaluationContext ctx) {
        return null;
    }
}
