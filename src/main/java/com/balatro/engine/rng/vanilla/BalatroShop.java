package com.balatro.engine.rng.vanilla;

/**
 * Bit-exact ports of the per-shop-card rate polls that wrap pool selection (Stage 3, Layer B):
 * {@code poll_edition} (misc_functions.lua:2055) and the joker rarity tier (get_current_pool:1969-1970).
 * Both reduce to thresholding a single {@link BalatroPrng#pseudorandom} value, so they inherit the
 * proven bit-exact PRNG. Returned strings use Balatro's own terms. Verified against LuaJIT vectors
 * (rate_polls) in BalatroPrngTest.
 */
public final class BalatroShop {

    private BalatroShop() {}

    /**
     * poll_edition with the run defaults (edition_rate = 1 per game.lua:1900, mod = 1, negatives allowed).
     * Bands: negative &gt; 0.997, polychrome &gt; 0.994, holo &gt; 0.98, foil &gt; 0.96, else none.
     */
    /** Shop-joker edition: mod 1, negatives allowed. */
    public static String pollEdition(BalatroPrng prng, String key) {
        return editionFor(prng.pseudorandom(key), 1, false);
    }

    /** Standard-card edition: {@code poll_edition('standard_edition'+append, mod=2, no_neg=true)} (card.lua:1760). */
    public static String pollStandardEdition(BalatroPrng prng, String append) {
        return editionFor(prng.pseudorandom("standard_edition" + append), 2, true);
    }

    /** Convenience for the common mod=1, negatives-allowed case. */
    public static String editionFor(double p) {
        return editionFor(p, 1, false);
    }

    /**
     * The edition a poll value maps to (split out so every band is directly testable). Bands (edition_rate
     * = 1, game.lua:1900): negative {@code 0.003*mod} (skipped if {@code noNeg}), polychrome {@code 0.006*mod},
     * holo {@code 0.02*mod}, foil {@code 0.04*mod}, measured from the top — see poll_edition.
     */
    public static String editionFor(double p, double mod, boolean noNeg) {
        if (!noNeg && p > 1 - 0.003 * mod) {
            return "negative";
        } else if (p > 1 - 0.006 * mod) {
            return "polychrome";
        } else if (p > 1 - 0.02 * mod) {
            return "holo";
        } else if (p > 1 - 0.04 * mod) {
            return "foil";
        }
        return "none";
    }

    /** Joker rarity tier from {@code pseudorandom("rarity"+append)}: &gt;0.95 Rare(3), &gt;0.7 Uncommon(2), else Common(1). */
    public static int rarityTier(BalatroPrng prng, String append) {
        double r = prng.pseudorandom("rarity" + append);
        return r > 0.95 ? 3 : (r > 0.7 ? 2 : 1);
    }

    /**
     * Shop slot type ('cdt' poll, UI_definitions.lua:765-777): {@code pseudorandom("cdt"+append) *
     * total_rate}, cumulative over the default rates Joker 20 / Tarot 4 / Planet 4 / Base 0 / Spectral 0
     * (game.lua:1901-1905; total 28). Bands are half-open low, closed high ({@code > check && <= check+val}).
     */
    public static String slotType(BalatroPrng prng, String append) {
        double polled = prng.pseudorandom("cdt" + append) * 28.0;
        if (polled > 0 && polled <= 20) {
            return "Joker";
        } else if (polled > 20 && polled <= 24) {
            return "Tarot";
        } else if (polled > 24 && polled <= 28) {
            return "Planet";
        }
        return "Joker"; // polled == 0 (measure-zero) fallback
    }

    /**
     * Standard-card seal (card.lua:1763-1771): seal_rate 10 → 20% chance of a seal ({@code stdseal > 0.8});
     * if so, equal Red/Blue/Gold/Purple from a second {@code stdsealtype} poll. Returns "none" otherwise.
     */
    public static String standardSeal(BalatroPrng prng, String append) {
        double sealPoll = prng.pseudorandom("stdseal" + append);
        if (sealPoll > 1 - 0.02 * 10) {
            double t = prng.pseudorandom("stdsealtype" + append);
            if (t > 0.75) {
                return "Red";
            } else if (t > 0.5) {
                return "Blue";
            } else if (t > 0.25) {
                return "Gold";
            }
            return "Purple";
        }
        return "none";
    }
}
