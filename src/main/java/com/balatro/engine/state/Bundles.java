package com.balatro.engine.state;

import com.balatro.engine.state.RulesetBundle.Mode;
import java.util.List;

/**
 * The first-party ruleset bundles, authored in the typed DSL and compiled to {@code /rulesets/bundles/*.json}.
 * Each is one composition of the three axes ({@link RulesetBundle}): content overlays · capabilities · mode.
 * Adding a competitive mode is a line here — not an engine fork.
 */
public final class Bundles {

    private Bundles() {}

    /** Vanilla content + vanilla capabilities, played head-to-head. The composition the old label couldn't name. */
    public static RulesetBundle vanillaPvp() {
        return RulesetBundle.standard("vanilla-pvp", "vanilla", List.of(), "default", Mode.PVP);
    }

    /** BMP 0.4.2 ranked: vanilla base + the bmp content overlay, MP capabilities, head-to-head. */
    public static RulesetBundle bmp042Ranked() {
        return RulesetBundle.standard("bmp-0.4.2-ranked", "vanilla", List.of("bmp-0.4.2-ranked"),
                "multiplayer", Mode.PVP);
    }

    /** Plain vanilla single-player, for reference/casual. */
    public static RulesetBundle vanillaSolo() {
        return RulesetBundle.standard("vanilla-solo", "vanilla", List.of(), "default", Mode.SOLO);
    }

    public static List<RulesetBundle> all() {
        return List.of(vanillaSolo(), vanillaPvp(), bmp042Ranked());
    }

    /**
     * Load a bundle from its compiled artifact ({@code /rulesets/bundles/<name>.json}) — the engine's
     * data-driven entry point. The DSL form above and this JSON form deserialize to identical objects, so
     * first-party content can use either while mods (JSON only) flow through exactly the same path.
     */
    public static RulesetBundle load(String name) {
        try (var in = Bundles.class.getResourceAsStream("/rulesets/bundles/" + name + ".json")) {
            if (in == null) throw new IllegalArgumentException("no bundle /rulesets/bundles/" + name + ".json");
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(in, RulesetBundle.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("loading bundle " + name, e);
        }
    }
}
