package com.balatro.engine.joker.def;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * Joker descriptions, as <b>localization data</b> — not code. Loaded from {@code /localization/en.json}
 * ({@code key -> text}); a {@code fr.json} etc. drops in beside it for other languages. A built-in joker def
 * carries only its effect (the rules); its display text lives here, where translations belong. The builder
 * reads it by key, so no def hardcodes a description string.
 */
public final class JokerLoc {

    private JokerLoc() {}

    private static final Map<String, String> DESC = load("en");

    private static Map<String, String> load(String locale) {
        Map<String, String> out = new HashMap<>();
        try (var in = JokerLoc.class.getResourceAsStream("/localization/" + locale + ".json")) {
            if (in == null) return out;
            JsonNode root = new ObjectMapper().readTree(in);
            root.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        } catch (Exception ignored) {
            // Missing/malformed localization: the builder's missing-field check surfaces an absent description.
        }
        return out;
    }

    /** True if the localization table has text for this joker key. */
    public static boolean has(String key) {
        return DESC.containsKey(key);
    }

    /** Localized description for a joker key, or "" if none. */
    public static String description(String key) {
        return DESC.getOrDefault(key, "");
    }
}
