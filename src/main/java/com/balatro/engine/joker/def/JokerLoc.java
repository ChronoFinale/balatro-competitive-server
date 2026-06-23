package com.balatro.engine.joker.def;

import com.balatro.dsl.*;


import com.balatro.engine.i18n.Loc;

/**
 * Joker descriptions, as <b>localization data</b> — a thin alias over the shared {@link Loc} layer (which
 * serves <i>all</i> content text from {@code /localization/<locale>.json}). A built-in joker def carries only
 * its effect; its display text lives in localization, keyed by joker key. Kept as a named entry point so the
 * Jokers builder reads "joker text" intentionally; new code can use {@link Loc} directly.
 */
public final class JokerLoc {

    private JokerLoc() {}

    /** True if the localization table has text for this joker key. */
    public static boolean has(String key) {
        return Loc.has(key);
    }

    /** Localized description for a joker key, or "" if none. */
    public static String description(String key) {
        return Loc.text(key);
    }
}
