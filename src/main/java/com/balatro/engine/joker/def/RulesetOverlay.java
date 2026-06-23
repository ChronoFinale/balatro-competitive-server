package com.balatro.engine.joker.def;

import com.balatro.content.jokers.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * A ruleset expressed as a <b>declarative overlay</b> on a base content set — not a fork of it. This is the
 * BMP layer model ({@code banned} / {@code reworked} / {@code added}) as data: a ruleset names what it
 * <i>changes</i>, and {@link JokerOverlays#apply} folds it onto the base to get the effective joker set.
 *
 * <p>Three ops, each carrying a human {@code reason} so the change is self-documenting and an automatic
 * changelog ({@link JokerOverlays#diff}) can explain <i>why</i>, not just <i>what</i>:
 * <ul>
 *   <li>{@link Remove} — drop a base joker by key (e.g. a PvP-broken boss-disable).</li>
 *   <li>{@link Override} — shallow-<b>patch</b> a base joker: a JSON object naming only the fields to change
 *       (change just the rarity, or just the rules), merged over the base def. Unnamed fields are untouched.</li>
 *   <li>{@link Add} — introduce a new joker not in the base (e.g. a Nemesis joker).</li>
 * </ul>
 *
 * <p>{@code base} names the content set this extends (for provenance/validation); the apply takes the base
 * defs directly. Overlays are authored as JSON under {@code resources/rulesets/}; the base itself is the
 * compiled {@code vanilla.json}, generated from the typed {@link BuiltinJokerDefs} authoring form.
 */
public record RulesetOverlay(
        String name,
        String base,
        List<Remove> remove,
        List<Override> override,
        List<Add> add) {

    @JsonCreator
    public RulesetOverlay(
            @JsonProperty("name") String name,
            @JsonProperty("base") String base,
            @JsonProperty("remove") List<Remove> remove,
            @JsonProperty("override") List<Override> override,
            @JsonProperty("add") List<Add> add) {
        this.name = name;
        this.base = base;
        this.remove = remove == null ? List.of() : List.copyOf(remove);
        this.override = override == null ? List.of() : List.copyOf(override);
        this.add = add == null ? List.of() : List.copyOf(add);
    }

    /** Drop a base joker. */
    public record Remove(@JsonProperty("key") String key, @JsonProperty("reason") String reason) {}

    /** Shallow-merge {@code patch} (a partial JokerDef: only the fields to change) over the base joker. */
    public record Override(@JsonProperty("key") String key, @JsonProperty("reason") String reason,
                           @JsonProperty("patch") JsonNode patch) {}

    /** Introduce a joker not present in the base. */
    public record Add(@JsonProperty("reason") String reason, @JsonProperty("def") JokerDef def) {}
}
