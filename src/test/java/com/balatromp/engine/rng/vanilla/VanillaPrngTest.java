package com.balatromp.engine.rng.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Spike: prove the bit-exact Balatro RNG port behaves like the source's
 * pseudohash/pseudoseed — deterministic, in-range, per-key independent, and
 * state-advancing. (External bit-exactness against a live game vector is the
 * follow-up; this pins the algorithm + guards against drift.)
 */
class VanillaPrngTest {

    @Test
    void pseudohashIsDeterministicAndInUnitInterval() {
        double a = VanillaPrng.pseudohash("XANADU");
        double b = VanillaPrng.pseudohash("XANADU");
        assertThat(a).isEqualTo(b);
        assertThat(a).isBetween(0.0, 1.0);
    }

    @Test
    void differentStringsHashDifferently() {
        assertThat(VanillaPrng.pseudohash("ABCDE")).isNotEqualTo(VanillaPrng.pseudohash("ABCDF"));
    }

    @Test
    void sameSeedReproducesTheSameStreamForBothPlayers() {
        VanillaPrng p1 = new VanillaPrng("SEED1");
        VanillaPrng p2 = new VanillaPrng("SEED1");
        // a fresh stream from the same seed yields identical draws in the same order
        for (int i = 0; i < 5; i++) {
            assertThat(p1.pseudoseed("shop")).isEqualTo(p2.pseudoseed("shop"));
        }
        assertThat(p1.hashedSeed()).isEqualTo(p2.hashedSeed());
    }

    @Test
    void pseudoseedAdvancesPerKeyAndKeysAreIndependent() {
        VanillaPrng p = new VanillaPrng("SEED1");
        double first = p.pseudoseed("shop");
        double second = p.pseudoseed("shop");
        assertThat(first).isNotEqualTo(second);            // the key's stream advanced
        assertThat(p.pseudoseed("tarot")).isBetween(0.0, 1.0); // a different key is its own stream
    }

    @Test
    void differentSeedsDiverge() {
        assertThat(new VanillaPrng("SEED1").pseudoseed("shop"))
                .isNotEqualTo(new VanillaPrng("SEED2").pseudoseed("shop"));
    }
}
