package com.balatro.engine.joker.def;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Applies a {@link RulesetOverlay} to a base joker set and computes the {@link RulesetDiff} between them.
 * This is the whole point of the overlay model: a ruleset is authored as a small list of remove/override/add
 * ops, folded here onto the compiled base to get the effective content — and the same fold yields an
 * automatic, reasoned changelog. Authoring stays in the typed {@link BuiltinJokerDefs} (which compiles to the
 * base JSON via {@link #toJson}); rulesets are pure data overlays on top.
 */
public final class JokerOverlays {

    private JokerOverlays() {}

    /** Shared mapper; the Effect/Condition/Value/Selector type-discriminators live on the classes. */
    static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Fold {@code overlay} onto {@code base}, returning the effective joker set keyed by joker key (insertion
     * order = base order, with adds appended). Validates that every remove/override names a base joker and
     * every add introduces a genuinely new key — a typo fails fast rather than silently no-op'ing.
     */
    public static Map<String, JokerDef> apply(List<JokerDef> base, RulesetOverlay overlay) {
        Map<String, JokerDef> eff = new LinkedHashMap<>();
        for (JokerDef d : base) eff.put(d.key(), d);

        for (RulesetOverlay.Remove r : overlay.remove()) {
            if (eff.remove(r.key()) == null) {
                throw new IllegalArgumentException(overlay.name() + ": remove names unknown joker " + r.key());
            }
        }
        for (RulesetOverlay.Override o : overlay.override()) {
            JokerDef cur = eff.get(o.key());
            if (cur == null) {
                throw new IllegalArgumentException(overlay.name() + ": override names unknown joker " + o.key());
            }
            eff.put(o.key(), patch(cur, o.patch()));
        }
        for (RulesetOverlay.Add a : overlay.add()) {
            if (eff.containsKey(a.def().key())) {
                throw new IllegalArgumentException(
                        overlay.name() + ": add introduces existing joker " + a.def().key() + " (use override)");
            }
            eff.put(a.def().key(), a.def());
        }
        return eff;
    }

    /**
     * Shallow-merge {@code patch} (a partial JokerDef object naming only the fields to change) over
     * {@code base}. A named field replaces the base's value wholesale (so changing behaviour means restating
     * the {@code rules} array); unnamed fields are carried through untouched. Returns a new {@link JokerDef}.
     */
    public static JokerDef patch(JokerDef base, JsonNode patch) {
        if (patch == null || patch.isNull()) return base;
        if (!patch.isObject()) {
            throw new IllegalArgumentException("override patch for " + base.key() + " must be a JSON object");
        }
        ObjectNode merged = JSON.valueToTree(base);
        merged.setAll((ObjectNode) patch);   // named fields overwrite; key stays the base key
        try {
            return JSON.treeToValue(merged, JokerDef.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid override patch for " + base.key() + ": " + e.getMessage(), e);
        }
    }

    /** Compute the reasoned diff of {@code overlay} applied to {@code base} (base vs effective). */
    public static RulesetDiff diff(List<JokerDef> base, RulesetOverlay overlay) {
        Map<String, JokerDef> baseMap = new LinkedHashMap<>();
        for (JokerDef d : base) baseMap.put(d.key(), d);
        Map<String, JokerDef> eff = apply(base, overlay);

        Map<String, String> removeReason = new LinkedHashMap<>();
        overlay.remove().forEach(r -> removeReason.put(r.key(), r.reason()));
        Map<String, String> overrideReason = new LinkedHashMap<>();
        overlay.override().forEach(o -> overrideReason.put(o.key(), o.reason()));
        Map<String, String> addReason = new LinkedHashMap<>();
        overlay.add().forEach(a -> addReason.put(a.def().key(), a.reason()));

        List<RulesetDiff.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, JokerDef> e : baseMap.entrySet()) {
            String key = e.getKey();
            JokerDef before = e.getValue();
            JokerDef after = eff.get(key);
            if (after == null) {
                entries.add(new RulesetDiff.Entry(RulesetDiff.Kind.REMOVED, key, before.name(),
                        List.of(), removeReason.get(key)));
            } else if (before != after) {
                List<String> fields = changedFields(before, after);
                if (!fields.isEmpty()) {
                    entries.add(new RulesetDiff.Entry(RulesetDiff.Kind.CHANGED, key, after.name(),
                            fields, overrideReason.get(key)));
                }
            }
        }
        for (Map.Entry<String, JokerDef> e : eff.entrySet()) {
            if (!baseMap.containsKey(e.getKey())) {
                entries.add(new RulesetDiff.Entry(RulesetDiff.Kind.ADDED, e.getKey(), e.getValue().name(),
                        List.of(), addReason.get(e.getKey())));
            }
        }
        return new RulesetDiff(overlay.name(), overlay.base(), entries);
    }

    /** Top-level def fields whose serialized value differs between {@code a} and {@code b}. */
    static List<String> changedFields(JokerDef a, JokerDef b) {
        ObjectNode na = JSON.valueToTree(a);
        ObjectNode nb = JSON.valueToTree(b);
        TreeSet<String> fields = new TreeSet<>();
        na.fieldNames().forEachRemaining(fields::add);
        nb.fieldNames().forEachRemaining(fields::add);
        List<String> out = new ArrayList<>();
        for (String f : fields) {
            if (!Objects.equals(na.get(f), nb.get(f))) out.add(f);
        }
        return out;
    }

    /** Serialize a base joker set to canonical, pretty JSON — the compiled artifact authored via the DSL. */
    public static String toJson(List<JokerDef> defs) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(defs) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException("serializing base joker set", e);
        }
    }

    /** Render a diff as a Markdown changelog — the auto-generated, reason-bearing ruleset doc. */
    public static String toMarkdown(RulesetDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Ruleset `").append(diff.overlay()).append("` vs base `").append(diff.base()).append("`\n\n");
        sb.append("> Auto-generated from the overlay by `JokerOverlays.diff`. Do not edit by hand.\n\n");
        section(sb, "Removed", diff.of(RulesetDiff.Kind.REMOVED), false);
        section(sb, "Changed", diff.of(RulesetDiff.Kind.CHANGED), true);
        section(sb, "Added", diff.of(RulesetDiff.Kind.ADDED), false);
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, List<RulesetDiff.Entry> es, boolean fields) {
        sb.append("## ").append(title).append(" (").append(es.size()).append(")\n\n");
        if (es.isEmpty()) {
            sb.append("_none_\n\n");
            return;
        }
        for (RulesetDiff.Entry e : es) {
            sb.append("- **").append(e.name()).append("** (`").append(e.key()).append("`)");
            if (fields && !e.fields().isEmpty()) sb.append(" — changed: ").append(String.join(", ", e.fields()));
            sb.append("\n");
            if (e.reason() != null && !e.reason().isBlank()) sb.append("  - _").append(e.reason()).append("_\n");
        }
        sb.append("\n");
    }
}
