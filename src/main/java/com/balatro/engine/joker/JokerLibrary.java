package com.balatro.engine.joker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The starter joker set. Each one exercises a distinct path through the scoring
 * pipeline (spec §1–§4) and carries display metadata ({@link JokerInfo}:
 * description/rarity/cost/sprite). This is the codegen source-of-truth: metadata
 * here + server-only logic, with client display generated from {@code info()}.
 *
 * Sprite positions are Balatro's atlas cells (game.lua), used only if the local
 * Jokers atlas is present; no art is shipped.
 */
public final class JokerLibrary {

    private JokerLibrary() {}

    private static final Map<String, Supplier<Joker>> REGISTRY = new LinkedHashMap<>();

    static {
        // Every built-in joker is now data — there are no hand-coded jokers left. The whole catalog is
        // expressed in the card language (BuiltinJokerDefs): conditions, values, effects, mutations, copy.

        // Data-driven built-ins (real Balatro jokers as pure JokerDef data). Registered
        // here, BEFORE the BUILTIN_KEYS snapshot, so they join the curated shop pool and
        // score through the same authoritative path as the hand-coded set above.
        for (com.balatro.engine.joker.def.JokerDef def
                : com.balatro.engine.joker.def.BuiltinJokerDefs.all()) {
            REGISTRY.put(def.key(), () -> new com.balatro.engine.joker.def.DataJoker(def));
        }
    }

    /**
     * The curated, hand-coded joker keys — the default competitive shop pool,
     * captured once at class init so later {@link #registerDef} calls (custom
     * builder jokers) never leak into the standard shop or perturb its
     * determinism. Custom jokers enter a shop only when a ruleset's pool opts
     * them in.
     */
    private static final java.util.List<String> BUILTIN_KEYS = java.util.List.copyOf(REGISTRY.keySet());

    public static java.util.List<String> builtinKeys() {
        return BUILTIN_KEYS;
    }

    /**
     * Jokers banned in Standard Ranked multiplayer (boss-blind interactions: Mr. Bones, Luchador,
     * Matador, Chicot). Excluded from every pool the shop, packs, AND creation effects draw from — not
     * merely skipped — so they can't be acquired at all in an MP run. The single source of truth.
     */
    public static final java.util.Set<String> MP_BANNED =
            java.util.Set.of("j_chicot", "j_matador", "j_mr_bones", "j_luchador");

    /** Built-in joker keys of a given rarity (e.g. "Common") — Riff Raff's creation pool. */
    public static java.util.List<String> keysByRarity(String rarity) {
        return BUILTIN_KEYS.stream()
                .filter(k -> rarity.equals(create(k).info().rarity()))
                .toList();
    }

    private static void register(Supplier<Joker> factory) {
        REGISTRY.put(factory.get().key(), factory);
    }

    /**
     * Register a data-driven joker (from {@link com.balatro.engine.joker.def.JokerDef})
     * so it flows through {@link #create(String)} into shops exactly like a
     * hand-coded one. This is how custom jokers authored in the builder enter the
     * game: validated def in, a server-side {@code DataJoker} factory registered.
     */
    public static void registerDef(com.balatro.engine.joker.def.JokerDef def) {
        REGISTRY.put(def.key(), () -> new com.balatro.engine.joker.def.DataJoker(def));
    }

    public static Joker create(String key) {
        Supplier<Joker> f = REGISTRY.get(key);
        if (f == null) throw new IllegalArgumentException("Unknown joker: " + key);
        return f.get();
    }

    // Variant defs: jokerKey -> variant -> def. A joker (e.g. Hanging Chad) can behave
    // differently in single-player vs multiplayer; the active ruleset names the variant.
    private static final Map<String, Map<String, com.balatro.engine.joker.def.JokerDef>> VARIANTS =
            new LinkedHashMap<>();

    static {
        com.balatro.engine.joker.def.BuiltinJokerDefs.variants()
                .forEach((variant, defs) -> defs.forEach(d -> registerVariant(variant, d)));
    }

    /** Register an alternate behavior for an existing joker key under a named variant. */
    public static void registerVariant(String variant, com.balatro.engine.joker.def.JokerDef def) {
        VARIANTS.computeIfAbsent(def.key(), k -> new LinkedHashMap<>()).put(variant, def);
    }

    /** Create a joker, using {@code variant}'s behavior if one is registered, else the default. */
    public static Joker create(String key, String variant) {
        Map<String, com.balatro.engine.joker.def.JokerDef> vs = VARIANTS.get(key);
        if (variant != null && vs != null && vs.containsKey(variant)) {
            return new com.balatro.engine.joker.def.DataJoker(vs.get(variant));
        }
        return create(key);
    }

    public static Map<String, Supplier<Joker>> registry() {
        return REGISTRY;
    }

    // Every built-in joker now lives in the card language (BuiltinJokerDefs) — including the stateful
    // ones (Ride the Bus's streak reset) and the higher-order copiers (Blueprint/Brainstorm via Copy).
}
