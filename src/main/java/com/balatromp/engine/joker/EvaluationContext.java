package com.balatromp.engine.joker;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.rng.RandomStreams;
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
