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
        put(Consumables.tarot("c_magician", "The Magician").desc("Enhance up to 2 selected cards to Lucky")
                .targets(2).enhance(CardMod.setEnhancement(Enhancement.LUCKY)).build());
        put(Consumables.tarot("c_empress", "The Empress").desc("Enhance up to 2 selected cards to Mult")
                .targets(2).enhance(CardMod.setEnhancement(Enhancement.MULT)).build());
        put(Consumables.tarot("c_hierophant", "The Hierophant").desc("Enhance up to 2 selected cards to Bonus")
                .targets(2).enhance(CardMod.setEnhancement(Enhancement.BONUS)).build());
        put(Consumables.tarot("c_devil", "The Devil").desc("Enhance 1 selected card to Gold")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.GOLD)).build());
        put(Consumables.tarot("c_hanged_man", "The Hanged Man").desc("Destroy up to 2 selected cards")
                .targets(2).destroySelected().build());
        put(Consumables.tarot("c_strength", "Strength").desc("Increase rank of up to 2 selected cards by 1")
                .targets(2).enhance(CardMod.incRank(1)).build());
        put(Consumables.tarot("c_star", "The Star").desc("Convert up to 3 selected cards to Diamonds")
                .targets(3).enhance(CardMod.setSuit(Suit.DIAMONDS)).build());
        put(Consumables.tarot("c_moon", "The Moon").desc("Convert up to 3 selected cards to Clubs")
                .targets(3).enhance(CardMod.setSuit(Suit.CLUBS)).build());
        put(Consumables.tarot("c_sun", "The Sun").desc("Convert up to 3 selected cards to Hearts")
                .targets(3).enhance(CardMod.setSuit(Suit.HEARTS)).build());
        put(Consumables.tarot("c_world", "The World").desc("Convert up to 3 selected cards to Spades")
                .targets(3).enhance(CardMod.setSuit(Suit.SPADES)).build());
        put(Consumables.tarot("c_lovers", "The Lovers").desc("Enhance 1 selected card to Wild (counts as every suit)")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.WILD)).build());
        put(Consumables.tarot("c_chariot", "The Chariot").desc("Enhance 1 selected card to Steel")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.STEEL)).build());
        put(Consumables.tarot("c_justice", "Justice").desc("Enhance 1 selected card to Glass")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.GLASS)).build());
        put(Consumables.tarot("c_tower", "The Tower").desc("Enhance 1 selected card to Stone")
                .targets(1).enhance(CardMod.setEnhancement(Enhancement.STONE)).build());
        put(Consumables.spectral("c_incantation", "Incantation").desc("Add 4 Bonus number cards to your deck")
                .createCards(4, Enhancement.BONUS).build());

        // Spectral seal/edition cards (each targets one held card).
        put(Consumables.spectral("c_talisman", "Talisman").desc("Add a Gold Seal to 1 selected card")
                .targets(1).enhance(CardMod.setSeal(Seal.GOLD)).build());
        put(Consumables.spectral("c_deja_vu", "Deja Vu").desc("Add a Red Seal to 1 selected card")
                .targets(1).enhance(CardMod.setSeal(Seal.RED)).build());
        put(Consumables.spectral("c_trance", "Trance").desc("Add a Blue Seal to 1 selected card")
                .targets(1).enhance(CardMod.setSeal(Seal.BLUE)).build());
        put(Consumables.spectral("c_medium", "Medium").desc("Add a Purple Seal to 1 selected card")
                .targets(1).enhance(CardMod.setSeal(Seal.PURPLE)).build());
        put(Consumables.spectral("c_aura", "Aura").desc("Add Polychrome to 1 selected card in hand")
                .targets(1).enhance(CardMod.setEdition(Edition.POLYCHROME)).build());
        put(Consumables.spectral("c_black_hole", "Black Hole").desc("Upgrade every poker hand by 1 level")
                .levelAllHands().build());

        // Joker-edition consumables (build on the joker-edition scoring mechanic).
        put(Consumables.tarot("c_wheel_of_fortune", "The Wheel of Fortune")
                .desc("1 in 4 chance to add Foil, Holographic, or Polychrome to a random Joker")
                .jokerEdition(Edition.NONE, 4, 0, false).build());
        put(Consumables.spectral("c_ectoplasm", "Ectoplasm").desc("Add Negative to a random Joker, -1 hand size")
                .jokerEdition(Edition.NEGATIVE, 1, -1, false).build());
        put(Consumables.spectral("c_hex", "Hex").desc("Add Polychrome to a random Joker, destroy all other Jokers")
                .jokerEdition(Edition.POLYCHROME, 1, 0, true).build());

        // Generative consumables: create cards/consumables/jokers and/or move money.
        put(Consumables.tarot("c_emperor", "The Emperor").desc("Create up to 2 random Tarot cards")
                .createTarots(2).build());
        put(Consumables.tarot("c_high_priestess", "The High Priestess").desc("Create up to 2 random Planet cards")
                .createPlanets(2).build());
        put(Consumables.tarot("c_judgement", "Judgement").desc("Create a random Joker")
                .createJoker().build());
        put(Consumables.tarot("c_hermit", "The Hermit").desc("Double your money (max gain of $20)")
                .doubleMoney(20).build());
        put(Consumables.tarot("c_temperance", "Temperance").desc("Gain the total sell value of all Jokers (max $50)")
                .gainSellValue(50).build());

        // Generative spectrals.
        put(Consumables.spectral("c_the_soul", "The Soul").desc("Create a random Legendary Joker")
                .createJoker("Legendary").build());
        put(Consumables.spectral("c_wraith", "The Wraith").desc("Create a random Rare Joker, set money to $0")
                .createJoker("Rare").setMoney(0).build());
        put(Consumables.spectral("c_immolate", "Immolate").desc("Destroy 5 random cards in hand, gain $20")
                .destroyInHand(5).gainMoney(20).build());
        put(Consumables.spectral("c_familiar", "Familiar").desc("Destroy 1 random card, add 3 random Enhanced face cards")
                .destroyInHand(1).addFaceCards(3).build());
        put(Consumables.spectral("c_grim", "Grim").desc("Destroy 1 random card, add 2 random Enhanced Aces")
                .destroyInHand(1).addAces(2).build());

        // Transform / copy consumables.
        put(Consumables.tarot("c_fool", "The Fool").desc("Create a copy of the last Tarot or Planet used this run")
                .copyLastConsumable().build());
        put(Consumables.tarot("c_death", "Death").desc("Select 2 cards: the left card becomes a copy of the right")
                .targets(2).overwriteSelected().build());
        put(Consumables.spectral("c_sigil", "Sigil").desc("Convert all cards in hand to a single random suit")
                .convertHandToSuit().build());
        put(Consumables.spectral("c_ouija", "Ouija").desc("Convert all cards in hand to a single random rank, -1 hand size")
                .convertHandToRank(-1).build());
        put(Consumables.spectral("c_cryptid", "Cryptid").desc("Create 2 copies of 1 selected card")
                .targets(1).copySelected(2).build());
        put(Consumables.spectral("c_ankh", "Ankh").desc("Copy a random Joker, destroy all other Jokers")
                .copyRandomJoker(true).build());

        // MP-exclusive planet: delevels the nemesis's highest hand (PLANET type keeps it out of the
        // single-player Tarot/Spectral pools; it's distributed only in multiplayer).
        put(Consumables.planet("c_asteroid", "Asteroid").desc("Delevel your nemesis's highest-level poker hand")
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
