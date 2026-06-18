package com.balatro.engine.rng.vanilla;

/**
 * The two ranked-mode ("The Order") transforms applied on top of vanilla Balatro's PRNG/pool flow, so
 * the oracle can model a BMP ranked run, not just vanilla (Stage 3, Layer C). Both are documented from
 * {@code lovely/TheOrder.toml}; the rest of The Order (resample-advance) already lives in
 * {@link BalatroPool}.
 *
 * <ol>
 *   <li><b>Seed prefix</b> — ranked prepends {@code "*"} to the seed string (TheOrder.toml:21-38). It's
 *       a visible "this isn't a vanilla seed" marker, but because the seed feeds {@code pseudohash}, it
 *       shifts {@code hashed_seed} and therefore the ENTIRE value sequence away from vanilla.</li>
 *   <li><b>Ante-free keys</b> — under The Order {@code G.GAME.round_resets.ante} is forced to 0 while
 *       keys are built, so game-long keys carry no ante (one sequence per game). Encoded here as a key
 *       helper; callers that build pool/poll keys use the ante-free form.</li>
 * </ol>
 */
public final class BalatroOrder {

    private BalatroOrder() {}

    /** Prepend the ranked {@code "*"} marker to a seed (idempotent). */
    public static String rankedSeed(String seed) {
        return seed.startsWith("*") ? seed : "*" + seed;
    }

    /** A {@link BalatroPrng} for a ranked run on {@code seed} (i.e. seeded with {@link #rankedSeed}). */
    public static BalatroPrng rankedPrng(String seed) {
        return new BalatroPrng(rankedSeed(seed));
    }

    /**
     * The ante stripped (0) from a game-long key under The Order — i.e. {@code base + "0"} where vanilla
     * would append the real ante. Mirrors {@code MP.ante_based()} returning 0.
     */
    public static String anteFreeKey(String base) {
        return base + "0";
    }
}
