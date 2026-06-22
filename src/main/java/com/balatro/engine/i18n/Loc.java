package com.balatro.engine.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one localization layer for <b>all</b> content — jokers, bosses, decks, vouchers, tags, consumables —
 * keyed by the content's stable key. Text lives in {@code /localization/<locale>.json} ({@code key -> template});
 * a def/catalog carries no English string, so translating is dropping a {@code fr.json} beside it.
 *
 * <p>Templates may embed <b>placeholders</b> {@code ${field}} that {@link #fill} substitutes from the
 * content's own data — so a number ("4× score") lives <b>once</b>, in the data, and the wording stays
 * translatable without restating (or desyncing) it. Example: {@code "bl_wall": "Very large blind (${reqMult}× score)"}.
 */
public final class Loc {

    private Loc() {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");
    private static final Map<String, String> TEXT = load("en");

    private static Map<String, String> load(String locale) {
        Map<String, String> out = new HashMap<>();
        try (var in = Loc.class.getResourceAsStream("/localization/" + locale + ".json")) {
            if (in == null) return out;
            JsonNode root = new ObjectMapper().readTree(in);
            root.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        } catch (Exception ignored) {
            // Missing/malformed: callers' missing-text checks surface it.
        }
        return out;
    }

    public static boolean has(String key) {
        return TEXT.containsKey(key);
    }

    /** Raw localized template for a key (placeholders unfilled), or "" if none. */
    public static String text(String key) {
        return TEXT.getOrDefault(key, "");
    }

    /** Localized text for a key with {@code ${field}} placeholders filled from {@code values}. */
    public static String fill(String key, Map<String, ?> values) {
        String template = text(key);
        if (template.isEmpty() || template.indexOf('$') < 0) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = values.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
