package com.balatro.engine.joker.def;

import com.balatro.content.jokers.BuiltinJokerDefs;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A by-key view of the built-in {@link JokerDef} catalog — sourced directly from
 * {@link BuiltinJokerDefs} (the single authoring home), NOT re-authored here. It used to hold 10 starter
 * jokers re-expressed as data, back when the rest were hand-coded Java and {@code DataJokerTest} proved the
 * data matched. Every joker is data now, so those copies were pure duplication; this is the thin accessor
 * the tests and the client-preview lookup use.
 */
public final class JokerDefLibrary {

    private JokerDefLibrary() {}

    private static final Map<String, JokerDef> DEFS = new LinkedHashMap<>();

    static {
        for (JokerDef def : BuiltinJokerDefs.all()) DEFS.put(def.key(), def);
    }

    public static JokerDef get(String key) {
        return DEFS.get(key);
    }

    public static Map<String, JokerDef> all() {
        return Collections.unmodifiableMap(DEFS);
    }
}
