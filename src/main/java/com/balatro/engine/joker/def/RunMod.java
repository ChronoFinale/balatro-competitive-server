package com.balatro.engine.joker.def;

/**
 * <b>Drained.</b> This used to hold a joker's passive, non-grammar capabilities. Over this session every one
 * of them became data: Oops! All 6s → {@code multiply(PROBABILITY_MULTIPLIER, 2)}, Mr Bones → a
 * {@code BLIND_LOST} rule, Chicot → a {@code max(BOSS_ABILITY_DISABLED, 1)} policy, Skip-Off → a dynamic
 * {@code Modify} with a {@code Diff} Value, Turtle Bean → a dynamic {@code Modify} with a decaying Value.
 * Nothing populates a RunMod anymore — it's a vestigial empty type kept only so {@code JokerDef}'s shape is
 * unchanged this pass; it can be deleted outright in a focused follow-up (JokerDef record + codegen).
 */
public record RunMod() {

    public static final RunMod NONE = new RunMod();

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNone() {
        return true;
    }
}
