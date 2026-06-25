package com.balatro.engine.state;

import com.balatro.content.jokers.BuiltinJokerDefs;
import com.balatro.grammar.JokerDef;
import com.balatro.engine.joker.def.JokerOverlays;
import com.balatro.engine.joker.def.Rulesets;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole competitive ruleset as one <b>composable</b> data object — the top-level source of truth that
 * the DSL authors, JSON carries, the engine runs, and client codegen reads. It composes three independent
 * axes that the legacy {@code jokerVariant} label used to fuse:
 * <ul>
 *   <li><b>content</b> — a {@code base} joker set ({@code "vanilla"}) folded with ordered {@code overlays}
 *       (each a {@link com.balatro.engine.joker.def.RulesetOverlay}); this is <i>what jokers exist</i>.</li>
 *   <li><b>capabilities</b> — the {@code variant} label resolving to a {@link Capabilities} knob set
 *       (Glass mult, restricted pools, …); this is <i>how content behaves</i>.</li>
 *   <li><b>mode</b> — {@link Mode#SOLO} vs {@link Mode#PVP}; this is the <i>match structure</i>.</li>
 * </ul>
 * "vanilla-pvp" is vanilla content + vanilla capabilities + PvP structure — a composition the old
 * label couldn't express. {@link #resolve()} compiles a bundle down to the effective joker set + the
 * {@link Ruleset} the engine already knows how to run, so the engine consumes bundles without new plumbing.
 */
public record RulesetBundle(
        String name,
        String base,
        List<String> overlays,
        String variant,
        Mode mode,
        // Allowed deck keys for this ruleset; empty = all decks (mirrors jokerPool semantics).
        List<String> decks,
        int startingMoney,
        int hands,
        int discards,
        int handSize,
        double anteScaling,
        int winAnte,
        int[] blindBaseAmounts,
        String deckType) {

    public enum Mode { SOLO, PVP }

    @JsonCreator
    public RulesetBundle(
            @JsonProperty("name") String name,
            @JsonProperty("base") String base,
            @JsonProperty("overlays") List<String> overlays,
            @JsonProperty("variant") String variant,
            @JsonProperty("mode") Mode mode,
            @JsonProperty("decks") List<String> decks,
            @JsonProperty("startingMoney") int startingMoney,
            @JsonProperty("hands") int hands,
            @JsonProperty("discards") int discards,
            @JsonProperty("handSize") int handSize,
            @JsonProperty("anteScaling") double anteScaling,
            @JsonProperty("winAnte") int winAnte,
            @JsonProperty("blindBaseAmounts") int[] blindBaseAmounts,
            @JsonProperty("deckType") String deckType) {
        this.name = name;
        this.base = (base == null || base.isBlank()) ? "vanilla" : base;
        this.overlays = overlays == null ? List.of() : List.copyOf(overlays);
        this.variant = (variant == null || variant.isBlank()) ? "default" : variant;
        this.mode = mode == null ? Mode.SOLO : mode;
        this.decks = decks == null ? List.of() : List.copyOf(decks);
        this.startingMoney = startingMoney;
        this.hands = hands;
        this.discards = discards;
        this.handSize = handSize;
        this.anteScaling = anteScaling;
        this.winAnte = winAnte;
        this.blindBaseAmounts = blindBaseAmounts;
        this.deckType = (deckType == null || deckType.isBlank()) ? "d_base" : deckType;
    }

    /** Standard ante curve + economy, parameterized only by the composition axes. */
    public static RulesetBundle standard(String name, String base, List<String> overlays,
                                         String variant, Mode mode) {
        return new RulesetBundle(name, base, overlays, variant, mode, List.of(),
                4, 4, 3, 8, 1.0, 8,
                new int[]{300, 800, 2000, 5000, 11000, 20000, 35000, 50000}, "d_base");
    }

    public boolean pvp() {
        return mode == Mode.PVP;
    }

    /** The effective joker set: the base content folded with each overlay in order. */
    public Map<String, JokerDef> content() {
        if (!"vanilla".equals(base)) {
            throw new IllegalArgumentException("unknown content base: " + base);
        }
        Map<String, JokerDef> eff = new LinkedHashMap<>();
        for (JokerDef d : BuiltinJokerDefs.all()) eff.put(d.key(), d);
        for (String overlay : overlays) {
            eff = JokerOverlays.apply(new ArrayList<>(eff.values()), Rulesets.overlay(overlay));
        }
        return eff;
    }

    /** Compile to the {@link Ruleset} the engine runs: pool = effective content keys, capabilities via variant. */
    public Ruleset resolve() {
        return new Ruleset(name, startingMoney, hands, discards, handSize, anteScaling, winAnte,
                blindBaseAmounts, List.copyOf(content().keySet()), variant, deckType);
    }
}
