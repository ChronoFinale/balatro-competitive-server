package com.balatromp.engine.consumable;

import com.balatromp.engine.card.CardMod;
import com.balatromp.engine.card.Enhancement;
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
        put(new Consumable("c_incantation", "Incantation", "Add 4 Bonus number cards to your deck",
                ConsumableType.SPECTRAL, 0, new Consumable.Create(4, Enhancement.BONUS)));
    }

    private static void put(Consumable c) {
        BY_KEY.put(c.key(), c);
    }

    public static Consumable get(String key) {
        return BY_KEY.get(key);
    }
}
