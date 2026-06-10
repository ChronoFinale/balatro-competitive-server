package com.balatromp.engine.consumable;

import com.balatromp.engine.card.CardMod;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
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
        put(new Consumable("c_magician", "The Magician", "Enhance up to 2 selected cards to Lucky",
                ConsumableType.TAROT, 2, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.LUCKY))));
        put(new Consumable("c_empress", "The Empress", "Enhance up to 2 selected cards to Mult",
                ConsumableType.TAROT, 2, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.MULT))));
        put(new Consumable("c_hierophant", "The Hierophant", "Enhance up to 2 selected cards to Bonus",
                ConsumableType.TAROT, 2, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.BONUS))));
        put(new Consumable("c_devil", "The Devil", "Enhance 1 selected card to Gold",
                ConsumableType.TAROT, 1, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.GOLD))));
        put(new Consumable("c_hanged_man", "The Hanged Man", "Destroy up to 2 selected cards",
                ConsumableType.TAROT, 2, new Consumable.Destroy()));
        put(new Consumable("c_strength", "Strength", "Increase rank of up to 2 selected cards by 1",
                ConsumableType.TAROT, 2, new Consumable.Enhance(CardMod.incRank(1))));
        put(new Consumable("c_star", "The Star", "Convert up to 3 selected cards to Diamonds",
                ConsumableType.TAROT, 3, new Consumable.Enhance(CardMod.setSuit(Suit.DIAMONDS))));
        put(new Consumable("c_moon", "The Moon", "Convert up to 3 selected cards to Clubs",
                ConsumableType.TAROT, 3, new Consumable.Enhance(CardMod.setSuit(Suit.CLUBS))));
        put(new Consumable("c_sun", "The Sun", "Convert up to 3 selected cards to Hearts",
                ConsumableType.TAROT, 3, new Consumable.Enhance(CardMod.setSuit(Suit.HEARTS))));
        put(new Consumable("c_world", "The World", "Convert up to 3 selected cards to Spades",
                ConsumableType.TAROT, 3, new Consumable.Enhance(CardMod.setSuit(Suit.SPADES))));
        put(new Consumable("c_lovers", "The Lovers", "Enhance 1 selected card to Wild (counts as every suit)",
                ConsumableType.TAROT, 1, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.WILD))));
        put(new Consumable("c_chariot", "The Chariot", "Enhance 1 selected card to Steel",
                ConsumableType.TAROT, 1, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.STEEL))));
        put(new Consumable("c_justice", "Justice", "Enhance 1 selected card to Glass",
                ConsumableType.TAROT, 1, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.GLASS))));
        put(new Consumable("c_tower", "The Tower", "Enhance 1 selected card to Stone",
                ConsumableType.TAROT, 1, new Consumable.Enhance(CardMod.setEnhancement(Enhancement.STONE))));
        put(new Consumable("c_incantation", "Incantation", "Add 4 Bonus number cards to your deck",
                ConsumableType.SPECTRAL, 0, new Consumable.Create(4, Enhancement.BONUS)));

        // Spectral seal/edition cards (each targets one held card).
        put(new Consumable("c_talisman", "Talisman", "Add a Gold Seal to 1 selected card",
                ConsumableType.SPECTRAL, 1, new Consumable.Enhance(CardMod.setSeal(Seal.GOLD))));
        put(new Consumable("c_deja_vu", "Deja Vu", "Add a Red Seal to 1 selected card",
                ConsumableType.SPECTRAL, 1, new Consumable.Enhance(CardMod.setSeal(Seal.RED))));
        put(new Consumable("c_trance", "Trance", "Add a Blue Seal to 1 selected card",
                ConsumableType.SPECTRAL, 1, new Consumable.Enhance(CardMod.setSeal(Seal.BLUE))));
        put(new Consumable("c_medium", "Medium", "Add a Purple Seal to 1 selected card",
                ConsumableType.SPECTRAL, 1, new Consumable.Enhance(CardMod.setSeal(Seal.PURPLE))));
        put(new Consumable("c_aura", "Aura", "Add Polychrome to 1 selected card in hand",
                ConsumableType.SPECTRAL, 1, new Consumable.Enhance(CardMod.setEdition(Edition.POLYCHROME))));
        put(new Consumable("c_black_hole", "Black Hole", "Upgrade every poker hand by 1 level",
                ConsumableType.SPECTRAL, 0, new Consumable.LevelAllHands()));
    }

    private static void put(Consumable c) {
        BY_KEY.put(c.key(), c);
    }

    public static Consumable get(String key) {
        return BY_KEY.get(key);
    }

    /** Tarot keys eligible to appear in the main shop (Spectrals come from packs). */
    public static java.util.List<String> tarotKeys() {
        return BY_KEY.values().stream()
                .filter(c -> c.type() == ConsumableType.TAROT)
                .map(Consumable::key)
                .toList();
    }

    /** Spectral keys (from packs / Spectral-creating jokers). */
    public static java.util.List<String> spectralKeys() {
        return BY_KEY.values().stream()
                .filter(c -> c.type() == ConsumableType.SPECTRAL)
                .map(Consumable::key)
                .toList();
    }
}
