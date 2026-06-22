package com.balatro.engine.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The registry of {@link RulesetBundle}s by name — the lookup the lobby/match layer uses to ask a ruleset's
 * composition (its {@link RulesetBundle.Mode mode}, its deck allow-list) without re-deriving it. A bundle's
 * name equals the {@link Ruleset} name it {@linkplain RulesetBundle#resolve resolves} to, so a ruleset agreed
 * by name can be traced back to the mode it was authored under.
 *
 * <p>Seeded with the first-party {@link Bundles}, but a bundle is just data: {@link #loadDir} registers
 * <b>custom</b> bundles dropped in as JSON, so a new competitive mode (its content overlays + capabilities +
 * mode) is authored without touching code — the extensibility payoff of the composable ruleset model.
 */
public final class BundleCatalog {

    private BundleCatalog() {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, RulesetBundle> BY_NAME = new LinkedHashMap<>();

    static {
        for (RulesetBundle b : Bundles.all()) BY_NAME.put(b.name(), b);
    }

    /** Register a bundle (curated or custom); later registrations override an earlier same-named one. */
    public static synchronized void register(RulesetBundle b) {
        BY_NAME.put(b.name(), b);
    }

    /** Load every {@code *.json} in {@code dir} as a custom bundle (no-op if the directory is absent). */
    public static synchronized void loadDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    RulesetBundle b = JSON.readValue(Files.readAllBytes(p), RulesetBundle.class);
                    b.content(); // validate it resolves (known base + every overlay exists) before offering it
                    register(b);
                } catch (Exception e) {
                    System.err.println("skipping invalid bundle " + p + ": " + e.getMessage());
                }
            });
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("listing " + dir, e);
        }
    }

    public static synchronized RulesetBundle get(String name) {
        return BY_NAME.get(name);
    }

    public static synchronized List<String> names() {
        return List.copyOf(BY_NAME.keySet());
    }

    /** Whether a ruleset is meant for head-to-head play. Unknown names default to {@code true} (a custom
     *  ruleset carries no bundle yet, and matches are the lobby's only caller — don't block them). */
    public static synchronized boolean isPvp(String name) {
        RulesetBundle b = BY_NAME.get(name);
        return b == null || b.pvp();
    }
}
