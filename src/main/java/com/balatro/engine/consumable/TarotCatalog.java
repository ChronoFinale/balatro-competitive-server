package com.balatro.engine.consumable;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A starter set of Tarot/Spectral consumables, as data — exercising the
 * consumable effect model: enhance selected cards, destroy selected cards, and
 * create new cards. Enhance/Destroy target cards the player selects (by id);
 * Create adds to the deck. (Rank/suit-converting Tarots — Strength, the suit
 * Tarots — land once Card rank/suit are mutable, a small follow-up.)
 */
public final class TarotCatalog {

    private TarotCatalog() {}

    private static final Map<String, Consumable> BY_KEY = new LinkedHashMap<>();

    static {
        put(Consumables.tarot("c_magician", "The Magician")
                .targets(2).enhance(CardMod.setEnhancement(Enhancement.LUCKY)).build());
        put(Consumables.tarot("c_empress", "The Empress")
                .targets(2).enhance(CardMod.setEnhancement(Enhancement.MULT)).build());
        put(Consumables.tarot("c_hierophant", "The Hierophant")
                .targets(2).enhance(CardMod.setEnhancement(Enhancement.BONUS)).build());
        put(Consumables.tarot("c_devil", "The Devil")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.GOLD)).build());
        put(Consumables.tarot("c_hanged_man", "The Hanged Man")
                .targets(2).destroySelected().build());
        put(Consumables.tarot("c_strength", "Strength")
                .targets(2).enhance(CardMod.incRank(1)).build());
        put(Consumables.tarot("c_star", "The Star")
                .targets(3).enhance(CardMod.setSuit(Suit.DIAMONDS)).build());
        put(Consumables.tarot("c_moon", "The Moon")
                .targets(3).enhance(CardMod.setSuit(Suit.CLUBS)).build());
        put(Consumables.tarot("c_sun", "The Sun")
                .targets(3).enhance(CardMod.setSuit(Suit.HEARTS)).build());
        put(Consumables.tarot("c_world", "The World")
                .targets(3).enhance(CardMod.setSuit(Suit.SPADES)).build());
        put(Consumables.tarot("c_lovers", "The Lovers")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.WILD)).build());
        put(Consumables.tarot("c_chariot", "The Chariot")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.STEEL)).build());
        put(Consumables.tarot("c_justice", "Justice")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.GLASS)).build());
        put(Consumables.tarot("c_tower", "The Tower")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.STONE)).build());
        put(Consumables.spectral("c_incantation", "Incantation")
                .createCards(4, Enhancement.BONUS).build());

        // Spectral seal/edition cards (each targets one held card).
        put(Consumables.spectral("c_talisman", "Talisman")
                .targets(1).enhance(CardMod.setSeal(Seal.GOLD)).build());
        put(Consumables.spectral("c_deja_vu", "Deja Vu")
                .targets(1).enhance(CardMod.setSeal(Seal.RED)).build());
        put(Consumables.spectral("c_trance", "Trance")
                .targets(1).enhance(CardMod.setSeal(Seal.BLUE)).build());
        put(Consumables.spectral("c_medium", "Medium")
                .targets(1).enhance(CardMod.setSeal(Seal.PURPLE)).build());
        put(Consumables.spectral("c_aura", "Aura")
                .targets(1).enhance(CardMod.setEdition(Edition.POLYCHROME)).build());
        put(Consumables.spectral("c_black_hole", "Black Hole")
                .levelAllHands().build());

        // Joker-edition consumables (build on the joker-edition scoring mechanic).
        put(Consumables.tarot("c_wheel_of_fortune", "The Wheel of Fortune")
                
                .jokerEdition(Edition.NONE, 4, 0, false).build());
        put(Consumables.spectral("c_ectoplasm", "Ectoplasm")
                .jokerEdition(Edition.NEGATIVE, 1, -1, false).build());
        put(Consumables.spectral("c_hex", "Hex")
                .jokerEdition(Edition.POLYCHROME, 1, 0, true).build());

        // Generative consumables: create cards/consumables/jokers and/or move money.
        put(Consumables.tarot("c_emperor", "The Emperor")
                .createTarots(2).build());
        put(Consumables.tarot("c_high_priestess", "The High Priestess")
                .createPlanets(2).build());
        put(Consumables.tarot("c_judgement", "Judgement")
                .createJoker().build());
        put(Consumables.tarot("c_hermit", "The Hermit")
                .doubleMoney(20).build());
        put(Consumables.tarot("c_temperance", "Temperance")
                .gainSellValue(50).build());

        // Generative spectrals.
        put(Consumables.spectral("c_the_soul", "The Soul")
                .createJoker("Legendary").build());
        put(Consumables.spectral("c_wraith", "The Wraith")
                .createJoker("Rare").setMoney(0).build());
        put(Consumables.spectral("c_immolate", "Immolate")
                .destroyInHand(5).gainMoney(20).build());
        put(Consumables.spectral("c_familiar", "Familiar")
                .destroyInHand(1).addFaceCards(3).build());
        put(Consumables.spectral("c_grim", "Grim")
                .destroyInHand(1).addAces(2).build());

        // Transform / copy consumables.
        put(Consumables.tarot("c_fool", "The Fool")
                .copyLastConsumable().build());
        put(Consumables.tarot("c_death", "Death")
                .targets(2).overwriteSelected().build());
        put(Consumables.spectral("c_sigil", "Sigil")
                .convertHandToSuit().build());
        put(Consumables.spectral("c_ouija", "Ouija")
                .convertHandToRank(-1).build());
        put(Consumables.spectral("c_cryptid", "Cryptid")
                .targets(1).copySelected(2).build());
        put(Consumables.spectral("c_ankh", "Ankh")
                .copyRandomJoker(true).build());

        // MP-exclusive planet: delevels the nemesis's highest hand (PLANET type keeps it out of the
        // single-player Tarot/Spectral pools; it's distributed only in multiplayer).
        put(Consumables.planet("c_asteroid", "Asteroid")
                .nemesisDelevel().build());
    }

    private static void put(Consumable c) {
        BY_KEY.put(c.key(), c);
    }

    public static Consumable get(String key) {
        return BY_KEY.get(key);
    }

    /** Every Tarot/Spectral key in the catalog — the surface the coverage net enumerates. */
    public static java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(BY_KEY.keySet());
    }

    /** Tarot keys eligible to appear in the main shop (Spectrals come from packs). */
    public static java.util.List<String> tarotKeys() {
        return BY_KEY.values().stream()
                .filter(c -> c.type() == ConsumableType.TAROT)
                .map(Consumable::key)
                .toList();
    }

    /**
     * Spectral keys eligible for a normal draw (packs / Spectral-creating jokers). The Soul and Black
     * Hole are EXCLUDED — exactly as BMP's get_current_pool does (common_events.lua:2022-2024) — because
     * they only surface via the dedicated soul roll, never as ordinary pool content.
     */
    public static java.util.List<String> spectralKeys() {
        return BY_KEY.values().stream()
                .filter(c -> c.type() == ConsumableType.SPECTRAL)
                .filter(c -> !c.key().equals("c_the_soul") && !c.key().equals("c_black_hole"))
                .map(Consumable::key)
                .toList();
    }
}
