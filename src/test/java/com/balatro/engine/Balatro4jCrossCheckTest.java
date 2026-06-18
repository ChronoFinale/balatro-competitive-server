package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.rng.Seeds;
import com.balatro.engine.rng.vanilla.BalatroPrng;
import com.balatro.engine.rng.vanilla.LuaJitRandom;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Independent second-source cross-check of our PRNG port. The reference functions below are Balatro's own
 * PRNG (TW223 + pseudohash) as ported by Balatro4J (github alex-cova/Balatro4J: Util.pseudohash,
 * LuaRandom._randInt/random) — a separate Java reimplementation the seed-finder community validates against
 * the real game. Our oracle already matches actual LuaJIT byte-for-byte (BalatroPrngTest); agreeing with
 * Balatro4J's independent code too is defense-in-depth: a shared subtle assumption in our vector generation
 * couldn't survive both checks.
 *
 * <p>The algorithm is Balatro's, not Balatro4J's invention — reproduced here purely as a verification oracle.
 */
class Balatro4jCrossCheckTest {

    // --- Balatro4J reference: Util.pseudohash(byte[]) ---
    private static double b4jFract(double n) {
        return n - Math.floor(n);
    }

    private static double b4jPseudohash(String str) {
        byte[] s = str.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1); // 1 byte/char, == charAt&0xFF
        double num = 1;
        for (int i = s.length; i > 0; i--) {
            num = b4jFract(1.1239285023 / num * s[i - 1] * 3.141592653589793 + 3.141592653589793 * i);
        }
        return num;
    }

    // --- Balatro4J reference: LuaRandom._randInt / random (stateless: reseed + 11 advances per state) ---
    private static final long MAX_UINT64 = Long.MAX_VALUE;

    private static long b4jRandInt(double seed) {
        long state;
        long randint = 0;
        int r = 0x11090601;

        long m = 1L << (r & 255);
        r >>= 8;
        seed = seed * 3.14159265358979323846 + 2.7182818284590452354;
        long bits = Double.doubleToLongBits(seed);
        if (bits < m) bits += m;
        state = bits;
        for (int i = 0; i < 5; i++) {
            state = (((state << 31) ^ state) >>> 45) ^ ((state & (MAX_UINT64 << 1)) << 18);
            state = (((state << 31) ^ state) >>> 45) ^ ((state & (MAX_UINT64 << 1)) << 18);
        }
        state = (((state << 31) ^ state) >>> 45) ^ ((state & (MAX_UINT64 << 1)) << 18);
        randint ^= state;

        m = 1L << (r & 255);
        r >>= 8;
        seed = seed * 3.14159265358979323846 + 2.7182818284590452354;
        bits = Double.doubleToLongBits(seed);
        if (bits < m) bits += m;
        state = bits;
        for (int i = 0; i < 5; i++) {
            state = (((state << 19) ^ state) >>> 30) ^ ((state & (MAX_UINT64 << 6)) << 28);
            state = (((state << 19) ^ state) >>> 30) ^ ((state & (MAX_UINT64 << 6)) << 28);
        }
        state = (((state << 19) ^ state) >>> 30) ^ ((state & (MAX_UINT64 << 6)) << 28);
        randint ^= state;

        m = 1L << (r & 255);
        r >>= 8;
        seed = seed * 3.14159265358979323846 + 2.7182818284590452354;
        bits = Double.doubleToLongBits(seed);
        if (bits < m) bits += m;
        state = bits;
        for (int i = 0; i < 5; i++) {
            state = (((state << 24) ^ state) >>> 48) ^ ((state & (MAX_UINT64 << 9)) << 7);
            state = (((state << 24) ^ state) >>> 48) ^ ((state & (MAX_UINT64 << 9)) << 7);
        }
        state = (((state << 24) ^ state) >>> 48) ^ ((state & (MAX_UINT64 << 9)) << 7);
        randint ^= state;

        m = 1L << (r & 255);
        seed = seed * 3.14159265358979323846 + 2.7182818284590452354;
        bits = Double.doubleToLongBits(seed);
        if (bits < m) bits += m;
        state = bits;
        for (int i = 0; i < 5; i++) {
            state = (((state << 21) ^ state) >>> 39) ^ ((state & (MAX_UINT64 << 17)) << 8);
            state = (((state << 21) ^ state) >>> 39) ^ ((state & (MAX_UINT64 << 17)) << 8);
        }
        state = (((state << 21) ^ state) >>> 39) ^ ((state & (MAX_UINT64 << 17)) << 8);
        randint ^= state;

        return randint;
    }

    private static double b4jRandom(double seed) {
        long bitsMem = (b4jRandInt(seed) & 4503599627370495L) | 4607182418800017408L;
        return Double.longBitsToDouble(bitsMem) - 1.0;
    }

    @Test
    void ourPseudohashMatchesBalatro4j() {
        for (String s : new String[]{"", "A", "shuffle", "Joker3sho", "rarity0sho", "*TESTSEED", "ABCD1234"}) {
            assertThat(BalatroPrng.pseudohash(s)).as("pseudohash(%s)", s).isEqualTo(b4jPseudohash(s));
        }
    }

    @Test
    void properGeneratedSeedsDeriveTheSameHashedSeedAsBalatro4j() {
        // The seed→state derivation (hashed_seed = pseudohash(seed)) on ACTUAL valid Balatro seeds — both
        // ones our Seeds generator produces and literal proper seeds — matches Balatro4J's independent code.
        Random rng = new Random(123);
        for (int i = 0; i < 50; i++) {
            String seed = Seeds.random(rng);
            assertThat(Seeds.isValid(seed)).as("generated a proper Balatro seed: %s", seed).isTrue();
            assertThat(new BalatroPrng(seed).hashedSeed())
                    .as("hashed_seed for generated seed %s", seed)
                    .isEqualTo(b4jPseudohash(seed));
        }
        for (String seed : new String[]{"TESTSEED", "ABCD1234", "7MABKPQZ", "C3D9FH2J"}) {
            assertThat(Seeds.isValid(seed)).as("%s is a valid Balatro seed", seed).isTrue();
            assertThat(new BalatroPrng(seed).hashedSeed()).isEqualTo(b4jPseudohash(seed));
        }
    }

    @Test
    void ourMathRandomMatchesBalatro4j() {
        // Our randomseed + first random() == Balatro4J's stateless random(seed): both = reseed + 11 advances.
        for (double seed : new double[]{0.0, 0.5, 0.123456789, 0.999999, 0.0000001, 0.7320508075688772,
                0.31927207822235459 /* a real pseudoseed value */}) {
            LuaJitRandom rng = new LuaJitRandom();
            rng.randomseed(seed);
            assertThat(rng.random()).as("math.random after randomseed(%s)", seed).isEqualTo(b4jRandom(seed));
        }
    }
}
