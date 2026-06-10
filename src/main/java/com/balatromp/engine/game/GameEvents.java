package com.balatromp.engine.game;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerEffect;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ReplayEntry;
import com.balatromp.engine.state.RunState;
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

        for (int i = 0; i < jokers.size(); i++) {
            ctx.phase = trigger;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            applyEconomy(jokers.get(i).calculate(ctx), run, log, jokers.get(i).name());
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
                log.add(new ReplayEntry(c.toString(), "dollars", "+$3 (Gold card)", 0, 0));
            }
        }
        return log;
    }

    /** Before a discard resolves; jokers see the discarded set via {@code eventCards}. */
    public static List<ReplayEntry> preDiscard(RunState run, RandomStreams rng, List<Card> discarded) {
        return raise(Trigger.PRE_DISCARD, run, rng, ctx -> ctx.eventCards = discarded);
    }

    /** A consumable (Tarot/Planet/Spectral) is used. */
    public static List<ReplayEntry> useConsumable(RunState run, RandomStreams rng, String category) {
        return raise(Trigger.USE_CONSUMABLE, run, rng, ctx -> ctx.consumableType = category);
    }

    private static void applyEconomy(JokerEffect e, RunState run, List<ReplayEntry> log, String source) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.dollars != 0) {
                run.money += cur.dollars;
                String text = cur.message != null ? cur.message : "+$" + cur.dollars;
                log.add(new ReplayEntry(source, "dollars", text, 0, 0));
            }
            if (cur.create != null && run.queues != null) {
                int before = run.consumables.size();
                com.balatromp.engine.consumable.Creation.apply(run, cur.create, run.queues);
                if (run.consumables.size() > before) {
                    log.add(new ReplayEntry(source, "create", "Created " + cur.create.kind(), 0, 0));
                }
            }
        }
    }
}
