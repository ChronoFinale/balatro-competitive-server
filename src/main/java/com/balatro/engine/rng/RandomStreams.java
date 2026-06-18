package com.balatro.engine.rng;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-purpose keyed RNG streams — the structural pattern lifted from Balatro's
 * {@code pseudoseed(key)} (spec §8). Each named purpose ("shuffle", "shop",
 * "glass", ...) draws from its own independent stream derived from the master
 * seed, so consuming RNG for one purpose never shifts another. This keeps runs
 * reproducible and debuggable.
 *
 * <p>The master seed and every stream's state are server-only and must never be
 * sent to a client (the hidden-information boundary, spec §8).
 */
public final class RandomStreams {

    private final long masterSeed;
    private final Map<String, Rng> streams = new HashMap<>();

    public RandomStreams(String seed) {
        this.masterSeed = stringToSeed(seed);
    }

    public RandomStreams(long seed) {
        this.masterSeed = seed;
    }

    /** The stream for a given purpose; created (lazily, deterministically) on first use. */
    public Rng stream(String key) {
        return streams.computeIfAbsent(key, k -> new Rng(mix(masterSeed, k)));
    }

    /**
     * A <b>fresh, uncached</b> stream for {@code key} — a new {@link Rng} seeded
     * deterministically from the master seed each call, never stored. Used for
     * composition shuffle values, which must be <i>idempotent</i>: recomputing the
     * shuffle for the same key must yield the same values, so the draw must not
     * advance (or be advanced by) any persistent stream state. The BMP equivalent
     * marks these {@code G._MP_UNSAVED_PRNG} and purges them after use.
     */
    public Rng freshStream(String key) {
        return new Rng(mix(masterSeed, key));
    }

    /** Fisher–Yates shuffle of {@code list} using the named stream. */
    public <T> void shuffle(List<T> list, String key) {
        Rng r = stream(key);
        for (int i = list.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }

    /** Deterministic string -> 64-bit seed (FNV-1a style). */
    public static long stringToSeed(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }

    /** Combine the master seed with a stream key into a derived seed. */
    static long mix(long seed, String key) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 0; i < key.length(); i++) {
            h = (h ^ key.charAt(i)) * 0x100000001B3L;
        }
        return h;
    }
}
