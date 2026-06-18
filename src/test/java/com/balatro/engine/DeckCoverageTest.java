package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.game.Shop;
import org.junit.jupiter.api.Test;

/** The previously-hardcoded / missing decks are now pure data: Ghost, Plasma, Anaglyph. */
class DeckCoverageTest {

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
