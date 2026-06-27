package com.balatro.grammar;

/**
 * The irreducible higher-order primitive of the card language: <b>copy another card's effect</b>. PURE DATA —
 * a copier doesn't produce an effect of its own; it names (via {@link #selector}) which joker's whole
 * calculation to re-run in the current context (Blueprint copies the joker to its right, Brainstorm the
 * leftmost). {@code DataJoker} interprets it: resolve the target, re-enter it via
 * {@code EvaluationContext.forCopy} (bumping the {@code blueprintDepth} recursion guard), relabel the source.
 *
 * <p>The target is a {@link Direction}, not the top-level {@link Selector} interface — copiers pick by fixed
 * board position (neighbour/leftmost), so they don't share that vocabulary. The distinct name avoids the
 * namespace collision with {@code Selector}.
 */
public record CopySpec(Direction selector) {

    public enum Direction {
        /** The joker immediately to the right (Blueprint). */
        RIGHT_NEIGHBOR,
        /** The leftmost joker (Brainstorm). */
        LEFTMOST
    }
}
