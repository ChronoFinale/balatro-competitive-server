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
    private boolean freeRerollUsed = false; // Chaos the Clown: one free reroll per shop visit
    private boolean boosterAvailable = false; // one booster pack offered per shop visit
    private boolean luchadorDisabledBoss = false; // Luchador: boss disabled for the current blind
    private final List<Card> composition = state.deckComposition; // the full deck (lives on RunState)

    public Run(Ruleset ruleset, String seed) {
        this(ruleset, seed, Deck.standard(), List.of());
    }

    public Run(Ruleset ruleset, String seed, Deck deck, List<Joker> jokers) {
        this.ruleset = ruleset;
        this.rng = new RandomStreams(seed);
        DeckCatalog.DeckType deckType = DeckCatalog.get(ruleset.deckType());
        state.multiplayer = "multiplayer".equals(ruleset.jokerVariant()); // MP rules (Glass x1.5, ...)
        state.money = ruleset.startingMoney() + deckType.startMoneyDelta();
        state.jokerSlots = 5 + deckType.jokerSlotsDelta();
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
        state.handTypesThisRound.clear();
        luchadorDisabledBoss = false; // a fresh blind re-arms the boss (Luchador must be re-sold)
        pvpActive = false;
        boolean pvpBoss = blind == BlindType.BOSS && pvpFromAnte > 0 && ante >= pvpFromAnte;
        state.inPvpBlind = pvpBoss; // Nemesis jokers (Pacifist, Conjoined) read this
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
            boolean disabled = bossDisabled(); // Chicot: ignore the boss's hand/discard/size ability
            state.handsLeft = (!disabled && boss.handsOverride() >= 0) ? boss.handsOverride() : ruleset.hands();
            state.discardsLeft = (!disabled && boss.discardsOverride() >= 0) ? boss.discardsOverride() : ruleset.discards();
            state.handSize = Math.max(1, ruleset.handSize() + (disabled ? 0 : boss.handSizeDelta()));
            requirement = Math.round(Blinds.getBlindAmount(ante, ruleset) * boss.reqMult() * ruleset.anteScaling());
        } else {
            boss = null;
            state.handsLeft = ruleset.hands();
            state.discardsLeft = ruleset.discards();
            state.handSize = ruleset.handSize();
            requirement = Blinds.requirement(ante, blind, ruleset);
        }
        applyJokerRunMods(); // passive hand/discard/hand-size deltas from owned jokers
        applyJokerDestroyers(); // Ceremonial Dagger / Madness eat a joker at blind select
        // Oops! All 6s doubles every listed probability (numerator) per copy owned.
        long oops = state.jokers().stream().filter(j -> j.key().equals("j_oops")).count();
        state.probabilityNumerator = 1 << Math.min((int) oops, 8);
        rollRoundTargets();  // The Idol / Ancient targets, re-rolled each blind
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

    /** Jokers that consume another joker at blind select: Ceremonial Dagger (right neighbour ->
     *  +Mult from 2x its sell value) and Madness (a random other joker -> +x0.5 Mult, non-boss only). */
    private void applyJokerDestroyers() {
        List<Joker> js = state.jokers();
        for (int i = 0; i < js.size(); i++) {
            if (!js.get(i).key().equals("j_ceremonial") || i + 1 >= js.size()) continue;
            Joker victim = js.remove(i + 1);
            int gain = 2 * Math.max(1, victim.info().cost() / 2);
            var st = state.jokerState(js.get(i));
            st.put("mult", ((Number) st.getOrDefault("mult", 0)).intValue() + gain);
        }
        if (blind == BlindType.BOSS) return; // Madness doesn't trigger on boss blinds
        for (int i = 0; i < js.size(); i++) {
            if (!js.get(i).key().equals("j_madness")) continue;
            var st = state.jokerState(js.get(i));
            st.put("xm", ((Number) st.getOrDefault("xm", 0.0)).doubleValue() + 0.5);
            List<Integer> others = new ArrayList<>();
            for (int k = 0; k < js.size(); k++) if (k != i) others.add(k);
            if (others.isEmpty()) continue;
            int victim = others.get((int) (roll("madness:destroy") * others.size()) % others.size());
            js.remove(victim);
            if (victim < i) i--; // a joker before us was removed; stay aligned
        }
    }

    /** Whether the active boss has an ability (a debuff or a hand/discard/size override). */
    private boolean bossHasAbility() {
        return boss != null && (boss.debuffSuit() != null || boss.debuffFaces()
                || boss.handsOverride() >= 0 || boss.discardsOverride() >= 0 || boss.handSizeDelta() != 0);
    }

    /** The boss blind's ability is off — Chicot (always) or Luchador (sold this blind). */
    private boolean bossDisabled() {
        return luchadorDisabledBoss || state.jokers().stream().anyMatch(j -> j.key().equals("j_chicot"));
    }

    /** Mr Bones: survive a failed blind (and self-destruct) if at least 25% of the requirement was scored. */
    private boolean mrBonesSaves() {
        if (requirement <= 0 || state.roundScore < requirement / 4) return false;
        for (int i = 0; i < state.jokers().size(); i++) {
            if (state.jokers().get(i).key().equals("j_mr_bones")) {
                state.jokers().remove(i);
                return true;
            }
        }
        return false;
    }

    /** End-of-round interest: Green Deck pays per remaining hand/discard instead of $-interest. */
    private int roundInterest() {
        DeckCatalog.DeckType deck = DeckCatalog.get(ruleset.deckType());
        if (deck.greenEconomy()) return 2 * state.handsLeft + state.discardsLeft;
        return Math.min(state.interestCap, state.money / 5) + extraInterest();
    }

    /** To the Moon: extra $1 of interest per $5 held at end of round (on top of the capped base). */
    private int extraInterest() {
        boolean toTheMoon = state.jokers().stream().anyMatch(j -> j.key().equals("j_to_the_moon"));
        return toTheMoon ? state.money / 5 : 0;
    }

    private boolean hasJoker(String key) {
        return state.jokers().stream().anyMatch(j -> j.key().equals(key));
    }

    /** Generate a shop that skips jokers you already own (unless Showman allows duplicates). */
    /** Jokers banned in Standard Ranked multiplayer (boss-blind interactions). */
    private static final java.util.Set<String> MP_DISABLED =
            java.util.Set.of("j_chicot", "j_matador", "j_mr_bones", "j_luchador");

    private Shop generateShop() {
        java.util.Set<String> owned = new java.util.HashSet<>();
        for (Joker j : state.jokers()) owned.add(j.key());
        owned.addAll(state.vouchers); // skip vouchers you already own too (distinct v_ namespace)
        // In multiplayer, the boss-disabling jokers are banned — treat them as "already owned" so
        // the shop queue skips over them.
        if ("multiplayer".equals(ruleset.jokerVariant())) owned.addAll(MP_DISABLED);
        int slots = state.vouchers.contains("v_overstock") ? 3 : 2; // Overstock: +1 shop slot
        return Shop.generate(state.queues, slots, ruleset.jokerPool(), owned, hasJoker("j_showman"));
    }

    /** Gift Card: +$1 of sell value to every owned Joker at end of round (Egg bumps only itself). */
    private void applyGiftCard() {
        if (!hasJoker("j_gift_card")) return;
        for (Joker j : state.jokers()) {
            var st = state.jokerState(j);
            st.put("sellBonus", ((Number) st.getOrDefault("sellBonus", 0)).intValue() + 1);
        }
    }

    /** The lowest money a purchase may leave you at — Credit Card allows up to -$20 of debt. */
    private int minMoney() {
        return hasJoker("j_credit_card") ? -20 : 0;
    }

    /** True if {@code cost} is affordable given the current debt floor. */
    private boolean canAfford(int cost) {
        return state.money - cost >= minMoney();
    }

    /** Re-roll the per-round dynamic targets (The Idol's card, Ancient's suit). */
    private void rollRoundTargets() {
        com.balatromp.engine.card.Suit[] suits = com.balatromp.engine.card.Suit.values();
        state.idolSuit = suits[(int) (roll("target:idol:suit") * suits.length) % suits.length];
        state.idolRankId = 2 + (int) (roll("target:idol:rank") * 13) % 13;
        state.ancientSuit = suits[(int) (roll("target:ancient:suit") * suits.length) % suits.length];
        state.castleSuit = suits[(int) (roll("target:castle:suit") * suits.length) % suits.length];
        com.balatromp.engine.hand.HandType[] hands = com.balatromp.engine.hand.HandType.values();
        state.todoHandType = hands[(int) (roll("target:todo:hand") * hands.length) % hands.length];
        state.rebateRankId = 2 + (int) (roll("target:rebate:rank") * 13) % 13;
    }

    private double roll(String key) {
        return state.queues.queue(key, com.balatromp.engine.rng.Rng::nextDouble).next();
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
        // Turtle Bean: +5 hand size, decaying by 1 each round since it was acquired (floors at 0).
        for (Joker j : state.jokers()) {
            if (!j.key().equals("j_turtle_bean")) continue;
            int acq = ((Number) state.jokerState(j).getOrDefault("acqRounds", 0)).intValue();
            state.handSize += Math.max(0, 5 - (state.roundsPlayedTotal - acq));
        }
        // Vouchers: permanent per-blind hand/discard upgrades.
        if (state.vouchers.contains("v_grabber")) state.handsLeft += 1;
        if (state.vouchers.contains("v_wasteful")) state.discardsLeft += 1;
        // Deck variant: per-blind hand/discard deltas (Red/Blue/Black).
        DeckCatalog.DeckType deck = DeckCatalog.get(ruleset.deckType());
        state.handsLeft += deck.handsDelta();
        state.discardsLeft += deck.discardsDelta();
        // Skip-Off (Nemesis): +1 hand and +1 discard per extra blind skipped vs your Nemesis.
        if (hasJoker("j_skip_off")) {
            int diff = Math.max(0, state.blindsSkipped - state.oppBlindsSkipped);
            state.handsLeft += diff;
            state.discardsLeft += diff;
        }
        if (noDiscards) state.discardsLeft = 0;
        state.handsLeft = Math.max(1, state.handsLeft);
        state.discardsLeft = Math.max(0, state.discardsLeft);
        state.handSize = Math.max(1, state.handSize);
    }

    /** Mark hand cards debuffed per the active boss (recomputed each deal/draw). */
    private void refreshDebuffs() {
        boolean disabled = bossDisabled(); // Chicot turns off the boss's debuffs
        for (Card c : state.hand) {
            c.debuffed = !disabled && boss != null
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
            // Matador: $8 when you play a hand against an active boss ability.
            if (hasJoker("j_matador") && !bossDisabled() && bossHasAbility()) state.money += 8;
            if (pvpActive) {
                // PvP blind: play all hands, then await the head-to-head comparison.
                if (state.handsLeft <= 0) phase = Phase.PVP_PENDING;
            } else if (state.roundScore >= requirement) {
                winBlind();
            } else if (state.handsLeft <= 0) {
                if (mrBonesSaves()) {
                    winBlind(); // Mr Bones prevents the death (and is consumed)
                } else {
                    // Attrition: dying to a blind costs a life (match handles it), not the run.
                    phase = (pvpFromAnte > 0) ? Phase.BLIND_FAILED : Phase.RUN_LOST;
                }
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
        shop = generateShop(); // no reward for a failed blind
        phase = Phase.SHOP;
    }

    /** End a Nemesis blind once the match decided it (works for the locked loser AND
     *  the ahead winner who may still have hands): award economy, proceed to the shop. */
    public void endPvp() {
        if (!pvpActive) return;
        pvpActive = false;
        state.cardsSoldSinceLastPvp = 0; // Taxes counts sells between PvP blinds
        int interest = roundInterest();
        state.money += NEMESIS.reward() + interest;
        GameEvents.endOfRound(state, rng, true); // Nemesis is a Boss blind
        applyGiftCard();
        shop = generateShop();
        freeRerollUsed = false;
        boosterAvailable = true;
        phase = Phase.SHOP;
    }

    private void winBlind() {
        // Economy: blind reward + interest ($1 per $5 held, capped at $5) + joker/gold payouts.
        int interest = roundInterest();
        int reward = (boss != null) ? boss.reward() : blind.reward;
        state.money += reward + interest;
        state.roundsPlayedTotal++;
        GameEvents.endOfRound(state, rng, boss != null);
        applyGiftCard();
        phase = Phase.SHOP;
        shop = generateShop();
        freeRerollUsed = false;
        boosterAvailable = true;
    }

    /** Buy the joker at the given shop slot. Returns null on success, else a reason. */
    public String buyJoker(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.items().size()) return "invalid shop slot";
        Shop.Item item = shop.items().get(index);
        if (!canAfford(price(item.cost()))) return "not enough money";
        if (state.jokers().size() >= state.jokerSlots) return "joker slots full";
        state.money -= price(item.cost());
        state.addJoker(JokerLibrary.create(item.jokerKey(), ruleset.jokerVariant()));
        shop.items().remove(index);
        return null;
    }

    /** Buy the shop's offered voucher. Returns null on success, else a reason. */
    public String buyVoucher() {
        if (phase != Phase.SHOP || shop == null || shop.voucher() == null) return "no voucher offered";
        var v = VoucherCatalog.get(shop.voucher());
        if (!canAfford(price(v.cost()))) return "not enough money";
        state.money -= price(v.cost());
        state.vouchers.add(v.key());
        // Immediate-effect vouchers resolve now; per-blind ones (Grabber/Wasteful) apply each blind.
        switch (v.key()) {
            case "v_crystal_ball" -> state.consumableSlots += 1;
            case "v_seed_money" -> state.interestCap = 10;
            default -> { /* applied elsewhere (per blind / shop) */ }
        }
        shop.clearVoucher();
        return null;
    }

    /** Apply the Clearance Sale discount (25% off, rounded down) to a shop price. */
    private int price(int cost) {
        return state.vouchers.contains("v_clearance_sale") ? (int) (cost * 0.75) : cost;
    }

    /** Sell the joker at the given slot (shop or during a blind). Returns null on success. */
    public String sellJoker(int index) {
        if (phase != Phase.SHOP && phase != Phase.BLIND_ACTIVE) return "cannot sell now";
        if (index < 0 || index >= state.jokers().size()) return "invalid joker";
        Joker sold = state.jokers().remove(index);
        state.cardsSoldSinceLastPvp++; // feeds the opponent's Taxes joker
        int bonus = ((Number) state.jokerState(sold).getOrDefault("sellBonus", 0)).intValue();
        state.money += Math.max(1, sold.info().cost() / 2) + bonus; // sell value (+ Egg/Gift bonus)
        // Invisible Joker: sold after >=2 rounds owned, duplicate a random remaining joker.
        if (sold.key().equals("j_invisible")
                && ((Number) state.jokerState(sold).getOrDefault("rounds", 0)).intValue() >= 2
                && !state.jokers().isEmpty() && state.jokers().size() < Shop.JOKER_SLOT_LIMIT) {
            int pick = (int) (roll("invisible:dup") * state.jokers().size()) % state.jokers().size();
            state.addJoker(JokerLibrary.create(state.jokers().get(pick).key()));
        }
        // Diet Cola: sold, creates a free Double Tag (duplicates the next tag you gain).
        if (sold.key().equals("j_diet_cola")) state.tags.add("tag_double");
        // Luchador: sold during a boss blind, disable that boss's ability for the rest of the blind.
        if (sold.key().equals("j_luchador") && boss != null) {
            luchadorDisabledBoss = true;
            refreshDebuffs();
        }
        // A card was sold: remaining jokers react (Campfire gains x0.25 each).
        GameEvents.raise(Trigger.SELL_CARD, state, rng, null);
        return null;
    }

    /** Reroll the shop offerings. Returns null on success, else a reason. */
    public String reroll() {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        // Chaos the Clown: the first reroll each shop visit is free.
        boolean free = hasJoker("j_chaos") && !freeRerollUsed;
        int cost = free ? 0 : Shop.REROLL_COST;
        if (!canAfford(cost)) return "not enough money";
        state.money -= cost;
        if (free) freeRerollUsed = true;
        shop = generateShop(); // advances the same game-long queue, skipping owned
        return null;
    }

    /** Buy the planet at the given shop slot into your consumable inventory. */
    public String buyPlanet(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.planets().size()) return "invalid planet slot";
        if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
        int cost = hasJoker("j_astronomer") ? 0 : price(PlanetCatalog.COST); // Astronomer: Planets free
        if (!canAfford(cost)) return "not enough money";
        state.money -= cost;
        state.consumables.add(shop.planets().get(index).key());
        shop.planets().remove(index);
        return null;
    }

    /** Buy a Tarot/Spectral from the shop into your consumable inventory. */
    public String buyConsumable(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.consumables().size()) return "invalid consumable slot";
        if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
        if (!canAfford(price(Shop.CONSUMABLE_COST))) return "not enough money";
        state.money -= price(Shop.CONSUMABLE_COST);
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
            state.planetsUsedThisRun.add(key); // Satellite counts unique planets used
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
            jv.put("cost", info.cost()); // for Swashbuckler's sell-value sum on the client
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
        counters.put("LUCKY_TRIGGERS", state.luckyTriggersTotal);
        counters.put("DISCARDS_USED", state.discardsUsedThisRound);
        counters.put("HANDS_PLAYED", state.handsPlayedThisRound);
        Map<String, Object> typePlays = new LinkedHashMap<>();
        state.handTypePlays.forEach((t, n) -> typePlays.put(t.name(), n));
        counters.put("handTypePlays", typePlays);
        counters.put("handTypesThisRound", state.handTypesThisRound.stream().map(Enum::name).toList());
        counters.put("idolRankId", state.idolRankId);
        counters.put("idolSuit", state.idolSuit.name());
        counters.put("ancientSuit", state.ancientSuit.name());
        counters.put("castleSuit", state.castleSuit.name());
        counters.put("todoHand", state.todoHandType.name());
        counters.put("rebateRankId", state.rebateRankId);
        counters.put("OBELISK_STREAK", state.obeliskStreak);
        counters.put("BLINDS_SKIPPED", state.blindsSkipped);
        counters.put("inPvpBlind", state.inPvpBlind);
        counters.put("multiplayer", state.multiplayer);
        counters.put("OPP_LIVES_BEHIND", Math.max(0, state.oppLives - state.myLives));
        counters.put("OPP_HANDS_LEFT", state.oppHandsLeft);
        counters.put("OPP_CARDS_SOLD", state.oppCardsSold);

        Map<String, Object> shopVoucher = null;
        if (phase == Phase.SHOP && shop != null && shop.voucher() != null) {
            var v = VoucherCatalog.get(shop.voucher());
            shopVoucher = Map.of("key", v.key(), "name", v.name(), "description", v.description(),
                    "cost", price(v.cost()));
        }

        return new ClientView(ante, blind.display, requirement, state.roundScore,
                state.handsLeft, state.discardsLeft, state.money, state.handSize,
                phase.name(), handView, jokerView, shopView, rerollCost,
                boss != null ? boss.name() : null, boss != null ? boss.effect() : null,
                shopPlanets, shopConsumables, consumables, handLevels, deckStats, counters, shopVoucher);
    }

    /** Perkeo: leaving the shop, create a (Negative) copy of a random held consumable. */
    private void applyShopExit() {
        if (!hasJoker("j_perkeo") || state.consumables.isEmpty()) return;
        int idx = (int) (roll("perkeo:dup") * state.consumables.size()) % state.consumables.size();
        state.consumables.add(state.consumables.get(idx)); // Negative copy ignores the slot cap
    }

    /** Grant a tag, honoring a held Double Tag (which duplicates the next tag gained). */
    private void grantTag(String key) {
        int copies = state.tags.remove("tag_double") ? 2 : 1; // Double Tag duplicates the next tag
        for (int i = 0; i < copies; i++) applyTag(key);
    }

    /** Apply a tag's effect (immediate ones resolve now; others are held in the inventory). */
    private void applyTag(String key) {
        switch (key) {
            case "tag_investment" -> state.money += 15; // skip reward: a cash bonus
            default -> state.tags.add(key);             // held (e.g. tag_double) for later
        }
    }

    /** Open the shop's booster pack: pay, reveal a Tarot, raise OPEN_BOOSTER (Hallucination). */
    public String openBooster() {
        if (phase != Phase.SHOP || !boosterAvailable) return "no booster available";
        if (!canAfford(4)) return "not enough money";
        state.money -= 4;
        boosterAvailable = false;
        GameEvents.raise(Trigger.OPEN_BOOSTER, state, rng, null); // Hallucination may add a Tarot
        // The pack's pick (simplified to one Tarot, if there's a consumable slot).
        com.balatromp.engine.consumable.Creation.apply(state,
                new com.balatromp.engine.joker.def.CreateSpec(
                        com.balatromp.engine.joker.def.CreateSpec.Kind.TAROT), state.queues);
        return null;
    }

    /** Skip the shop's booster pack (no cost), raising SKIP_BOOSTER (Red Card). */
    public String skipBooster() {
        if (phase != Phase.SHOP || !boosterAvailable) return "no booster available";
        boosterAvailable = false;
        GameEvents.raise(Trigger.SKIP_BOOSTER, state, rng, null); // Red Card gains +3 Mult
        return null;
    }

    /** Skip the current Small/Big blind (forfeit its reward, bypass the shop). Returns null on success. */
    public String skipBlind() {
        if (phase != Phase.BLIND_ACTIVE) return "not in a blind";
        if (blind == BlindType.BOSS || pvpActive) return "cannot skip this blind";
        state.blindsSkipped++;
        GameEvents.raise(Trigger.SKIP_BLIND, state, rng, null); // Throwback / skip-tag jokers
        grantTag("tag_investment"); // skipping a blind awards a tag (honors a held Double Tag)
        blind = (blind == BlindType.SMALL) ? BlindType.BIG : BlindType.BOSS;
        startBlind();
        return null;
    }

    /** Leave the (stubbed) shop and advance to the next blind / ante / win. */
    public void proceed() {
        if (phase != Phase.SHOP) return;
        applyShopExit(); // Perkeo duplicates a held consumable on the way out
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
