package com.balatromp.engine.game;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.consumable.Consumable;
import com.balatromp.engine.consumable.TarotCatalog;
import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.intent.IntentHandler;
import com.balatromp.engine.intent.IntentResult;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.joker.JokerDisplay;
import com.balatromp.engine.joker.def.DataJoker;
import com.balatromp.engine.joker.def.JokerDef;
import com.balatromp.engine.joker.def.RunMod;
import com.balatromp.engine.joker.def.JokerDefLibrary;
import com.balatromp.engine.net.CardView;
import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.net.ServerUpdate;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.scoring.ReplayEntry;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.RunState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single-player run loop, ruleset-driven — the spine a competitive match is
 * built on. Authoritative: it owns the seed, the blind requirement, and phase
 * transitions; the client only sends intents through {@link #play(Intent)}.
 *
 * Flow: BLIND_ACTIVE (play/discard until score >= requirement, or hands run out)
 *       -> win: award economy, SHOP -> proceed() advances Small->Big->Boss->ante+1
 *       -> beat winAnte's boss: RUN_WON;  hands exhausted under requirement: RUN_LOST.
 *
 * The shop is a transition stub for now (proceed() just advances) — next increment.
 */
public final class Run {

    public enum Phase { BLIND_ACTIVE, SHOP, PVP_PENDING, BLIND_FAILED, RUN_WON, RUN_LOST }

    /** Synthetic "boss" shown for an Attrition Nemesis blind (no debuff effect). */
    private static final BossBlind NEMESIS = new BossBlind("bl_pvp", "Nemesis Blind",
            "Head-to-head — higher score wins; lower loses a life", 2, false, 1.0, 5, -1, -1, 0, null, false);

    public final Ruleset ruleset;
    public final RunState state = new RunState();
    public final RandomStreams rng;

    private final IntentHandler intents = new IntentHandler();

    public int ante = 1;
    public BlindType blind = BlindType.SMALL;
    public long requirement;
    public Phase phase;
    public Shop shop;
    public BossBlind boss;       // the chosen boss while in a boss blind, else null
    public BossBlind forcedBoss; // test/dev hook to pin a boss
    public int pvpFromAnte = 0;  // Attrition: boss blinds at/after this ante are PvP (0 = never)
    private boolean pvpActive = false;
    private final List<Card> composition = state.deckComposition; // the full deck (lives on RunState)

    public Run(Ruleset ruleset, String seed) {
        this(ruleset, seed, Deck.standard(), List.of());
    }

    public Run(Ruleset ruleset, String seed, Deck deck, List<Joker> jokers) {
        this.ruleset = ruleset;
        this.rng = new RandomStreams(seed);
        state.money = ruleset.startingMoney();
        state.rng = rng;
        state.queues = new com.balatromp.engine.rng.QueueSet(rng);
        for (Joker j : jokers) state.addJoker(j);
        for (Card c : deck.cards()) composition.add(c.copy()); // capture deck composition
        startBlind();
    }

    /**
     * Reconstitute the full deck and shuffle it fresh for this blind. Uses the SAME
     * card objects as {@code composition} (not copies), so permanent per-card
     * mutations (Hiker chips, Midas Gold, Vampire strips) persist across blinds —
     * the deck has stable card identity, like the real game. Transient flags
     * (debuffed) are recomputed each deal; destroyed cards are already gone.
     */
    private void dealNewDeck() {
        state.deck = Deck.of(new ArrayList<>(composition));
        state.deck.shuffle(rng, "deal:" + ante + ":" + blind);
    }

    private void startBlind() {
        state.roundScore = 0;
        state.discardsUsedThisRound = 0;
        state.handsPlayedThisRound = 0;
        pvpActive = false;
        boolean pvpBoss = blind == BlindType.BOSS && pvpFromAnte > 0 && ante >= pvpFromAnte;
        if (pvpBoss) {
            // Attrition Nemesis blind: no clear-requirement; play all hands, compare to opponent.
            pvpActive = true;
            boss = NEMESIS;
            state.handsLeft = ruleset.hands();
            state.discardsLeft = ruleset.discards();
            state.handSize = ruleset.handSize();
            requirement = 0; // outcome is decided by the head-to-head comparison
        } else if (blind == BlindType.BOSS) {
            boss = (forcedBoss != null) ? forcedBoss : BossCatalog.pick(ante, rng);
            state.handsLeft = boss.handsOverride() >= 0 ? boss.handsOverride() : ruleset.hands();
            state.discardsLeft = boss.discardsOverride() >= 0 ? boss.discardsOverride() : ruleset.discards();
            state.handSize = Math.max(1, ruleset.handSize() + boss.handSizeDelta());
            requirement = Math.round(Blinds.getBlindAmount(ante, ruleset) * boss.reqMult() * ruleset.anteScaling());
        } else {
            boss = null;
            state.handsLeft = ruleset.hands();
            state.discardsLeft = ruleset.discards();
            state.handSize = ruleset.handSize();
            requirement = Blinds.requirement(ante, blind, ruleset);
        }
        applyJokerRunMods(); // passive hand/discard/hand-size deltas from owned jokers
        int deckBefore = composition.size();
        GameEvents.raise(Trigger.BLIND_SELECTED, state, rng, null); // Cartomancer, Marble, ...
        // Cards added to the deck (Marble/Certificate) raise CARD_ADDED so Hologram counts them.
        for (int i = composition.size() - deckBefore; i > 0; i--) {
            GameEvents.raise(Trigger.CARD_ADDED, state, rng, null);
        }
        dealNewDeck(); // full deck reshuffled fresh each blind
        state.hand.clear();
        state.deck.drawTo(state.hand, state.handSize);
        refreshDebuffs();
        phase = Phase.BLIND_ACTIVE;
    }

    /** Sum and apply passive run modifiers from owned data jokers (Juggler, Burglar, ...). */
    private void applyJokerRunMods() {
        boolean noDiscards = false;
        for (Joker j : state.jokers()) {
            if (!(j instanceof DataJoker dj)) continue;
            RunMod m = dj.def().runMod();
            if (m.isNone()) continue;
            state.handsLeft += m.handsDelta();
            state.discardsLeft += m.discardsDelta();
            state.handSize += m.handSizeDelta();
            if (m.noDiscards()) noDiscards = true;
        }
        if (noDiscards) state.discardsLeft = 0;
        state.handsLeft = Math.max(1, state.handsLeft);
        state.discardsLeft = Math.max(0, state.discardsLeft);
        state.handSize = Math.max(1, state.handSize);
    }

    /** Mark hand cards debuffed per the active boss (recomputed each deal/draw). */
    private void refreshDebuffs() {
        for (Card c : state.hand) {
            c.debuffed = boss != null
                    && ((boss.debuffSuit() != null && c.isSuit(boss.debuffSuit()))
                        || (boss.debuffFaces() && c.isFace()));
        }
    }

    /** Process a client intent; advances win/lose state after a hand is played. */
    public IntentResult play(Intent intent) {
        if (phase != Phase.BLIND_ACTIVE) {
            return IntentResult.rejected("not in an active blind");
        }
        IntentResult result = intents.handle(state, rng, intent);
        if (!result.ok()) return result;
        // Destroyed cards (Glass break, etc.) leave the deck permanently — raise
        // CARD_DESTROYED per card (Glass Joker / Canio count these), then purge from
        // the persistent composition + hand so they don't return next blind.
        for (Card destroyed : new java.util.ArrayList<>(composition)) {
            if (destroyed.destroyed) {
                GameEvents.raise(Trigger.CARD_DESTROYED, state, rng, ctx -> ctx.scoredCard = destroyed);
            }
        }
        composition.removeIf(c -> c.destroyed);
        state.hand.removeIf(c -> c.destroyed);
        refreshDebuffs(); // re-mark the freshly drawn hand

        if (intent instanceof Intent.PlayHand) {
            state.handsPlayedThisRound++; // after scoring, so DNA's "first hand" check saw 0
            state.handsPlayedTotal++;
            if (pvpActive) {
                // PvP blind: play all hands, then await the head-to-head comparison.
                if (state.handsLeft <= 0) phase = Phase.PVP_PENDING;
            } else if (state.roundScore >= requirement) {
                winBlind();
            } else if (state.handsLeft <= 0) {
                // Attrition: dying to a blind costs a life (match handles it), not the run.
                phase = (pvpFromAnte > 0) ? Phase.BLIND_FAILED : Phase.RUN_LOST;
            }
        }
        return result;
    }

    /** True while this run is in a Nemesis (PvP) blind, whether still playing or locked. */
    public boolean inPvpBlind() {
        return pvpActive;
    }

    /** Attrition: after the match deducts a life for a failed blind, continue to the shop. */
    public void continueAfterFail() {
        if (phase != Phase.BLIND_FAILED) return;
        pvpActive = false;
        shop = Shop.generate(state.queues, 2, ruleset.jokerPool()); // no reward for a failed blind
        phase = Phase.SHOP;
    }

    /** End a Nemesis blind once the match decided it (works for the locked loser AND
     *  the ahead winner who may still have hands): award economy, proceed to the shop. */
    public void endPvp() {
        if (!pvpActive) return;
        pvpActive = false;
        int interest = Math.min(5, state.money / 5);
        state.money += NEMESIS.reward() + interest;
        GameEvents.endOfRound(state, rng, true); // Nemesis is a Boss blind
        shop = Shop.generate(state.queues, 2, ruleset.jokerPool());
        phase = Phase.SHOP;
    }

    private void winBlind() {
        // Economy: blind reward + interest ($1 per $5 held, capped at $5) + joker/gold payouts.
        int interest = Math.min(5, state.money / 5);
        int reward = (boss != null) ? boss.reward() : blind.reward;
        state.money += reward + interest;
        state.roundsPlayedTotal++;
        GameEvents.endOfRound(state, rng, boss != null);
        phase = Phase.SHOP;
        shop = Shop.generate(state.queues, 2, ruleset.jokerPool());
    }

    /** Buy the joker at the given shop slot. Returns null on success, else a reason. */
    public String buyJoker(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.items().size()) return "invalid shop slot";
        Shop.Item item = shop.items().get(index);
        if (state.money < item.cost()) return "not enough money";
        if (state.jokers().size() >= Shop.JOKER_SLOT_LIMIT) return "joker slots full";
        state.money -= item.cost();
        state.addJoker(JokerLibrary.create(item.jokerKey()));
        shop.items().remove(index);
        return null;
    }

    /** Reroll the shop offerings. Returns null on success, else a reason. */
    public String reroll() {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (state.money < Shop.REROLL_COST) return "not enough money";
        state.money -= Shop.REROLL_COST;
        shop = Shop.generate(state.queues, 2, ruleset.jokerPool()); // advances the same game-long queue
        return null;
    }

    /** Buy the planet at the given shop slot into your consumable inventory. */
    public String buyPlanet(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.planets().size()) return "invalid planet slot";
        if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
        if (state.money < PlanetCatalog.COST) return "not enough money";
        state.money -= PlanetCatalog.COST;
        state.consumables.add(shop.planets().get(index).key());
        shop.planets().remove(index);
        return null;
    }

    /** Buy a Tarot/Spectral from the shop into your consumable inventory. */
    public String buyConsumable(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.consumables().size()) return "invalid consumable slot";
        if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
        if (state.money < Shop.CONSUMABLE_COST) return "not enough money";
        state.money -= Shop.CONSUMABLE_COST;
        state.consumables.add(shop.consumables().get(index).key());
        shop.consumables().remove(index);
        return null;
    }

    /** Use a held Planet (no card targets). */
    public String useConsumable(int index) {
        return useConsumable(index, new long[0]);
    }

    /**
     * Use a held consumable. Planets level their hand; Tarots/Spectrals act on the
     * cards the client selected (resolved by unique id) — enhance, destroy, or
     * create. Returns null on success, else a rejection reason.
     */
    public String useConsumable(int index, long[] targetUids) {
        if (phase == Phase.RUN_WON || phase == Phase.RUN_LOST) return "run is over";
        if (index < 0 || index >= state.consumables.size()) return "invalid consumable";
        String key = state.consumables.get(index);

        PlanetCatalog.Planet p = PlanetCatalog.get(key);
        if (p != null) {
            state.levelUpHand(p.hand());
            GameEvents.useConsumable(state, rng, "Planet");
            state.consumables.remove(index);
            return null;
        }

        Consumable c = TarotCatalog.get(key);
        if (c != null) {
            List<Card> targets = new ArrayList<>();
            for (long uid : targetUids) {
                for (Card card : state.hand) {
                    if (card.uid == uid) targets.add(card);
                }
            }
            if (targets.size() > c.maxTargets()) return "too many targets";
            applyConsumable(c, targets);
            GameEvents.useConsumable(state, rng, c.type().label());
            state.consumables.remove(index);
            return null;
        }
        return "unknown consumable: " + key;
    }

    private void applyConsumable(Consumable c, List<Card> targets) {
        switch (c.effect()) {
            case Consumable.Enhance en -> targets.forEach(t -> en.mod().applyTo(t));
            case Consumable.Destroy ignored -> {
                targets.forEach(t -> t.destroyed = true);
                composition.removeIf(x -> x.destroyed);
                state.hand.removeIf(x -> x.destroyed);
            }
            case Consumable.Create cr -> {
                var r = rng.stream("create:" + c.key());
                Rank[] ranks = Rank.values();
                Suit[] suits = Suit.values();
                for (int i = 0; i < cr.count(); i++) {
                    Rank rank;
                    do { rank = ranks[r.nextInt(ranks.length)]; } while (rank.id > 10); // numbered cards
                    Card made = new Card(rank, suits[r.nextInt(suits.length)],
                            cr.enhancement(), Edition.NONE, Seal.NONE);
                    composition.add(made); // persistent deck (drawn next blind)
                    state.hand.add(made);  // and usable now
                }
            }
        }
    }

    /**
     * Side-effect-free preview of playing the selected hand cards — the
     * authoritative score projection the client renders as the player selects
     * cards (so jokers whose value depends on the prospective play, like Greedy or
     * The Duo, show correctly). Commits nothing. Returns null if nothing selected.
     */
    public ScoreResult previewScore(List<Integer> cardIndices) {
        List<Card> played = new ArrayList<>();
        for (int i : cardIndices) {
            if (i >= 0 && i < state.hand.size()) played.add(state.hand.get(i));
        }
        if (played.isEmpty()) return null;
        List<Card> held = new ArrayList<>(state.hand);
        held.removeAll(played);
        return new com.balatromp.engine.scoring.ScoringEngine().preview(played, held, state, rng);
    }

    /** Client-facing entry: validate+apply an intent, return the authoritative update. */
    public ServerUpdate submit(Intent intent) {
        IntentResult r = play(intent);
        List<ReplayEntry> replay = (r.score() != null) ? r.score().replayLog() : List.of();
        return new ServerUpdate(r.ok(), r.error(), view(), replay);
    }

    /** The data definition backing a joker (for the client preview), or null if native-only. */
    private static JokerDef defFor(Joker j) {
        if (j instanceof DataJoker dj) return dj.def();
        return JokerDefLibrary.get(j.key()); // hand-coded jokers have data equivalents (except Blueprint)
    }

    /** The safe projection of authoritative state the client may render (spec §8). */
    public ClientView view() {
        List<CardView> handView = new ArrayList<>();
        for (Card c : state.hand) handView.add(CardView.of(c));

        List<Map<String, Object>> jokerView = new ArrayList<>();
        for (int i = 0; i < state.jokers().size(); i++) {
            Joker j = state.jokers().get(i);
            var info = j.info();
            Map<String, Object> jv = new LinkedHashMap<>();
            jv.put("key", info.key());
            jv.put("name", info.name());
            jv.put("description", info.description());
            jv.put("rarity", info.rarity());
            jv.put("x", info.atlasX());
            jv.put("y", info.atlasY());
            // Built-in joker display: the joker's current live value (server-computed, no mod).
            jv.put("display", JokerDisplay.currentValue(state.jokers(), i, state));
            // The joker's data definition + live state, so the client can compute an
            // instant local score preview (interpreting the same JokerDef the server uses).
            JokerDef def = defFor(j);
            if (def != null) jv.put("def", def);
            jv.put("state", state.jokerState(j));
            jokerView.add(jv);
        }

        List<Map<String, Object>> shopView = null;
        int rerollCost = 0;
        if (phase == Phase.SHOP && shop != null) {
            shopView = new ArrayList<>();
            for (Shop.Item it : shop.items()) {
                var info = it.info();
                shopView.add(Map.of("key", info.key(), "name", info.name(), "cost", info.cost(),
                        "description", info.description(), "rarity", info.rarity(),
                        "x", info.atlasX(), "y", info.atlasY()));
            }
            rerollCost = Shop.REROLL_COST;
        }

        List<Map<String, Object>> shopPlanets = null;
        List<Map<String, Object>> shopConsumables = null;
        if (phase == Phase.SHOP && shop != null) {
            shopPlanets = new ArrayList<>();
            for (PlanetCatalog.Planet p : shop.planets()) {
                shopPlanets.add(Map.of("key", p.key(), "name", p.name(), "hand", p.hand().display,
                        "cost", PlanetCatalog.COST, "description", p.description()));
            }
            shopConsumables = new ArrayList<>();
            for (Consumable c : shop.consumables()) {
                shopConsumables.add(Map.of("key", c.key(), "name", c.name(),
                        "cost", Shop.CONSUMABLE_COST, "description", c.description(),
                        "maxTargets", c.maxTargets()));
            }
        }

        List<Map<String, Object>> consumables = new ArrayList<>();
        for (String key : state.consumables) {
            PlanetCatalog.Planet p = PlanetCatalog.get(key);
            if (p != null) {
                consumables.add(Map.of("key", key, "name", p.name(), "description", p.description()));
                continue;
            }
            Consumable c = TarotCatalog.get(key);
            if (c != null) {
                consumables.add(Map.of("key", key, "name", c.name(), "description", c.description(),
                        "maxTargets", c.maxTargets()));
            }
        }

        Map<String, Object> handLevels = new LinkedHashMap<>();
        for (HandType t : HandType.values()) handLevels.put(t.display, state.handLevel(t));

        // Deck aggregates the client's local preview needs for deck-stat jokers.
        Map<String, Object> deckStats = new LinkedHashMap<>();
        deckStats.put("size", state.deckComposition.size());
        deckStats.put("remaining", state.deck != null ? state.deck.remaining() : 0);
        Map<String, Integer> enh = new LinkedHashMap<>();
        for (Card c : state.deckComposition) {
            if (c.enhancement != com.balatromp.engine.card.Enhancement.NONE) {
                enh.merge(c.enhancement.name(), 1, Integer::sum);
            }
        }
        deckStats.put("enhancements", enh);

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("HANDS_PLAYED_TOTAL", state.handsPlayedTotal);
        counters.put("ROUNDS_PLAYED", state.roundsPlayedTotal);
        counters.put("CARDS_DISCARDED_TOTAL", state.cardsDiscardedTotal);
        counters.put("DISCARDS_USED", state.discardsUsedThisRound);
        counters.put("HANDS_PLAYED", state.handsPlayedThisRound);

        return new ClientView(ante, blind.display, requirement, state.roundScore,
                state.handsLeft, state.discardsLeft, state.money, state.handSize,
                phase.name(), handView, jokerView, shopView, rerollCost,
                boss != null ? boss.name() : null, boss != null ? boss.effect() : null,
                shopPlanets, shopConsumables, consumables, handLevels, deckStats, counters);
    }

    /** Leave the (stubbed) shop and advance to the next blind / ante / win. */
    public void proceed() {
        if (phase != Phase.SHOP) return;
        switch (blind) {
            case SMALL -> blind = BlindType.BIG;
            case BIG -> blind = BlindType.BOSS;
            case BOSS -> {
                // Attrition (pvpFromAnte>0) is endless — only lives decide it.
                if (pvpFromAnte == 0 && ruleset.winAnte() > 0 && ante >= ruleset.winAnte()) {
                    phase = Phase.RUN_WON;
                    return;
                }
                ante++;
                blind = BlindType.SMALL;
            }
        }
        startBlind();
    }
}
