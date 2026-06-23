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

    /** One joker's ground-truth metadata. */
    public record Meta(String rarity, int cost, int atlasX, int atlasY) {}

    private static final String[] RARITY = {"", "Common", "Uncommon", "Rare", "Legendary"};
    private static final Map<String, Meta> META = load();

    private static Map<String, Meta> load() {
        Map<String, Meta> out = new HashMap<>();
        try (var in = JokerMeta.class.getResourceAsStream("/balatro-joker-stats.json")) {
            if (in == null) return out;
            JsonNode jokers = new ObjectMapper().readTree(in).path("jokers");
            jokers.properties().forEach(e -> {
                JsonNode v = e.getValue();
                if (!v.isObject() || !v.has("cost")) return; // skip the "_note" strings
                int r = v.path("rarity").asInt();
                out.put(e.getKey(), new Meta(r >= 1 && r <= 4 ? RARITY[r] : "Common",
                        v.path("cost").asInt(),
                        v.has("x") ? v.path("x").asInt() : 0,
                        v.has("y") ? v.path("y").asInt() : 0));
            });
        } catch (Exception ignored) {
            // Resource absent/malformed: fall back to defaults; the gate would catch a real drift.
        }
        return out;
    }

    /** True if the table knows this joker (a built-in); false for custom jokers, which carry their own meta. */
    public static boolean has(String key) {
        return META.containsKey(key);
    }

    public static String rarity(String key) {
        Meta m = META.get(key);
        return m != null ? m.rarity() : "Common";
    }

    public static int cost(String key) {
        Meta m = META.get(key);
        return m != null ? m.cost() : 0;
    }

    /** Sprite atlas (x,y) for a joker key, or {0,0} if unknown (e.g. a custom joker not in the table). */
    public static int[] atlas(String key) {
        Meta m = META.get(key);
        return m != null ? new int[]{m.atlasX(), m.atlasY()} : new int[]{0, 0};
    }
}
