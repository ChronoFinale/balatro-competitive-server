package com.balatro.engine.state;

/**
 * Per-round targets — a value rolled fresh each blind and matched during scoring (Ancient/Castle/Idol-suit,
 * Idol/Rebate rank, To Do List hand). These are now <b>per-joker</b>, like counters: there is no global
 * registry of named targets (which used to bake specific jokers into the grammar). The owning joker rolls
 * one value per {@link Domain} it references, stored in {@link RunState#roundTargets} under {@link #key},
 * and its {@code *IsTarget} conditions read its own rolled value.
 */
public final class RoundTargets {

    private RoundTargets() {}

    /** What kind of value a target rolls. */
    public enum Domain { SUIT, RANK, HAND_TYPE }

    /** The {@link RunState#roundTargets} bag key for a joker's rolled value of a domain (derived, not magic). */
    public static String key(String jokerKey, Domain domain) {
        return jokerKey + ":" + domain.name();
    }
}
