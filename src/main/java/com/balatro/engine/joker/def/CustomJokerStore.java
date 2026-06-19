package com.balatro.engine.joker.def;

import com.balatro.engine.joker.JokerLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Persistence + validation for builder-authored jokers. Defs are stored one
 * JSON file per joker under a directory (default {@code web-assets/custom-jokers},
 * git-ignored — user content, not shipped); sprites live beside them as
 * {@code <key>.1x.png} / {@code <key>.2x.png}. {@link #loadAll()} reads every def
 * at startup and registers it with {@link JokerLibrary} so a custom joker enters
 * the game through the same authoritative path as a hand-coded one.
 *
 * <p>Validation here is about safety, not balance: a custom joker can never cheat
 * (its effect is still computed by the authoritative pipeline), so we only guard
 * against malformed keys, collisions with built-ins, runaway sizes, and unsafe
 * sprite paths. Balance/fairness is handled by curation + ruleset agreement.
 */
public final class CustomJokerStore {

    /** Custom keys must look like a joker key and be clearly namespaced. */
    private static final Pattern KEY = Pattern.compile("^j_[a-z0-9_]{3,48}$");
    private static final int MAX_RULES = 24;
    private static final int MAX_MUTATIONS = 16;
    public static final int MAX_SPRITE_BYTES = 2 * 1024 * 1024;

    private final Path dir;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, JokerDef> defs = new ConcurrentHashMap<>();
    /** Keys that already exist as built-ins; custom defs may not shadow them. */
    private final Set<String> reserved;

    public CustomJokerStore(Path dir) {
        this.dir = dir;
        this.reserved = new LinkedHashSet<>(JokerLibrary.registry().keySet());
    }

    /** Read and register every persisted def. Tolerates a missing directory. */
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
            JokerDef def = json.readValue(Files.readAllBytes(file), JokerDef.class);
            defs.put(def.key(), def);
            JokerLibrary.registerDef(def);
        } catch (IOException e) {
            // A single bad file shouldn't take down startup; skip it.
            System.err.println("skipping unreadable joker def " + file + ": " + e.getMessage());
        }
    }

    /** Validate, persist, and register a def. Returns it; throws on invalid input. */
    public synchronized JokerDef save(JokerDef def) {
        validate(def);
        try {
            Files.createDirectories(dir);
            Files.write(defFile(def.key()), json.writerWithDefaultPrettyPrinter().writeValueAsBytes(def));
        } catch (IOException e) {
            throw new UncheckedIOException("saving joker " + def.key(), e);
        }
        defs.put(def.key(), def);
        JokerLibrary.registerDef(def);
        return def;
    }

    /** Persist an uploaded sprite for an existing def and record its URL on the def. */
    public synchronized JokerDef saveSprite(String key, int scale, byte[] png) {
        JokerDef def = defs.get(key);
        if (def == null) throw new IllegalArgumentException("unknown joker: " + key);
        if (scale != 1 && scale != 2) throw new IllegalArgumentException("scale must be 1 or 2");
        if (png.length == 0 || png.length > MAX_SPRITE_BYTES) {
            throw new IllegalArgumentException("sprite must be 1.." + MAX_SPRITE_BYTES + " bytes");
        }
        if (!isPng(png)) throw new IllegalArgumentException("sprite must be a PNG");
        String name = key + "." + scale + "x.png";
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), png);
        } catch (IOException e) {
            throw new UncheckedIOException("saving sprite " + name, e);
        }
        String url = "/custom/" + name;
        JokerDef updated = scale == 1
                ? withSprites(def, url, def.spriteUrl2x())
                : withSprites(def, def.spriteUrl(), url);
        return save(updated);
    }

    public Collection<JokerDef> all() {
        return defs.values();
    }

    public JokerDef get(String key) {
        return defs.get(key);
    }

    /** Resolve a {@code /custom/<name>} request to a file inside the store (traversal-safe). */
    public Path resolveAsset(String name) {
        Path resolved = dir.resolve(name).normalize();
        return resolved.startsWith(dir.normalize()) ? resolved : null;
    }

    public Path directory() {
        return dir;
    }

    private void validate(JokerDef def) {
        if (def.key() == null || !KEY.matcher(def.key()).matches()) {
            throw new IllegalArgumentException("key must match " + KEY.pattern());
        }
        if (reserved.contains(def.key()) && !defs.containsKey(def.key())) {
            throw new IllegalArgumentException("key collides with a built-in joker: " + def.key());
        }
        if (def.name() == null || def.name().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (def.rules().size() > MAX_RULES) {
            throw new IllegalArgumentException("too many rules (max " + MAX_RULES + ")");
        }
        if (def.mutations().size() > MAX_MUTATIONS) {
            throw new IllegalArgumentException("too many mutations (max " + MAX_MUTATIONS + ")");
        }
        for (Rule r : def.rules()) {
            if (r.when() == null || r.condition() == null || r.effects().isEmpty()) {
                throw new IllegalArgumentException("each rule needs a trigger, condition, and effect");
            }
        }
    }

    private Path defFile(String key) {
        return dir.resolve(key + ".json");
    }

    private static JokerDef withSprites(JokerDef d, String url1x, String url2x) {
        return new JokerDef(d.key(), d.name(), d.description(), d.rarity(), d.cost(),
                d.atlasX(), d.atlasY(), url1x, url2x, d.blueprintCompatible(),
                d.mutations(), d.rules());
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
    }
}
