package com.balatro.content;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.consumable.Consumable;
import com.balatro.dsl.Consumables;
import java.util.ArrayList;
import java.util.List;

/** The consumable CONTENT (tarots / spectrals / the MP planet), authored in the {@link Consumables} DSL and
 *  compiled to {@code content/consumables.json}. Descriptions come from localization (en.json). */
public final class ConsumableDefs {

    private ConsumableDefs() {}

    public static List<Consumable> authored() {
        List<Consumable> c = new ArrayList<>();
        c.add(Consumables.tarot("c_magician", "The Magician").targets(2).enhance(CardMod.setEnhancement(Enhancement.LUCKY)).build());
        c.add(Consumables.tarot("c_empress", "The Empress").targets(2).enhance(CardMod.setEnhancement(Enhancement.MULT)).build());
        c.add(Consumables.tarot("c_hierophant", "The Hierophant").targets(2).enhance(CardMod.setEnhancement(Enhancement.BONUS)).build());
        c.add(Consumables.tarot("c_devil", "The Devil").targets(1).enhance(CardMod.setEnhancement(Enhancement.GOLD)).build());
        c.add(Consumables.tarot("c_hanged_man", "The Hanged Man").targets(2).destroySelected().build());
        c.add(Consumables.tarot("c_strength", "Strength").targets(2).enhance(CardMod.incRank(1)).build());
        c.add(Consumables.tarot("c_star", "The Star").targets(3).enhance(CardMod.setSuit(Suit.DIAMONDS)).build());
        c.add(Consumables.tarot("c_moon", "The Moon").targets(3).enhance(CardMod.setSuit(Suit.CLUBS)).build());
        c.add(Consumables.tarot("c_sun", "The Sun").targets(3).enhance(CardMod.setSuit(Suit.HEARTS)).build());
        c.add(Consumables.tarot("c_world", "The World").targets(3).enhance(CardMod.setSuit(Suit.SPADES)).build());
        c.add(Consumables.tarot("c_lovers", "The Lovers").targets(1).enhance(CardMod.setEnhancement(Enhancement.WILD)).build());
        c.add(Consumables.tarot("c_chariot", "The Chariot").targets(1).enhance(CardMod.setEnhancement(Enhancement.STEEL)).build());
        c.add(Consumables.tarot("c_justice", "Justice").targets(1).enhance(CardMod.setEnhancement(Enhancement.GLASS)).build());
        c.add(Consumables.tarot("c_tower", "The Tower").targets(1).enhance(CardMod.setEnhancement(Enhancement.STONE)).build());
        c.add(Consumables.spectral("c_incantation", "Incantation").createCards(4, Enhancement.BONUS).build());
        c.add(Consumables.spectral("c_talisman", "Talisman").targets(1).enhance(CardMod.setSeal(Seal.GOLD)).build());
        c.add(Consumables.spectral("c_deja_vu", "Deja Vu").targets(1).enhance(CardMod.setSeal(Seal.RED)).build());
        c.add(Consumables.spectral("c_trance", "Trance").targets(1).enhance(CardMod.setSeal(Seal.BLUE)).build());
        c.add(Consumables.spectral("c_medium", "Medium").targets(1).enhance(CardMod.setSeal(Seal.PURPLE)).build());
        c.add(Consumables.spectral("c_aura", "Aura").targets(1).enhance(CardMod.setEdition(Edition.POLYCHROME)).build());
        c.add(Consumables.spectral("c_black_hole", "Black Hole").levelAllHands().build());
        c.add(Consumables.tarot("c_wheel_of_fortune", "The Wheel of Fortune").chanceEditionRandomJoker(Edition.NONE, 4).build());
        c.add(Consumables.spectral("c_ectoplasm", "Ectoplasm").editionRandomJoker(Edition.NEGATIVE, -1, false).build());
        c.add(Consumables.spectral("c_hex", "Hex").editionRandomJoker(Edition.POLYCHROME, 0, true).build());
        c.add(Consumables.tarot("c_emperor", "The Emperor").createTarots(2).build());
        c.add(Consumables.tarot("c_high_priestess", "The High Priestess").createPlanets(2).build());
        c.add(Consumables.tarot("c_judgement", "Judgement").createJoker().build());
        c.add(Consumables.tarot("c_hermit", "The Hermit").doubleMoney(20).build());
        c.add(Consumables.tarot("c_temperance", "Temperance").gainSellValue(50).build());
        c.add(Consumables.spectral("c_the_soul", "The Soul").createJoker(com.balatro.grammar.Rarity.LEGENDARY).build());
        c.add(Consumables.spectral("c_wraith", "The Wraith").createJoker(com.balatro.grammar.Rarity.RARE).setMoney(0).build());
        c.add(Consumables.spectral("c_immolate", "Immolate").destroyInHand(5).gainMoney(20).build());
        c.add(Consumables.spectral("c_familiar", "Familiar").destroyInHand(1).addFaceCards(3).build());
        c.add(Consumables.spectral("c_grim", "Grim").destroyInHand(1).addAces(2).build());
        c.add(Consumables.tarot("c_fool", "The Fool").copyLastConsumable().build());
        c.add(Consumables.tarot("c_death", "Death").targets(2).overwriteSelected().build());
        c.add(Consumables.spectral("c_sigil", "Sigil").convertHandToSuit().build());
        c.add(Consumables.spectral("c_ouija", "Ouija").convertHandToRank(-1).build());
        c.add(Consumables.spectral("c_cryptid", "Cryptid").targets(1).copySelected(2).build());
        c.add(Consumables.spectral("c_ankh", "Ankh").copyRandomJoker(true).build());
        c.add(Consumables.planet("c_asteroid", "Asteroid").nemesisDelevel().build());
        return c;
    }
}
