package com.balatro.engine.joker;

import com.balatro.engine.card.Card;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.rng.RngContext;
import com.balatro.engine.rng.RngSources;
import com.balatro.engine.state.RunState;
import java.util.List;
import java.util.Map;

/**
 * The Balatro {@code context} table, as a Java object (spec §5). The engine
 * reconfigures it per dispatch (phase, current card, self index). Blueprint
 * uses {@link #forCopy(int)} to re-enter a neighbour with the same context —
 * the re-entrant copy mechanic from spec §4.
 */
public final class EvaluationContext {

    public Trigger phase;
    public HandType handType;
    public List<Card> playedCards;
    public List<Card> scoringCards;
    /** Cards still held in hand during this evaluation (for held-card scaling). */
    public List<Card> heldCards;
    /** Cards removed/destroyed this evaluation (carried by REMOVE_PLAYING_CARDS). */
    public List<Card> removedCards;
    /** Cards relevant to a lifecycle event (discarded set, held-at-round-end, ...). */
    public List<Card> eventCards;
    /** For USE_CONSUMABLE: the consumable category ("Tarot" | "Planet" | "Spectral"). */
    public String consumableType;
    public Card scoredCard;
    public Joker otherJoker;
    public List<Joker> jokers;
    public int selfIndex;
    public int blueprintDepth;
    public RunState run;
    public RandomStreams rng;
    /** Pareidolia: when true, every card is treated as a face card by face conditions. */
    public boolean allFaces;
    /** END_OF_ROUND: true when the round just won was a Boss blind (Rocket). */
    public boolean bossDefeated;
    /** BLIND_SELECTED: true when the blind just selected is a Boss blind (Madness skips bosses). */
    public boolean bossBlind;
    /** PVP_BLIND_REACHED: true when this run entered the PvP blind before its Nemesis (Speedrun). */
    public boolean reachedPvpFirst;

    public Joker self() {
        return jokers.get(selfIndex);
    }

    /** A declared numeric property of the joker currently calculating (0 if absent/non-numeric). */
    public double selfProp(String name) {
        Object v = self().prop(name);
        return (v instanceof Number n) ? n.doubleValue() : 0;
    }

    /** Display dry-run: when true, probabilistic reads use a throwaway queue and never
     *  advance the run's real queues (so computing joker display has no side effects). */
    public boolean preview;

    /**
     * Pop the next roll [0,1) from a named game-long probability queue (Chance /
     * Random effects). Uses the run's persistent QueueSet so both players get the
     * same procs; falls back to a transient set for bare-state/preview contexts.
     */
    public double nextProb(String seedKey) {
        QueueSet qs;
        RngContext ctx;
        if (preview) {
            qs = new QueueSet(new RandomStreams("preview")); // throwaway: never touch real queues
            ctx = RngContext.of(0, false, true);
        } else if (run != null && run.queues != null) {
            qs = run.queues;
            ctx = run.rngContext();
        } else {
            qs = new QueueSet(rng != null ? rng : new RandomStreams("prob"));
            ctx = RngContext.of(0, false, true);
        }
        // Every Chance joker (Bloodstone, Business Card, …) is one PROB source: game-long normally,
        // but a per-hand PvP variant inside a Nemesis blind (the Run rewinds it each hand) so equal
        // hands proc equally regardless of hands-left. The routing lives in QueueSet.resolve.
        return qs.roll(RngSources.PROB.sub(seedKey), ctx);
    }

    /** Persistent, server-only per-joker state (e.g. Ride the Bus streak). */
    public Map<String, Object> selfState() {
        return run.jokerState(self());
    }

    /** A context for Blueprint to evaluate the joker at {@code newSelfIndex}. */
    public EvaluationContext forCopy(int newSelfIndex) {
        EvaluationContext c = new EvaluationContext();
        c.phase = phase;
        c.handType = handType;
        c.playedCards = playedCards;
        c.scoringCards = scoringCards;
        c.heldCards = heldCards;
        c.removedCards = removedCards;
        c.eventCards = eventCards;
        c.consumableType = consumableType;
        c.scoredCard = scoredCard;
        c.otherJoker = otherJoker;
        c.jokers = jokers;
        c.selfIndex = newSelfIndex;
        c.blueprintDepth = blueprintDepth + 1;
        c.run = run;
        c.rng = rng;
        c.preview = preview;
        c.allFaces = allFaces;
        c.bossDefeated = bossDefeated;
        return c;
    }
}
