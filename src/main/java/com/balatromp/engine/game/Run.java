package com.balatromp.engine.game;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.consumable.Consumable;
import com.balatromp.engine.consumable.Creation;
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

    public enum Phase { BLIND_SELECT, BLIND_ACTIVE, SHOP, PVP_PENDING, BLIND_FAILED, RUN_WON, RUN_LOST }

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
    private final java.util.List<PackCatalog.Pack> shopPacks = new ArrayList<>(); // 2 packs/shop, kept across rerolls
    private java.util.List<RevealedItem> openPack = null; // the currently-open pack's revealed cards (null = none open)
    private int packPicksLeft = 0;

    /** A revealed pack card — enough to render it and to resolve a pick. type: CONSUMABLE | JOKER | CARD. */
    private record RevealedItem(String type, String key, Card card) {}
    private String anteVoucher = null;        // the single voucher offered this ante (persists across its shops)
    private int anteVoucherAnte = -1;         // which ante anteVoucher was rolled for (-1 = none yet)
    private String lastVoucherShown = null;   // last resolved voucher (for the queue's dup-skip rule)
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
        state.ante = ante; // keep RunState's ante in sync (ante-based conditions + PvP queue keys read it)
        state.roundScore = 0;
        state.discardsUsedThisRound = 0;
        state.handsPlayedThisRound = 0;
        state.handTypesThisRound.clear();
        luchadorDisabledBoss = false; // a fresh blind re-arms the boss (Luchador must be re-sold)
        pvpActive = false;
        state.bossHalveBase = false; // re-armed below only for The Flint
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
            state.bossHalveBase = !disabled && boss.halveBase(); // The Flint
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
        // Offer a skip tag for skippable blinds (Small/Big, non-PvP), from the game-long tag queue
        // resolved against this ante's offerable pool (Ante-1 lockouts + MP bans).
        boolean skippable = blind != BlindType.BOSS && !pvpBoss;
        if (skippable) {
            List<String> pool = TagCatalog.offerable(ante, "multiplayer".equals(ruleset.jokerVariant()));
            state.offeredTag = pool.isEmpty() ? null
                    : pool.get((int) (roll("tags") * pool.size()) % pool.size());
        } else {
            state.offeredTag = null;
        }
        applyBlindTags(); // NEXT_BLIND tags (Juggle: +3 hand size) before the hand is dealt
        dealNewDeck(); // full deck reshuffled fresh each blind
        state.hand.clear();
        state.deck.drawTo(state.hand, state.handSize);
        refreshDebuffs();
        // The blind is set up and dealt; the player now Selects it (play) or Skips it (Small/Big,
        // for a tag). Boss/PvP blinds can't be skipped. play() auto-selects for convenience.
        phase = Phase.BLIND_SELECT;
    }

    /** Commit to playing the offered blind (leave the Select screen). Null on success. */
    public String selectBlind() {
        if (phase != Phase.BLIND_SELECT) return "not selecting a blind";
        phase = Phase.BLIND_ACTIVE;
        return null;
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
        return boss != null && (boss.debuffSuit() != null || boss.debuffFaces() || boss.halveBase()
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
        int slots = state.vouchers.contains("v_overstock_plus") ? 4
                : state.vouchers.contains("v_overstock") ? 3 : 2; // Overstock (+1) / Overstock Plus (+2)
        // Hone (Foil/Holo 2×, Poly 3×) and its upgrade Glow Up (4× / 7×) raise edition odds.
        double editionMult = 1.0, polyMult = 1.0;
        if (state.vouchers.contains("v_glow_up")) { editionMult = 4.0; polyMult = 7.0; }
        else if (state.vouchers.contains("v_hone")) { editionMult = 2.0; polyMult = 3.0; }
        rollAnteVoucherIfNeeded(owned);
        return Shop.generate(state.queues, slots, jokerPoolForShop(), owned,
                hasJoker("j_showman"), editionMult, polyMult, anteVoucher);
    }

    /** The joker pool the shop/packs draw from — in multiplayer the boss-interacting jokers
     *  are excluded from the pool entirely (not merely skipped). */
    private List<String> jokerPoolForShop() {
        if (!"multiplayer".equals(ruleset.jokerVariant())) return ruleset.jokerPool();
        List<String> base = ruleset.jokerPool().isEmpty() ? JokerLibrary.builtinKeys() : ruleset.jokerPool();
        return base.stream().filter(k -> !MP_DISABLED.contains(k)).toList();
    }

    /**
     * Decide the ante's single voucher once per ante, from the game-long voucher queue
     * over the 16 base vouchers. Resolution per drawn position: show Tier 1 until bought,
     * then Tier 2, then skip the position once both tiers are owned. Two consecutive
     * positions resolving to the same voucher skip the second (dup-skip). The queue
     * advances per ante (not per shop), so the voucher is stable within an ante.
     */
    private void rollAnteVoucherIfNeeded(java.util.Set<String> ownedUnused) {
        if (anteVoucherAnte == ante) return;
        anteVoucherAnte = ante;
        boolean mp = "multiplayer".equals(ruleset.jokerVariant());
        java.util.List<String> bases = VoucherCatalog.baseKeys(mp);
        var q = state.queues.queue("vouchers", r -> bases.get(r.nextInt(bases.size())));
        anteVoucher = null;
        for (int attempt = 0; attempt < 64; attempt++) {
            String base = q.next();
            String show;
            if (!state.vouchers.contains(base)) {
                show = base;                                   // Tier 1 not yet bought
            } else {
                String up = VoucherCatalog.upgradeKey(base);
                show = (up != null && !state.vouchers.contains(up)) ? up : null; // Tier 2, or both owned -> skip
            }
            if (show == null) continue;                        // both tiers owned: skip this position
            if (show.equals(lastVoucherShown)) continue;       // dup-skip consecutive identical
            lastVoucherShown = show;
            anteVoucher = show;
            return;
        }
    }

    /** Penny Pincher (Nemesis): on entering the shop, gain $1 per $3 your Nemesis spent last ante. */
    private void applyPennyPincher() {
        if (hasJoker("j_penny_pincher")) state.money += state.oppShopSpentLastAnte / 3;
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
        if ("multiplayer".equals(ruleset.jokerVariant())) {
            rollMpIdol(); // MP: deck-position roll (shared number, each player's own deck)
        } else {
            state.idolSuit = suits[(int) (roll("target:idol:suit") * suits.length) % suits.length];
            state.idolRankId = 2 + (int) (roll("target:idol:rank") * 13) % 13;
        }
        state.ancientSuit = suits[(int) (roll("target:ancient:suit") * suits.length) % suits.length];
        state.castleSuit = suits[(int) (roll("target:castle:suit") * suits.length) % suits.length];
        com.balatromp.engine.hand.HandType[] hands = com.balatromp.engine.hand.HandType.values();
        state.todoHandType = hands[(int) (roll("target:todo:hand") * hands.length) % hands.length];
        state.rebateRankId = 2 + (int) (roll("target:rebate:rank") * 13) % 13;
    }

    /**
     * Multiplayer Idol: sort the deck from the cards you have the most duplicates of to the
     * fewest (ties by suit, then rank with Ace low), roll 1–1000 (the same number for both
     * players), and pick the card at that position. A better-stacked deck reliably lands the
     * roll on your most-common card; both players share the roll, not the result.
     */
    private void rollMpIdol() {
        List<Card> deck = state.deckComposition;
        if (deck.isEmpty()) return;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (Card c : deck) counts.merge(c.rank + "|" + c.suit, 1, Integer::sum);
        List<Card> sorted = new ArrayList<>(deck);
        sorted.sort((a, b) -> {
            int ca = counts.get(a.rank + "|" + a.suit), cb = counts.get(b.rank + "|" + b.suit);
            if (ca != cb) return cb - ca;                       // most duplicates first
            if (a.suit != b.suit) return a.suit.ordinal() - b.suit.ordinal();
            int ra = (a.rank == Rank.ACE) ? 1 : a.rank.id, rb = (b.rank == Rank.ACE) ? 1 : b.rank.id;
            return ra - rb;                                     // Ace low
        });
        int rollPos = 1 + (int) (roll("target:idol:pos") * 1000); // shared 1..1000
        Card target = sorted.get((rollPos - 1) % sorted.size());
        state.idolSuit = target.suit;
        state.idolRankId = target.rank.id;
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
        // Vouchers: permanent per-blind hand / discard / hand-size upgrades (base + Tier 2).
        if (state.vouchers.contains("v_grabber")) state.handsLeft += 1;
        if (state.vouchers.contains("v_nacho_tong")) state.handsLeft += 1;
        if (state.vouchers.contains("v_wasteful")) state.discardsLeft += 1;
        if (state.vouchers.contains("v_recyclomancy")) state.discardsLeft += 1;
        if (state.vouchers.contains("v_paint_brush")) state.handSize += 1;
        if (state.vouchers.contains("v_palette")) state.handSize += 1;
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
        // Pizza: temporary +discards for the ante after it's consumed at a PvP blind.
        if (state.pizzaBlindsLeft > 0) {
            state.discardsLeft += state.pizzaDiscardBonus;
            state.pizzaBlindsLeft--;
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
        if (phase == Phase.BLIND_SELECT) selectBlind(); // playing the blind = selecting it
        if (phase != Phase.BLIND_ACTIVE) {
            return IntentResult.rejected("not in an active blind");
        }
        // PvP blind: each hand replays this ante's PvP queues from the start, so equal hands
        // proc Lucky/Glass/Bloodstone/etc. equally regardless of how many hands are left.
        if (intent instanceof Intent.PlayHand && state.inPvpBlind && state.queues != null) {
            state.queues.reset("pvp:" + state.ante + ":");
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

    /** Whether this run currently owns a joker with the given key (match-layer queries). */
    public boolean ownsJoker(String key) {
        return hasJoker(key);
    }

    /** Remove the first joker with the given key (match-layer consumption, e.g. Pizza). */
    public void removeJoker(String key) {
        for (int i = 0; i < state.jokers().size(); i++) {
            if (state.jokers().get(i).key().equals(key)) { state.jokers().remove(i); return; }
        }
    }

    /** Pizza: add a temporary discard bonus that applies for the next {@code blinds} blinds. */
    public void grantPizzaDiscards(int amount, int blinds) {
        state.pizzaDiscardBonus += amount;
        state.pizzaBlindsLeft = Math.max(state.pizzaBlindsLeft, blinds);
    }

    /** Create a random Spectral into this run if there's a consumable slot (Speedrun). */
    public void grantSpectral() {
        com.balatromp.engine.consumable.Creation.apply(state,
                new com.balatromp.engine.joker.def.CreateSpec(
                        com.balatromp.engine.joker.def.CreateSpec.Kind.SPECTRAL), state.queues);
    }

    /** Attrition: after the match deducts a life for a failed blind, continue to the shop. */
    public void continueAfterFail() {
        if (phase != Phase.BLIND_FAILED) return;
        pvpActive = false;
        shop = generateShop(); // no reward for a failed blind
        shopPacks.clear(); // no packs after a failed blind
        openPack = null;
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
        applyBossDefeatTags(); // Investment Tag pays out after the PvP (Boss) blind too
        GameEvents.endOfRound(state, rng, true); // Nemesis is a Boss blind
        applyGiftCard();
        applyPennyPincher();
        shop = generateShop();
        freeRerollUsed = false;
        rollShopPacks();
        phase = Phase.SHOP;
    }

    private void winBlind() {
        // Economy: blind reward + interest ($1 per $5 held, capped at $5) + joker/gold payouts.
        int interest = roundInterest();
        int reward = (boss != null) ? boss.reward() : blind.reward;
        state.money += reward + interest;
        state.lastBlindReward = reward; // cash-out breakdown for the end-of-round screen
        state.lastInterest = interest;
        if (boss != null) applyBossDefeatTags(); // Investment Tag pays out after a boss
        state.roundsPlayedTotal++;
        GameEvents.endOfRound(state, rng, boss != null);
        applyGiftCard();
        applyPennyPincher();
        phase = Phase.SHOP;
        shop = generateShop();
        freeRerollUsed = false;
        rollShopPacks();
    }

    /**
     * Buy the shop slot at {@code index}, whatever its type: a Joker is added to the
     * joker row (carrying any rolled edition), a Tarot/Planet is added to your
     * consumables. Returns null on success, else a reason.
     */
    public String buyShopItem(int index) {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        if (index < 0 || index >= shop.items().size()) return "invalid shop slot";
        Shop.Item item = shop.items().get(index);
        switch (item.kind()) {
            case JOKER -> {
                if (!canAfford(price(item.cost()))) return "not enough money";
                // A Negative joker grants its own slot, so it never fails the slots-full check.
                boolean negative = item.edition() == Edition.NEGATIVE;
                if (!negative && state.jokers().size() >= state.jokerSlots) return "joker slots full";
                spend(price(item.cost()));
                Joker bought = JokerLibrary.create(item.key(), ruleset.jokerVariant());
                state.addJoker(bought);
                if (item.edition() != Edition.NONE) state.setJokerEdition(bought, item.edition());
            }
            case TAROT, PLANET -> {
                if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
                // Astronomer makes Planets free.
                int cost = (item.kind() == Shop.Kind.PLANET && hasJoker("j_astronomer")) ? 0 : price(item.cost());
                if (!canAfford(cost)) return "not enough money";
                spend(cost);
                state.consumables.add(item.key());
            }
        }
        shop.items().remove(index);
        return null;
    }

    /** Buy the shop's offered voucher. Returns null on success, else a reason. */
    public String buyVoucher() {
        if (phase != Phase.SHOP || shop == null || shop.voucher() == null) return "no voucher offered";
        var v = VoucherCatalog.get(shop.voucher());
        if (!canAfford(price(v.cost()))) return "not enough money";
        spend(price(v.cost()));
        state.vouchers.add(v.key());
        // Immediate-effect vouchers resolve now; per-blind/shop ones apply where they're read.
        switch (v.key()) {
            case "v_crystal_ball", "v_omen_globe" -> state.consumableSlots += 1;
            case "v_seed_money" -> state.interestCap = 10;
            case "v_money_tree" -> state.interestCap = 20;
            case "v_antimatter" -> state.jokerSlots += 1;
            default -> { /* passive — read in generateShop / startBlind / price / reroll */ }
        }
        shop.clearVoucher();
        anteVoucher = null; // bought — don't re-offer it in this ante's later shops
        return null;
    }

    /** Apply the Clearance Sale (25% off) / Liquidation (50% off) shop discount, rounded down. */
    private int price(int cost) {
        if (state.vouchers.contains("v_liquidation")) return (int) (cost * 0.50);
        return state.vouchers.contains("v_clearance_sale") ? (int) (cost * 0.75) : cost;
    }

    /** Pay {@code cost} from money and record it as shop spend this ante (feeds Penny Pincher). */
    private void spend(int cost) {
        state.money -= cost;
        state.shopSpentThisAnte += cost;
    }

    /** Sell the joker at the given slot (shop or during a blind). Returns null on success. */
    public String sellJoker(int index) {
        if (phase != Phase.SHOP && phase != Phase.BLIND_ACTIVE && phase != Phase.BLIND_SELECT) {
            return "cannot sell now";
        }
        if (index < 0 || index >= state.jokers().size()) return "invalid joker";
        Joker sold = state.jokers().remove(index);
        state.cardsSoldSinceLastPvp++; // feeds the opponent's Taxes joker
        int bonus = ((Number) state.jokerState(sold).getOrDefault("sellBonus", 0)).intValue();
        state.money += Math.max(1, sold.info().cost() / 2) + bonus; // sell value (+ Egg/Gift bonus)
        // Invisible Joker: sold after >=2 rounds owned, duplicate a random remaining joker.
        if (sold.key().equals("j_invisible")
                && ((Number) state.jokerState(sold).getOrDefault("rounds", 0)).intValue() >= 2
                && !state.jokers().isEmpty() && state.jokers().size() < Shop.JOKER_SLOT_LIMIT) {
            // MP: copy the rightmost remaining joker (deterministic + comparable between players);
            // single-player keeps the vanilla random copy.
            int pick = "multiplayer".equals(ruleset.jokerVariant())
                    ? state.jokers().size() - 1
                    : (int) (roll("invisible:dup") * state.jokers().size()) % state.jokers().size();
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

    /** Current reroll cost: base $5, reduced $2 by Reroll Surplus and a further $2 by Reroll Glut (floor $0). */
    private int rerollCost() {
        int cost = Shop.REROLL_COST;
        if (state.vouchers.contains("v_reroll_surplus")) cost -= 2;
        if (state.vouchers.contains("v_reroll_glut")) cost -= 2;
        return Math.max(0, cost);
    }

    /** Reroll the shop offerings. Returns null on success, else a reason. */
    public String reroll() {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        // Chaos the Clown: the first reroll each shop visit is free.
        boolean free = hasJoker("j_chaos") && !freeRerollUsed;
        int cost = free ? 0 : rerollCost();
        if (!canAfford(cost)) return "not enough money";
        spend(cost);
        if (free) freeRerollUsed = true;
        shop = generateShop(); // advances the same game-long queue, skipping owned
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
            state.lastTarotPlanetUsed = key;    // The Fool can copy it
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
            // Free this consumable's slot BEFORE applying, so a generative effect's created
            // consumables can occupy the slot it just vacated (Balatro's ordering).
            state.consumables.remove(index);
            applyConsumable(c, targets);
            // The Fool copies the last Tarot/Planet used — track Tarots here (but never The Fool itself).
            if (c.type() == com.balatromp.engine.consumable.ConsumableType.TAROT && !key.equals("c_fool")) {
                state.lastTarotPlanetUsed = key;
            }
            GameEvents.useConsumable(state, rng, c.type().label());
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
            case Consumable.LevelAllHands ignored -> {
                for (HandType t : HandType.values()) state.levelUpHand(t); // Black Hole
            }
            case Consumable.JokerEdition je -> applyJokerEdition(c, je);
            case Consumable.Generate g -> applyGenerate(c, g);
            case Consumable.ConvertHand ch -> applyConvertHand(c, ch);
            case Consumable.CopySelected cs -> {
                if (!targets.isEmpty()) {
                    Card src = targets.get(0);
                    for (int i = 0; i < cs.copies(); i++) {
                        Card dup = src.copy(); // fresh uid, same rank/suit/enh/edition/seal
                        composition.add(dup);
                        state.hand.add(dup);
                    }
                }
            }
            case Consumable.OverwriteSelected ignored -> {
                if (targets.size() == 2) { // Death: the left (first) card becomes a copy of the right
                    Card left = targets.get(0), right = targets.get(1);
                    left.rank = right.rank;
                    left.suit = right.suit;
                    left.enhancement = right.enhancement;
                    left.edition = right.edition;
                    left.seal = right.seal;
                }
            }
            case Consumable.CopyRandomJoker cj -> applyCopyRandomJoker(c, cj);
            case Consumable.CopyLastConsumable ignored -> {
                String last = state.lastTarotPlanetUsed;
                if (last != null && state.consumables.size() < state.consumableSlots) {
                    state.consumables.add(last);
                }
            }
            case Consumable.NemesisDelevel ignored -> state.nemesisDelevelPending++; // Match applies it to the opponent
        }
    }

    /** Sigil (all cards to one random suit) / Ouija (all to one random rank, -1 hand size). */
    private void applyConvertHand(Consumable c, Consumable.ConvertHand ch) {
        // MP Ouija rework: destroy 3 random cards first, convert the rest to one rank, and DON'T
        // reduce hand size (vanilla Ouija converts the whole hand and loses 1 hand size).
        boolean mpOuija = "c_ouija".equals(c.key()) && "multiplayer".equals(ruleset.jokerVariant());
        if (mpOuija) {
            for (int i = 0; i < 3; i++) {
                List<Card> live = state.hand.stream().filter(x -> !x.destroyed).toList();
                if (live.isEmpty()) break;
                int pick = (int) (roll("consumable:" + c.key() + ":destroy:" + i) * live.size()) % live.size();
                live.get(pick).destroyed = true;
            }
            composition.removeIf(x -> x.destroyed);
            state.hand.removeIf(x -> x.destroyed);
            Rank[] ranks = Rank.values();
            Rank r = ranks[(int) (roll("consumable:" + c.key() + ":rank") * ranks.length) % ranks.length];
            for (Card card : state.hand) card.rank = r;
            return; // no hand-size reduction in MP
        }
        if (ch.toRandomSuit()) {
            Suit[] suits = Suit.values();
            Suit s = suits[(int) (roll("consumable:" + c.key() + ":suit") * suits.length) % suits.length];
            for (Card card : state.hand) card.suit = s;
        }
        if (ch.toRandomRank()) {
            Rank[] ranks = Rank.values();
            Rank r = ranks[(int) (roll("consumable:" + c.key() + ":rank") * ranks.length) % ranks.length];
            for (Card card : state.hand) card.rank = r;
        }
        if (ch.handSizeDelta() != 0) state.handSize = Math.max(1, state.handSize + ch.handSizeDelta());
    }

    /** Ankh: copy a random owned joker (edition-free) and, if set, destroy all other jokers. */
    private void applyCopyRandomJoker(Consumable c, Consumable.CopyRandomJoker cj) {
        if (state.jokers().isEmpty()) return;
        int pick = (int) (roll("consumable:" + c.key() + ":joker") * state.jokers().size())
                % state.jokers().size();
        Joker chosen = state.jokers().get(pick);
        if (cj.destroyOthers()) state.jokers().removeIf(j -> j != chosen);
        if (state.jokers().size() < state.jokerSlots) {
            state.addJoker(JokerLibrary.create(chosen.key(), ruleset.jokerVariant())); // copy has no edition
        }
    }

    // Enhancements a "random Enhanced" created card (Familiar/Grim) may roll — every type but NONE.
    private static final Enhancement[] RANDOM_ENHANCEMENTS = {
        Enhancement.BONUS, Enhancement.MULT, Enhancement.GLASS, Enhancement.STEEL,
        Enhancement.STONE, Enhancement.GOLD, Enhancement.WILD, Enhancement.LUCKY
    };

    /**
     * Apply a generative consumable: destroy random hand cards, create
     * consumables/jokers/cards, add rank-class cards, then run a money op — in order.
     */
    private void applyGenerate(Consumable c, Consumable.Generate g) {
        // 1. destroy N random hand cards (and the same objects from the persistent deck).
        for (int i = 0; i < g.destroyRandomInHand(); i++) {
            List<Card> live = state.hand.stream().filter(x -> !x.destroyed).toList();
            if (live.isEmpty()) break;
            int pick = (int) (roll("consumable:" + c.key() + ":destroy:" + i) * live.size()) % live.size();
            live.get(pick).destroyed = true;
        }
        if (g.destroyRandomInHand() > 0) {
            composition.removeIf(x -> x.destroyed);
            state.hand.removeIf(x -> x.destroyed);
        }
        // 2. create consumables / jokers / cards from the spec (server-only, queue-driven).
        if (g.create() != null) Creation.apply(state, g.create(), state.queues);
        // 3. add rank-class cards with a fixed or random enhancement.
        if (g.add() != null) addRankClassCards(c, g.add());
        // 4. money op.
        if (g.money() != null) applyMoneyOp(g.money());
    }

    private void addRankClassCards(Consumable c, Consumable.Generate.AddCards add) {
        Rank[] pool = switch (add.rankClass()) {
            case FACE -> new Rank[]{Rank.JACK, Rank.QUEEN, Rank.KING};
            case ACE -> new Rank[]{Rank.ACE};
            case NUMBER -> java.util.Arrays.stream(Rank.values()).filter(r -> r.id <= 10).toArray(Rank[]::new);
            case ANY -> Rank.values();
        };
        Suit[] suits = Suit.values();
        for (int i = 0; i < add.count(); i++) {
            Rank r = pool[(int) (roll("consumable:" + c.key() + ":rank:" + i) * pool.length) % pool.length];
            Suit s = suits[(int) (roll("consumable:" + c.key() + ":suit:" + i) * suits.length) % suits.length];
            Enhancement e = add.enhancement() != null ? add.enhancement()
                    : RANDOM_ENHANCEMENTS[(int) (roll("consumable:" + c.key() + ":enh:" + i)
                            * RANDOM_ENHANCEMENTS.length) % RANDOM_ENHANCEMENTS.length];
            Card made = new Card(r, s, e, Edition.NONE, Seal.NONE);
            composition.add(made); // persistent deck (drawn next blind)
            state.hand.add(made);  // and usable now
        }
    }

    private void applyMoneyOp(Consumable.Generate.MoneyOp m) {
        switch (m.kind()) {
            case FLAT -> state.money = Math.max(0, state.money + m.amount());
            case SET -> state.money = Math.max(0, m.amount());
            case DOUBLE_CAP -> state.money += Math.min(state.money, m.amount()); // Hermit: double, gain ≤ cap
            case SELL_VALUE_CAP -> {
                int sell = 0;
                for (Joker j : state.jokers()) {
                    int bonus = ((Number) state.jokerState(j).getOrDefault("sellBonus", 0)).intValue();
                    sell += Math.max(1, j.info().cost() / 2) + bonus;
                }
                state.money += Math.min(sell, m.amount()); // Temperance: total sell value, capped
            }
        }
    }

    /**
     * Add an edition to a random owned joker (Wheel of Fortune / Ectoplasm / Hex).
     * Wheel gates on a 1-in-N roll and picks a random Foil/Holo/Poly; Ectoplasm and
     * Hex always fire and carry their own side effects (hand-size / destroy-others).
     */
    private void applyJokerEdition(Consumable c, Consumable.JokerEdition je) {
        if (state.jokers().isEmpty()) return;
        if (je.chanceDenominator() > 1 && roll("consumable:" + c.key() + ":gate") >= 1.0 / je.chanceDenominator()) {
            return; // the roll missed (Wheel of Fortune's 3-in-4 nothing-happens)
        }
        int pick = (int) (roll("consumable:" + c.key() + ":joker") * state.jokers().size()) % state.jokers().size();
        Joker target = state.jokers().get(pick);
        Edition ed = je.edition();
        if (ed == Edition.NONE) {
            Edition[] pool = {Edition.FOIL, Edition.HOLOGRAPHIC, Edition.POLYCHROME};
            ed = pool[(int) (roll("consumable:" + c.key() + ":ed") * pool.length) % pool.length];
        }
        if (je.destroyOtherJokers()) { // Hex: keep only the chosen joker
            state.jokers().removeIf(j -> j != target);
        }
        state.setJokerEdition(target, ed);
        if (je.handSizeDelta() != 0) state.handSize += je.handSizeDelta(); // Ectoplasm -1
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

        // The shop's mixed main slots — each a Joker, Tarot, or Planet (kind tells the client
        // how to render/label it, and which buy path the server takes on buyShopItem).
        List<Map<String, Object>> shopView = null;
        int rerollCost = 0;
        if (phase == Phase.SHOP && shop != null) {
            shopView = new ArrayList<>();
            for (Shop.Item it : shop.items()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", it.kind().name());
                m.put("key", it.key());
                m.put("name", it.name());
                m.put("description", it.description());
                m.put("cost", it.cost());
                m.put("rarity", it.rarity());
                m.put("edition", it.edition().name());
                shopView.add(m);
            }
            rerollCost = rerollCost();
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
        counters.put("bossHalveBase", state.bossHalveBase); // The Flint: preview halves base too
        counters.put("multiplayer", state.multiplayer);
        // Cash-out breakdown (the end-of-round screen reads these when entering the shop).
        counters.put("cashOutReward", state.lastBlindReward);
        counters.put("cashOutInterest", state.lastInterest);
        // The tag offered for skipping this blind (shown on the Select/Skip screen).
        counters.put("offeredTag", state.offeredTag == null ? "" : state.offeredTag);
        counters.put("offeredTagName", state.offeredTag == null ? ""
                : (TagCatalog.get(state.offeredTag) != null ? TagCatalog.get(state.offeredTag).name() : state.offeredTag));
        counters.put("heldTags", new ArrayList<>(state.tags));
        counters.put("OPP_LIVES_BEHIND", Math.max(0, state.oppLives - state.myLives));
        counters.put("OPP_HANDS_LEFT", state.oppHandsLeft);
        counters.put("OPP_CARDS_SOLD", state.oppCardsSold);

        Map<String, Object> shopVoucher = null;
        if (phase == Phase.SHOP && shop != null && shop.voucher() != null) {
            var v = VoucherCatalog.get(shop.voucher());
            shopVoucher = Map.of("key", v.key(), "name", v.name(), "description", v.description(),
                    "cost", price(v.cost()));
        }

        // The shop's two booster packs (kept across rerolls), and the currently-open pack if any.
        List<Map<String, Object>> packsView = null;
        if (phase == Phase.SHOP) {
            packsView = new ArrayList<>();
            for (PackCatalog.Pack p : shopPacks) {
                packsView.add(Map.of("kind", p.kind().name(), "size", p.size().name(),
                        "name", p.displayName(), "cost", price(p.cost()),
                        "shown", p.shown(), "choose", p.choose()));
            }
        }
        Map<String, Object> openPackView = null;
        if (openPack != null) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (RevealedItem it : openPack) items.add(revealedItemView(it));
            openPackView = Map.of("picksLeft", packPicksLeft, "items", items);
        }

        return new ClientView(ante, blind.display, requirement, state.roundScore,
                state.handsLeft, state.discardsLeft, state.money, state.handSize,
                phase.name(), handView, jokerView, shopView, rerollCost,
                boss != null ? boss.name() : null, boss != null ? boss.effect() : null,
                consumables, handLevels, deckStats, counters, shopVoucher, packsView, openPackView);
    }

    /** View of one revealed pack card: a consumable/joker (key+name+desc) or a playing card. */
    private Map<String, Object> revealedItemView(RevealedItem it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", it.type());
        switch (it.type()) {
            case "JOKER" -> {
                var info = JokerLibrary.create(it.key()).info();
                m.put("key", it.key());
                m.put("name", info.name());
                m.put("description", info.description());
            }
            case "CARD" -> {
                Card c = it.card();
                m.put("name", c.rank.name() + " of " + c.suit.name());
                m.put("rank", c.rank.name());
                m.put("suit", c.suit.name());
                m.put("enhancement", c.enhancement.name());
            }
            default -> { // CONSUMABLE
                PlanetCatalog.Planet p = PlanetCatalog.get(it.key());
                Consumable c = TarotCatalog.get(it.key());
                m.put("key", it.key());
                m.put("name", p != null ? p.name() : (c != null ? c.name() : it.key()));
                m.put("description", p != null ? p.description() : (c != null ? c.description() : ""));
            }
        }
        return m;
    }

    /** Perkeo: leaving the shop, create a (Negative) copy of a random held consumable. */
    private void applyShopExit() {
        if (!hasJoker("j_perkeo") || state.consumables.isEmpty()) return;
        int idx = (int) (roll("perkeo:dup") * state.consumables.size()) % state.consumables.size();
        state.consumables.add(state.consumables.get(idx)); // Negative copy ignores the slot cap
    }

    /** Grant a tag, honoring a held Double Tag (which duplicates the next tag gained). */
    private void grantTag(String key) {
        if (key == null) return;
        int copies = state.tags.remove("tag_double") ? 2 : 1; // Double Tag duplicates the next tag
        for (int i = 0; i < copies; i++) applyTag(key);
    }

    /** Apply a tag: IMMEDIATE effects resolve now; the rest are held for their trigger
     *  (ON_SHOP / ON_BOSS_DEFEAT / NEXT_BLIND), resolved at those moments. */
    private void applyTag(String key) {
        if (TagCatalog.timing(key) == TagCatalog.Timing.IMMEDIATE) {
            applyImmediateTag(key);
        } else {
            state.tags.add(key);
        }
    }

    private void applyImmediateTag(String key) {
        switch (key) {
            case "tag_economy" -> state.money += Math.min(state.money, 40);   // double money, max +$40
            case "tag_speed" -> state.money += 5 * state.blindsSkipped;        // $5 per blind skipped
            case "tag_handy" -> state.money += state.handsPlayedTotal;         // $1 per hand played
            case "tag_garbage" -> state.money += state.cardsDiscardedTotal;    // ~$1 per discard this run
            case "tag_orbital" -> {
                HandType best = HandType.HIGH_CARD;
                int most = -1;
                for (var e : state.handTypePlays.entrySet()) {
                    if (e.getValue() > most) { most = e.getValue(); best = e.getKey(); }
                }
                for (int i = 0; i < 3; i++) state.levelUpHand(best);           // +3 levels to most-played
            }
            case "tag_top_up" -> {                                            // up to 2 Common Jokers
                List<String> commons = JokerLibrary.keysByRarity("Common");
                var q = state.queues.queue("tag:topup", r -> commons.get(r.nextInt(commons.size())));
                for (int i = 0; i < 2 && state.jokers().size() < state.jokerSlots && !commons.isEmpty(); i++) {
                    state.addJoker(JokerLibrary.create(q.next(), ruleset.jokerVariant()));
                }
            }
            default -> { /* not an immediate tag */ }
        }
    }

    /** NEXT_BLIND tags, applied at blind start (before the hand is dealt). */
    private void applyBlindTags() {
        if (state.tags.remove("tag_juggle")) state.handSize += 3; // Juggle: +3 hand size this round
    }

    /** ON_BOSS_DEFEAT tags: each held Investment Tag pays $25 after a boss is beaten. */
    private void applyBossDefeatTags() {
        long inv = state.tags.stream().filter(t -> t.equals("tag_investment")).count();
        if (inv > 0) {
            state.tags.removeIf(t -> t.equals("tag_investment"));
            state.money += (int) (25 * inv);
        }
    }

    /** Roll this shop's two booster packs from the game-long packs queue (kept across rerolls). */
    private void rollShopPacks() {
        shopPacks.clear();
        openPack = null;
        packPicksLeft = 0;
        var q = state.queues.queue("packs", r -> PackCatalog.roll(r.nextDouble()));
        shopPacks.add(q.next());
        shopPacks.add(q.next());
    }

    /** Open a shop pack: pay, remove it, reveal its contents (from the Pack queues + Soul queue). */
    public String openPack(int index) {
        if (phase != Phase.SHOP) return "not in shop";
        if (openPack != null) return "finish the open pack first";
        if (index < 0 || index >= shopPacks.size()) return "no such pack";
        PackCatalog.Pack pack = shopPacks.get(index);
        if (!canAfford(price(pack.cost()))) return "not enough money";
        spend(price(pack.cost()));
        shopPacks.remove(index);
        GameEvents.raise(Trigger.OPEN_BOOSTER, state, rng, null); // Hallucination etc. (Up-Top queue)
        openPack = revealPack(pack);
        packPicksLeft = pack.choose();
        return null;
    }

    /** Reveal a pack's cards from its dedicated Pack queue (separate from the Up-Top creation queues),
     *  rolling the Soul queue per consumable slot so The Soul / Black Hole can surface. */
    private java.util.List<RevealedItem> revealPack(PackCatalog.Pack pack) {
        java.util.List<RevealedItem> out = new ArrayList<>();
        int n = pack.shown();
        switch (pack.kind()) {
            case ARCANA -> fillConsumables(out, n, "pack:tarot", TarotCatalog.tarotKeys(), "c_the_soul");
            case SPECTRAL -> fillConsumables(out, n, "pack:spectral", TarotCatalog.spectralKeys(), "c_the_soul");
            case CELESTIAL -> fillConsumables(out, n, "pack:planet", PlanetCatalog.keys(), "c_black_hole");
            case BUFFOON -> {
                // Shares the shop joker rarity sub-queues (opening a Buffoon consumes those jokers).
                java.util.Set<String> offered = new java.util.HashSet<>();
                for (int i = 0; i < n; i++) {
                    out.add(new RevealedItem("JOKER",
                            Shop.drawJoker(state.queues, jokerPoolForShop(), java.util.Set.of(), offered, false), null));
                }
            }
            case STANDARD -> {
                Rank[] ranks = Rank.values();
                Suit[] suits = Suit.values();
                var q = state.queues.queue("pack:card",
                        r -> new Card(ranks[r.nextInt(ranks.length)], suits[r.nextInt(suits.length)],
                                Enhancement.NONE, Edition.NONE, Seal.NONE));
                for (int i = 0; i < n; i++) out.add(new RevealedItem("CARD", null, q.next()));
            }
        }
        return out;
    }

    /** Fill {@code n} consumable slots from the pack queue, rolling the Soul queue per slot;
     *  on a Soul hit the content queue is NOT advanced (it's pushed back) and the Soul is inserted. */
    private void fillConsumables(java.util.List<RevealedItem> out, int n, String queueKey,
            List<String> pool, String soulKey) {
        var q = state.queues.queue(queueKey, r -> pool.get(r.nextInt(pool.size())));
        var soulQ = state.queues.queue("soul", r -> r.nextDouble() < 0.003); // ~0.3% per slot
        for (int i = 0; i < n; i++) {
            if (soulQ.next()) out.add(new RevealedItem("CONSUMABLE", soulKey, null));
            else out.add(new RevealedItem("CONSUMABLE", q.next(), null));
        }
    }

    /** Take one revealed card from the open pack into your inventory/deck. */
    public String pickPackItem(int index) {
        if (openPack == null) return "no open pack";
        if (index < 0 || index >= openPack.size()) return "no such pack card";
        if (packPicksLeft <= 0) return "no picks left";
        RevealedItem it = openPack.get(index);
        switch (it.type()) {
            case "JOKER" -> {
                if (state.jokers().size() >= state.jokerSlots) return "joker slots full";
                state.addJoker(JokerLibrary.create(it.key(), ruleset.jokerVariant()));
            }
            case "CARD" -> {
                Card c = it.card().copy();
                composition.add(c);
                state.hand.add(c);
            }
            default -> { // CONSUMABLE (Tarot/Planet/Spectral)
                if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
                state.consumables.add(it.key());
            }
        }
        openPack.remove(index);
        if (--packPicksLeft <= 0) { // all picks used -> pack closes
            openPack = null;
        }
        return null;
    }

    /** Skip the rest of the open pack (counts as a skip for Red Card). */
    public String skipPack() {
        if (openPack == null) return "no open pack";
        openPack = null;
        packPicksLeft = 0;
        GameEvents.raise(Trigger.SKIP_BOOSTER, state, rng, null); // Red Card gains +Mult
        return null;
    }

    /** Skip the current Small/Big blind (forfeit its reward, bypass the shop). Returns null on success. */
    public String skipBlind() {
        if (phase != Phase.BLIND_SELECT && phase != Phase.BLIND_ACTIVE) return "not at a blind";
        if (blind == BlindType.BOSS || pvpActive) return "cannot skip this blind";
        state.blindsSkipped++;
        GameEvents.raise(Trigger.SKIP_BLIND, state, rng, null); // Throwback / skip-tag jokers
        grantTag(state.offeredTag != null ? state.offeredTag : "tag_investment"); // claim the offered tag
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
                state.shopSpentLastAnte = state.shopSpentThisAnte; // snapshot for Penny Pincher
                state.shopSpentThisAnte = 0;
            }
        }
        startBlind();
    }
}
