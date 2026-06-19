package com.balatro.engine.joker.def;

import com.balatro.engine.joker.Trigger;
import java.util.List;

/**
 * One scoring rule of a data-driven joker: when the engine raises {@code when} and {@code condition}
 * holds, run {@code effects} in order. A joker is an ordered list of these (plus state mutations); for a
 * given trigger the matching rules contribute. {@code effects} is the sealed {@link Effect} list — the old
 * single {@code EffectTemplate} (with its {@code extra} chain) is now just a longer list.
 */
public record Rule(Trigger when, Condition condition, List<Effect> effects) {

    public Rule {
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    /** Convenience: a single-effect rule. */
    public Rule(Trigger when, Condition condition, Effect effect) {
        this(when, condition, List.of(effect));
    }

    /** Transitional: accept a legacy {@link EffectTemplate} (decomposed to effects). Removed when
     *  {@code EffectTemplate} is deleted and the last call sites move to {@link Effect} directly. */
    public Rule(Trigger when, Condition condition, EffectTemplate effect) {
        this(when, condition, effect.toEffects());
    }
}
