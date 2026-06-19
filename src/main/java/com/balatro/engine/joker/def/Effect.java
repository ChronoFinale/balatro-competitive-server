package com.balatro.engine.joker.def;

import com.balatro.engine.card.CardMod;
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
})
public sealed interface Effect {

    /** Contribute a {@link JokerEffect} for this moment, or {@code null} for a no-op (identity-skip). */
    JokerEffect apply(EvaluationContext ctx);

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

    /** The numeric scoring ops — {@code Modify(scoring.slot)} under the hood. */
    enum Op { CHIPS, MULT, XMULT, POW_MULT, DOLLARS, REPETITIONS, HELD_MULT }

    /** A numeric contribution to the running score (+chips / +mult / x mult / ^mult / +$ / retrigger / held-mult). */
    record Score(Op op, Value value) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            double v = value.resolve(ctx);
            return switch (op) {
                case CHIPS -> v == 0 ? null : JokerEffect.chips(Math.round(v)).msg("+" + fmt(v) + " Chips");
                case MULT -> v == 0 ? null : JokerEffect.mult(v).msg("+" + fmt(v) + " Mult");
                case XMULT -> v == 1.0 ? null : JokerEffect.xMult(v).msg("x" + fmt(v) + " Mult");
                case POW_MULT -> {
                    if (v == 1.0) yield null;
                    JokerEffect e = new JokerEffect();
                    e.powMult = v;
                    yield e.msg("^" + fmt(v) + " Mult");
                }
                case DOLLARS -> v == 0 ? null : JokerEffect.dollars(Math.round(v)).msg("+$" + fmt(v));
                case REPETITIONS -> {
                    int r = (int) Math.round(v);
                    yield r == 0 ? null : JokerEffect.repetitions(r).msg("Retrigger");
                }
                case HELD_MULT -> {
                    if (v == 0) yield null;
                    JokerEffect e = new JokerEffect();
                    e.hMult = v;
                    yield e.msg("+" + fmt(v) + " Mult");
                }
            };
        }
    }

    /** Permanently mutate the card in focus (Hiker / Midas / Vampire). */
    record MutateCard(CardMod mod) implements Effect {
        public JokerEffect apply(EvaluationContext ctx) {
            JokerEffect e = new JokerEffect();
            e.cardMod = mod;
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
