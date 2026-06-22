package com.balatro.engine.joker.def;

import java.util.List;

/**
 * The computed difference between a base content set and an overlay-applied one — generated mechanically by
 * {@link JokerOverlays#diff}, never hand-written, so a ruleset's changelog can't drift from what the overlay
 * actually does. Each entry pairs the change with the overlay's {@code reason}, which is what makes the
 * output a real changelog ("Removed Chicot — boss-disable too strong in PvP") rather than a bare field dump.
 */
public record RulesetDiff(String overlay, String base, List<Entry> entries) {

    public enum Kind { REMOVED, ADDED, CHANGED }

    /** One changed joker. {@code fields} is populated only for {@link Kind#CHANGED} (which def fields differ). */
    public record Entry(Kind kind, String key, String name, List<String> fields, String reason) {}

    public List<Entry> of(Kind kind) {
        return entries.stream().filter(e -> e.kind() == kind).toList();
    }
}
