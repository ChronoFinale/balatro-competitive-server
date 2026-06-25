package com.balatro.grammar;

import com.balatro.grammar.Trigger;
import java.util.List;

/**
 * One scoring rule of a data-driven joker: when the engine raises {@code when} and {@code condition}
 * holds, run {@code effects} in order. A joker is an ordered list of these (plus state mutations); for a
 * given trigger the matching rules contribute. {@code effects} is the sealed {@link Effect} list — the old
 * effect chain is now an ordered {@link Effect} list.
 */
public record Rule(Trigger when, Condition condition, List<Effect> effects) {

    public Rule {
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    /** Convenience: a single-effect rule. */
    public Rule(Trigger when, Condition condition, Effect effect) {
        this(when, condition, List.of(effect));
    }
}
