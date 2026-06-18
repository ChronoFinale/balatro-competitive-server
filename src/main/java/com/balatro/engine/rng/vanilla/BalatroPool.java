package com.balatro.engine.rng.vanilla;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bit-exact port of Balatro's pool draw under "The Order" (Stage 3, Layer B). Mirrors {@code create_card}
 * (common_events.lua:2113-2119): {@code pseudorandom_element(pool, pseudoseed(pool_key))} — which for the
 * fixed-length array pool that {@code get_current_pool} returns is {@code pool[math.random(#pool)]} — with
 * {@code UNAVAILABLE} holes skipped by re-drawing. Under The Order the resample advances the SAME pool_key
 * stream (TheOrder.toml:88-100), so this is just repeated {@link BalatroPrng#pseudorandom} floored.
 *
 * <p>The pool MUST be the fixed-length array with {@code "UNAVAILABLE"} placeholders in registration order
 * (owned/ineligible entries are holes, not removed) — that's what makes the index draw match Balatro.
 * Verified against LuaJIT vectors (pool_select) in BalatroPrngTest.
 */
public final class BalatroPool {

    private BalatroPool() {}

    public static final String UNAVAILABLE = "UNAVAILABLE";

    /**
     * Port of {@code get_current_pool}'s cull (common_events.lua:1976-2050): walk the master pool in
     * registration order and emit, for each entry, either the key (if {@code available}) or the literal
     * {@code "UNAVAILABLE"} placeholder — NEVER compacting, so every surviving entry keeps its index.
     * If everything is culled the pool collapses to a single {@code emptyFallback} (the per-type default:
     * j_joker / c_strength / c_pluto / c_incantation / v_blank / tag_handy). The result is the
     * fixed-length array {@link #draw} expects.
     *
     * @param orderedPool the master pool in Balatro's registration order (the bit-exact ordering)
     * @param available   the cull predicate (e.g. {@code k -> !owned(k) || showman}); Soul/Black-Hole,
     *                    planet softlock, voucher used/requires/in-shop are folded in by the caller
     * @param emptyFallback the single key used when the whole pool is unavailable
     */
    public static List<String> cull(List<String> orderedPool, Predicate<String> available, String emptyFallback) {
        List<String> out = new ArrayList<>(orderedPool.size());
        int size = 0;
        for (String key : orderedPool) {
            if (available.test(key)) {
                out.add(key);
                size++;
            } else {
                out.add(UNAVAILABLE);
            }
        }
        if (size == 0) {
            out.clear();
            out.add(emptyFallback);
        }
        return out;
    }

    /**
     * VANILLA pool draw (the_order OFF): index = floor(pseudorandom(poolKey)*n); on an {@code UNAVAILABLE}
     * hole, resample with a NEW key {@code poolKey + "_resample" + it} (it=2,3,…) — Balatro's create_card
     * (common_events.lua:2114-2119), distinct from The Order's advance-same-key resample. Returns the chosen
     * INDEX so the caller can mark it UNAVAILABLE for within-shop dedup (used_jokers). {@code pool} is the
     * fixed-length array WITH holes (e.g. get_current_pool's output).
     */
    public static int pickIndexVanilla(BalatroPrng prng, String poolKey, List<String> pool) {
        int n = pool.size();
        int idx = (int) Math.floor(prng.pseudorandom(poolKey) * n);
        int it = 1;
        while (UNAVAILABLE.equals(pool.get(idx)) && it <= 1000) {
            it++;
            idx = (int) Math.floor(prng.pseudorandom(poolKey + "_resample" + it) * n);
        }
        return idx;
    }

    /**
     * Draw one key from {@code pool} for {@code poolKey}, advancing {@code prng}. Re-draws past
     * {@code UNAVAILABLE} (the order resample). Returns "j_joker" if the pool is all holes (the
     * get_current_pool empty-pool fallback for jokers).
     */
    public static String draw(BalatroPrng prng, String poolKey, List<String> pool) {
        int n = pool.size();
        for (int it = 0; it <= 1000; it++) {
            // pseudorandom(poolKey) = randomseed(pseudoseed(poolKey)); random()  -> [0,1)
            // math.random(#pool) == floor(that * n) + 1 (1-based); pool.get(floor(that*n)) is the same element.
            int idx = (int) Math.floor(prng.pseudorandom(poolKey) * n);
            String key = pool.get(idx);
            if (!UNAVAILABLE.equals(key)) {
                return key;
            }
        }
        return "j_joker";
    }
}
