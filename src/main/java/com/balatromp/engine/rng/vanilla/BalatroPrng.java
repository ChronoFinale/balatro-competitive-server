package com.balatromp.engine.rng.vanilla;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * A <b>bit-exact</b> port of Balatro's PRNG (Stage 3 of the BMP-parity work — the bit-exact milestone).
 * Mirrors {@code functions/misc_functions.lua} and {@code game.lua:2164-2168} so that, given the same
 * seed string, this produces the identical sequence of values to the real game seed-for-seed. Verified
 * against golden vectors dumped from LuaJIT 2.1 (Balatro's runtime) by {@code tools/balatro_prng_ref.lua}
 * — see BalatroPrngTest.
 *
 * <p>Three layers: {@link #pseudohash} (a float hash over the string bytes), {@link #pseudoseed} (a
 * stateful per-key advance), and {@code pseudorandom} (LuaJIT's {@code math.random}, in
 * {@link LuaJitRandom}). This class is the deterministic oracle our engine's pools/queues will be checked
 * against; it is NOT (yet) wired into gameplay — {@link com.balatromp.engine.rng.Rng} stays the engine's
 * RNG until pool-order parity lands.
 */
public final class BalatroPrng {

    private final String seed;
    private final double hashedSeed;
    private final Map<String, Double> state = new HashMap<>();
    private final LuaJitRandom random = new LuaJitRandom();

    public BalatroPrng(String seed) {
        this.seed = seed;
        this.hashedSeed = pseudohash(seed);
    }

    /** Balatro's hashed_seed = pseudohash(seed) (game.lua:2168). */
    public double hashedSeed() {
        return hashedSeed;
    }

    /**
     * pseudohash(str) — misc_functions.lua:279. Pure IEEE-754 double math iterating the string's bytes
     * in reverse. {@code i} is Lua's 1-based index, so the {@code Math.PI * i} term uses {@code j+1}.
     */
    public static double pseudohash(String str) {
        double num = 1.0;
        for (int j = str.length() - 1; j >= 0; j--) {
            int oneBased = j + 1;
            num = mod1((1.1239285023 / num) * (str.charAt(j) & 0xFF) * Math.PI + Math.PI * oneBased);
        }
        return num;
    }

    /**
     * pseudoseed(key) — misc_functions.lua:298-313. Stateful: each key's value advances and is folded
     * with hashed_seed. Per-key state initialises to pseudohash(key..seed).
     */
    public double pseudoseed(String key) {
        Double s = state.get(key);
        if (s == null) {
            s = pseudohash(key + seed);
        }
        s = Math.abs(round13(mod1(2.134453429141 + s * 1.72431234)));
        state.put(key, s);
        return (s + hashedSeed) / 2.0;
    }

    /** pseudorandom(key) — misc_functions.lua:315-320: randomseed(pseudoseed(key)); return random(). */
    public double pseudorandom(String key) {
        random.randomseed(pseudoseed(key));
        return random.random();
    }

    /**
     * Lua's {@code x % 1} = {@code x - floor(x)} (floored modulo). Java's {@code %} is truncated (fmod)
     * and would differ for negatives; the values here are positive, but match Lua's operator exactly.
     */
    private static double mod1(double x) {
        return x - Math.floor(x);
    }

    /**
     * Replicate Lua's {@code tonumber(string.format("%.13f", x))}. C's {@code %f} rounds the EXACT
     * binary value half-to-even; Java's {@code String.format("%.13f")} rounds half-UP, so we must round
     * the exact value ({@code new BigDecimal(double)}) with HALF_EVEN to match bit-for-bit.
     */
    static double round13(double x) {
        return new BigDecimal(x).setScale(13, RoundingMode.HALF_EVEN).doubleValue();
    }
}
