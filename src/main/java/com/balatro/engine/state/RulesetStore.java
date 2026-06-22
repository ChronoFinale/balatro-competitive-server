package com.balatro.engine.state;

import com.balatro.engine.joker.JokerLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Persistence + validation for player-authored rulesets, alongside the curated
 * {@link RulesetCatalog}. A ruleset is the bundle that — with the jokers it names
 * — dictates a match; this store lets players define and share their own. Custom
 * rulesets are saved one JSON file per ruleset under a directory (default
 * {@code web-assets/custom-rulesets}, git-ignored) and reloaded at startup.
 *
 * <p>{@link #all()} and {@link #get(String)} present curated + custom as one
 * namespace (curated names are reserved). Validation guards the joker pool:
 * every key must be a known joker (built-in or a loaded custom def), so a match
 * can never reference a joker the server can't instantiate.
 */
public final class RulesetStore {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _-]{1,31}$");

    private final Path dir;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Ruleset> custom = new LinkedHashMap<>();

    public RulesetStore(Path dir) {
        this.dir = dir;
    }

    public void loadAll() {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(this::loadOne);
        } catch (IOException e) {
            throw new UncheckedIOException("listing " + dir, e);
        }
    }

    private void loadOne(Path file) {
        try {
            Ruleset r = json.readValue(Files.readAllBytes(file), Ruleset.class);
            custom.put(r.name(), r);
        } catch (IOException e) {
            System.err.println("skipping unreadable ruleset " + file + ": " + e.getMessage());
        }
    }

    public synchronized Ruleset save(Ruleset r) {
        validate(r);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(slug(r.name()) + ".json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsBytes(r));
        } catch (IOException e) {
            throw new UncheckedIOException("saving ruleset " + r.name(), e);
        }
        custom.put(r.name(), r);
        return r;
    }

    /** Curated + bundle + custom names, curated first. */
    public List<String> names() {
        List<String> out = new ArrayList<>(RulesetCatalog.names());
        for (String n : BundleCatalog.names()) {
            if (!out.contains(n)) out.add(n);
        }
        for (String n : custom.keySet()) {
            if (!out.contains(n)) out.add(n);
        }
        return out;
    }

    /** All rulesets (curated + custom) as data, for the lobby/builder to show. */
    public List<Ruleset> all() {
        List<Ruleset> out = new ArrayList<>();
        for (String n : RulesetCatalog.names()) out.add(RulesetCatalog.get(n));
        for (String n : BundleCatalog.names()) out.add(BundleCatalog.get(n).resolve());
        out.addAll(custom.values());
        return out;
    }

    public Ruleset get(String name) {
        Ruleset curated = RulesetCatalog.get(name);
        if (curated != null) return curated;
        Ruleset c = custom.get(name);
        if (c != null) return c;
        // Composable bundles are resolvable as rulesets by name (the lobby can propose them).
        RulesetBundle b = BundleCatalog.get(name);
        return b != null ? b.resolve() : null;
    }

    private void validate(Ruleset r) {
        if (r.name() == null || !NAME.matcher(r.name()).matches()) {
            throw new IllegalArgumentException("name must match " + NAME.pattern());
        }
        if (RulesetCatalog.get(r.name()) != null) {
            throw new IllegalArgumentException("name collides with a curated ruleset: " + r.name());
        }
        if (r.hands() < 1 || r.discards() < 0 || r.handSize() < 1) {
            throw new IllegalArgumentException("hands>=1, discards>=0, handSize>=1 required");
        }
        if (r.winAnte() < 0) {
            throw new IllegalArgumentException("winAnte must be >= 0 (0 = endless)");
        }
        if (r.blindBaseAmounts() == null || r.blindBaseAmounts().length < 8) {
            throw new IllegalArgumentException("blindBaseAmounts needs 8 ante values");
        }
        var known = JokerLibrary.registry().keySet();
        for (String key : r.jokerPool()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException("unknown joker in pool: " + key);
            }
        }
    }

    private static String slug(String name) {
        return name.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
    }
}
