package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.rng.vanilla.BalatroPool;
import com.balatro.engine.rng.vanilla.BalatroPrng;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The get_current_pool cull (fixed-length array with UNAVAILABLE holes + empty-pool fallback) and its
 * composition with the oracle-verified {@link BalatroPool#draw} selector. The cull is deterministic
 * logic (no RNG), so these checks are structural/behavioural; the draw's bit-exactness is proven
 * separately against LuaJIT in BalatroPrngTest.
 */
class BalatroPoolTest {

    private static final List<String> MASTER = List.of("j_a", "j_b", "j_c", "j_d", "j_e");

    @Test
    void cullKeepsPositionsAndMarksHoles() {
        Set<String> owned = Set.of("j_b", "j_d");
        List<String> pool = BalatroPool.cull(MASTER, k -> !owned.contains(k), "j_joker");
        assertThat(pool).containsExactly("j_a", "UNAVAILABLE", "j_c", "UNAVAILABLE", "j_e");
    }

    @Test
    void cullCollapsesToFallbackWhenEverythingUnavailable() {
        List<String> pool = BalatroPool.cull(MASTER, k -> false, "c_strength");
        assertThat(pool).containsExactly("c_strength"); // get_current_pool's empty-pool fallback
    }

    @Test
    void drawOverACulledPoolSkipsHolesAndNeverReturnsOwned() {
        Set<String> owned = Set.of("j_b", "j_d");
        List<String> pool = BalatroPool.cull(MASTER, k -> !owned.contains(k), "j_joker");
        BalatroPrng prng = new BalatroPrng("POOLDRAW");
        for (int i = 0; i < 200; i++) {
            String picked = BalatroPool.draw(prng, "Joker1", pool);
            assertThat(picked).isNotEqualTo("UNAVAILABLE");
            assertThat(owned).doesNotContain(picked); // holes are skipped, owned never offered
            assertThat(MASTER).contains(picked);
        }
    }

    @Test
    void drawReturnsFallbackWhenPoolIsFullyOwned() {
        List<String> pool = BalatroPool.cull(MASTER, k -> false, "j_joker"); // -> ["j_joker"]
        BalatroPrng prng = new BalatroPrng("ALLOWNED");
        assertThat(BalatroPool.draw(prng, "Joker1", pool)).isEqualTo("j_joker");
    }
}
