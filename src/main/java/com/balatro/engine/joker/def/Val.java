package com.balatro.engine.joker.def;

/**
 * Fluent authoring sugar for {@link Value}s — the magnitude half of an effect, read like a small
 * expression over the joker's declared bindings and the game context. Produces the same serializable
 * {@link Value} records (the source of truth); this just reads nicely in code, with autocomplete.
 *
 * <p>The point of {@link #prop(String)}: a joker's numbers are declared, named constants, so effects are
 * functions over those names ({@code mult(Val.prop("mult"))}) rather than anonymous magic literals.
 */
public final class Val {

    private Val() {}

    /** A fixed amount. */
    public static Value of(double amount) { return new Value.Const(amount); }

    /** A declared constant property of this joker (its named parameter). */
    public static Value prop(String name) { return new Value.Prop(name); }

    /** The raw value of a state counter (e.g. {@code add(MULT, state("streak"))} = +streak Mult). */
    public static Value state(String var) { return new Value.State(var, 0, 1); }

    /** {@code each} per unit of the counter — additive scaling (+{@code each} per unit). */
    public static Value perState(String var, double each) { return new Value.State(var, 0, each); }

    /** {@code 1 + each} per unit — the x-Mult convention (Constellation: {@code x(1 + 0.1·planets)}). */
    public static Value xPerState(String var, double each) { return new Value.State(var, 1, each); }
}
