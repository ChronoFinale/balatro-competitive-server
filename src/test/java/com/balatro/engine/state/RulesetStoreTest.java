package com.balatro.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Player-authored rulesets persist, reload, sit beside the curated catalog, and
 * are validated — in particular every joker named in the pool must be a known
 * joker, so a match can never reference one the server can't build.
 */
class RulesetStoreTest {

    private static final int[] BLINDS = {300, 800, 2000, 5000, 11000, 20000, 35000, 50000};

    private static Ruleset custom(String name, List<String> pool) {
        return new Ruleset(name, 4, 4, 3, 8, 1.0, 8, BLINDS, pool);
    }

    @Test
    void savesReloadsAndSitsBesideCurated(@TempDir Path dir) {
        new RulesetStore(dir).save(custom("My Format", List.of("j_joker", "j_hack")));

        RulesetStore reopened = new RulesetStore(dir);
        reopened.loadAll();
        assertThat(reopened.get("My Format")).isNotNull();
        assertThat(reopened.get("My Format").jokerPool()).containsExactly("j_joker", "j_hack");
        assertThat(reopened.get("Standard")).isNotNull(); // curated still resolvable
        assertThat(reopened.names()).startsWith("Standard", "Blitz", "Marathon"); // curated first
    }

    @Test
    void rejectsCuratedCollisionAndUnknownJokers(@TempDir Path dir) {
        RulesetStore store = new RulesetStore(dir);
        assertThatThrownBy(() -> store.save(custom("Standard", List.of("j_joker"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.save(custom("Bad Pool", List.of("j_not_a_real_joker"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyPoolDefaultsToBuiltins(@TempDir Path dir) {
        Ruleset r = new RulesetStore(dir).save(custom("Open", List.of()));
        assertThat(r.jokerPool()).isNotEmpty(); // normalized to the curated built-in set
    }
}
