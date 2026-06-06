package com.balatromp.engine.joker;

/**
 * A joker. All effect logic is server-side (spec §5/§6): the client never holds
 * this code, only a generated display stub. A joker reacts to a moment by
 * branching on {@link EvaluationContext#phase} and returning a {@link JokerEffect}
 * (or {@code null} for "no effect this call").
 */
public interface Joker {

    String key();

    String name();

    /** Whether this joker can be copied by Blueprint/Brainstorm. */
    default boolean blueprintCompatible() {
        return true;
    }

    default JokerEffect calculate(EvaluationContext ctx) {
        return null;
    }
}
