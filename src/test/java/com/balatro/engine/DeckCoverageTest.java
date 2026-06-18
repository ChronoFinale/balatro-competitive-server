package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.game.Shop;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The previously-hardcoded / missing decks are now pure data: Ghost, Plasma, Anaglyph. */
class DeckCoverageTest {

    /** The only deck that legitimately has no effect (the plain 52-card baseline). */
    private static final Set<String> BASELINE = Set.of("d_base");

    /**
     * The safety net (sibling of voucher/joker/tag/consumable/boss coverage): every deck but the Base
     * Deck must change the run — a deck that declares only {@code .desc(...)} and forgets its effect is
     * a silent Base Deck clone, the Boss Tag no-op trap.
     */
    @Test
    void everyDeckExceptBaseChangesTheRun() {
        List<String> inert = new ArrayList<>();
        for (String key : DeckCatalog.keys()) {
            if (BASELINE.contains(key)) continue;
            if (!DeckCatalog.get(key).hasEffect()) inert.add(key);
        }
        assertThat(inert)
                .as("decks that do nothing but aren't the Base Deck — wire their declared effect")
                .isEmpty();
    }

    @Test
    void theBaselineIsTrulyInert() {
        // Keeps BASELINE honest: if Base Deck ever gains an effect, drop it from the allow-list.
        assertThat(DeckCatalog.get("d_base").hasEffect())
                .as("d_base now has an effect — remove it from BASELINE").isFalse();
    }

    @Test
    void ghostDeckHasASpectralShopRateAndStartsWithHex() {
        DeckCatalog.DeckType ghost = DeckCatalog.get("d_ghost");
        assertThat(ghost.key()).isEqualTo("d_ghost"); // exists (was missing entirely)
        assertThat(ghost.spectralRate()).isEqualTo(2);
        assertThat(ghost.startingConsumables()).containsExactly("c_hex");
        // and the shop slot roll yields SPECTRAL in the tail when the rate is on
        assertThat(Shop.rollSlotType(0.99, 2)).isEqualTo(Shop.Kind.SPECTRAL);
        assertThat(Shop.rollSlotType(0.99, 0)).isEqualTo(Shop.Kind.PLANET); // never spectral without Ghost
    }

    @Test
    void plasmaAndAnaglyphAreDataNotHardcoded() {
        DeckCatalog.DeckType plasma = DeckCatalog.get("d_plasma");
        assertThat(plasma.balanceChipsMult()).isTrue();
        assertThat(plasma.blindSizeMult()).isEqualTo(2);

        DeckCatalog.DeckType anaglyph = DeckCatalog.get("d_anaglyph");
        assertThat(anaglyph.onBossDefeatTags()).containsExactly("tag_double");
    }
}
