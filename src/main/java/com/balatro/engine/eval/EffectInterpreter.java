package com.balatro.engine.eval;

import com.balatro.engine.exec.Command;
import com.balatro.engine.joker.Contribution;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.JokerResult;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The interpreter for the {@link Effect} grammar — turns a scoring-time effect node into a {@link JokerResult}
 * (typed {@link Contribution}s for the count-up + resolved {@link Command}s for side-effects). Lives in the
 * engine; the {@link Effect} types stay pure data. Action effects (the consumable/boss verbs) contribute
 * nothing to a played hand and yield {@link JokerResult#EMPTY}; {@code Run} interprets those separately.
 *
 * <p>The scoring axes are modeled ONCE here: each {@link Effect.Score} cell maps to one {@code Contribution}
 * carrying the grammar's own {@link Effect.Operation} × {@link Effect.Term}. Zero/identity cells yield EMPTY
 * (the old {@code null}), so they neither score nor appear in the replay log.
 */
public final class EffectInterpreter {

    private EffectInterpreter() {}

    /** Apply effects in order, concatenating their contributions + commands into one result (in order). */
    public static JokerResult applyAll(List<Effect> effects, EvaluationContext ctx) {
        List<Contribution> contribs = null;
        List<Command> cmds = null;
        for (Effect e : effects) {
            JokerResult r = apply(e, ctx);
            if (r.contributions().isEmpty() && r.commands().isEmpty()) continue;
            if (contribs == null) { contribs = new ArrayList<>(); cmds = new ArrayList<>(); }
            contribs.addAll(r.contributions());
            cmds.addAll(r.commands());
        }
        return contribs == null ? JokerResult.EMPTY : new JokerResult(contribs, cmds);
    }

    /** Interpret one effect into a {@link JokerResult}, or {@link JokerResult#EMPTY} for "nothing here". */
    public static JokerResult apply(Effect e, EvaluationContext ctx) {
        return switch (e) {
            case Effect.Score s -> score(s, ctx);
            case Effect.MutateCard mc -> command(new Command.MutateScoredCard(mc.mod())); // scoring path: the scored focus
            case Effect.Create cr -> command(new Command.Create(cr.spec()));
            case Effect.Destroy d -> switch (d.selector()) {
                case Selector.Focus ignored -> command(new Command.DestroyScored());      // the scored card (Sixth Sense)
                case Selector.Discarded ignored -> command(new Command.DestroyEventCards()); // the discard set (Trading Card)
                case Selector.Self ignored -> command(new Command.DestroySelf());          // this joker (Gros Michel, Pizza)
                default -> JokerResult.EMPTY; // run-loop selectors are applied by Run, not the scorer
            };
            case Effect.LevelHands lh -> {
                if (lh.scope() != Effect.LevelHands.Scope.PLAYED
                        || lh.target() != com.balatro.grammar.Side.SELF || ctx.handType == null) yield JokerResult.EMPTY;
                int amt = Math.max(1, (int) Math.round(ValueResolver.resolve(lh.levels(), ctx)));
                yield command(new Command.LevelHand(ctx.handType, amt));
            }
            case Effect.Copy cp ->
                    cp.selector() instanceof Selector.Focus ? command(new Command.CopyScored()) : JokerResult.EMPTY;
            case Effect.GrantDiscards gd ->
                    command(new Command.GrantDiscards(gd.amount(), gd.blinds(), gd.recipient()));
            case Effect.MutateState ms -> { mutateState(ms, ctx); yield JokerResult.EMPTY; }
            default -> JokerResult.EMPTY; // action effects contribute nothing to the played hand's count-up
        };
    }

    private static JokerResult command(Command c) {
        return new JokerResult(List.of(), List.of(c));
    }

    private static JokerResult contribution(Effect.Operation op, Effect.Term term, double amount) {
        return new JokerResult(List.of(new Contribution(op, term, amount, null)), List.of());
    }

    private static JokerResult score(Effect.Score s, EvaluationContext ctx) {
        double v = ValueResolver.resolve(s.value(), ctx);
        Effect.Operation op = s.op();
        return switch (s.term()) {
            case CHIPS -> { requireAdd(op, s); yield v == 0 ? JokerResult.EMPTY
                    : contribution(Effect.Operation.ADD, Effect.Term.CHIPS, Math.round(v)); }
            case DOLLARS -> { requireAdd(op, s); yield v == 0 ? JokerResult.EMPTY
                    : contribution(Effect.Operation.ADD, Effect.Term.DOLLARS, Math.round(v)); }
            case RETRIGGERS -> {
                requireAdd(op, s);
                int r = (int) Math.round(v);
                yield r == 0 ? JokerResult.EMPTY : contribution(Effect.Operation.ADD, Effect.Term.RETRIGGERS, r);
            }
            case HELD_MULT -> { requireAdd(op, s); yield v == 0 ? JokerResult.EMPTY
                    : contribution(Effect.Operation.ADD, Effect.Term.HELD_MULT, v); }
            case MULT -> switch (op) {
                case ADD -> v == 0 ? JokerResult.EMPTY : contribution(Effect.Operation.ADD, Effect.Term.MULT, v);
                case MULTIPLY -> v == 1.0 ? JokerResult.EMPTY : contribution(Effect.Operation.MULTIPLY, Effect.Term.MULT, v);
                case POWER -> v == 1.0 ? JokerResult.EMPTY : contribution(Effect.Operation.POWER, Effect.Term.MULT, v);
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

    private static void mutateState(Effect.MutateState ms, EvaluationContext ctx) {
        // Previewing a hand or running a Blueprint copy must not advance scaling counters.
        if (ctx.blueprintDepth != 0 || ctx.preview) return;
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
