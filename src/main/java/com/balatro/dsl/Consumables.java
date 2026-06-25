package com.balatro.dsl;

import com.balatro.engine.consumable.*;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.joker.def.Effect;
import com.balatro.engine.joker.def.Effect.AddCards;
import com.balatro.engine.joker.def.CreateSpec;
import com.balatro.engine.joker.def.Selector;
import com.balatro.engine.joker.def.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link Consumable}s — the sibling of the joker {@code Jokers} builder. Authors a
 * Tarot/Spectral as a readable sentence over the same {@link Effect} vocabulary jokers use. Produces the
 * serializable {@link Consumable} record (the source of truth), whose effect list {@code Run} interprets.
 *
 * <p>Two effect families: a <b>direct</b> effect ({@link #enhance}, {@link #destroySelected}, …) sets the
 * effect outright; the <b>generative</b> verbs ({@link #createTarots}, {@link #destroyInHand},
 * {@link #addFaceCards}, {@link #doubleMoney}, …) accumulate into an ordered {@code List<Effect>}
 * (Destroy → Create → AddCards → AdjustMoney) — no fused composite verb.
 */
public final class Consumables {

    private final String key;
    private final String name;
    private final ConsumableType type;
    private String desc = "";
    private int maxTargets;
    private final List<Effect> directEffects = new ArrayList<>(); // direct effects, applied in order

    // generative accumulator — each piece becomes its own Effect in an ordered list at build()
    // (destroy → create → add → money); no fused Generate composite.
    private CreateSpec create;
    private int destroyInHand;
    private AddCards add;
    private Effect money; // an Effect.AdjustMoney

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
    public Consumables enhance(CardMod mod) { return direct(new Effect.MutateCard(new Selector.Selected(), mod)); }

    /** Destroy each selected target (The Hanged Man). */
    public Consumables destroySelected() { return direct(new Effect.Destroy(new Selector.Selected())); }

    /** Add {@code count} new numbered cards with an enhancement to the deck (Incantation). */
    public Consumables createCards(int count, Enhancement enhancement) {
        return direct(new Effect.CreateCards(count, enhancement));
    }

    /** Level up every poker hand by 1 (Black Hole). */
    public Consumables levelAllHands() {
        return direct(new Effect.LevelHands(Effect.LevelHands.Scope.ALL, new com.balatro.engine.joker.def.Value.Const(1)));
    }

    /** Wheel of Fortune: 1-in-{@code chanceDenominator} to edition a random joker — a {@code When(chance)}
     *  gate over the same Bind+EditionJoker the always-fire ones use. No bespoke verb; the gate is a Condition,
     *  exactly like a joker/boss rule's {@code IF} (the chance rolls on the consumable's own "gate" stream). */
    public Consumables chanceEditionRandomJoker(Edition edition, int chanceDenominator) {
        return direct(new Effect.When(Cond.chance(1, chanceDenominator, "gate"),
                List.of(new Effect.Bind("j", new Selector.RandomJoker()),
                        new Effect.EditionJoker(new Selector.Bound("j"), edition))));
    }

    /** Ectoplasm/Hex: bind a random joker, give it {@code edition}, then optionally adjust hand size and/or
     *  destroy every other joker — composable grammar over the shared selection (no fused composite). */
    public Consumables editionRandomJoker(Edition edition, int handSizeDelta, boolean destroyOthers) {
        direct(new Effect.Bind("j", new Selector.RandomJoker()));
        direct(new Effect.EditionJoker(new Selector.Bound("j"), edition));
        if (destroyOthers) direct(new Effect.Destroy(new Selector.Others("j")));
        if (handSizeDelta != 0) direct(new Effect.AdjustHandSize(handSizeDelta));
        return this;
    }

    /** Convert every card in hand to one random suit (Sigil). */
    public Consumables convertHandToSuit() { return direct(new Effect.ConvertHand(Effect.ConvertHand.Axis.SUIT, 0)); }

    /** Convert every card in hand to one random rank; {@code handSizeDelta} is Ouija's -1. */
    public Consumables convertHandToRank(int handSizeDelta) {
        return direct(new Effect.ConvertHand(Effect.ConvertHand.Axis.RANK, handSizeDelta));
    }

    /** Create {@code copies} exact copies of the single selected card (Cryptid). */
    public Consumables copySelected(int copies) { return direct(new Effect.Copy(new Selector.Selected(), copies)); }

    /** Overwrite the first selected card with the second (Death). */
    public Consumables overwriteSelected() { return direct(new Effect.OverwriteSelected()); }

    /** Copy a random owned joker; optionally destroy all others (Ankh). Authored as composable grammar over
     *  a SHARED selection: bind one random joker, (destroy all the others,) then copy that same bound joker —
     *  so the copy and the destroy agree on the same pick (a single roll), expressed without a bespoke verb. */
    public Consumables copyRandomJoker(boolean destroyOthers) {
        direct(new Effect.Bind("j", new Selector.RandomJoker()));
        if (destroyOthers) direct(new Effect.Destroy(new Selector.Others("j")));
        return direct(new Effect.Copy(new Selector.Bound("j"), 1));
    }

    /** Copy the last Tarot or Planet used this run (The Fool). */
    public Consumables copyLastConsumable() { return direct(new Effect.Copy(new Selector.LastConsumable(), 1)); }

    /** Delevel the nemesis's highest poker hand (Asteroid, MP). */
    public Consumables nemesisDelevel() { return direct(new Effect.NemesisDelevel()); }

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

    /** Double current money, capped at {@code cap} gain (Hermit) = ADD min(MONEY, cap). */
    public Consumables doubleMoney(int cap) { return money(Effect.Operation.ADD, clampedVar(Value.Var.MONEY, cap)); }

    /** Gain the total joker sell value, capped at {@code cap} (Temperance) = ADD min(TOTAL_SELL_VALUE, cap). */
    public Consumables gainSellValue(int cap) { return money(Effect.Operation.ADD, clampedVar(Value.Var.TOTAL_SELL_VALUE, cap)); }

    /** Gain a flat {@code amount} (Immolate). */
    public Consumables gainMoney(int amount) { return money(Effect.Operation.ADD, new Value.Const(amount)); }

    /** Set money to a fixed {@code value} (Wraith → $0). */
    public Consumables setMoney(int value) { return money(Effect.Operation.SET, new Value.Const(value)); }

    private Consumables money(Effect.Operation op, Value amount) {
        this.money = new Effect.AdjustMoney(op, amount);
        return this;
    }

    /** {@code min(max(var, 0), cap)} — a "gain var, capped" amount (Hermit/Temperance). */
    private static Value clampedVar(Value.Var v, int cap) {
        return new Value.Clamp(new Value.RunVar(v, 0, 1), 0, cap);
    }

    public Consumable build() {
        // A "generative" consumable is just an ordered List<Effect> — there is no Generate composite. The
        // pieces apply in the same order the old Generate did: destroy random → create → add rank cards →
        // money. Each is the same first-class Effect a joker/boss could use.
        List<Effect> effects = new ArrayList<>(directEffects);
        if (effects.isEmpty()) {
            if (destroyInHand != 0) effects.add(new Effect.Destroy(new Selector.RandomInHand(destroyInHand)));
            if (create != null) effects.add(new Effect.Create(create));
            if (add != null) effects.add(add);
            if (money != null) effects.add(money);
            if (effects.isEmpty()) {
                throw new IllegalStateException("Consumable '" + key + "' has no effect — set one before build()");
            }
        }
        // Description is localization data: default from Loc keyed by consumable key when not set explicitly.
        String text = desc.isEmpty() ? com.balatro.engine.i18n.Loc.text(key) : desc;
        return new Consumable(key, name, text, type, maxTargets, List.copyOf(effects));
    }

    private Consumables direct(Effect e) {
        directEffects.add(e);
        return this;
    }
}
