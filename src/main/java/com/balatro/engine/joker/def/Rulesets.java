package com.balatro.engine.joker.def;

import com.balatro.content.jokers.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads content {@link RulesetOverlay}s from {@code /rulesets/*.json} on the classpath and resolves them
 * against the compiled base ({@link BuiltinJokerDefs#all()}). This is the runtime entry point that makes the
 * overlay the source of truth: the engine asks for an overlay's effective joker set, rather than the content
 * being baked into Java. The base itself is authored in the typed DSL and {@linkplain JokerOverlays#toJson
 * compiled} to {@code vanilla.json}; overlays are pure data on top.
 */
public final class Rulesets {

    private Rulesets() {}

    private static final Map<String, RulesetOverlay> OVERLAYS = new LinkedHashMap<>();
    private static final Map<String, Map<String, JokerDef>> EFFECTIVE = new LinkedHashMap<>();

    /** Load an overlay from {@code /rulesets/<name>.json}, caching it (and its applied result). */
    public static synchronized RulesetOverlay overlay(String name) {
        return OVERLAYS.computeIfAbsent(name, Rulesets::load);
    }

    /** The effective joker set for an overlay (base folded with its remove/override/add), keyed by joker key. */
    public static synchronized Map<String, JokerDef> effective(String overlayName) {
        return EFFECTIVE.computeIfAbsent(overlayName,
                n -> JokerOverlays.apply(BuiltinJokerDefs.all(), overlay(n)));
    }

    private static RulesetOverlay load(String name) {
        try (var in = Rulesets.class.getResourceAsStream("/rulesets/" + name + ".json")) {
            if (in == null) throw new IllegalArgumentException("no overlay resource /rulesets/" + name + ".json");
            return JokerOverlays.JSON.readValue(in, RulesetOverlay.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("loading overlay " + name, e);
        }
    }

    /** Keys an overlay removes from the base — the ban list for that ruleset. */
    public static List<String> removedKeys(String overlayName) {
        return overlay(overlayName).remove().stream().map(RulesetOverlay.Remove::key).toList();
    }
}
