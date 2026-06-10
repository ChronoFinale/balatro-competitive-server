package com.balatromp.engine.rng.vanilla;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Spike — bit-exact Balatro RNG (validation substrate, NOT gameplay).</b>
 *
 * <p>A faithful Java port of Balatro's seed RNG, transcribed directly from the
 * decompiled source {@code functions/misc_functions.lua} ({@code pseudohash},
 * {@code pseudoseed}) and {@code game.lua:2243} ({@code hashed_seed =
 * pseudohash(seed)}). Its purpose is the differential-test harness: reproduce the
 * <em>real game's</em> RNG so we can validate our engine (scoring, content
 * generation) against vanilla Balatro / reference sims on a shared seed. Our
 * actual gameplay RNG stays the clean-room {@link com.balatromp.engine.rng.Rng}
 * (xoshiro) with {@link com.balatromp.engine.rng.QueueSet} structure on top —
 * this class is never used to drive a live run.
 *
 * <p>Layers (jackdaw's "3-layer PRNG"):
 * <ol>
 *   <li><b>pseudohash</b> — string → double in [0,1). Implemented exactly.</li>
 *   <li><b>pseudoseed(key)</b> — per-key advancing stream seed mixed with the
 *       run's hashed seed. Implemented exactly.</li>
 *   <li><b>LuaJIT {@code math.randomseed}/{@code math.random}</b> — turns a
 *       pseudoseed into an element/shuffle. NOT yet ported (the generic LuaJIT
 *       Tausworthe; portable from Immolate/Balatro4J). See {@link #randomElementIndex}.</li>
 * </ol>
 *
 * <p><b>Known bit-exactness caveat:</b> {@code pseudoseed} rounds via Lua
 * {@code string.format("%.13f", x)}. C/LuaJIT {@code printf} rounds half-to-even;
 * Java {@code String.format} rounds half-up. The two differ only on exact
 * 13th-decimal halfway values (vanishingly rare), but this is the first thing to
 * verify against a known vector before trusting the harness.
 */
public final class VanillaPrng {

    private final String seed;
    private final double hashedSeed;
    private final Map<String, Double> state = new HashMap<>();

    public VanillaPrng(String seed) {
        this.seed = seed;
        this.hashedSeed = pseudohash(seed);
    }

    /**
     * {@code pseudohash(str)} — exact port:
     * <pre>num=1; for i=#str..1: num = ((1.1239285023/num)*byte(i)*pi + pi*i) % 1</pre>
     * (Lua is 1-indexed, so the {@code pi*i} term uses the 1-based position.)
     */
    public static double pseudohash(String str) {
        double num = 1.0;
        for (int idx = str.length() - 1; idx >= 0; idx--) {
            int b = str.charAt(idx) & 0xFF;       // string.byte — Balatro seeds are ASCII
            int pos = idx + 1;                    // Lua 1-based position
            num = luaMod1((1.1239285023 / num) * b * Math.PI + Math.PI * pos);
        }
        return num;
    }

    /**
     * {@code pseudoseed(key)} — exact port (non-predict branch): lazily seed the
     * per-key stream from {@code pseudohash(key..seed)}, advance it, and return it
     * mixed with the run's {@code hashed_seed}. Mutates per-key state like the game.
     */
    public double pseudoseed(String key) {
        double v = state.containsKey(key) ? state.get(key) : pseudohash(key + seed);
        v = Math.abs(round13(luaMod1(2.134453429141 + v * 1.72431234)));
        state.put(key, v);
        return (v + hashedSeed) / 2.0;
    }

    /** The run's hashed seed ({@code pseudohash(seed)}). */
    public double hashedSeed() {
        return hashedSeed;
    }

    public String seed() {
        return seed;
    }

    /**
     * Layer 3 (NOT YET PORTED): Balatro does {@code math.randomseed(pseudoseed(key));
     * return math.random(n)} to pick an element/shuffle. Reproducing it bit-exactly
     * needs LuaJIT's Tausworthe PRNG (portable from Immolate/Balatro4J). Until then
     * the harness can compare the layer-1/2 doubles directly.
     */
    public int randomElementIndex(String key, int n) {
        throw new UnsupportedOperationException(
                "LuaJIT math.random layer not yet ported — see Immolate/Balatro4J");
    }

    /** Lua {@code x % 1} — always non-negative (Lua modulo follows the divisor's sign). */
    private static double luaMod1(double x) {
        double r = x % 1.0;
        return r < 0 ? r + 1.0 : r;
    }

    /** Lua {@code tonumber(string.format("%.13f", x))} — round to 13 decimals. */
    private static double round13(double x) {
        return Double.parseDouble(String.format(Locale.ROOT, "%.13f", x));
    }
}
