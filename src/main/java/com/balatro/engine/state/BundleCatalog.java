package com.balatro.engine.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of first-party {@link RulesetBundle}s by name — the lookup the lobby/match layer uses to ask
 * a ruleset's composition (its {@link RulesetBundle.Mode mode}, its deck allow-list) without re-deriving it.
 * A bundle's name equals the {@link Ruleset} name it {@linkplain RulesetBundle#resolve resolves} to, so a
 * ruleset agreed by name can be traced back to the mode it was authored under.
 */
public final class BundleCatalog {

    private BundleCatalog() {}

    private static final Map<String, RulesetBundle> BY_NAME = new LinkedHashMap<>();

    static {
        for (RulesetBundle b : Bundles.all()) BY_NAME.put(b.name(), b);
    }

    public static RulesetBundle get(String name) {
        return BY_NAME.get(name);
    }

    public static List<String> names() {
        return List.copyOf(BY_NAME.keySet());
    }

    /** Whether a ruleset is meant for head-to-head play. Unknown names default to {@code true} (a custom
     *  ruleset carries no bundle yet, and matches are the lobby's only caller — don't block them). */
    public static boolean isPvp(String name) {
        RulesetBundle b = BY_NAME.get(name);
        return b == null || b.pvp();
    }
}
