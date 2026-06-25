package com.balatro.engine.joker.def;

/**
 * The irreducible higher-order primitive of the card language: <b>copy another card's effect</b>. PURE DATA —
 * a copier doesn't produce an effect of its own; it names (via {@link #selector}) which joker's whole
 * calculation to re-run in the current context (Blueprint copies the joker to its right, Brainstorm the
 * leftmost). {@code DataJoker} interprets it: resolve the target, re-enter it via
 * {@code EvaluationContext.forCopy} (bumping the {@code blueprintDepth} recursion guard), relabel the source.
 */
public record CopySpec(Selector selector) {

    public enum Selector {
        /** The joker immediately to the right (Blueprint). */
        RIGHT_NEIGHBOR,
        /** The leftmost joker (Brainstorm). */
        LEFTMOST
    }
}
