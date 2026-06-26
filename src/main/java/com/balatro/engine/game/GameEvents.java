package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerEffect;
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
            JokerEffect e = jokers.get(i).calculate(ctx);
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

    private static void applyEconomy(JokerEffect e, RunState run, List<ReplayEntry> log, String source,
                                     EvaluationContext ctx) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.dollars != 0) {
                run.money += cur.dollars;
                log.add(new ReplayEntry(source, ReplayEntry.Kind.DOLLARS, cur.dollars, 0, 0));
            }
            if (cur.destroyEventCards && ctx.eventCards != null) { // Trading Card: destroy the discarded set
                for (Card c : ctx.eventCards) c.destroyed = true;  // Run.play purges destroyed from the deck
                log.add(new ReplayEntry(source, ReplayEntry.Kind.DESTROY, 0, 0, 0));
            }
            if (cur.create != null && run.queues != null) {
                int before = run.consumables.size();
                com.balatro.engine.consumable.Creation.apply(run, cur.create, run.queues);
                if (run.consumables.size() > before) {
                    log.add(new ReplayEntry(source, ReplayEntry.Kind.CREATE, 0, 0, 0));
                }
            }
            if (cur.levelUpHand != null) {
                for (int i = 0; i < cur.levelUpAmount; i++) run.levelUpHand(cur.levelUpHand);
                log.add(new ReplayEntry(source, ReplayEntry.Kind.LEVELUP, cur.levelUpAmount, 0, 0));
            }
            if (cur.grantDiscards != 0) { // Pizza: temp discards to self, or to the Nemesis (Match-supplied)
                RunState target = cur.grantToOpponent ? ctx.opponentRun : run;
                if (target != null) {
                    target.grantTempDiscards(cur.grantDiscards, cur.grantDiscardBlinds);
                    log.add(new ReplayEntry(source, ReplayEntry.Kind.DISCARDS, cur.grantDiscards, 0, 0));
                }
            }
        }
    }

    /** True if any link in the chain asks to consume its joker (Pizza on PvP end). */
    private static boolean wantsSelfDestroy(JokerEffect e) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) if (cur.destroySelf) return true;
        return false;
    }
}
