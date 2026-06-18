package com.balatro.engine.rng.vanilla;

/**
 * Verbatim port of LuaJIT 2.1's {@code math.random} / {@code math.randomseed} — the generator Balatro
 * draws through (it does not override the global). The core is a TW223 (Tausworthe, L'Ecuyer 1991,
 * period 2^223) in {@code lj_prng.c}; seeding is {@code random_seed} in {@code lib_math.c}. Verified
 * bit-for-bit against LuaJIT 2.1 golden vectors (build/prng-vectors.json) — see BalatroPrngTest.
 *
 * <p>All arithmetic is on {@code long} treated as uint64; right shifts that C does on unsigned use
 * {@code >>>}, and the top-k-bit masks use {@code -1L << (64-k)}.
 */
public final class LuaJitRandom {

    private final long[] u = new long[4]; // PRNGState.u[4]

    /** random_seed(rs, d): fill the 4 LFSR words from d (d = d*π + e each step), then 10 warm-up steps. */
    public void randomseed(double d) {
        int r = 0x11090601; // 64-k[i] schedule as four bytes -> shifts 1,6,9,17
        for (int i = 0; i < 4; i++) {
            long m = 1L << (r & 0xFF);
            r >>>= 8;
            d = d * Math.PI + Math.E; // 3.14159265358979323846 / 2.7182818284590452354 == Math.PI / Math.E
            long bits = Double.doubleToRawLongBits(d);
            if (Long.compareUnsigned(bits, m) < 0) {
                bits += m; // lj_prng_condition: keep low words above their threshold
            }
            u[i] = bits;
        }
        for (int i = 0; i < 10; i++) {
            step();
        }
    }

    /** math.random() with no args: lj_prng_u64d -> double in [1,2), minus 1.0 -> [0,1). */
    public double random() {
        long r = step();
        long bits = (r & 0x000fffffffffffffL) | 0x3ff0000000000000L; // [1,2) double bit pattern
        return Double.longBitsToDouble(bits) - 1.0;
    }

    /** One TW223_STEP: advance all four generators, xor-accumulate into r. (lj_prng_u64's body.) */
    private long step() {
        long z, r = 0;
        z = u[0]; z = (((z << 31) ^ z) >>> (63 - 18)) ^ ((z & (-1L << (64 - 63))) << 18); r ^= z; u[0] = z;
        z = u[1]; z = (((z << 19) ^ z) >>> (58 - 28)) ^ ((z & (-1L << (64 - 58))) << 28); r ^= z; u[1] = z;
        z = u[2]; z = (((z << 24) ^ z) >>> (55 - 7))  ^ ((z & (-1L << (64 - 55))) << 7);  r ^= z; u[2] = z;
        z = u[3]; z = (((z << 21) ^ z) >>> (47 - 8))  ^ ((z & (-1L << (64 - 47))) << 8);  r ^= z; u[3] = z;
        return r;
    }
}
