package com.balatro.engine.joker.def;

import com.balatro.engine.joker.EvaluationContext;

/**
 * The irreducible higher-order primitive of the card language: <b>copy another card's effect</b>. A copier
 * doesn't produce an effect of its own — it re-runs the {@link #selector}-chosen joker's whole calculation
 * in the current context (Blueprint copies the joker to its right, Brainstorm the leftmost). There is no
 * more generic verb than this, so it's modelled directly rather than decomposed.
 *
 * <p>Interpreted by {@link DataJoker}: it resolves the target, re-enters it via
 * {@code EvaluationContext.forCopy} (which bumps {@code blueprintDepth}, the recursion guard), and relabels
 * the resulting effect's source.
 */
public record CopySpec(Selector selector) {

    public enum Selector {
        /** The joker immediately to the right (Blueprint). */
        RIGHT_NEIGHBOR,
        /** The leftmost joker (Brainstorm). */
        LEFTMOST
    }

    /** Index of the joker to copy in {@code ctx}, or -1 if there is none / it would be self. */
    public int targetIndex(EvaluationContext ctx) {
        int idx = switch (selector) {
            case RIGHT_NEIGHBOR -> ctx.selfIndex + 1;
            case LEFTMOST -> 0;
        };
        if (idx < 0 || idx >= ctx.jokers.size() || idx == ctx.selfIndex) return -1;
        return idx;
    }
}
