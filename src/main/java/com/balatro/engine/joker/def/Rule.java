package com.balatro.engine.joker.def;

import com.balatro.engine.joker.Trigger;

/**
 * One scoring rule of a data-driven joker: when the engine raises {@code when} and
 * {@code condition} holds, contribute {@code effect}. A joker is an ordered list
 * of these (plus state {@link Mutation}s); the first matching rule for a given
 * trigger wins.
 */
public record Rule(Trigger when, Condition condition, EffectTemplate effect) {}
