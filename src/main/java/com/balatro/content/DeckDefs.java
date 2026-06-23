package com.balatro.content;

import com.balatro.engine.game.DeckCatalog.DeckType;
import com.balatro.engine.game.Decks;
import java.util.List;

/**
 * The deck CONTENT — authored in the {@link Decks} DSL, compiled to {@code content/decks.json}. The runtime
 * ({@link com.balatro.engine.game.DeckCatalog}) loads from that JSON; this is the source it's generated from.
 * Find the decks here, the way the jokers live in {@code content/jokers}.
 */
public final class DeckDefs {

    private DeckDefs() {}

    public static List<DeckType> authored() {
        return List.of(
                Decks.of("d_base", "Base Deck").build(),
                Decks.of("d_red", "Red Deck").discards(1).build(),
                Decks.of("d_blue", "Blue Deck").hands(1).build(),
                Decks.of("d_yellow", "Yellow Deck").money(10).build(),
                Decks.of("d_black", "Black Deck").jokerSlots(1).hands(-1).build(),
                Decks.of("d_green", "Green Deck").greenEconomy().build(),
                Decks.of("d_painted", "Painted Deck").handSize(2).jokerSlots(-1).build(),
                Decks.of("d_abandoned", "Abandoned Deck").noFaces().build(),
                Decks.of("d_checkered", "Checkered Deck").checkered().build(),
                Decks.of("d_erratic", "Erratic Deck").erratic().build(),
                Decks.of("d_magic", "Magic Deck")
                        .startsWithVouchers("v_crystal_ball").startsWith("c_fool", "c_fool").build(),
                Decks.of("d_nebula", "Nebula Deck")
                        .startsWithVouchers("v_telescope").consumableSlots(-1).build(),
                Decks.of("d_zodiac", "Zodiac Deck")
                        .startsWithVouchers("v_tarot_merchant", "v_planet_merchant", "v_overstock").build(),
                Decks.of("d_ghost", "Ghost Deck")
                        .spectralRate(2).startsWith("c_hex").build(),
                Decks.of("d_anaglyph", "Anaglyph Deck")
                        .tagAfterBoss("tag_double").build(),
                Decks.of("d_plasma", "Plasma Deck")
                        .balancesChipsAndMult().blindSizeMult(2).build());
    }
}
