package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.rng.Seeds;
import com.balatro.engine.rng.vanilla.BalatroPrng;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Our match seeds live in Balatro's seed space (8 chars from {1-9, A-N, P-Z}), so a seed the server issues
 * is a valid, typeable Balatro seed — and since the seed→RNG-state derivation is bit-identical (verified
 * elsewhere), a match can be reproduced/verified in real Balatro from its seed.
 */
class SeedsTest {

    @Test
    void alphabetIsBalatrosExcludingZeroAndO() {
        assertThat(Seeds.ALPHABET).hasSize(34)
                .doesNotContain("0").doesNotContain("O")
                .contains("1").contains("9").contains("A").contains("N").contains("P").contains("Z");
    }

    @Test
    void generatedSeedsAreAlwaysValidBalatroSeeds() {
        Random rng = new Random(42); // fixed for a deterministic test
        for (int i = 0; i < 5000; i++) {
            String seed = Seeds.random(rng);
            assertThat(seed).hasSize(8);
            assertThat(Seeds.isValid(seed)).as("valid Balatro seed: %s", seed).isTrue();
        }
    }

    @Test
    void isValidRejectsMalformedSeeds() {
        assertThat(Seeds.isValid("ABCD1234")).isTrue();
        assertThat(Seeds.isValid("ABCD123")).as("wrong length").isFalse();
        assertThat(Seeds.isValid("ABCD12O4")).as("contains O").isFalse();
        assertThat(Seeds.isValid("ABCD1204")).as("contains 0").isFalse();
        assertThat(Seeds.isValid(null)).isFalse();
    }

    @Test
    void aGeneratedSeedDrivesTheVerifiedBalatroDerivation() {
        // A server-issued seed feeds the bit-exact PRNG: same seed -> same hashed_seed, deterministically.
        String seed = Seeds.random(new Random(7));
        assertThat(Seeds.isValid(seed)).isTrue();
        assertThat(new BalatroPrng(seed).hashedSeed())
                .isEqualTo(new BalatroPrng(seed).hashedSeed()); // deterministic from the seed string
    }
}
