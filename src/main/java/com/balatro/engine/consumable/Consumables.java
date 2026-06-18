package com.balatro.engine.consumable;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.consumable.Consumable.Generate.AddCards;
import com.balatro.engine.consumable.Consumable.Generate.MoneyOp;
import com.balatro.engine.joker.def.CreateSpec;

/**
 * Fluent builder for {@link Consumable}s — the sibling of the joker {@code Jokers} builder. Authors a
 * Tarot/Spectral as a readable sentence instead of the 6-arg positional constructor with nested
 * {@code Generate(null, 0, null, new MoneyOp(Kind.DOUBLE_CAP, 20))} soup. Produces the same serializable
 * {@link Consumable} record (the source of truth).
 *
 * <p>Two effect families: a <b>direct</b> effect ({@link #enhance}, {@link #destroySelected}, …) sets the
 * effect outright; the <b>generative</b> verbs ({@link #createTarots}, {@link #destroyInHand},
 * {@link #addFaceCards}, {@link #doubleMoney}, …) accumulate into one {@link Consumable.Generate}.
 */
public final class Consumables {

    private final String key;
    private final String name;
    private final ConsumableType type;
    private String desc = "";
    private int maxTargets;
    private Consumable.Effect effect;

    // generative accumulator (folded into one Generate at build() if any is set)
    private CreateSpec create;
    private int destroyInHand;
    private AddCards add;
    private MoneyOp money;

    private Consumables(String key, String name, ConsumableType type) {
        this.key = key;
        this.name = name;
        this.type = type;
    }

    public static Consumables tarot(String key, String name) { return new Consumables(key, name, ConsumableType.TAROT); }

    public static Consumables spectral(String key, String name) { return new Consumables(key, name, ConsumableType.SPECTRAL); }

    /** A PLANET-typed consumable — keeps it out of the single-player Tarot/Spectral pools (MP Asteroid). */
    public static Consumables planet(String key, String name) { return new Consumables(key, name, ConsumableType.PLANET); }

    public Consumables desc(String d) { this.desc = d; return this; }

    /** How many held cards the player may select as targets (enhance/convert/copy effects). */
    public Consumables targets(int n) { this.maxTargets = n; return this; }

    // --- direct effects (set the effect outright) ---

    /** Apply a card mutation to each selected target — enhance/convert-suit/seal/edition (Magician, Star, Aura). */
    public Consumables enhance(CardMod mod) { return direct(new Consumable.Enhance(mod)); }

    /** Destroy each selected target (The Hanged Man). */
    public Consumables destroySelected() { return direct(new Consumable.Destroy()); }

    /** Add {@code count} new numbered cards with an enhancement to the deck (Incantation). */
    public Consumables createCards(int count, Enhancement enhancement) {
        return direct(new Consumable.Create(count, enhancement));
    }

    /** Level up every poker hand by 1 (Black Hole). */
    public Consumables levelAllHands() { return direct(new Consumable.LevelAllHands()); }

    /** Add an edition to a random owned joker (Wheel of Fortune / Ectoplasm / Hex). */
    public Consumables jokerEdition(com.balatro.engine.card.Edition edition, int chanceDenominator,
            int handSizeDelta, boolean destroyOtherJokers) {
        return direct(new Consumable.JokerEdition(edition, chanceDenominator, handSizeDelta, destroyOtherJokers));
    }

    /** Convert every card in hand to one random suit (Sigil). */
    public Consumables convertHandToSuit() { return direct(new Consumable.ConvertHand(true, false, 0)); }

    /** Convert every card in hand to one random rank; {@code handSizeDelta} is Ouija's -1. */
    public Consumables convertHandToRank(int handSizeDelta) { return direct(new Consumable.ConvertHand(false, true, handSizeDelta)); }

    /** Create {@code copies} exact copies of the single selected card (Cryptid). */
    public Consumables copySelected(int copies) { return direct(new Consumable.CopySelected(copies)); }

    /** Overwrite the first selected card with the second (Death). */
    public Consumables overwriteSelected() { return direct(new Consumable.OverwriteSelected()); }

    /** Copy a random owned joker; optionally destroy all others (Ankh). */
    public Consumables copyRandomJoker(boolean destroyOthers) { return direct(new Consumable.CopyRandomJoker(destroyOthers)); }

    /** Copy the last Tarot or Planet used this run (The Fool). */
    public Consumables copyLastConsumable() { return direct(new Consumable.CopyLastConsumable()); }

    /** Delevel the nemesis's highest poker hand (Asteroid, MP). */
    public Consumables nemesisDelevel() { return direct(new Consumable.NemesisDelevel()); }

    // --- generative verbs (accumulate into one Generate) ---

    /** Create {@code n} random Tarots (Emperor). */
    public Consumables createTarots(int n) { this.create = new CreateSpec(CreateSpec.Kind.TAROT, n); return this; }

    /** Create {@code n} random Planets (High Priestess). */
    public Consumables createPlanets(int n) { this.create = new CreateSpec(CreateSpec.Kind.PLANET, n); return this; }

    /** Create one random Joker from any rarity (Judgement). */
    public Consumables createJoker() { this.create = new CreateSpec(CreateSpec.Kind.JOKER, 1); return this; }

    /** Create one random Joker of a fixed {@code rarity} (The Soul = Legendary, Wraith = Rare). */
    public Consumables createJoker(String rarity) { this.create = new CreateSpec(CreateSpec.Kind.JOKER, 1, rarity, null); return this; }

    /** Destroy {@code n} random cards in hand (Immolate / Familiar / Grim). */
    public Consumables destroyInHand(int n) { this.destroyInHand = n; return this; }

    /** Add {@code count} random enhanced FACE cards (Familiar). */
    public Consumables addFaceCards(int count) { this.add = new AddCards(AddCards.RankClass.FACE, count, null); return this; }

    /** Add {@code count} random enhanced ACES (Grim). */
    public Consumables addAces(int count) { this.add = new AddCards(AddCards.RankClass.ACE, count, null); return this; }

    /** Double current money, capped at {@code cap} gain (Hermit). */
    public Consumables doubleMoney(int cap) { this.money = new MoneyOp(MoneyOp.Kind.DOUBLE_CAP, cap); return this; }

    /** Gain the total joker sell value, capped at {@code cap} (Temperance). */
    public Consumables gainSellValue(int cap) { this.money = new MoneyOp(MoneyOp.Kind.SELL_VALUE_CAP, cap); return this; }

    /** Gain a flat {@code amount} (Immolate). */
    public Consumables gainMoney(int amount) { this.money = new MoneyOp(MoneyOp.Kind.FLAT, amount); return this; }

    /** Set money to a fixed {@code value} (Wraith → $0). */
    public Consumables setMoney(int value) { this.money = new MoneyOp(MoneyOp.Kind.SET, value); return this; }

    public Consumable build() {
        if (effect == null) {
            boolean generative = create != null || destroyInHand != 0 || add != null || money != null;
            if (!generative) {
                throw new IllegalStateException("Consumable '" + key + "' has no effect — set one before build()");
            }
            effect = new Consumable.Generate(create, destroyInHand, add, money);
        }
        return new Consumable(key, name, desc, type, maxTargets, effect);
    }

    private Consumables direct(Consumable.Effect e) {
        if (effect != null) throw new IllegalStateException("Consumable '" + key + "' already has a direct effect");
        this.effect = e;
        return this;
    }
}
