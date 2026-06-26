package com.balatro.engine.eval;

import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerEffect;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Selector;
import java.util.List;
import java.util.Map;

/**
 * The interpreter for the {@link Effect} grammar — turns a scoring-time effect node into a runtime
 * {@link JokerEffect} contribution (or {@code null} for "nothing to score"). Lives in the engine; the
 * {@link Effect} types stay pure data. Action effects (the consumable/boss verbs) contribute nothing to a
 * played hand's count-up and fall to {@code null}; {@code Run} interprets those separately. Behaviour-
 * identical to the old {@code Effect.apply} methods — only the dispatch moved here.
 */
public final class EffectInterpreter {

    private EffectInterpreter() {}

    /** Apply effects in order, chaining the non-null contributions into one {@code extra} chain. */
    public static JokerEffect applyAll(List<Effect> effects, EvaluationContext ctx) {
        JokerEffect head = null;
        JokerEffect tail = null;
        for (Effect e : effects) {
            JokerEffect je = apply(e, ctx);
            if (je == null) continue;
            if (head == null) head = je; else tail.extra = je;
            tail = je;
            while (tail.extra != null) tail = tail.extra; // a contribution may already carry its own chain
        }
        return head;
    }

    /** Contribute a {@link JokerEffect} for a scoring moment, or {@code null} for "nothing to score here". */
    public static JokerEffect apply(Effect e, EvaluationContext ctx) {
        return switch (e) {
            case Effect.Score s -> score(s, ctx);
            case Effect.MutateCard mc -> {
                JokerEffect je = new JokerEffect();
                je.cardMod = mc.mod(); // scoring path: defer to the scored focus
                yield je;
            }
            case Effect.Create cr -> {
                JokerEffect je = new JokerEffect();
                je.create = cr.spec();
                yield je;
            }
            case Effect.Destroy d -> {
                JokerEffect je = new JokerEffect();
                switch (d.selector()) {
                    case Selector.Focus ignored -> je.destroyScored = true;        // the scored card (Sixth Sense)
                    case Selector.Discarded ignored -> je.destroyEventCards = true; // the discard set (Trading Card)
                    case Selector.Self ignored -> je.destroySelf = true;            // this joker (Gros Michel, Pizza)
                    default -> { /* run-loop selectors are applied by Run, not the scorer */ }
                }
                yield je;
            }
            case Effect.LevelHands lh -> {
                if (lh.scope() != Effect.LevelHands.Scope.PLAYED || lh.target() != com.balatro.grammar.Side.SELF || ctx.handType == null) yield null;
                JokerEffect je = new JokerEffect();
                je.levelUpHand = ctx.handType;
                je.levelUpAmount = Math.max(1, (int) Math.round(ValueResolver.resolve(lh.levels(), ctx)));
                yield je;
            }
            case Effect.Copy cp -> {
                JokerEffect je = new JokerEffect();
                if (cp.selector() instanceof Selector.Focus) je.copyScored = true; // DNA: copy scored card to deck
                yield je;
            }
            case Effect.GrantDiscards gd -> {
                JokerEffect je = new JokerEffect();
                je.grantDiscards = gd.amount();
                je.grantDiscardBlinds = gd.blinds();
                je.grantToOpponent = gd.recipient() == com.balatro.grammar.Side.OPPONENT;
                yield je;
            }
            case Effect.MutateState ms -> mutateState(ms, ctx);
            default -> null; // action effects contribute nothing to the played hand's count-up
        };
    }

    private static JokerEffect score(Effect.Score s, EvaluationContext ctx) {
        double v = ValueResolver.resolve(s.value(), ctx);
        Effect.Operation op = s.op();
        return switch (s.term()) {
            case CHIPS -> { requireAdd(op, s); yield v == 0 ? null : JokerEffect.chips(Math.round(v)); }
            case DOLLARS -> { requireAdd(op, s); yield v == 0 ? null : JokerEffect.dollars(Math.round(v)); }
            case RETRIGGERS -> {
                requireAdd(op, s);
                int r = (int) Math.round(v);
                yield r == 0 ? null : JokerEffect.repetitions(r);
            }
            case HELD_MULT -> {
                requireAdd(op, s);
                if (v == 0) yield null;
                JokerEffect e = new JokerEffect();
                e.hMult = v;
                yield e;
            }
            case MULT -> switch (op) {
                case ADD -> v == 0 ? null : JokerEffect.mult(v);
                case MULTIPLY -> v == 1.0 ? null : JokerEffect.xMult(v);
                case POWER -> {
                    if (v == 1.0) yield null;
                    JokerEffect e = new JokerEffect();
                    e.powMult = v;
                    yield e;
                }
                default -> throw new IllegalStateException(op + " is not a scoring operation (only ADD/MULTIPLY/POWER)");
            };
        };
    }

    /** Additive-only terms have no ×/^ accumulator slot — reject those cells loudly. */
    private static void requireAdd(Effect.Operation op, Effect.Score s) {
        if (op != Effect.Operation.ADD) {
            throw new IllegalStateException(op + " is not supported for term " + s.term()
                    + " (only ADD; no accumulator slot for that cell)");
        }
    }

    private static JokerEffect mutateState(Effect.MutateState ms, EvaluationContext ctx) {
        // Previewing a hand or running a Blueprint copy must not advance scaling counters.
        if (ctx.blueprintDepth != 0 || ctx.preview) return null;
        double amount = ms.by();
        if (ms.perCard() != null && ctx.eventCards != null) {
            int count = 0;
            var prev = ctx.scoredCard;
            for (var c : ctx.eventCards) {
                ctx.scoredCard = c;
                if (ConditionEvaluator.test(ms.perCard(), ctx)) count++;
            }
            ctx.scoredCard = prev;
            amount = ms.by() * count;
        }
        if (ms.scope() == Effect.MutateState.Scope.ALL_JOKERS && ctx.run != null) {
            for (var j : ctx.run.jokers()) writeInto(ms, ctx.run.jokerState(j), amount);
        } else {
            writeInto(ms, ctx.selfState(), amount);
        }
        return null; // a write contributes nothing to the running score
    }

    /** Write {@code op} of {@code amount} into one joker's state bag, keeping whole numbers as ints. */
    private static void writeInto(Effect.MutateState ms, Map<String, Object> state, double amount) {
        Object cur = state.getOrDefault(ms.var(), 0);
        double n = (cur instanceof Number num) ? num.doubleValue() : 0;
        double next = switch (ms.op()) {
            case ADD -> n + amount;
            case SET -> amount;                         // reset() authors SET 0 — RESET folded into the one Operation
            default -> throw new IllegalStateException(ms.op() + " is not a state-write op (only ADD/SET)");
        };
        if (next == Math.rint(next) && !Double.isInfinite(next)) {
            state.put(ms.var(), (int) next);
        } else {
            state.put(ms.var(), next);
        }
    }
}
