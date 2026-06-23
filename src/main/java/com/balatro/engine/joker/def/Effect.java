package com.balatro.engine.joker.def;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerEffect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * One thing a rule does — the sealed sum derived in docs 42/44 ({@code Modify} family + structural verbs).
 * Each {@link #apply} contributes a runtime {@link JokerEffect}; a rule carries an ordered {@code List<Effect>}
 * (a rules effect chain is just an ordered list). This is the closed authoring set;
 * {@code Score}'s ops are the numeric writes (conceptually {@code Modify(scoring.slot)}), the rest are the
 * structural/control verbs. Serialized to JSON with a {@code "type"} discriminator like {@link Condition}.
 *
 * <p>Operates on the implicit context focus (scored card / hand); an explicit {@code Selector} arrives
 * when {@code MutateCard}/{@code Destroy} need to target something other than the focus.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Effect.Score.class, name = "score"),
    @JsonSubTypes.Type(value = Effect.MutateCard.class, name = "mutateCard"),
    @JsonSubTypes.Type(value = Effect.Create.class, name = "create"),
    @JsonSubTypes.Type(value = Effect.DestroyScored.class, name = "destroyScored"),
    @JsonSubTypes.Type(value = Effect.DestroyDiscarded.class, name = "destroyDiscarded"),
    @JsonSubTypes.Type(value = Effect.LevelUpHand.class, name = "levelUpHand"),
    @JsonSubTypes.Type(value = Effect.CopyScored.class, name = "copyScored"),
    @JsonSubTypes.Type(value = Effect.MutateState.class, name = "mutateState"),
    // --- consumable / action effects (applied by Run's action interpreter, not the scorer) ---
    @JsonSubTypes.Type(value = Effect.DestroyTargets.class, name = "destroyTargets"),
    @JsonSubTypes.Type(value = Effect.CreateCards.class, name = "createCards"),
    @JsonSubTypes.Type(value = Effect.LevelAllHands.class, name = "levelAllHands"),
    @JsonSubTypes.Type(value = Effect.JokerEdition.class, name = "jokerEdition"),
    @JsonSubTypes.Type(value = Effect.Generate.class, name = "generate"),
    @JsonSubTypes.Type(value = Effect.ConvertHand.class, name = "convertHand"),
    @JsonSubTypes.Type(value = Effect.CopySelected.class, name = "copySelected"),
    @JsonSubTypes.Type(value = Effect.OverwriteSelected.class, name = "overwriteSelected"),
    @JsonSubTypes.Type(value = Effect.CopyRandomJoker.class, name = "copyRandomJoker"),
    @JsonSubTypes.Type(value = Effect.CopyLastConsumable.class, name = "copyLastConsumable"),
    @JsonSubTypes.Type(value = Effect.NemesisDelevel.class, name = "nemesisDelevel"),
    @JsonSubTypes.Type(value = Effect.DestroySelf.class, name = "destroySelf"),
    @JsonSubTypes.Type(value = Effect.GrantDiscards.class, name = "grantDiscards"),
    // --- boss run-loop effects (applied by Run's action interpreter at a lifecycle trigger) ---
    @JsonSubTypes.Type(value = Effect.AdjustMoney.class, name = "adjustMoney"),
    @JsonSubTypes.Type(value = Effect.DelevelPlayedHand.class, name = "delevelPlayedHand"),
    @JsonSubTypes.Type(value = Effect.DiscardRandomHeld.class, name = "discardRandomHeld"),
    @JsonSubTypes.Type(value = Effect.FlipAndShuffleJokers.class, name = "flipAndShuffleJokers"),
    @JsonSubTypes.Type(value = Effect.DisableRandomJoker.class, name = "disableRandomJoker"),
    @JsonSubTypes.Type(value = Effect.DisableBoss.class, name = "disableBoss"),
})
public sealed interface Effect {

    /**
     * Contribute a {@link JokerEffect} for a scoring moment, or {@code null} for "nothing to score here".
     * Scoring effects (the {@code Modify} family) override this; action effects (the consumable verbs below)
     * inherit the {@code null} default — they never run in the scorer, only through {@code Run}'s action
     * interpreter, so they contribute nothing to a played hand's count-up.
     */
    default JokerEffect apply(EvaluationContext ctx) {
        return null;
    }

    // --- readable factories for the common score cells (operation × term), so authoring/tests don't
    //     spell out both axes for every contribution. The grid is the model; these are the named cells. ---
    static Score chips(Value v)      { return new Score(Operation.ADD, Term.CHIPS, v); }
    static Score mult(Value v)       { return new Score(Operation.ADD, Term.MULT, v); }
    static Score xMult(Value v)      { return new Score(Operation.MULTIPLY, Term.MULT, v); }
    static Score powMult(Value v)    { return new Score(Operation.POWER, Term.MULT, v); }
    static Score dollars(Value v)    { return new Score(Operation.ADD, Term.DOLLARS, v); }
    static Score retriggers(Value v) { return new Score(Operation.ADD, Term.RETRIGGERS, v); }
    static Score heldMult(Value v)   { return new Score(Operation.ADD, Term.HELD_MULT, v); }

    /** Apply effects in order, chaining the non-null contributions into one {@code extra} chain. */
    static JokerEffect applyAll(List<Effect> effects, EvaluationContext ctx) {
        JokerEffect head = null;
        JokerEffect tail = null;
        for (Effect e : effects) {
            JokerEffect je = e.apply(ctx);
            if (je == null) continue;
            if (head == null) {
                head = je;
            } else {
                tail.extra = je;
            }
            tail = je;
            while (tail.extra != null) tail = tail.extra; // a contribution may already carry its own chain
        }
        return head;
    }

    /** A term of the score a contribution lands in — chips/mult (and the dollars/retriggers/held-mult
     *  side-channels), separated from the {@link Operation}. The ephemeral scoring twin of a {@link Property}
     *  (which is durable run state). (docs 49/50). */
    enum Term { CHIPS, MULT, DOLLARS, RETRIGGERS, HELD_MULT }

    /**
     * How a contribution combines with its term — the operation, separated from the term. ONE shared
     * verb vocabulary across scoring and money (and conceptually {@code Modify}); each effect supports the
     * subset that makes sense for it: scoring uses {@code ADD/MULTIPLY/POWER}, money uses
     * {@code ADD/SUBTRACT/MULTIPLY/DIVIDE/SET}. Unsupported cells are rejected loudly, not silently coerced.
     */
    enum Operation { ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER, SET }

    /**
     * A numeric contribution to the running score, modelled as {@code operation × term × value} — e.g.
     * {@code (ADD, CHIPS)} = +chips, {@code (MULTIPLY, MULT)} = ×mult (the old {@code XMULT}),
     * {@code (POWER, MULT)} = ^mult. The fused {@code Op} enum is gone: the two axes (what it does / what it
     * deals with) are independent, so empty cells (×chips) are expressible once an accumulator slot exists.
     */
    record Score(Operation op, Term term, Value value) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            double v = value.resolve(ctx);
            return switch (term) {
                case CHIPS -> { requireAdd(); yield v == 0 ? null : JokerEffect.chips(Math.round(v)).msg("+" + fmt(v) + " Chips"); }
                case DOLLARS -> { requireAdd(); yield v == 0 ? null : JokerEffect.dollars(Math.round(v)).msg("+$" + fmt(v)); }
                case RETRIGGERS -> {
                    requireAdd();
                    int r = (int) Math.round(v);
                    yield r == 0 ? null : JokerEffect.repetitions(r).msg("Retrigger");
                }
                case HELD_MULT -> {
                    requireAdd();
                    if (v == 0) yield null;
                    JokerEffect e = new JokerEffect();
                    e.hMult = v;
                    yield e.msg("+" + fmt(v) + " Mult");
                }
                case MULT -> switch (op) {
                    case ADD -> v == 0 ? null : JokerEffect.mult(v).msg("+" + fmt(v) + " Mult");
                    case MULTIPLY -> v == 1.0 ? null : JokerEffect.xMult(v).msg("x" + fmt(v) + " Mult");
                    case POWER -> {
                        if (v == 1.0) yield null;
                        JokerEffect e = new JokerEffect();
                        e.powMult = v;
                        yield e.msg("^" + fmt(v) + " Mult");
                    }
                    default -> throw new IllegalStateException(op + " is not a scoring operation (only ADD/MULTIPLY/POWER)");
                };
            };
        }

        /** The additive-only subjects have no ×/^ accumulator slot — reject those cells loudly rather than
         *  silently treating them as ADD. */
        private void requireAdd() {
            if (op != Operation.ADD) {
                throw new IllegalStateException(op + " is not supported for term " + term
                        + " (only ADD; no accumulator slot for that cell)");
            }
        }
    }

    /**
     * Mutate cards: the {@link Selector} says which, the {@link CardMod} says how (enhance / convert suit /
     * seal / edition / add-chips). With {@link Selector.Focus} this is a joker mutating the scored card — the
     * scorer defers it onto {@code cardMod} and streams the change into the count-up (Hiker / Midas / Vampire);
     * with {@link Selector.Selected} (etc.) it's a consumable mutating chosen cards, applied immediately by
     * {@code Run}'s action interpreter (Magician / Star / Aura). One verb, two interpreters.
     */
    record MutateCard(Selector selector, CardMod mod) implements Effect {

        /** A focus mutation (the joker default) — back-compat for {@code mutateCard(mod)} and selector-less JSON. */
        public MutateCard {
            selector = selector == null ? new Selector.Focus() : selector;
        }

        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.cardMod = mod; // scoring path: defer to the scored focus, exactly as before
            return e;
        }
    }

    /** Create cards / consumables / jokers (8 Ball / Cartomancer / Riff-Raff). */
    record Create(CreateSpec spec) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.create = spec;
            return e;
        }
    }

    /** Destroy the scored card in focus (Sixth Sense). */
    record DestroyScored() implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.destroyScored = true;
            return e;
        }
    }

    /** Destroy the discarded set (Trading Card; PRE_DISCARD). */
    record DestroyDiscarded() implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.destroyEventCards = true;
            return e;
        }
    }

    /** Level up the played poker hand by {@code levels} (Space / Burnt). */
    record LevelUpHand(Value levels) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            if (ctx.handType == null) return null;
            JokerEffect e = new JokerEffect();
            e.levelUpHand = ctx.handType;
            e.levelUpAmount = Math.max(1, (int) Math.round(levels.resolve(ctx)));
            return e;
        }
    }

    /** Add a permanent copy of the scored card to the deck (DNA). */
    record CopyScored() implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.copyScored = true;
            return e;
        }
    }

    // --- consumable / action effects: pure data, applied immediately to the run by Run's action
    //     interpreter (which resolves the Selector against the live hand / jokers / RNG). They are part of
    //     the one Effect vocabulary so a consumable is authored as a List<Effect>, exactly like a joker. ---

    /** Destroy the selector's targets, removing them from the deck permanently (Hanged Man). */
    record DestroyTargets(Selector selector) implements Effect {}

    /** Add {@code count} new numbered cards (random rank/suit) with an enhancement to the deck (Incantation). */
    record CreateCards(int count, Enhancement enhancement) implements Effect {}

    /** Level up every poker hand by 1 (Black Hole). */
    record LevelAllHands() implements Effect {}

    /**
     * Add an edition to a random owned joker. {@code edition == NONE} means "a random Foil/Holo/Poly" (Wheel
     * of Fortune). {@code chanceDenominator} gates the whole effect (Wheel = 4 → 1-in-4; others = 1 → always).
     * {@code handSizeDelta} and {@code destroyOtherJokers} carry Ectoplasm's -1 hand size and Hex's wipe.
     */
    record JokerEdition(Edition edition, int chanceDenominator,
                        int handSizeDelta, boolean destroyOtherJokers) implements Effect {}

    /**
     * The "generative" consumables (Emperor, High Priestess, Judgement, The Soul, Wraith, Hermit, Temperance,
     * Immolate, Familiar, Grim). Applied in order, each part optional (null / 0 = skip): destroy N random hand
     * cards, then create consumables/jokers/cards, then add rank-class cards, then run a money op.
     */
    record Generate(CreateSpec create, int destroyRandomInHand, AddCards add, MoneyOp money) implements Effect {

        /** Add {@code count} cards of {@code rankClass}; {@code enhancement == null} = random per card. */
        public record AddCards(RankClass rankClass, int count, Enhancement enhancement) {
            public enum RankClass { FACE, ACE, NUMBER, ANY }
        }

        /** Double current money (capped), gain total joker sell value (capped), gain a flat amount, or set
         *  money to a fixed value. {@code amount} is the cap / delta / target. */
        public record MoneyOp(Kind kind, int amount) {
            public enum Kind { DOUBLE_CAP, SELL_VALUE_CAP, FLAT, SET }
        }
    }

    /** Convert EVERY card in hand to one random suit (Sigil) or rank (Ouija); Ouija also -1 hand size. */
    record ConvertHand(boolean toRandomSuit, boolean toRandomRank, int handSizeDelta) implements Effect {}

    /** Create {@code copies} exact copies of the single selected card (Cryptid). */
    record CopySelected(int copies) implements Effect {}

    /** Overwrite the first selected card with the attributes of the second (Death: left becomes right). */
    record OverwriteSelected() implements Effect {}

    /** Copy a random owned joker (edition-free); optionally destroy all others (Ankh). */
    record CopyRandomJoker(boolean destroyOthers) implements Effect {}

    /** Create a copy of the last Tarot or Planet used this run (The Fool). */
    record CopyLastConsumable() implements Effect {}

    /** Asteroid (MP): delevel the nemesis's highest poker hand (resolved by the Match). */
    record NemesisDelevel() implements Effect {}

    // --- boss run-loop effects: pure data fired by Run at a lifecycle trigger (ON_HAND_PLAYED), never in
    //     the scorer. They mutate run state directly through Run's action interpreter, exactly like the
    //     consumable verbs above — one Effect vocabulary, so a boss is authored as Rules like a joker. ---

    /** Change the run's money outside scoring. The verb carries the direction so {@code amount} is a plain
     *  magnitude (no signed operands): {@code ADD}/{@code SUBTRACT} a (possibly per-card) amount — floored
     *  at the run's minimum money — {@code MULTIPLY}/{@code DIVIDE} the balance, or {@code SET} it to a
     *  fixed value. The Tooth is {@code SUBTRACT(count(played))}; the Ox is {@code SET(0)}. Mirrors the
     *  scoring op model (MULT vs XMULT vs POW_MULT), where each arithmetic verb is its own primitive. */
    record AdjustMoney(Operation op, Value amount) implements Effect {}

    /** Drop the played poker hand's level by one (The Arm). No-op if there is no played hand. */
    record DelevelPlayedHand() implements Effect {}

    /** Discard {@code count} random held cards, then refill the hand (The Hook). */
    record DiscardRandomHeld(int count) implements Effect {}

    /** Flip the owned Jokers face down and shuffle their order — which reorders scoring (Amber Acorn,
     *  at blind start). */
    record FlipAndShuffleJokers() implements Effect {}

    /** Switch off one random owned Joker for the coming hand, re-arming the rest (Crimson Heart, pre-hand). */
    record DisableRandomJoker() implements Effect {}

    /** Disable this boss's ability for the rest of the blind (Verdant Leaf, when any Joker is sold). */
    record DisableBoss() implements Effect {}

    /** Consume this joker — remove it from the run (Pizza on PvP end). Applied by {@code GameEvents}. */
    record DestroySelf() implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.destroySelf = true;
            return e;
        }
    }

    /** Grant a temporary discard bonus for {@code blinds} blinds, to this run or — when {@code toOpponent} —
     *  the Nemesis (Pizza). The Match supplies the opponent run on the context; {@code GameEvents} applies it. */
    record GrantDiscards(boolean toOpponent, int amount, int blinds) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.grantDiscards = amount;
            e.grantDiscardBlinds = blinds;
            e.grantToOpponent = toOpponent;
            return e;
        }
    }

    /**
     * Persistently write a scaling counter — Ride the Bus's streak, Constellation's planet count. This is
     * {@code Modify(self.state)}: the old {@code Mutation}, now just an effect. It self-gates to {@code
     * blueprintDepth == 0 && !preview} so a Blueprint copy or a scoring preview re-reads the counter without
     * advancing it twice, and contributes no score (returns {@code null}, so the rules loop falls through to
     * the next rule). {@code perCard} ⇒ an {@code ADD} adds {@code by} per matching {@code eventCard} (Hit the
     * Road: +0.5 per Jack discarded); {@code scope} writes to self (Egg) or every owned joker (Gift Card).
     */
    record MutateState(String var, Op op, double by, Condition perCard, Scope scope) implements Effect {

        public enum Op { ADD, SET, RESET }

        /** Whose state bag the write targets: the joker itself, or every owned joker (Gift Card). */
        public enum Scope { SELF, ALL_JOKERS }

        public MutateState {
            scope = scope == null ? Scope.SELF : scope; // omitted in JSON ⇒ writes to self
        }

        public JokerEffect apply(EvaluationContext ctx) {
            // Previewing a hand or running a Blueprint copy must not advance scaling counters.
            if (ctx.blueprintDepth != 0 || ctx.preview) return null;
            double amount = by;
            if (perCard != null && ctx.eventCards != null) {
                int count = 0;
                var prev = ctx.scoredCard;
                for (var c : ctx.eventCards) {
                    ctx.scoredCard = c;
                    if (perCard.test(ctx)) count++;
                }
                ctx.scoredCard = prev;
                amount = by * count;
            }
            if (scope == Scope.ALL_JOKERS && ctx.run != null) {
                for (var j : ctx.run.jokers()) writeInto(ctx.run.jokerState(j), amount);
            } else {
                writeInto(ctx.selfState(), amount);
            }
            return null; // a write contributes nothing to the running score
        }

        /** Write {@code op} of {@code amount} into one joker's state bag, keeping whole numbers as ints. */
        private void writeInto(java.util.Map<String, Object> state, double amount) {
            Object cur = state.getOrDefault(var, 0);
            double n = (cur instanceof Number num) ? num.doubleValue() : 0;
            double next = switch (op) {
                case ADD -> n + amount;
                case SET -> amount;
                case RESET -> 0;
            };
            if (next == Math.rint(next) && !Double.isInfinite(next)) {
                state.put(var, (int) next);
            } else {
                state.put(var, next);
            }
        }
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return Double.toString(v);
    }
}
