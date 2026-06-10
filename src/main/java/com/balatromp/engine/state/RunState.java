package com.balatromp.engine.state;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative per-player run state. Everything here is server-owned; the
 * client receives only the visible projection (spec §7/§8). Joker state is keyed
 * by instance identity so two copies of the same joker scale independently.
 */
public final class RunState {

    public int money = 4;
    public int handsLeft = 4;
    public int discardsLeft = 3;
    public int ante = 1;
    public int handSize = 8;
    public long roundScore = 0;
    public int discardsUsedThisRound = 0; // reset at blind start (Delayed Gratification)
    public int handsPlayedThisRound = 0;  // reset at blind start (DNA's "first hand")
    // Run-long counters (decay jokers); shipped to the client so they preview exactly.
    public int handsPlayedTotal = 0;      // Ice Cream
    public int roundsPlayedTotal = 0;     // Popcorn
    public int cardsDiscardedTotal = 0;   // Ramen / Yorick
    public int luckyTriggersTotal = 0;    // Lucky Cat
    // Per-poker-hand play tracking (Supernova counts run-long; Card Sharp checks this round).
    public final Map<HandType, Integer> handTypePlays = new EnumMap<>(HandType.class);
    public final java.util.Set<HandType> handTypesThisRound = java.util.EnumSet.noneOf(HandType.class);
    // Per-round dynamic targets (re-rolled each blind), shipped to the client.
    public int idolRankId = 14;                                  // The Idol's target rank
    public com.balatromp.engine.card.Suit idolSuit = com.balatromp.engine.card.Suit.HEARTS;
    public com.balatromp.engine.card.Suit ancientSuit = com.balatromp.engine.card.Suit.HEARTS; // Ancient
    public com.balatromp.engine.card.Suit castleSuit = com.balatromp.engine.card.Suit.HEARTS;  // Castle
    public HandType todoHandType = HandType.PAIR;                                              // To Do List
    public int rebateRankId = 2;                                                              // Mail-In Rebate
    public final java.util.Set<String> planetsUsedThisRun = new java.util.HashSet<>();         // Satellite
    public int obeliskStreak = 0; // consecutive hands not playing your most-played hand (Obelisk)
    public int blindsSkipped = 0; // blinds skipped this run (Throwback)

    // Probability numerator (raised by "Oops! All 6s" etc.); odds are num/denom.
    public int probabilityNumerator = 1;

    public final List<Card> hand = new ArrayList<>();
    /** The full persistent deck (every card the run owns), reshuffled each blind. Read by deck-stat jokers. */
    public final List<Card> deckComposition = new ArrayList<>();
    public final List<String> consumables = new ArrayList<>(); // held Planet (etc.) card keys
    public int consumableSlots = 2;
    public Deck deck;
    public RandomStreams rng;
    /** Game-long deterministic queues (shop, planets, …) — see {@link QueueSet}. */
    public QueueSet queues;

    public RandomStreams rng() {
        return rng;
    }

    private final List<Joker> jokers = new ArrayList<>();
    private final Map<HandType, Integer> handLevels = new EnumMap<>(HandType.class);
    private final IdentityHashMap<Joker, Map<String, Object>> jokerStates = new IdentityHashMap<>();

    public List<Joker> jokers() {
        return jokers;
    }

    public void addJoker(Joker j) {
        jokers.add(j);
        // Stamp when this joker was acquired (for "since acquired" jokers: Turtle Bean, Seltzer).
        var st = jokerState(j);
        st.putIfAbsent("acqHands", handsPlayedTotal);
        st.putIfAbsent("acqRounds", roundsPlayedTotal);
    }

    public int handLevel(HandType t) {
        return handLevels.getOrDefault(t, 1);
    }

    public void setHandLevel(HandType t, int level) {
        handLevels.put(t, level);
    }

    /** Level a poker hand by one (Planet card). */
    public void levelUpHand(HandType t) {
        handLevels.put(t, handLevel(t) + 1);
    }

    public int handLevelChipBonus(HandType t) {
        return (handLevel(t) - 1) * t.lChips;
    }

    public int handLevelMultBonus(HandType t) {
        return (handLevel(t) - 1) * t.lMult;
    }

    /** Persistent server-only state bag for a specific joker instance. */
    public Map<String, Object> jokerState(Joker j) {
        return jokerStates.computeIfAbsent(j, k -> new HashMap<>());
    }
}
