package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.exec.Command;
import com.balatro.engine.joker.Contribution;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerResult;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Trigger;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ReplayEntry;
import com.balatro.engine.state.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Raises LIFECYCLE triggers (everything outside the scoring pipeline) through the
 * same {@code calculate(context)} dispatch the ScoringEngine uses. This is the
 * proof that "all the other triggers" are not a separate system — they share the
 * exact mechanism, just fired from the round/shop/discard flows.
 *
 * <p>Effects returned by lifecycle jokers currently apply {@code dollars} to the
 * authoritative {@link RunState#money}; card creation/destruction hooks land here
 * as the round/shop loop grows.
 */
public final class GameEvents {

    private GameEvents() {}

    /** Generic raise: dispatch a trigger to every joker, apply economy effects. */
    public static List<ReplayEntry> raise(Trigger trigger, RunState run, RandomStreams rng,
                                          Consumer<EvaluationContext> setup) {
        List<ReplayEntry> log = new ArrayList<>();
        List<Joker> jokers = run.jokers();
        EvaluationContext ctx = new EvaluationContext();
        ctx.run = run;
        ctx.rng = rng;
        ctx.jokers = jokers;
        ctx.phase = trigger;
        if (setup != null) setup.accept(ctx);

        List<Joker> consumed = new ArrayList<>();
        for (int i = 0; i < jokers.size(); i++) {
            ctx.phase = trigger;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            JokerResult e = jokers.get(i).calculate(ctx);
            applyEconomy(e, run, log, jokers.get(i).name(), ctx);
            if (wantsSelfDestroy(e)) consumed.add(jokers.get(i)); // remove after the pass, never mid-iteration
        }
        for (Joker j : consumed) {
            run.jokers().remove(j);
            log.add(new ReplayEntry(j.name(), ReplayEntry.Kind.DESTROY, 0, 0, 0));
        }
        return log;
    }

    /** End of a won round: joker economy + Gold-card payouts on held cards. */
    public static List<ReplayEntry> endOfRound(RunState run, RandomStreams rng) {
        return endOfRound(run, rng, false);
    }

    /** End of a won round; {@code bossDefeated} marks a Boss-blind win (Rocket). */
    public static List<ReplayEntry> endOfRound(RunState run, RandomStreams rng, boolean bossDefeated) {
        List<ReplayEntry> log = raise(Trigger.END_OF_ROUND, run, rng, ctx -> {
            ctx.eventCards = run.hand;
            ctx.bossDefeated = bossDefeated;
        });
        for (Card c : run.hand) {
            if (c.enhancement == Enhancement.GOLD) {
                run.money += 3;
                log.add(new ReplayEntry(c.toString(), ReplayEntry.Kind.DOLLARS, 3, 0, 0));
            }
        }
        return log;
    }

    /** Before a discard resolves; jokers see the discarded set via {@code eventCards}, and
     *  {@code handType} is the discarded cards' poker hand (Burnt levels it up). */
    public static List<ReplayEntry> preDiscard(RunState run, RandomStreams rng, List<Card> discarded) {
        return raise(Trigger.PRE_DISCARD, run, rng, ctx -> {
            ctx.eventCards = discarded;
            ctx.handType = com.balatro.engine.hand.HandEvaluator.evaluate(discarded).type();
        });
    }

    /** A consumable (Tarot/Planet/Spectral) is used. */
    public static List<ReplayEntry> useConsumable(RunState run, RandomStreams rng, com.balatro.grammar.ConsumableKind category) {
        return raise(Trigger.USE_CONSUMABLE, run, rng, ctx -> ctx.consumableType = category);
    }

    private static void applyEconomy(JokerResult e, RunState run, List<ReplayEntry> log, String source,
                                     EvaluationContext ctx) {
        for (Contribution c : e.contributions()) { // economy dollars (forward order; not the scoring defer)
            if (c.term() == Effect.Term.DOLLARS) {
                run.money += (long) c.amount();
                log.add(new ReplayEntry(source, ReplayEntry.Kind.DOLLARS, c.amount(), 0, 0));
            }
        }
        for (Command cmd : e.commands()) {
            switch (cmd) {
                case Command.DestroyEventCards ignored -> { // Trading Card: destroy the discarded set
                    if (ctx.eventCards != null) {
                        for (Card c : ctx.eventCards) c.destroyed = true; // Run.play purges destroyed from the deck
                        run.triggerLog.add(source + " triggered → destroyed the discarded card(s)");
                        log.add(new ReplayEntry(source, ReplayEntry.Kind.DESTROY, 0, 0, 0));
                    }
                }
                case Command.Create cr -> {
                    if (run.queues != null) {
                        int before = run.consumables.size();
                        com.balatro.engine.consumable.Creation.apply(run, cr.spec(), run.queues);
                        if (run.consumables.size() > before) {
                            String created = run.consumables.get(run.consumables.size() - 1); // the just-created item
                            run.triggerLog.add(source + " triggered → created " + created);
                            log.add(new ReplayEntry(source, ReplayEntry.Kind.CREATE, 0, 0, 0));
                        }
                    }
                }
                case Command.LevelHand lh -> {
                    for (int i = 0; i < lh.levels(); i++) run.levelUpHand(lh.hand());
                    run.triggerLog.add(source + " triggered → leveled " + lh.hand() + " x" + lh.levels());
                    log.add(new ReplayEntry(source, ReplayEntry.Kind.LEVELUP, lh.levels(), 0, 0));
                }
                case Command.GrantDiscards gd -> { // Pizza: temp discards to self, or to the Nemesis (Match-supplied)
                    RunState target = gd.recipient() == com.balatro.grammar.Side.OPPONENT ? ctx.opponentRun : run;
                    if (target != null) {
                        target.grantTempDiscards(gd.amount(), gd.blinds());
                        run.triggerLog.add(source + " triggered → +" + gd.amount() + " discard(s)");
                        log.add(new ReplayEntry(source, ReplayEntry.Kind.DISCARDS, gd.amount(), 0, 0));
                    }
                }
                default -> { /* DestroySelf handled by wantsSelfDestroy; scoring-only commands don't occur here */ }
            }
        }
    }

    /** True if the result asks to consume its joker (Gros Michel / Pizza). */
    private static boolean wantsSelfDestroy(JokerResult e) {
        for (Command cmd : e.commands()) if (cmd instanceof Command.DestroySelf) return true;
        return false;
    }
}
