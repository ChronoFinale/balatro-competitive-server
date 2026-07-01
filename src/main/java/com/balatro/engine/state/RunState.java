package com.balatro.engine.state;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
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
    public int drawCountOverride = -1;    // The Serpent: draw exactly N on refill (-1 = fill to hand size)
    public boolean bossHasActiveAbility = false; // a non-disabled Boss ability is in play (Matador's $8)
    // Run-long counters (decay jokers); shipped to the client so they preview exactly.
    public int handsPlayedTotal = 0;      // Ice Cream
    public int roundsPlayedTotal = 0;     // Popcorn
    public int cardsDiscardedTotal = 0;   // Ramen / Yorick
    public int luckyTriggersTotal = 0;    // Lucky Cat
    public double blindProgress = 0;      // roundScore/requirement at blind-loss, for a BLIND_LOST rule to read (Mr Bones)
    public boolean blindSurvived = false; // a BLIND_LOST rule wrote BLIND_SURVIVED this pass (Mr Bones saved the run)
    // Per-poker-hand play tracking (Supernova counts run-long; Card Sharp checks this round).
    public final Map<HandType, Integer> handTypePlays = new EnumMap<>(HandType.class);
    public final java.util.Set<HandType> handTypesThisRound = java.util.EnumSet.noneOf(HandType.class);
    public HandType lastPlayedHandType = null; // Blue Seal: the Planet it creates is for this round's last hand
    /** Per-round dynamic targets (Idol/Ancient/Castle/To Do/Rebate), re-rolled each blind and shipped to the
     *  client. A generic bag keyed PER JOKER by {@link RoundTargets#key} ({@code jokerKey:DOMAIN}) — values
     *  are Suit / Integer rank id / HandType. Matched in scoring by the {@code *IsTarget} conditions, which
     *  read the calculating joker's own rolled value. Empty until the first blind rolls (rolled before any
     *  scoring), so a read before then simply doesn't match. */
    public final Map<String, Object> roundTargets = new HashMap<>();
    public final java.util.Set<String> planetsUsedThisRun = new java.util.HashSet<>();         // Satellite
    public int obeliskStreak = 0; // consecutive hands not playing your most-played hand (Obelisk)
    public int blindsSkipped = 0; // blinds skipped this run (Throwback)
    // Multiplayer "Nemesis" state (set by the match layer; default 0/false in solo).
    public Capabilities capabilities = Capabilities.VANILLA; // the active mode's behavioural knobs
    public String jokerVariant = "default"; // the active ruleset's joker-behavior variant (MP reworks)
    public boolean inPvpBlind = false; // currently in a PvP boss blind (Pacifist, Conjoined)
    public boolean bossHalveBase = false; // The Flint: base chips AND mult are halved this blind
    public boolean balanceChipsMult = false; // Plasma Deck: floor((chips+mult)/2) into each before x
    // Observatory voucher: a held Planet gives heldPlanetMult to its hand (resolved by Run, read by the scorer).
    public double heldPlanetMult = 1.0;
    public final java.util.Set<HandType> heldPlanetHands = java.util.EnumSet.noneOf(HandType.class);
    public String lastTarotPlanetUsed = null; // The Fool copies whatever this was (excludes The Fool itself)
    public int myLives = 0;            // your Attrition lives
    /** The Nemesis as a first-class noun — the opponent's PvP state (was the scattered opp* ints). */
    public final Opponent opponent = new Opponent();
    public int cardsSoldSinceLastPvp = 0; // your sells since the last PvP (feeds the opponent's Taxes)
    public int shopSpentThisAnte = 0;     // money spent in the shop this ante
    public int shopSpentLastAnte = 0;     // snapshot at ante rollover
    public int bossRerollsThisAnte = 0;   // Director's Cut/Retcon: boss rerolls used this ante
    public int pizzaDiscardBonus = 0;     // temporary +discards from Pizza
    public int pizzaBlindsLeft = 0;       // blinds the Pizza discard bonus still applies for

    /** Pizza: add a temporary discard bonus for the next {@code blinds} blinds (self or — via the Match —
     *  the Nemesis). The single home for the mechanism; Run and the GameEvents action path both call it. */
    public void grantTempDiscards(int amount, int blinds) {
        pizzaDiscardBonus += amount;
        pizzaBlindsLeft = Math.max(pizzaBlindsLeft, blinds);
    }
    public final java.util.List<String> tags = new java.util.ArrayList<>(); // held tags (skip rewards, Diet Cola)
    public String offeredTag = null; // the tag offered for skipping the current (Small/Big) blind, else null

    // Probability numerator (raised by "Oops! All 6s" etc.); odds are num/denom.
    public int probabilityNumerator = 1;

    public final List<Card> hand = new ArrayList<>();
    /** The full persistent deck (every card the run owns), reshuffled each blind. Read by deck-stat jokers. */
    public final List<Card> deckComposition = new ArrayList<>();
    public final List<String> consumables = new ArrayList<>(); // held Planet (etc.) card keys
    /** Human-readable joker-trigger events since the last ClientView (Hallucination created X, etc.), for the
     *  client's dev log. Drained (read + cleared) when the view is built, so each response carries only new ones. */
    public final List<String> triggerLog = new ArrayList<>();
    public int consumableSlots = 2;
    public int jokerSlots = 5; // max owned jokers (Black Deck raises it)
    // End-of-round economy is NOT stored as mutable flags — it is resolved as a pure function of the
    // owned sources (deck + vouchers + jokers) in EconomyConfig.resolve, read by Run.endOfRoundMoney.
    public final java.util.Set<String> vouchers = new java.util.HashSet<>(); // owned vouchers
    public Deck deck;
    public RandomStreams rng;
    /** Game-long deterministic queues (shop, planets, …) — see {@link QueueSet}. */
    public QueueSet queues;
    /** Whether "The Order" variance reduction is active (from the ruleset; set by Run). */
    public boolean order = true;

    public RandomStreams rng() {
        return rng;
    }

    /**
     * The RNG resolution context for scoring/probability sources, which are all game-long (the blind
     * name is only needed by the per-blind deck deal, built in Run). Carries ante, PvP state and the
     * order flag so {@link QueueSet#resolve} can route PvP-per-hand sources correctly.
     */
    public com.balatro.engine.rng.RngContext rngContext() {
        return com.balatro.engine.rng.RngContext.of(ante, inPvpBlind, order);
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

    /** Level a poker hand down by one, floored at 1 (the Asteroid MP planet on the nemesis). */
    public void levelDownHand(HandType t) {
        handLevels.put(t, Math.max(1, handLevel(t) - 1));
    }

    /** Asteroids used this turn, awaiting the Match to delevel the opponent's highest hand. */
    public int nemesisDelevelPending = 0;

    // Last blind's cash-out breakdown (shown on the end-of-round screen before the shop).
    public int lastBlindReward = 0;
    public int lastInterest = 0;    // the ACTUAL interest ($1/$5, capped)
    public int lastRoundMoney = 0;  // per-remaining-hand + per-remaining-discard money (the rest of the bonus)

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

    // --- Typed accessors over the joker state bag (the values are stored as boxed Number/Boolean,
    //     so these centralize the cast/coercion that would otherwise be copy-pasted at every call). ---

    /** A boolean flag in the joker's state bag (absent ⇒ false). */
    public boolean jokerFlag(Joker j, String key) {
        return Boolean.TRUE.equals(jokerState(j).get(key));
    }

    /** An int counter in the joker's state bag ({@code def} if absent). */
    public int jokerInt(Joker j, String key, int def) {
        Object v = jokerState(j).get(key);
        return (v instanceof Number n) ? n.intValue() : def;
    }

    /** A double counter in the joker's state bag ({@code def} if absent). */
    public double jokerDouble(Joker j, String key, double def) {
        Object v = jokerState(j).get(key);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    /** Add {@code delta} to an int counter (0-based) and return the new value. */
    public int addJokerInt(Joker j, String key, int delta) {
        int v = jokerInt(j, key, 0) + delta;
        jokerState(j).put(key, v);
        return v;
    }

    /** Add {@code delta} to a double counter (0-based) and return the new value. */
    public double addJokerDouble(Joker j, String key, double delta) {
        double v = jokerDouble(j, key, 0) + delta;
        jokerState(j).put(key, v);
        return v;
    }

    /**
     * The edition stamped on an owned joker (Foil/Holo/Poly/Negative), stored in
     * its state bag so it ships to the client and previews exactly. Defaults to NONE.
     */
    public Edition jokerEdition(Joker j) {
        Object e = jokerState(j).get("edition");
        if (e instanceof Edition ed) return ed;
        if (e instanceof String s) return Edition.valueOf(s);
        return Edition.NONE;
    }

    /**
     * Stamp an edition onto an owned joker. Negative grants a joker slot (so the
     * deck can hold one more), exactly once — re-stamping the same edition is a no-op.
     */
    public void setJokerEdition(Joker j, Edition edition) {
        Edition prev = jokerEdition(j);
        if (prev == edition) return;
        if (prev == Edition.NEGATIVE) jokerSlots -= 1; // removing a negative reclaims its slot
        jokerState(j).put("edition", edition);
        if (edition == Edition.NEGATIVE) jokerSlots += 1;
    }
}
