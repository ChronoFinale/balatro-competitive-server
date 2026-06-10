package com.balatromp.engine.joker;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.rng.Rng;
import com.balatromp.engine.state.RunState;
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

    public Joker self() {
        return jokers.get(selfIndex);
    }

    /**
     * Pop the next roll [0,1) from a named game-long probability queue (Chance /
     * Random effects). Uses the run's persistent QueueSet so both players get the
     * same procs; falls back to a transient set for bare-state contexts.
     */
    public double nextProb(String seedKey) {
        QueueSet qs = (run != null && run.queues != null) ? run.queues
                : new QueueSet(rng != null ? rng : new RandomStreams("prob"));
        return qs.queue("prob:" + seedKey, Rng::nextDouble).next();
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
        return c;
    }
}
