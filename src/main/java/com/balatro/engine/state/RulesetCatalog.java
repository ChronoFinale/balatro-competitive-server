package com.balatro.engine.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The curated set of ranked rulesets players draft from. Each is just data
 * (see {@link Ruleset}); a competitive format is added by adding an entry here,
 * never by changing the engine. Deliberately small — competitive play wants a
 * known, balanced pool, not open-ended modding.
 */
public final class RulesetCatalog {

    private RulesetCatalog() {}

    private static final int[] STD_BLINDS = {300, 800, 2000, 5000, 11000, 20000, 35000, 50000};

    private static final Map<String, Ruleset> BY_NAME = new LinkedHashMap<>();

    static {
        add(new Ruleset("Standard", 4, 4, 3, 8, 1.0, 8, STD_BLINDS));
        add(new Ruleset("Blitz", 4, 3, 2, 7, 1.0, 8, STD_BLINDS));     // fewer hands/discards, smaller hand
        add(new Ruleset("Marathon", 4, 5, 4, 8, 1.0, 8, STD_BLINDS));  // more hands/discards
    }

    private static void add(Ruleset r) {
        BY_NAME.put(r.name(), r);
    }

    public static List<String> names() {
        return new ArrayList<>(BY_NAME.keySet());
    }

    public static Ruleset get(String name) {
        return BY_NAME.get(name);
    }
}
