package com.balatro.engine.codegen;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.MessageDigest;
import java.util.List;

/**
 * The delta-sync manifest for client auto-update: every shipped content file with its {@code sha256} + size,
 * plus a {@code version} that is the hash of all of them. A client (Electron, or the real-Balatro Lua mod)
 * fetches this on connect, diffs it against its local cache, and downloads only the files whose hash changed —
 * then applies them by tier (data: no-op for the thin client; assets: hot-load; mod code: restart).
 *
 * <p>Authoring stays the DSL; this just indexes the compiled artifacts so they can be shipped incrementally.
 */
public record ContentManifest(String version, List<FileEntry> files) {

    @JsonCreator
    public ContentManifest(@JsonProperty("version") String version,
                           @JsonProperty("files") List<FileEntry> files) {
        this.version = version;
        this.files = files == null ? List.of() : List.copyOf(files);
    }

    public record FileEntry(@JsonProperty("path") String path,
                            @JsonProperty("sha256") String sha256,
                            @JsonProperty("bytes") int bytes) {}

    /** The content files shipped to clients (relative to {@code resources/}). Asset files join this later. */
    public static final List<String> FILES = List.of(
            "rulesets/vanilla.json",
            "rulesets/bmp-0.4.2-ranked.json",
            "content/decks.json",
            "content/bosses.json",
            "content/tags.json",
            "content/vouchers.json",
            "content/consumables.json",
            "content/planets.json",
            "content/hand-scores.json",
            "rulesets/bundles/vanilla-solo.json",
            "rulesets/bundles/vanilla-pvp.json",
            "rulesets/bundles/bmp-0.4.2-ranked.json",
            "localization/en.json",
            "localization/fr.json");

    public static String sha256(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256", e);
        }
    }

    /** The manifest version = sha256 over the per-file hashes (changes iff any file changes). */
    public static String versionOf(List<FileEntry> files) {
        StringBuilder sb = new StringBuilder();
        for (FileEntry f : files) sb.append(f.path()).append(':').append(f.sha256()).append('\n');
        return sha256(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 16);
    }
}
