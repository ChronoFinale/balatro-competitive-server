package com.balatro.grammar;

/**
 * A modifier on a game {@link Property} — the <i>write</i> half of the same vocabulary conditions <i>read</i>.
 * A condition reads {@code MONEY} ({@code money >= 5}); a modifier writes it ({@code add(MONEY, 4)}). Same
 * nouns, opposite verbs. Targets a {@link Property} (a {@link Hand} noun like {@code Hand.SIZE}, or a
 * run/shop/economy {@link Value.Var}).
 *
 * <p>PURE DATA: the amount is a {@link Value}, not a constant — so a modifier can be DYNAMIC (Turtle Bean's
 * decaying bonus, Skip-Off's cross-player diff). The {@code double} factories wrap the number in a
 * {@link Value.Const}; {@code com.balatro.engine.eval.ModifyFolder} resolves + folds a list of modifiers.
 */
public record Modify(Property variable, Effect.Operation op, Value value) {

    public static Modify add(Property variable, double value) { return add(variable, new Value.Const(value)); }
    public static Modify set(Property variable, double value) { return set(variable, new Value.Const(value)); }
    public static Modify multiply(Property variable, double value) { return multiply(variable, new Value.Const(value)); }

    /** Raise the value to at least {@code value} — the highest tier wins (Seed Money/Money Tree cap, Overstock). */
    public static Modify max(Property variable, double value) { return max(variable, new Value.Const(value)); }

    /** Lower the value to at most {@code value} — the deepest wins (Clearance/Liquidation; Green's no-interest 0). */
    public static Modify min(Property variable, double value) { return min(variable, new Value.Const(value)); }

    // --- Value overloads: a DYNAMIC modifier whose amount is resolved per fold (Turtle Bean / Skip-Off) ---
    public static Modify add(Property variable, Value value) { return new Modify(variable, Effect.Operation.ADD, value); }
    public static Modify set(Property variable, Value value) { return new Modify(variable, Effect.Operation.SET, value); }
    public static Modify multiply(Property variable, Value value) { return new Modify(variable, Effect.Operation.MULTIPLY, value); }
    public static Modify max(Property variable, Value value) { return new Modify(variable, Effect.Operation.MAX, value); }
    public static Modify min(Property variable, Value value) { return new Modify(variable, Effect.Operation.MIN, value); }
}
