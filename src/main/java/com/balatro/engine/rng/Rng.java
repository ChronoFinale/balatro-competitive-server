package com.balatro.engine.rng;

/**
 * Deterministic, fully-specified PRNG (xoshiro256** with a SplitMix64 seeder).
 *
 * <p>Per spec §8: the server is the sole RNG caller, so we do NOT replicate
 * Balatro/LuaJIT's Tausworthe arithmetic (fragile to port bit-exactly).
 * We only need {@code same seed -> same run} on our own server, which a clean,
 * documented PRNG gives us. The keyed-stream <i>structure</i> from Balatro is
 * preserved in {@link RandomStreams}; only the underlying generator differs.
 */
public final class Rng {

    private long s0, s1, s2, s3;

    public Rng(long seed) {
        seed(seed);
    }

    /** (Re)seed the generator deterministically from a single 64-bit value. */
    public void seed(long seed) {
        long z = seed;
        s0 = splitmix64(z += 0x9E3779B97F4A7C15L);
        s1 = splitmix64(z += 0x9E3779B97F4A7C15L);
        s2 = splitmix64(z += 0x9E3779B97F4A7C15L);
        s3 = splitmix64(z + 0x9E3779B97F4A7C15L);
        if ((s0 | s1 | s2 | s3) == 0L) {
            s0 = 1L; // xoshiro must not be all-zero
        }
    }

    private static long splitmix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long rotl(long x, int k) {
        return (x << k) | (x >>> (64 - k));
    }

    /** Next raw 64-bit value. */
    public long nextLong() {
        final long result = rotl(s1 * 5L, 7) * 9L;
        final long t = s1 << 17;
        s2 ^= s0;
        s3 ^= s1;
        s1 ^= s2;
        s0 ^= s3;
        s2 ^= t;
        s3 = rotl(s3, 45);
        return result;
    }

    /** Uniform double in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform int in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        // Lemire's unbiased bounded method.
        long x = nextLong() >>> 33;            // 31 random bits
        long m = x * bound;
        return (int) (m >>> 31);
    }

    /** True with probability {@code numerator/denominator} (e.g. Glass break). */
    public boolean chance(int numerator, int denominator) {
        return nextInt(denominator) < numerator;
    }
}
