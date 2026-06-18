package com.balatro.engine.game;

import java.util.List;

/**
 * A modifier on a game {@link Aspect} — the one shape every card uses to change the run. Juggler's
 * "+1 hand size" is {@code add(HAND_SIZE, 1)}; the Manacle's "-1 hand size" is {@code add(HAND_SIZE,
 * -1)} — the same sentence, a different card. A boss override like the Needle's "1 hand" is
 * {@code set(HANDS, 1)}; the Wall's "x4 score" is {@code multiply(BLIND_REQUIREMENT, 4)}. Jokers,
 * bosses, decks and vouchers all contribute these, and one {@link #fold} resolves them — no more
 * per-source field bags (RunMod.handSizeDelta vs BossBlind.handSizeDelta for the same aspect).
 */
public record Modify(Aspect aspect, Op op, double value) {

    public enum Op { ADD, SET, MULTIPLY }

    public static Modify add(Aspect aspect, double value) { return new Modify(aspect, Op.ADD, value); }

    public static Modify set(Aspect aspect, double value) { return new Modify(aspect, Op.SET, value); }

    public static Modify multiply(Aspect aspect, double value) { return new Modify(aspect, Op.MULTIPLY, value); }

    /**
     * Resolve {@code base} through every modifier for one aspect, op-priority order so it is
     * independent of source order: a SET replaces the base (last one wins — a boss override beats
     * the ruleset default), then ADDs accumulate, then MULTIPLYs scale.
     */
    public static double fold(double base, Aspect aspect, List<Modify> mods) {
        double v = base;
        for (Modify m : mods) if (m.aspect == aspect && m.op == Op.SET) v = m.value;
        for (Modify m : mods) if (m.aspect == aspect && m.op == Op.ADD) v += m.value;
        for (Modify m : mods) if (m.aspect == aspect && m.op == Op.MULTIPLY) v *= m.value;
        return v;
    }
}
