package com.balatro.engine;

import com.balatro.engine.i18n.Loc;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Localization with value-templating: a second locale only supplies wording; the {@code ${field}} numbers
 * come from the same data, so they're single-sourced and can't desync per language.
 */
class LocTest {

    @Test void frenchTranslatesWordingButNumbersComeFromData() {
        // en + fr fill the SAME reqMult=4 from data into their own wording — the number is never in the json.
        assertThat(Loc.fill("bl_wall", Map.of("reqMult", 4))).isEqualTo("Very large blind (4x score)");
        assertThat(Loc.fill("fr", "bl_wall", Map.of("reqMult", 4))).isEqualTo("Très grande blinde (4× score)");

        assertThat(Loc.text("fr", "j_joker")).isEqualTo("+4 Mult");
        assertThat(Loc.text("fr", "d_blue")).isEqualTo("+1 main par manche");
    }

    @Test void untranslatedKeysFallBackToDefaultLocale() {
        // a key present in en but not fr.json resolves to the en text (never blank)
        assertThat(Loc.has("d_plasma")).isTrue();
        assertThat(Loc.text("fr", "d_plasma")).isEqualTo(Loc.text("d_plasma")).isNotEmpty();
    }
}
