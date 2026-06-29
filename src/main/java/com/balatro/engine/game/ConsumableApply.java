package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.ConsumableType;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.eval.ConditionEvaluator;
import com.balatro.engine.exec.Command;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.rng.RngSources;
import com.balatro.engine.state.Deck;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a used consumable (Tarot / Spectral / Planet) — the action-side {@link Effect} interpreter, resolving
 * each effect's {@link Selector} against the live hand and emitting {@link Command}s (which {@code Run} applies).
 * Extracted from {@code Run} (the orchestrator): this is the tarot/spectral concern in one focused place. It
 * leans on {@code Run}'s package-internal toolkit — {@code apply(Command)}, {@code roll}/{@code pick},
 * {@code rngCtx}/{@code runLoopContext}, and the shared {@code applyWrite}/{@code applyLevelHands} interpreters
 * (those two are shared with the boss/joker run-loop path, so they stay on {@code Run}).
 */
final class ConsumableApply {

    private ConsumableApply() {}

    /** Enhancements a "random Enhanced" created card (Familiar/Grim) may roll — every type but NONE. */
    private static final Enhancement[] RANDOM_ENHANCEMENTS = {
        Enhancement.BONUS, Enhancement.MULT, Enhancement.GLASS, Enhancement.STEEL,
        Enhancement.STONE, Enhancement.GOLD, Enhancement.WILD, Enhancement.LUCKY
    };

    /**
     * Use a held consumable. Planets level their hand; Tarots/Spectrals act on the cards the client selected
     * (resolved by unique id) — enhance, destroy, or create. Returns null on success, else a rejection reason.
     */
    static String use(Run r, int index, UUID[] targetUids) {
        if (r.phase == Run.Phase.RUN_WON || r.phase == Run.Phase.RUN_LOST) return "run is over";
        if (index < 0 || index >= r.state.consumables.size()) return "invalid consumable";
        String key = r.state.consumables.get(index);

        PlanetCatalog.Planet p = PlanetCatalog.get(key);
        if (p != null) {
            r.state.levelUpHand(p.hand());
            r.state.planetsUsedThisRun.add(key); // Satellite counts unique planets used
            r.state.lastTarotPlanetUsed = key;    // The Fool can copy it
            GameEvents.useConsumable(r.state, r.rng, com.balatro.grammar.ConsumableKind.PLANET);
            r.state.consumables.remove(index);
            return null;
        }

        Consumable c = TarotCatalog.get(key);
        if (c != null) {
            List<Card> targets = new ArrayList<>();
            for (UUID uid : targetUids) {
                for (Card card : r.state.hand) {
                    if (card.uid.equals(uid)) targets.add(card);
                }
            }
            if (targets.size() > c.maxTargets()) return "too many targets";
            // Free this consumable's slot BEFORE applying, so a generative effect's created
            // consumables can occupy the slot it just vacated (Balatro's ordering).
            r.state.consumables.remove(index);
            applyConsumable(r, c, targets);
            // The Fool copies the last Tarot/Planet used — track Tarots here (but never The Fool itself).
            if (c.type() == ConsumableType.TAROT && !key.equals("c_fool")) {
                r.state.lastTarotPlanetUsed = key;
            }
            GameEvents.useConsumable(r.state, r.rng, com.balatro.grammar.ConsumableKind.valueOf(c.type().name()));
            return null;
        }
        return "unknown consumable: " + key;
    }

    private static void applyConsumable(Run r, Consumable c, List<Card> targets) {
        // The binding map is action-scoped: it lives only for this one consumable's effect list, so a
        // Bind/Bound/Others reference can't leak between actions. It names a joker, never a value.
        Map<String, Joker> bindings = new java.util.HashMap<>();
        for (Effect e : c.effects()) applyEffect(r, c, e, targets, bindings);
    }

    /** The action interpreter: apply one unified {@link Effect} to the run, resolving its {@link Selector}
     *  against the live hand. The joker scoring verbs (Score/MutateState/…) never appear on a consumable. */
    private static void applyEffect(Run r, Consumable c, Effect e, List<Card> targets, Map<String, Joker> bindings) {
        switch (e) {
            case Effect.Bind b -> { // resolve the selector ONCE and remember it for later effects in this list
                if (b.selector() instanceof Selector.RandomJoker && !r.state.jokers().isEmpty()) {
                    bindings.put(b.name(), r.state.queues.pick(r.state.jokers(),
                            RngSources.consumable(c.key()).sub("joker").composition(), r.rngCtx(), Joker::key, Run.JOKER_QUALITY));
                }
            }
            case Effect.When w -> { // the consumable's IF — apply inner effects only if the gate holds (Wheel)
                if (consumableConditionHolds(r, c, w.condition())) {
                    for (Effect inner : w.effects()) applyEffect(r, c, inner, targets, bindings);
                }
            }
            case Effect.MutateCard mc -> {
                List<Card> sel = resolveTargets(r, c, mc.selector(), targets);
                if (mc.mod().action() == CardMod.Action.COPY_IDENTITY) {
                    // Death: each selected card becomes a copy of the last selected (the multi-property copy).
                    Card source = sel.get(sel.size() - 1);
                    for (Card t : sel) if (t != source) r.apply(new Command.OverwriteCard(t, source));
                } else {
                    r.apply(new Command.MutateCards(sel, mc.mod()));
                }
            }
            case Effect.Create cr -> // pure-create consumable (Emperor/High Priestess/Judgement/Soul)
                r.apply(new Command.Create(cr.spec()));
            case Effect.Destroy d -> { // consumable-context destroy
                if (d.selector() instanceof Selector.Others o) { // Hex/Ankh: destroy every joker but the bound one
                    Joker keep = bindings.get(o.name());
                    if (keep != null) r.apply(new Command.DestroyOtherJokers(keep));
                } else { // card selectors (Hanged Man, Immolate): select the victims, then destroy them
                    r.apply(new Command.DestroyCards(resolveTargets(r, c, d.selector(), targets)));
                }
            }
            case Effect.LevelHands lh -> // Black Hole (ALL); Asteroid (OPPONENT) routes to the Nemesis
                r.applyLevelHands(lh, r.runLoopContext());
            case Effect.AddCards a -> addRankClassCards(r, c, a);               // Familiar / Grim rank-class adds
            case Effect.EditionJoker ej -> { // Ectoplasm/Hex: edition the bound joker (Wheel keeps JokerEdition)
                Joker target = ej.selector() instanceof Selector.Bound bnd ? bindings.get(bnd.name())
                        : (!r.state.jokers().isEmpty() ? r.state.queues.pick(r.state.jokers(),
                                RngSources.consumable(c.key()).sub("joker").composition(), r.rngCtx(), Joker::key, Run.JOKER_QUALITY)
                           : null);
                if (target != null) r.apply(new Command.EditionJoker(target, resolveEdition(r, c, ej.edition())));
            }
            case Effect.Write w -> r.applyWrite(w.mod(), r.runLoopContext());     // Ouija/Ectoplasm (Hand.SIZE) / Immolate (MONEY)
            case Effect.ConvertHand ch -> applyConvertHand(r, c, ch);
            case Effect.Copy cp -> { // consumable-context copy
                if (cp.selector() instanceof Selector.Selected && !targets.isEmpty()) { // Cryptid: copy the card cp.count()×
                    Card src = targets.get(0);
                    List<Card> copies = new ArrayList<>();
                    for (int i = 0; i < cp.count(); i++) copies.add(src.copy()); // fresh uid, same attributes
                    r.apply(new Command.AddCardsToDeck(copies));
                } else if (cp.selector() instanceof Selector.LastConsumable      // The Fool: copy the last Tarot/Planet used
                        && r.state.lastTarotPlanetUsed != null) {
                    r.apply(new Command.CopyConsumable(r.state.lastTarotPlanetUsed, Command.CopyConsumable.SlotPolicy.RESPECT_CAP));
                } else if (cp.selector() instanceof Selector.Bound bnd) {         // Ankh: copy the bound joker
                    Joker src = bindings.get(bnd.name());
                    if (src != null && r.state.jokers().size() < r.state.jokerSlots)
                        r.apply(new Command.CopyJoker(src, r.ruleset.jokerVariant()));
                }
            }
            default -> throw new IllegalStateException("not a consumable effect: " + e);
        }
    }

    /** Resolve which held cards an effect targets. Selected = the player's chosen cards; the others let a
     *  consumable reach the whole hand or a random subset without bespoke code. */
    private static List<Card> resolveTargets(Run r, Consumable c, Selector sel, List<Card> selected) {
        return switch (sel) {
            case Selector.Selected ignored -> selected;
            case Selector.Focus ignored ->
                    throw new IllegalStateException("Focus targets the scored card — meaningless outside scoring");
            case Selector.RandomInHand rin -> {
                List<Card> out = new ArrayList<>();
                for (int i = 0; i < rin.count(); i++) {
                    List<Card> live = r.state.hand.stream().filter(x -> !x.destroyed && !out.contains(x)).toList();
                    if (live.isEmpty()) break;
                    // "destroy:i" sub-key matches the old Generate destroy stream, so Immolate/Familiar/Grim
                    // (now Destroy(RandomInHand) in their effect list) draw the same cards — byte-identical.
                    out.add(r.state.queues.pick(live, RngSources.consumable(c.key()).sub("destroy:" + i).composition(),
                            r.rngCtx(), Deck.CARD_GROUP, Deck.CARD_QUALITY));
                }
                yield out;
            }
            case Selector.RandomJoker ignored ->
                    throw new IllegalStateException("RandomJoker selects jokers, not cards");
            case Selector.Discarded ignored ->
                    throw new IllegalStateException("Discarded targets the discard event — scoring-time only");
            case Selector.Self ignored ->
                    throw new IllegalStateException("Self targets the emitting joker, not cards");
            case Selector.OtherJoker ignored ->
                    throw new IllegalStateException("OtherJoker selects a joker, not cards");
            case Selector.LastConsumable ignored ->
                    throw new IllegalStateException("LastConsumable selects a consumable, not cards");
            case Selector.RandomConsumable ignored ->
                    throw new IllegalStateException("RandomConsumable selects a consumable, not cards");
            case Selector.Bound ignored ->
                    throw new IllegalStateException("Bound names a joker, not cards");
            case Selector.Others ignored ->
                    throw new IllegalStateException("Others names jokers, not cards");
        };
    }

    /** Sigil (all cards to one random suit) / Ouija (all to one random rank, -1 hand size). */
    private static void applyConvertHand(Run r, Consumable c, Effect.ConvertHand ch) {
        // MP Ouija rework: destroy 3 random cards first, convert the rest to one rank, and DON'T
        // reduce hand size (vanilla Ouija converts the whole hand and loses 1 hand size).
        boolean mpOuija = "c_ouija".equals(c.key()) && r.ruleset.capabilities().ouijaRework();
        if (mpOuija) {
            for (int i = 0; i < 3; i++) {
                List<Card> live = r.state.hand.stream().filter(x -> !x.destroyed).toList();
                if (live.isEmpty()) break;
                Card victim = r.state.queues.pick(live, RngSources.consumable(c.key()).sub("destroy:" + i).composition(),
                        r.rngCtx(), Deck.CARD_GROUP, Deck.CARD_QUALITY);
                victim.destroyed = true;
            }
            r.composition.removeIf(x -> x.destroyed);
            r.state.hand.removeIf(x -> x.destroyed);
            Rank rank = r.pick(Rank.values(), RngSources.consumable(c.key()).sub("rank"));
            for (Card card : r.state.hand) card.rank = rank;
            return; // no hand-size reduction in MP
        }
        if (ch.axis() == Effect.ConvertHand.Axis.SUIT) {
            Suit s = r.pick(Suit.values(), RngSources.consumable(c.key()).sub("suit"));
            for (Card card : r.state.hand) card.suit = s;
        }
        if (ch.axis() == Effect.ConvertHand.Axis.RANK) {
            Rank rank = r.pick(Rank.values(), RngSources.consumable(c.key()).sub("rank"));
            for (Card card : r.state.hand) card.rank = rank;
        }
        if (ch.handSizeDelta() != 0) r.state.handSize = Math.max(1, r.state.handSize + ch.handSizeDelta());
    }

    private static void addRankClassCards(Run r, Consumable c, Effect.AddCards add) {
        Rank[] pool = switch (add.rankClass()) {
            case FACE -> new Rank[]{Rank.JACK, Rank.QUEEN, Rank.KING};
            case ACE -> new Rank[]{Rank.ACE};
            case NUMBER -> java.util.Arrays.stream(Rank.values()).filter(rk -> rk.id <= 10).toArray(Rank[]::new);
        };
        List<Card> made = new ArrayList<>();
        for (int i = 0; i < add.count(); i++) {
            Rank rank = r.pick(pool, RngSources.consumable(c.key()).sub("rank:" + i));
            Suit s = r.pick(Suit.values(), RngSources.consumable(c.key()).sub("suit:" + i));
            Enhancement e = add.enhancement() != null ? add.enhancement()
                    : r.pick(RANDOM_ENHANCEMENTS, RngSources.consumable(c.key()).sub("enh:" + i));
            made.add(new Card(rank, s, e, Edition.NONE, Seal.NONE));
        }
        r.apply(new Command.AddCardsToDeck(made));
    }

    /** Evaluate a consumable's {@link Effect.When} gate. A {@code Chance} rolls on the consumable's OWN
     *  stream ({@code consumable(key).sub(seedKey)}) — the same draw the hardcoded Wheel gate used — and
     *  PROBABILITY_MULTIPLIER (Oops!) scales the threshold like every other chance. Others test normally. */
    private static boolean consumableConditionHolds(Run r, Consumable c, com.balatro.grammar.Condition cond) {
        if (cond instanceof com.balatro.grammar.Condition.Chance ch) {
            // The gate's key names the consumable-specific sub-stream (the Wheel gate's "gate" seedKey).
            String key = switch (ch.gate()) {
                case com.balatro.grammar.Condition.RngGate.SharedProb sp -> sp.seedKey();
                case com.balatro.grammar.Condition.RngGate.DedicatedStream ds -> ds.name();
            };
            double roll = r.roll(RngSources.consumable(c.key()).sub(key));
            return roll < (double) (ch.odds().numerator() * r.state.probabilityNumerator) / ch.odds().denominator();
        }
        return ConditionEvaluator.test(cond, r.runLoopContext());
    }

    /** {@code NONE} = roll a random Foil/Holo/Poly (the "ed" sub-stream); else the fixed edition. */
    private static Edition resolveEdition(Run r, Consumable c, Edition ed) {
        if (ed != Edition.NONE) return ed;
        return r.pick(new Edition[]{Edition.FOIL, Edition.HOLOGRAPHIC, Edition.POLYCHROME},
                RngSources.consumable(c.key()).sub("ed"));
    }
}
