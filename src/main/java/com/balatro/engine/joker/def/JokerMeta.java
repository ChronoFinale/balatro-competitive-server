package com.balatro.engine.joker.def;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * Ground-truth joker presentation/metadata, loaded once from {@code /balatro-joker-stats.json} (the same
 * file the {@code JokerStatsAuditTest} gate diffs against). This is the separation-of-concerns boundary: a
 * joker's effect {@link JokerDef} carries only its <i>behaviour</i> (the rules), while the sprite location —
 * which is game data, not logic — lives here in one place, keyed by joker key. The fluent builder reads it
 * so no def hardcodes atlas coords; correcting a sprite location is a one-line data edit, not a code change.
 */
public final class JokerMeta {

    private JokerMeta() {}

    private static final Map<String, int[]> ATLAS = load();

    private static Map<String, int[]> load() {
        Map<String, int[]> out = new HashMap<>();
        try (var in = JokerMeta.class.getResourceAsStream("/balatro-joker-stats.json")) {
            if (in == null) return out;
            JsonNode jokers = new ObjectMapper().readTree(in).path("jokers");
            jokers.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (v.has("x")) out.put(e.getKey(), new int[]{v.path("x").asInt(), v.path("y").asInt()});
            });
        } catch (Exception ignored) {
            // Resource absent/malformed: fall back to (0,0) per joker; the gate would catch a real drift.
        }
        return out;
    }

    /** True if the table knows this joker's sprite location. */
    public static boolean hasAtlas(String key) {
        return ATLAS.containsKey(key);
    }

    /** Sprite atlas (x,y) for a joker key, or {0,0} if unknown (e.g. a custom joker not in the table). */
    public static int[] atlas(String key) {
        return ATLAS.getOrDefault(key, new int[]{0, 0});
    }
}
