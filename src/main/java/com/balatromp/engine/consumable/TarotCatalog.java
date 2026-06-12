package com.balatromp.engine.consumable;

import com.balatromp.engine.card.CardMod;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.joker.def.CreateSpec;
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

        // Joker-edition consumables (build on the joker-edition scoring mechanic).
        put(new Consumable("c_wheel_of_fortune", "The Wheel of Fortune",
                "1 in 4 chance to add Foil, Holographic, or Polychrome to a random Joker",
                ConsumableType.TAROT, 0, new Consumable.JokerEdition(Edition.NONE, 4, 0, false)));
        put(new Consumable("c_ectoplasm", "Ectoplasm", "Add Negative to a random Joker, -1 hand size",
                ConsumableType.SPECTRAL, 0, new Consumable.JokerEdition(Edition.NEGATIVE, 1, -1, false)));
        put(new Consumable("c_hex", "Hex", "Add Polychrome to a random Joker, destroy all other Jokers",
                ConsumableType.SPECTRAL, 0, new Consumable.JokerEdition(Edition.POLYCHROME, 1, 0, true)));

        // Generative consumables: create cards/consumables/jokers and/or move money.
        put(new Consumable("c_emperor", "The Emperor", "Create up to 2 random Tarot cards",
                ConsumableType.TAROT, 0, new Consumable.Generate(
                        new CreateSpec(CreateSpec.Kind.TAROT, 2), 0, null, null)));
        put(new Consumable("c_high_priestess", "The High Priestess", "Create up to 2 random Planet cards",
                ConsumableType.TAROT, 0, new Consumable.Generate(
                        new CreateSpec(CreateSpec.Kind.PLANET, 2), 0, null, null)));
        put(new Consumable("c_judgement", "Judgement", "Create a random Joker",
                ConsumableType.TAROT, 0, new Consumable.Generate(
                        new CreateSpec(CreateSpec.Kind.JOKER, 1), 0, null, null)));
        put(new Consumable("c_hermit", "The Hermit", "Double your money (max gain of $20)",
                ConsumableType.TAROT, 0, new Consumable.Generate(
                        null, 0, null, new Consumable.Generate.MoneyOp(
                                Consumable.Generate.MoneyOp.Kind.DOUBLE_CAP, 20))));
        put(new Consumable("c_temperance", "Temperance", "Gain the total sell value of all Jokers (max $50)",
                ConsumableType.TAROT, 0, new Consumable.Generate(
                        null, 0, null, new Consumable.Generate.MoneyOp(
                                Consumable.Generate.MoneyOp.Kind.SELL_VALUE_CAP, 50))));

        // Generative spectrals.
        put(new Consumable("c_the_soul", "The Soul", "Create a random Legendary Joker",
                ConsumableType.SPECTRAL, 0, new Consumable.Generate(
                        new CreateSpec(CreateSpec.Kind.JOKER, 1, "Legendary", null), 0, null, null)));
        put(new Consumable("c_wraith", "The Wraith", "Create a random Rare Joker, set money to $0",
                ConsumableType.SPECTRAL, 0, new Consumable.Generate(
                        new CreateSpec(CreateSpec.Kind.JOKER, 1, "Rare", null), 0, null,
                        new Consumable.Generate.MoneyOp(Consumable.Generate.MoneyOp.Kind.SET, 0))));
        put(new Consumable("c_immolate", "Immolate", "Destroy 5 random cards in hand, gain $20",
                ConsumableType.SPECTRAL, 0, new Consumable.Generate(
                        null, 5, null, new Consumable.Generate.MoneyOp(
                                Consumable.Generate.MoneyOp.Kind.FLAT, 20))));
        put(new Consumable("c_familiar", "Familiar", "Destroy 1 random card, add 3 random Enhanced face cards",
                ConsumableType.SPECTRAL, 0, new Consumable.Generate(null, 1,
                        new Consumable.Generate.AddCards(
                                Consumable.Generate.AddCards.RankClass.FACE, 3, null), null)));
        put(new Consumable("c_grim", "Grim", "Destroy 1 random card, add 2 random Enhanced Aces",
                ConsumableType.SPECTRAL, 0, new Consumable.Generate(null, 1,
                        new Consumable.Generate.AddCards(
                                Consumable.Generate.AddCards.RankClass.ACE, 2, null), null)));

        // Transform / copy consumables.
        put(new Consumable("c_fool", "The Fool", "Create a copy of the last Tarot or Planet used this run",
                ConsumableType.TAROT, 0, new Consumable.CopyLastConsumable()));
        put(new Consumable("c_death", "Death", "Select 2 cards: the left card becomes a copy of the right",
                ConsumableType.TAROT, 2, new Consumable.OverwriteSelected()));
        put(new Consumable("c_sigil", "Sigil", "Convert all cards in hand to a single random suit",
                ConsumableType.SPECTRAL, 0, new Consumable.ConvertHand(true, false, 0)));
        put(new Consumable("c_ouija", "Ouija", "Convert all cards in hand to a single random rank, -1 hand size",
                ConsumableType.SPECTRAL, 0, new Consumable.ConvertHand(false, true, -1)));
        put(new Consumable("c_cryptid", "Cryptid", "Create 2 copies of 1 selected card",
                ConsumableType.SPECTRAL, 1, new Consumable.CopySelected(2)));
        put(new Consumable("c_ankh", "Ankh", "Copy a random Joker, destroy all other Jokers",
                ConsumableType.SPECTRAL, 0, new Consumable.CopyRandomJoker(true)));

        // MP-exclusive planet: delevels the nemesis's highest hand (PLANET type keeps it out of the
        // single-player Tarot/Spectral pools; it's distributed only in multiplayer).
        put(new Consumable("c_asteroid", "Asteroid", "Delevel your nemesis's highest-level poker hand",
                ConsumableType.PLANET, 0, new Consumable.NemesisDelevel()));
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
