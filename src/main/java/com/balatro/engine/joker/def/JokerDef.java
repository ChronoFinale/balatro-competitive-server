package com.balatro.engine.joker.def;

import com.balatro.engine.hand.HandMod;
import com.balatro.engine.joker.JokerInfo;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A fully data-driven joker definition — pure data that {@link DataJoker} turns
 * into a live joker. This is the source-of-truth the builder UI produces and the
 * server validates and persists: you define a joker (metadata + state mutations +
 * scoring rules) without writing any code, and it flows through the same
 * authoritative pipeline as the hand-coded set.
 *
 * <p>{@code spriteUrl}/{@code spriteUrl2x} point at uploaded custom art (1x/2x);
 * when both are null the client falls back to the Balatro atlas cell
 * ({@code atlasX}/{@code atlasY}) if the local atlas is present. No art is shipped
 * with the server.
 */
public record JokerDef(
        String key,
        String name,
        String description,
        String rarity,
        int cost,
        int atlasX,
        int atlasY,
        String spriteUrl,
        String spriteUrl2x,
        // Boxed so "omitted in JSON" (null) is distinguishable from explicit false; the canonical
        // constructor coerces null ⇒ true, so the stored value is never null.
        Boolean blueprintCompatible,
        List<Mutation> mutations,
        List<Rule> rules,
        List<HandMod> handMods,
        RunMod runMod,
        CopySpec copy,
        java.util.Map<String, Object> props,
        java.util.Map<String, Object> state) {

    // Explicit canonical constructor annotated as the JSON creator (with property names),
    // so the convenience constructors below don't confuse Jackson's record introspection.
    @JsonCreator
    public JokerDef(
            @JsonProperty("key") String key,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("rarity") String rarity,
            @JsonProperty("cost") int cost,
            @JsonProperty("atlasX") int atlasX,
            @JsonProperty("atlasY") int atlasY,
            @JsonProperty("spriteUrl") String spriteUrl,
            @JsonProperty("spriteUrl2x") String spriteUrl2x,
            @JsonProperty("blueprintCompatible") Boolean blueprintCompatible,
            @JsonProperty("mutations") List<Mutation> mutations,
            @JsonProperty("rules") List<Rule> rules,
            @JsonProperty("handMods") List<HandMod> handMods,
            @JsonProperty("runMod") RunMod runMod,
            @JsonProperty("copy") CopySpec copy,
            @JsonProperty("props") java.util.Map<String, Object> props,
            @JsonProperty("state") java.util.Map<String, Object> state) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
        this.cost = cost;
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.spriteUrl = spriteUrl;
        this.spriteUrl2x = spriteUrl2x;
        // Omitted in authored JSON ⇒ blueprint-compatible (the common case); explicit false stays false.
        this.blueprintCompatible = blueprintCompatible == null || blueprintCompatible;
        this.mutations = mutations == null ? List.of() : List.copyOf(mutations);
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.handMods = handMods == null ? List.of() : List.copyOf(handMods);
        this.runMod = runMod == null ? RunMod.NONE : runMod;
        this.copy = copy;
        this.props = normalizeEnums(props);
        this.state = state == null ? java.util.Map.of() : java.util.Map.copyOf(state);
    }

    /** Props are JSON-authored constants read only as numbers/strings; an {@code Enum} value (e.g. a
     *  {@code Suit} passed in code) is stored as its {@code name()} so the in-memory shape is identical
     *  before and after a JSON round-trip (Jackson would otherwise deserialize it back as a String). */
    private static java.util.Map<String, Object> normalizeEnums(java.util.Map<String, Object> m) {
        if (m == null || m.isEmpty()) return java.util.Map.of();
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, v instanceof Enum<?> e ? e.name() : v));
        return java.util.Map.copyOf(out);
    }

    /** Back-compat: no global hand modifiers, no passive run modifiers (the common case). */
    public JokerDef(String key, String name, String description, String rarity, int cost,
            int atlasX, int atlasY, String spriteUrl, String spriteUrl2x,
            boolean blueprintCompatible, List<Mutation> mutations, List<Rule> rules) {
        this(key, name, description, rarity, cost, atlasX, atlasY, spriteUrl, spriteUrl2x,
                blueprintCompatible, mutations, rules, List.of(), RunMod.NONE, null,
                java.util.Map.of(), java.util.Map.of());
    }

    /** Hand-modifier joker with no passive run modifiers. */
    public JokerDef(String key, String name, String description, String rarity, int cost,
            int atlasX, int atlasY, String spriteUrl, String spriteUrl2x,
            boolean blueprintCompatible, List<Mutation> mutations, List<Rule> rules,
            List<HandMod> handMods) {
        this(key, name, description, rarity, cost, atlasX, atlasY, spriteUrl, spriteUrl2x,
                blueprintCompatible, mutations, rules, handMods, RunMod.NONE, null,
                java.util.Map.of(), java.util.Map.of());
    }

    /** Hand-modifier + passive run-modifier joker (no copy). */
    public JokerDef(String key, String name, String description, String rarity, int cost,
            int atlasX, int atlasY, String spriteUrl, String spriteUrl2x,
            boolean blueprintCompatible, List<Mutation> mutations, List<Rule> rules,
            List<HandMod> handMods, RunMod runMod) {
        this(key, name, description, rarity, cost, atlasX, atlasY, spriteUrl, spriteUrl2x,
                blueprintCompatible, mutations, rules, handMods, runMod, null,
                java.util.Map.of(), java.util.Map.of());
    }

    public JokerInfo info() {
        return new JokerInfo(key, name, description, rarity, cost, atlasX, atlasY);
    }
}
