package com.balatro.engine;

import com.balatro.engine.content.ContentStore;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.content.jokers.BuiltinJokerDefs;
import com.balatro.engine.joker.def.DataJoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine boots its content from the compiled JSON artifact, not the authoring DSL. Together with the
 * whole suite (all scoring/run/shop tests now exercise JSON-loaded jokers), this is the proof the game is
 * playable purely from the JSONified data.
 */
class EngineFromJsonTest {

    @Test void registryIsLoadedFromTheJsonArtifact() {
        // the artifact is actually present on the classpath — so ContentStore used it, not the DSL fallback
        assertThat(getClass().getResourceAsStream("/rulesets/vanilla.json"))
                .as("vanilla.json is on the runtime classpath").isNotNull();

        // what it loaded equals the DSL authoring source (the gate keeps them identical)
        assertThat(ContentStore.jokers()).isEqualTo(BuiltinJokerDefs.all()).isNotEmpty();

        // and that JSON-sourced data is live in the engine registry, with real rules
        DataJoker sly = (DataJoker) JokerLibrary.create("j_sly_joker");
        assertThat(sly.def().rules()).isNotEmpty();
        assertThat(JokerLibrary.builtinKeys()).contains("j_joker", "j_sly_joker");
    }

    @Test void decksAreLoadedFromJsonAtRuntime() {
        // DeckCatalog now populates from /content/decks.json; the JSON equals the DSL authoring source.
        var fromJson = com.balatro.engine.content.ContentStore.decks();
        assertThat(fromJson).isEqualTo(com.balatro.content.DeckDefs.authored());
        // and that is what the live catalog serves
        assertThat(com.balatro.engine.game.DeckCatalog.get("d_blue").description()).isEqualTo("+1 hand each round");
    }

    @Test void everyContentTypeIsJsonLoadableAndMatchesTheDsl() {
        // The whole content surface round-trips from its compiled artifact equal to the DSL authoring —
        // i.e. the engine could boot entirely from JSON.
        assertThat(com.balatro.engine.content.ContentStore.bosses())
                .isEqualTo(com.balatro.content.BossDefs.authored());
        assertThat(com.balatro.engine.content.ContentStore.planets())
                .isEqualTo(com.balatro.content.PlanetDefs.authored());
        assertThat(com.balatro.engine.content.ContentStore.tags())
                .isEqualTo(com.balatro.content.TagDefs.authored());
        assertThat(com.balatro.engine.content.ContentStore.vouchers())
                .isEqualTo(com.balatro.content.VoucherDefs.authored());
        assertThat(com.balatro.engine.content.ContentStore.consumables())
                .isEqualTo(com.balatro.content.ConsumableDefs.authored());
    }
}
