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
    @JsonSubTypes.Type(value = Effect.Destroy.class, name = "destroy"),
    @JsonSubTypes.Type(value = Effect.LevelHands.class, name = "levelHands"),
    @JsonSubTypes.Type(value = Effect.Copy.class, name = "copy"),
    @JsonSubTypes.Type(value = Effect.MutateState.class, name = "mutateState"),
    // --- consumable / action effects (applied by Run's action interpreter, not the scorer) ---
    @JsonSubTypes.Type(value = Effect.CreateCards.class, name = "createCards"),
    @JsonSubTypes.Type(value = Effect.JokerEdition.class, name = "jokerEdition"),
    @JsonSubTypes.Type(value = Effect.AddCards.class, name = "addCards"),
    @JsonSubTypes.Type(value = Effect.ConvertHand.class, name = "convertHand"),
    @JsonSubTypes.Type(value = Effect.OverwriteSelected.class, name = "overwriteSelected"),
    @JsonSubTypes.Type(value = Effect.CopyRandomJoker.class, name = "copyRandomJoker"),
    @JsonSubTypes.Type(value = Effect.NemesisDelevel.class, name = "nemesisDelevel"),
    @JsonSubTypes.Type(value = Effect.GrantDiscards.class, name = "grantDiscards"),
    // --- boss run-loop effects (applied by Run's action interpreter at a lifecycle trigger) ---
    @JsonSubTypes.Type(value = Effect.AdjustMoney.class, name = "adjustMoney"),
    @JsonSubTypes.Type(value = Effect.DelevelPlayedHand.class, name = "delevelPlayedHand"),
    @JsonSubTypes.Type(value = Effect.DiscardRandomHeld.class, name = "discardRandomHeld"),
    @JsonSubTypes.Type(value = Effect.FlipAndShuffleJokers.class, name = "flipAndShuffleJokers"),
    @JsonSubTypes.Type(value = Effect.DisableRandomJoker.class, name = "disableRandomJoker"),
    @JsonSubTypes.Type(value = Effect.DisableBoss.class, name = "disableBoss"),
    @JsonSubTypes.Type(value = Effect.SurviveBlind.class, name = "surviveBlind"),
    @JsonSubTypes.Type(value = Effect.AddPack.class, name = "addPack"),
    @JsonSubTypes.Type(value = Effect.AddShopVoucher.class, name = "addShopVoucher"),
    @JsonSubTypes.Type(value = Effect.ShopFlag.class, name = "shopFlag"),
    @JsonSubTypes.Type(value = Effect.AdjustHandSize.class, name = "adjustHandSize"),
    @JsonSubTypes.Type(value = Effect.CreateTag.class, name = "createTag"),
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

    /**
     * Destroy what the {@link Selector} names — the single destroy verb. The selector carries the target
     * that used to be baked into per-source verb names (DestroyScored/Discarded/Self/Targets/OtherJoker):
     * <ul>
     *   <li>scoring-time selectors set a flag the scorer/GameEvents consume during count-up:
     *       {@code Focus} → the scored card (Sixth Sense), {@code Discarded} → the discard set (Trading
     *       Card, PRE_DISCARD), {@code Self} → this joker (Gros Michel, Pizza);</li>
     *   <li>run-loop selectors ({@code Selected}/{@code AllInHand}/{@code RandomInHand}) are resolved by
     *       {@code Run}'s consumable interpreter (Hanged Man); {@code OtherJoker} by its blind-select
     *       joker-destruction machinery (Ceremonial, Madness).</li>
     * </ul>
     * Same internal flags/timing as before — only the authored verb is unified, so scoring + replay are
     * byte-identical.
     */
    record Destroy(Selector selector) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            switch (selector) {
                case Selector.Focus ignored -> e.destroyScored = true;       // the scored card (Sixth Sense)
                case Selector.Discarded ignored -> e.destroyEventCards = true; // the discard set (Trading Card)
                case Selector.Self ignored -> e.destroySelf = true;           // this joker (Gros Michel, Pizza)
                default -> { /* run-loop selectors are applied by Run, not the scorer */ }
            }
            return e;
        }
    }

    /**
     * Level up poker hand(s) by {@code levels} — the hand-leveling verb, with the scope an argument
     * instead of baked into the name (the old LevelUpHand/LevelAllHands/LevelMostPlayedHand). {@code PLAYED}
     * is scoring-time — the hand just played (Space, Burnt) — and sets the same {@code levelUpHand} flag;
     * {@code ALL} (Black Hole) and {@code MOST_PLAYED} (Orbital tag) are run-loop, applied by Run.
     */
    record LevelHands(Scope scope, Value levels) implements Effect {
        public enum Scope { PLAYED, ALL, MOST_PLAYED }

        public JokerEffect apply(EvaluationContext ctx) {
            if (scope != Scope.PLAYED || ctx.handType == null) return null; // ALL/MOST_PLAYED are run-loop
            JokerEffect e = new JokerEffect();
            e.levelUpHand = ctx.handType;
            e.levelUpAmount = Math.max(1, (int) Math.round(levels.resolve(ctx)));
            return e;
        }
    }

    /**
     * Copy what the {@link Selector} names, {@code count} times — the card-copy verb (the target is an
     * argument, not baked into the name). {@code Focus} sets the scoring-time flag that adds a permanent
     * copy of the scored card to the deck (DNA); {@code Selected} is resolved by {@code Run}'s consumable
     * interpreter, duplicating the chosen card {@code count}× into hand+deck (Cryptid). Same internal flag
     * for the scoring-time case — only the authored verb is unified.
     */
    record Copy(Selector selector, int count) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            if (selector instanceof Selector.Focus) e.copyScored = true; // DNA: copy the scored card to the deck
            return e;
        }
    }

    // --- consumable / action effects: pure data, applied immediately to the run by Run's action
    //     interpreter (which resolves the Selector against the live hand / jokers / RNG). They are part of
    //     the one Effect vocabulary so a consumable is authored as a List<Effect>, exactly like a joker. ---

    /** Add {@code count} new numbered cards (random rank/suit) with an enhancement to the deck (Incantation). */
    record CreateCards(int count, Enhancement enhancement) implements Effect {}


    /**
     * Add an edition to a random owned joker. {@code edition == NONE} means "a random Foil/Holo/Poly" (Wheel
     * of Fortune). {@code chanceDenominator} gates the whole effect (Wheel = 4 → 1-in-4; others = 1 → always).
     * {@code handSizeDelta} and {@code destroyOtherJokers} carry Ectoplasm's -1 hand size and Hex's wipe.
     */
    record JokerEdition(Edition edition, int chanceDenominator,
                        int handSizeDelta, boolean destroyOtherJokers) implements Effect {}

    /** Add {@code count} cards of a {@code rankClass} (Familiar = 3 FACE, Grim = 2 ACE) with a fixed
     *  {@code enhancement} ({@code null} = random per card) to hand+deck. (The old Generate composite is
     *  gone — a generative consumable is now just an ordered {@code List<Effect>}: Destroy / Create / AddCards
     *  / AdjustMoney.) */
    record AddCards(RankClass rankClass, int count, Enhancement enhancement) implements Effect {
        public enum RankClass { FACE, ACE, NUMBER, ANY }
    }

    /** Convert EVERY card in hand to one random {@code axis} — SUIT (Sigil) or RANK (Ouija) — with an
     *  optional {@code handSizeDelta} (Ouija's -1). The axis is an argument, not two fused booleans. */
    record ConvertHand(Axis axis, int handSizeDelta) implements Effect {
        public enum Axis { SUIT, RANK }
    }

    /** Overwrite the first selected card with the attributes of the second (Death: left becomes right). */
    record OverwriteSelected() implements Effect {}

    /** Copy a random owned joker (edition-free); optionally destroy all others (Ankh). */
    record CopyRandomJoker(boolean destroyOthers) implements Effect {}


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

    /** Mr Bones: survive a failed blind (the run continues) and consume the joker that did it. Emitted from
     *  a {@code BLIND_LOST} rule gated on {@code BLIND_PROGRESS} — the Blind lifecycle made hookable. */
    record SurviveBlind() implements Effect {}

    /** Add a booster pack to the next shop (Charm/Meteor/Buffoon/Standard/Ethereal tags). {@code kind} and
     *  {@code size} are the {@code PackCatalog.Kind}/{@code Size} names — strings so the model layer needn't
     *  depend on the game layer; {@code Run} resolves them when the shop opens. */
    record AddPack(String kind, String size) implements Effect {}


    /** Add an extra Voucher to the next shop (Voucher tag). */
    record AddShopVoucher() implements Effect {}

    /** Set a shop policy flag for the next shop: {@code "COUPON"} (free initial items) or {@code "D6"}
     *  ($0 base reroll) — Coupon / D6 tags. */
    record ShopFlag(String flag) implements Effect {}

    /** Bump the hand size by {@code delta} for the current round (Juggle tag: +3 this blind). */
    record AdjustHandSize(int delta) implements Effect {}

    /** Grant a free skip {@code tag} (honouring a held Double Tag) — Diet Cola creates a Double Tag on sell. */
    record CreateTag(String tag) implements Effect {}

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
