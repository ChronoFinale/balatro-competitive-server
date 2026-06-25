package com.balatro.engine.game;

import com.balatro.grammar.Hand;
import com.balatro.dsl.*;
import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.Creation;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.intent.IntentHandler;
import com.balatro.engine.intent.IntentResult;
import com.balatro.engine.intent.RunAction;
import com.balatro.engine.joker.Joker;
import com.balatro.grammar.Trigger;
import com.balatro.engine.joker.JokerDisplay;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Rule;
import com.balatro.grammar.Selector;
import com.balatro.grammar.Modify;
import com.balatro.grammar.Value;
import com.balatro.grammar.JokerDef;
import com.balatro.grammar.CreateSpec;
import com.balatro.engine.net.CardView;
import com.balatro.engine.net.ClientView;
import com.balatro.engine.net.ServerUpdate;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.rng.RngContext;
import com.balatro.engine.rng.RngSource;
import com.balatro.engine.rng.RngSources;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.scoring.ReplayEntry;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.state.Deck;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.RunState;
import com.balatro.engine.state.Stake;
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
 * The SHOP phase is fully live: rerolls, vouchers, packs, and buy/sell all run through here.
 */
public final class Run {

    public enum Phase { BLIND_SELECT, BLIND_ACTIVE, SHOP, PVP_PENDING, BLIND_FAILED, RUN_WON, RUN_LOST }

    /** Synthetic "boss" shown for an Attrition Nemesis blind (no debuff effect). */
    private static final BossBlind NEMESIS = Bosses.of("bl_pvp", "Nemesis Blind")
            .desc("Head-to-head — higher score wins; lower loses a life").minAnte(2).requirement(1.0).build();

    public final Ruleset ruleset;
    public final Stake stake;
    public final DeckCatalog.DeckType deckType;
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
    private int freeRerollsUsedThisShop = 0; // free rerolls spent this shop (vs the FREE_REROLLS fold: Chaos)
    private int rerollsThisShop = 0;         // reroll cost escalates +$1 per reroll, reset each shop
    private final java.util.List<PackCatalog.Pack> shopPacks = new ArrayList<>(); // 2 packs/shop, kept across rerolls
    private java.util.List<RevealedItem> openPack = null; // the currently-open pack's revealed cards (null = none open)
    private int packPicksLeft = 0;

    /** A revealed pack card — enough to render it and to resolve a pick. type: CONSUMABLE | JOKER | CARD. */
    private record RevealedItem(String type, String key, Card card) {}
    private String anteVoucher = null;        // the single voucher offered this ante (persists across its shops)
    private int anteVoucherAnte = -1;         // which ante anteVoucher was rolled for (-1 = none yet)
    private String lastVoucherShown = null;   // last resolved voucher (for the queue's dup-skip rule)
    private final List<String> tagVouchers = new ArrayList<>(); // extra vouchers a Voucher Tag added this shop visit
    private boolean couponActive = false;     // Coupon Tag: this shop's initial cards/packs are free
    private boolean d6Active = false;         // D6 Tag: rerolls start at $0 this shop
    private boolean luchadorDisabledBoss = false; // Luchador: boss disabled for the current blind
    private boolean jokersHidden = false;         // Amber Acorn: Jokers flipped face down (hidden in the view)
    private final List<Card> composition = state.deckComposition; // the full deck (lives on RunState)

    // Identity tiebreaks for composition picks. Same-key jokers/consumables are interchangeable for a
    // "random" pick, so the comparator is neutral; the grouping (by key) is what makes the choice
    // depend on the set held rather than its left-to-right order.
    private static final java.util.Comparator<Joker> JOKER_QUALITY = (a, b) -> 0;

    public Run(Ruleset ruleset, String seed) {
        this(ruleset, seed, Deck.standard(), List.of(), Stake.WHITE);
    }

    public Run(Ruleset ruleset, String seed, Stake stake) {
        this(ruleset, seed, Deck.standard(), List.of(), stake);
    }

    /** Pick a deck and a stake at run creation (standard composition, no starting jokers). */
    public Run(Ruleset ruleset, String seed, Stake stake, DeckCatalog.DeckType deckType) {
        this(ruleset, seed, Deck.standard(), List.of(), stake, deckType);
    }

    public Run(Ruleset ruleset, String seed, Deck deck, List<Joker> jokers) {
        this(ruleset, seed, deck, jokers, Stake.WHITE);
    }

    public Run(Ruleset ruleset, String seed, Deck deck, List<Joker> jokers, Stake stake) {
        this(ruleset, seed, deck, jokers, stake, DeckCatalog.get(ruleset.deckType()));
    }

    public Run(Ruleset ruleset, String seed, Deck deck, List<Joker> jokers, Stake stake,
               DeckCatalog.DeckType deckType) {
        this.ruleset = ruleset;
        this.stake = stake;
        this.deckType = deckType;
        this.rng = new RandomStreams(seed);
        state.capabilities = ruleset.capabilities(); // the mode's knobs (Glass mult, idol/dup/ouija/pools)
        state.jokerVariant = ruleset.jokerVariant();  // so server-side Creation applies MP joker reworks too
        state.balanceChipsMult = deckType.balanceChipsMult(); // Plasma Deck balances chips & mult
        state.money = (int) com.balatro.engine.eval.ModifyFolder.fold(ruleset.startingMoney(), Value.Var.MONEY, deckType.mods()); // Yellow Deck: +$10
        // (deck/voucher/joker economy is RESOLVED at end of round from the owned sources — see endOfRoundMoney)
        state.rng = rng;
        state.order = ruleset.order();
        state.queues = new com.balatro.engine.rng.QueueSet(rng);
        for (Joker j : jokers) state.addJoker(j);
        for (Card c : deck.cards()) composition.add(c.copy()); // capture deck composition
        applyDeckComposition(); // Abandoned/Checkered/Erratic reshape the starting deck
        applyDeckStartingItems(); // Magic/Nebula/Zodiac grant vouchers/consumables before the first blind
        startBlind();
    }

    /** Grant a deck's starting vouchers/consumables (game.lua:633-638); consumable slots are derived. */
    private void applyDeckStartingItems() {
        for (String v : deckType.startingVouchers()) grantVoucher(v);
        state.consumables.addAll(deckType.startingConsumables());
        recomputeSlots();
    }

    private static final int BASE_CONSUMABLE_SLOTS = 2; // before deck/voucher CONSUMABLE_SLOTS Modifys
    private static final int BASE_JOKER_SLOTS = 5;       // before deck/voucher JOKER_SLOTS Modifys

    /** Fold {@code var} from every owned voucher's Modifys over {@code base} — the derived-from-ownership
     *  pattern for voucher-driven variables (win ante, boss rerolls, held-planet mult, ...). */
    private double voucherFoldD(Value.Var var, double base) {
        List<Modify> mods = new ArrayList<>();
        for (String v : state.vouchers) {
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) mods.addAll(def.mods());
        }
        return com.balatro.engine.eval.ModifyFolder.fold(base, var, mods);
    }

    private int voucherFold(Value.Var var, int base) {
        return (int) voucherFoldD(var, base);
    }

    /** Slot counts = base + every CONSUMABLE_SLOTS / JOKER_SLOTS Modify owned (deck + Crystal Ball/Omen
     *  Globe / Antimatter / Black/Painted decks), folded fresh — a pure function of what you own,
     *  recomputed whenever ownership changes. */
    private void recomputeSlots() {
        List<Modify> mods = new ArrayList<>(deckType.mods());
        for (String v : state.vouchers) {
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) mods.addAll(def.mods());
        }
        state.consumableSlots = (int) com.balatro.engine.eval.ModifyFolder.fold(BASE_CONSUMABLE_SLOTS, Value.Var.CONSUMABLE_SLOTS, mods);
        state.jokerSlots = (int) com.balatro.engine.eval.ModifyFolder.fold(BASE_JOKER_SLOTS, Value.Var.JOKER_SLOTS, mods);
    }

    /** Reshape the starting composition for decks that change it (game.lua:636-642). */
    private void applyDeckComposition() {
        switch (deckType.composition()) {
            case NO_FACES -> composition.removeIf(c -> c.rank.isFace()); // Abandoned: drop J/Q/K
            case CHECKERED -> { // Clubs -> Spades, Diamonds -> Hearts (26 Spades + 26 Hearts)
                for (Card c : composition) {
                    if (c.suit == Suit.CLUBS) c.suit = Suit.SPADES;
                    else if (c.suit == Suit.DIAMONDS) c.suit = Suit.HEARTS;
                }
            }
            case ERRATIC -> { // each card: a random rank AND suit, from the 'erratic' stream
                var r = rng.stream("erratic");
                Rank[] ranks = Rank.values();
                Suit[] suits = Suit.values();
                for (Card c : composition) {
                    c.rank = ranks[r.nextInt(ranks.length)];
                    c.suit = suits[r.nextInt(suits.length)];
                }
            }
            case STANDARD -> { /* the plain 52 */ }
        }
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
        state.deck.shuffle(state.queues, rngCtx());
    }

    private void startBlind() {
        state.ante = ante; // keep RunState's ante in sync (ante-based conditions + PvP queue keys read it)
        tagVouchers.clear(); // Voucher-Tag vouchers belong to one shop visit, not across blinds
        state.roundScore = 0;
        state.discardsUsedThisRound = 0;
        state.handsPlayedThisRound = 0;
        state.handTypesThisRound.clear();
        state.lastPlayedHandType = null;
        luchadorDisabledBoss = false; // a fresh blind re-arms the boss (Luchador must be re-sold)
        pvpActive = false;
        state.bossHalveBase = false; // re-armed below only for The Flint
        boolean pvpBoss = blind == BlindType.BOSS && pvpFromAnte > 0 && ante >= pvpFromAnte;
        state.inPvpBlind = pvpBoss; // Nemesis jokers (Pacifist, Conjoined) read this
        // Blue stake (+) starts every round with one fewer discard (game.lua:2056). The stake reduces
        // the per-round baseline; a boss discard-override still replaces it outright.
        // Hands, discards and hand size are no longer set per-branch: every contributor (deck, boss,
        // joker, voucher, skip-off…) is a Modify on a game variable, folded in compute* below — one
        // variable, one applier. The branches only decide the boss and the requirement.
        if (pvpBoss) {
            // Attrition Nemesis blind: no clear-requirement; play all hands, compare to opponent.
            pvpActive = true;
            boss = NEMESIS;
            requirement = 0; // outcome is decided by the head-to-head comparison
        } else if (blind == BlindType.BOSS) {
            boss = (forcedBoss != null) ? forcedBoss : BossCatalog.pick(ante, rng);
            // Boss Tag (held): free one-shot reroll of the boss on arrival, consumed in the process.
            if (forcedBoss == null && state.tags.remove("tag_boss")) {
                boss = BossCatalog.pick(ante, rng, 1);
            }
            boolean disabled = bossDisabled(); // Chicot: ignore the boss's hand/discard/size ability
            state.bossHalveBase = !disabled && boss.halveBase(); // The Flint
            requirement = Math.round(Blinds.getBlindAmount(ante, ruleset, stake.scaling()) * boss.reqMult() * ruleset.anteScaling());
        } else {
            boss = null;
            requirement = Blinds.requirement(ante, blind, ruleset, stake.scaling());
        }
        // Plasma Deck (ante_scaling=2): blinds are 2x larger. Our tables are even, so doubling the
        // rounded requirement equals doubling inside get_blind_amount.
        requirement *= deckType.blindSizeMult();
        applyResourceMods(); // fold every resource Modify (deck/boss/joker/voucher/skip-off/pizza/Oops!) at once
        applyJokerDestroyers(); // Ceremonial Dagger / Madness eat a joker at blind select
        rollRoundTargets();  // The Idol / Ancient targets, re-rolled each blind
        int deckBefore = composition.size();
        boolean bossNow = blind == BlindType.BOSS;
        GameEvents.raise(Trigger.BLIND_SELECTED, state, rng, ctx -> ctx.bossBlind = bossNow); // Cartomancer, Marble, Madness
        // Cards added to the deck (Marble/Certificate) raise CARD_ADDED so Hologram counts them.
        for (int i = composition.size() - deckBefore; i > 0; i--) {
            GameEvents.raise(Trigger.CARD_ADDED, state, rng, null);
        }
        // Offer a skip tag for skippable blinds (Small/Big, non-PvP), from the game-long tag queue
        // resolved against this ante's offerable pool (Ante-1 lockouts + MP bans).
        boolean skippable = blind != BlindType.BOSS && !pvpBoss;
        if (skippable) {
            List<String> pool = TagCatalog.offerable(ante, ruleset.capabilities().restrictedPools());
            state.offeredTag = pool.isEmpty() ? null
                    : pool.get((int) (roll(RngSources.TAGS) * pool.size()) % pool.size());
        } else {
            state.offeredTag = null;
        }
        applyBlindTags(); // NEXT_BLIND tags (Juggle: +3 hand size) before the hand is dealt
        dealNewDeck(); // full deck reshuffled fresh each blind
        state.hand.clear();
        state.deck.drawTo(state.hand, state.handSize);
        markFaceDown(state.hand, DrawContext.INITIAL); // The House: the opening hand is dealt face down
        // The Serpent: subsequent refills draw a fixed count instead of filling to hand size.
        // The Serpent: draw a fixed count instead of filling to hand size — a Modify on DRAW_COUNT, folded
        // alongside every other resource (a disabled boss contributes no mods, so it falls back to -1).
        state.drawCountOverride = (int) com.balatro.engine.eval.ModifyFolder.fold(-1, Hand.DRAW_COUNT, resourceMods());
        for (Joker j : state.jokers()) state.jokerState(j).put("bossDisabled", false); // Crimson Heart re-arms each blind
        jokersHidden = false; // reset; Amber Acorn's BLIND_SELECTED rule re-hides + shuffles the Jokers
        raiseBossRules(Trigger.BLIND_SELECTED, null);
        ensureForcedSelection(); // Cerulean Bell: lock one opening-hand card as force-selected
        refreshDebuffs();
        GameEvents.raise(Trigger.FIRST_HAND_DRAWN, state, rng, null); // Certificate: add a sealed card on first draw
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

    /** Director's Cut / Retcon: reroll the Boss Blind at the select screen for $10, up to the owned
     *  per-ante limit (folded from the BOSS_REROLLS_PER_ANTE voucher data). Re-picks the boss and
     *  re-applies its effects to the dealt hand. Null on success, else a reason. */
    public String rerollBoss() {
        if (phase != Phase.BLIND_SELECT || blind != BlindType.BOSS || boss == null) return "no boss to reroll";
        if (state.bossRerollsThisAnte >= voucherFold(Value.Var.BOSS_REROLLS_PER_ANTE, 0)) return "no boss rerolls left";
        if (!canAfford(BOSS_REROLL_COST)) return "not enough money";
        spend(BOSS_REROLL_COST);
        state.bossRerollsThisAnte++;
        boss = BossCatalog.pick(ante, rng, state.bossRerollsThisAnte);
        boolean disabled = bossDisabled();
        state.bossHalveBase = !disabled && boss.halveBase(); // The Flint
        requirement = Math.round(Blinds.getBlindAmount(ante, ruleset, stake.scaling())
                * boss.reqMult() * ruleset.anteScaling()) * deckType.blindSizeMult();
        applyResourceMods();                            // recompute hands/discards/size for the new boss
        markFaceDown(state.hand, DrawContext.INITIAL);  // re-mark face-down for the new boss
        refreshDebuffs();                               // re-mark card debuffs for the new boss
        return null;
    }

    private static final int BOSS_REROLL_COST = 10;

    /** Jokers that consume another joker at blind select: Ceremonial Dagger (right neighbour ->
     *  +Mult from 2x its sell value) and Madness (a random other joker -> +x0.5 Mult, non-boss only). */
    private void applyJokerDestroyers() {
        List<Joker> js = state.jokers();
        // Ceremonial Dagger (RIGHT_NEIGHBOR): any blind; eats its right neighbour, gains 2x its sell value as Mult.
        for (int i = 0; i < js.size(); i++) {
            Selector.OtherJoker d = jokerDestroyer(js.get(i));
            if (d == null || !"RIGHT_NEIGHBOR".equals(d.scope()) || i + 1 >= js.size()) continue;
            if (state.jokerFlag(js.get(i + 1), "eternal")) continue; // eternal can't be eaten
            Joker victim = js.remove(i + 1);
            if (d.gainMult()) state.addJokerInt(js.get(i), "mult", 2 * Math.max(1, victim.info().cost() / 2));
        }
        if (blind == BlindType.BOSS) return; // random joker-eaters (Madness) don't trigger on boss blinds
        // Madness (RANDOM_OTHER): Small/Big only; the ×0.5 Mult rides a state-write rule, this is just "eat a joker".
        for (int i = 0; i < js.size(); i++) {
            Selector.OtherJoker d = jokerDestroyer(js.get(i));
            if (d == null || !"RANDOM_OTHER".equals(d.scope())) continue;
            List<Joker> others = new ArrayList<>();
            for (int k = 0; k < js.size(); k++) { // eternal jokers can't be destroyed -> excluded as targets
                if (k != i && !state.jokerFlag(js.get(k), "eternal")) others.add(js.get(k));
            }
            if (others.isEmpty()) continue;
            // Identity-based pick: which joker is destroyed depends on the set held, not its order.
            Joker victim = state.queues.pick(others, RngSources.MADNESS_DESTROY, rngCtx(), Joker::key, JOKER_QUALITY);
            int vidx = js.indexOf(victim);
            js.remove(vidx);
            if (vidx < i) i--; // a joker before us was removed; stay aligned
        }
    }

    /** A joker's blind-select "eat a joker" intent as DATA — the {@link Selector.OtherJoker} of its
     *  {@code BLIND_SELECTED} {@code Destroy} rule, or null. The careful destruction stays engine machinery above. */
    private static Selector.OtherJoker jokerDestroyer(Joker j) {
        if (!(j instanceof DataJoker dj)) return null;
        for (Rule r : dj.def().rules()) {
            if (r.when() != Trigger.BLIND_SELECTED) continue;
            for (Effect e : r.effects()) {
                if (e instanceof Effect.Destroy d && d.selector() instanceof Selector.OtherJoker oj) return oj;
            }
        }
        return null;
    }

    /** Whether the active boss has an ability (a debuff or a hand/discard/size override). */
    private boolean bossHasAbility() {
        return boss != null && (boss.debuff() != null || boss.halveBase()
                || !boss.mods().isEmpty() // hand/discard/size resource modifiers
                || !boss.rules().isEmpty() // rule-driven effects (per-hand, blind-start, pre-hand, on-sell)
                || boss.requires() != null || boss.faceDown() != null
                || boss.forcesCardSelection());
    }

    /** The boss blind's ability is off — Chicot (always) or Luchador (sold this blind). */
    private boolean bossDisabled() {
        // Chicot is a dynamic boolean policy now (mods(max(BOSS_ABILITY_DISABLED, 1))), folded from the
        // owned jokers — same pattern as Showman/Astronomer — so it re-arms the boss if Chicot is sold.
        return luchadorDisabledBoss
                || com.balatro.engine.joker.def.DataJoker.policyEnabled(
                        state.jokers(), Value.Var.BOSS_ABILITY_DISABLED);
    }

    private int survivingJoker = -1; // set by a SurviveBlind effect during raiseJokerRules(BLIND_LOST)

    /** A failed blind, as a hookable Blind-lifecycle event: raise {@code BLIND_LOST} over the joker rules.
     *  A {@link Effect.SurviveBlind} (Mr Bones, gated on {@code BLIND_PROGRESS} >= 0.25) marks its joker as
     *  the saver; we consume it and the run continues. No more bespoke capability — the blind is a noun. */
    private boolean survivesLostBlind() {
        if (requirement <= 0) return false;
        state.blindProgress = (double) state.roundScore / requirement;
        survivingJoker = -1;
        raiseJokerRules(Trigger.BLIND_LOST);
        if (survivingJoker >= 0 && survivingJoker < state.jokers().size()) {
            state.jokers().remove(survivingJoker); // consume the saver, after iteration (no concurrent mod)
            return true;
        }
        return false;
    }

    /**
     * End-of-round bonus money (everything except the flat blind reward). The economy is RESOLVED as a
     * pure function of the currently-owned sources ({@link EconomyConfig#resolve}) — nothing is mutated
     * or special-cased here, and adding a new economy effect means extending {@code resolve}, not editing
     * this. Faithful to state_events.lua:1166–1202: per-hand + per-discard money, then interest.
     */
    private int endOfRoundMoney() {
        EconomyConfig econ = EconomyConfig.resolve(deckType, state.vouchers, state.jokers());
        return econ.perCardMoney(state.handsLeft, state.discardsLeft) + econ.interest(state.money);
    }

    private boolean hasJoker(String key) {
        return state.jokers().stream().anyMatch(j -> j.key().equals(key));
    }

    /** Generate a shop that skips jokers you already own (unless Showman allows duplicates). */
    /** Jokers banned in Standard Ranked multiplayer (boss-blind interactions) — see {@link JokerLibrary#MP_BANNED}. */
    private static final java.util.Set<String> MP_DISABLED = JokerLibrary.MP_BANNED;

    private Shop generateShop() {
        java.util.Set<String> owned = new java.util.HashSet<>();
        for (Joker j : state.jokers()) owned.add(j.key());
        owned.addAll(state.vouchers); // skip vouchers you already own too (distinct v_ namespace)
        ShopEconomy econ = shopEconomy(); // Overstock slots + Hone/Glow Up edition odds, derived from vouchers
        rollAnteVoucherIfNeeded(owned);
        Shop s = Shop.generate(state.queues, econ.slots(), jokerPoolForShop(), owned,
                shopConfig().allowDuplicates(), econ.editionMultiplier(), econ.polyMultiplier(),
                anteVoucher, playedHands(), econ.spectralWeight(), econ.tarotWeight(), econ.planetWeight(),
                econ.playingCardWeight(), econ.playingCardsEnhanced());
        // Re-add any Voucher-Tag vouchers so they persist across rerolls within this shop visit.
        for (String tv : tagVouchers) {
            if (!state.vouchers.contains(tv)) s.addVoucher(tv);
        }
        applyShopStickers(s);
        return s;
    }

    /** Roll stake stickers (eternal/perishable/rental, 30% each) onto this shop's joker slots. */
    private void applyShopStickers(Shop s) {
        if (!(stake.eternalsInShop() || stake.perishablesInShop() || stake.rentalsInShop())) return;
        double chance = com.balatro.content.Sticker.STICKER_CHANCE;
        var q = state.queues.queue(RngSources.JOKER_STICKER, r -> new boolean[]{
                r.nextDouble() < chance,   // eternal roll
                r.nextDouble() < chance,   // perishable roll (gated: not if eternal)
                r.nextDouble() < chance}); // rental roll
        var items = s.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).kind() != Shop.Kind.JOKER) continue;
            boolean[] roll = q.next();
            boolean eternal = stake.eternalsInShop() && roll[0];
            boolean perishable = !eternal && stake.perishablesInShop() && roll[1];
            boolean rental = stake.rentalsInShop() && roll[2];
            if (eternal || perishable || rental) {
                items.set(i, items.get(i).withStickers(eternal, perishable, rental));
            }
        }
    }

    /** Stamp a bought/created joker with its stake stickers (server-only state read by sell/score/economy). */
    private void applyStickersToJoker(Joker j, boolean eternal, boolean perishable, boolean rental) {
        var st = state.jokerState(j);
        if (eternal) st.put(com.balatro.content.Sticker.ETERNAL.flag(), true);
        if (perishable) {
            st.put(com.balatro.content.Sticker.PERISHABLE.flag(), true);
            st.put("perishTally", com.balatro.content.Sticker.PERISHABLE_ROUNDS);
        }
        if (rental) st.put(com.balatro.content.Sticker.RENTAL.flag(), true);
    }

    /** End-of-round sticker upkeep, driven by the {@link com.balatro.content.Sticker} primitive: the
     *  Perishable countdown (a per-joker Counter that debuffs at 0) and each sticker's data end-of-round
     *  effects (Rental = AdjustMoney −$3, applied through the same interpreter a joker's money effect uses). */
    private void applyJokerStickerEffects() {
        var ctx = runLoopContext();
        for (Joker j : state.jokers()) {
            // Perishable: a per-joker countdown to a debuff.
            if (state.jokerFlag(j, com.balatro.content.Sticker.PERISHABLE.flag())
                    && !state.jokerFlag(j, "debuffed")) {
                int tally = state.jokerInt(j, "perishTally", com.balatro.content.Sticker.PERISHABLE_ROUNDS) - 1;
                state.jokerState(j).put("perishTally", tally);
                if (tally <= 0) state.jokerState(j).put("debuffed", true); // perished — the scorer now skips it
            }
            // Each owned sticker's end-of-round behaviour as data (Rental = AdjustMoney −$3).
            ctx.jokers = state.jokers();
            ctx.selfIndex = state.jokers().indexOf(j);
            for (com.balatro.content.Sticker s : com.balatro.content.Sticker.values()) {
                if (state.jokerFlag(j, s.flag())) {
                    for (Effect e : s.endOfRound()) applyRunLoopEffect(e, ctx);
                }
            }
        }
    }

    /** Hand types played at least once this run — gates the softlocked secret-hand planets. */
    private java.util.Set<HandType> playedHands() {
        java.util.Set<HandType> s = java.util.EnumSet.noneOf(HandType.class);
        for (var e : state.handTypePlays.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) s.add(e.getKey());
        }
        return s;
    }

    /** The joker pool the shop/packs draw from — in multiplayer the boss-interacting jokers
     *  are excluded from the pool entirely (not merely skipped). */
    private List<String> jokerPoolForShop() {
        if (!ruleset.capabilities().restrictedPools()) return ruleset.jokerPool();
        List<String> base = ruleset.jokerPool().isEmpty() ? JokerLibrary.builtinKeys() : ruleset.jokerPool();
        // MP pool = configured base minus the overlay's bans, plus its MP-only adds (the Nemesis jokers, which
        // are not in the vanilla base). Source: the bmp-0.4.2-ranked overlay.
        List<String> out = new java.util.ArrayList<>(base.stream().filter(k -> !MP_DISABLED.contains(k)).toList());
        for (var a : com.balatro.engine.joker.def.Rulesets.overlay(JokerLibrary.MP_OVERLAY).add()) {
            if (!out.contains(a.def().key())) out.add(a.def().key());
        }
        return out;
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
        anteVoucher = nextShowableVoucher();
    }

    /**
     * Draw the next showable voucher from the game-long voucher queue, advancing it. Resolution per
     * drawn position: Tier 1 until bought, then Tier 2, then skip the position once both tiers are
     * owned; a position resolving to the same voucher as the last shown is skipped (dup-skip). Shared
     * by the per-ante voucher and the Voucher Tag — the BMP {@code get_next_voucher_key(_from_tag)}
     * model, where a tag draws the next voucher from the very same queue. Returns null if exhausted.
     */
    private String nextShowableVoucher() {
        boolean mp = ruleset.capabilities().restrictedPools();
        java.util.List<String> bases = VoucherCatalog.baseKeys(mp);
        var q = state.queues.queue(RngSources.VOUCHERS, r -> bases.get(r.nextInt(bases.size())));
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
            return show;
        }
        return null;
    }

    /** The lowest money a purchase may leave you at — derived economy (Credit Card allows -$20 of debt). */
    private int minMoney() {
        return EconomyConfig.resolve(deckType, state.vouchers, state.jokers()).minMoney();
    }

    /** The uniform action-effect trace (review plan #1): every {@link com.balatro.engine.exec.Command} the
     *  engine applies logs a step here, so consumable/boss mutations are inspectable like scoring's replay. */
    public final java.util.List<com.balatro.engine.exec.TraceEntry> actionTrace = new java.util.ArrayList<>();

    /** Apply one resolved {@link com.balatro.engine.exec.Command} — the single mutation+trace path that the
     *  action-effect families migrate onto (replacing the direct mutation in the interpreter switches). */
    private void apply(com.balatro.engine.exec.Command cmd) {
        switch (cmd) {
            case com.balatro.engine.exec.Command.Money m -> {
                int floor = minMoney();
                int v = (int) Math.round(m.amount());
                state.money = switch (m.op()) {
                    case ADD -> Math.max(floor, state.money + v);          // gains / The Tooth uses SUBTRACT
                    case SUBTRACT -> Math.max(floor, state.money - v);
                    case MULTIPLY -> Math.max(0, (int) Math.round(state.money * m.amount()));
                    case DIVIDE -> m.amount() == 0 ? state.money : Math.max(0, (int) Math.round(state.money / m.amount()));
                    case SET -> Math.max(0, v);                            // The Ox: set to $0
                    case POWER -> throw new IllegalStateException("POWER is not a money operation");
                };
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("money", m.op() + " " + v + " → $" + state.money));
            }
            case com.balatro.engine.exec.Command.HandSize h -> {
                state.handSize += h.delta();
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("handSize",
                        (h.delta() >= 0 ? "+" : "") + h.delta() + " → " + state.handSize));
            }
            case com.balatro.engine.exec.Command.EditionJoker ej -> {
                state.setJokerEdition(ej.target(), ej.edition());
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("edition",
                        ej.target().name() + " → " + ej.edition()));
            }
            case com.balatro.engine.exec.Command.DestroyCards dc -> {
                dc.cards().forEach(t -> t.destroyed = true);
                composition.removeIf(x -> x.destroyed);
                state.hand.removeIf(x -> x.destroyed);
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("destroy", "destroyed " + dc.cards().size() + " card(s)"));
            }
            case com.balatro.engine.exec.Command.MutateCards mc -> {
                mc.cards().forEach(t -> mc.mod().applyTo(t));
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("mutate", "mutated " + mc.cards().size() + " card(s)"));
            }
            case com.balatro.engine.exec.Command.Create cr -> {
                com.balatro.engine.consumable.Creation.apply(state, cr.spec(), state.queues);
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("create", cr.spec().count() + "× " + cr.spec().kind()));
            }
            case com.balatro.engine.exec.Command.AddCardsToDeck ac -> {
                for (Card made : ac.cards()) { composition.add(made); state.hand.add(made); }
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("addCards", "+" + ac.cards().size() + " card(s)"));
            }
            case com.balatro.engine.exec.Command.LevelHand lvl -> {
                for (int i = 0; i < Math.abs(lvl.levels()); i++) {
                    if (lvl.levels() < 0) state.levelDownHand(lvl.hand()); else state.levelUpHand(lvl.hand());
                }
                actionTrace.add(new com.balatro.engine.exec.TraceEntry("levelHand",
                        lvl.hand() + " " + (lvl.levels() >= 0 ? "+" : "") + lvl.levels()));
            }
            default -> throw new IllegalStateException("command not yet applied: " + cmd);
        }
    }

    /** The effective shop rules, derived from owned jokers (Showman/Astronomer/Chaos). */
    private ShopConfig shopConfig() {
        return ShopConfig.resolve(state.jokers());
    }

    /** The effective shop economy, derived from owned vouchers (Overstock/Clearance/Reroll/Hone). */
    private ShopEconomy shopEconomy() {
        return ShopEconomy.resolve(state.vouchers, deckType.mods()); // deck mods carry Ghost's Spectral rate
    }

    /** True if {@code cost} is affordable given the current debt floor. */
    private boolean canAfford(int cost) {
        return state.money - cost >= minMoney();
    }

    /** Re-roll the per-round dynamic targets (The Idol's card, Ancient's suit). */
    private void rollRoundTargets() {
        // Multiplayer rolls the Idol target a different way (deck position) — skip its generic roll below.
        boolean mpIdol = ruleset.capabilities().idolDeckPosition();
        for (com.balatro.engine.state.RoundTargets.Spec t : com.balatro.engine.state.RoundTargets.ALL) {
            if (mpIdol && (t.id().equals("idolRankId") || t.id().equals("idolSuit"))) continue;
            Object v = switch (t.domain()) {
                case SUIT -> pick(com.balatro.engine.card.Suit.values(), RngSources.TARGET.sub(t.rngKey()));
                case RANK -> 2 + (int) (roll(RngSources.TARGET.sub(t.rngKey())) * 13) % 13;
                case HAND_TYPE -> pick(com.balatro.engine.hand.HandType.values(), RngSources.TARGET.sub(t.rngKey()));
            };
            state.roundTargets.put(t.id(), v);
        }
        if (mpIdol) rollMpIdol(); // MP: deck-position roll (shared number, each player's own deck)
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
        int rollPos = 1 + (int) (roll(RngSources.TARGET.sub("idol:pos")) * 1000); // shared 1..1000
        Card target = sorted.get((rollPos - 1) % sorted.size());
        state.roundTargets.put("idolSuit", target.suit);
        state.roundTargets.put("idolRankId", target.rank.id);
    }

    /** The RNG resolution context for this run right now (ante, blind, PvP state, order flag). */
    public RngContext rngCtx() {
        return new RngContext(ante, blind.name(), state.inPvpBlind, state.order);
    }

    /** A roll in [0,1) from a declared source, resolved against the current context. */
    private double roll(RngSource src) {
        return state.queues.roll(src, rngCtx());
    }

    /** Uniformly pick one element of {@code arr} using a roll from {@code src}. */
    private <T> T pick(T[] arr, RngSource src) {
        return arr[(int) (roll(src) * arr.length) % arr.length];
    }

    /** Per-blind base discards: the ruleset default reduced by the stake (Blue: -1), floored at 0. */
    private int baseDiscards() {
        return ruleset.discards(); // the stake's −1 (Blue+) is now a Modify in resourceMods(), folded below
    }

    /**
     * Every per-blind resource {@link Modify} from every source — deck, boss, jokers (flat deltas +
     * Turtle Bean's decaying bonus), vouchers, Skip-Off and Pizza — in one flat list. {@code fold}
     * then resolves each game variable (Hand.PLAYS / Hand.DISCARDS / Hand.SIZE) from it. Every card
     * type contributes through the same {@code mods()} interface; Run no longer special-cases any of
     * them. Has one side effect: it ticks down the Pizza counter (called once per blind start).
     */
    private List<Modify> resourceMods() {
        List<Modify> all = new ArrayList<>();
        all.addAll(deckType.mods());                                       // deck: Blue/Red/Painted/...
        all.addAll(stake.mods());                                          // stake: Blue+ −1 discard (a Modify now)
        if (boss != null && !bossDisabled()) all.addAll(boss.mods());      // boss: Needle/Water/Manacle (SET/add)
        for (Joker j : state.jokers()) {                                   // jokers: flat + dynamic var modifiers
            if (j instanceof DataJoker dj) all.addAll(dj.def().mods());     // Juggler/Chaos/Skip-Off/Turtle Bean
        }
        for (String v : state.vouchers) {                                 // vouchers: Grabber/Wasteful/Paint Brush
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) all.addAll(def.mods());
        }
        // Skip-Off's +hands/+discards is a dynamic Modify carried by the joker's own mods() now (a Diff Value).
        if (state.pizzaBlindsLeft > 0) {                                  // Pizza (PvP): temporary +discards
            all.add(Modify.add(Hand.DISCARDS, state.pizzaDiscardBonus));
            state.pizzaBlindsLeft--;
        }
        return all;
    }

    /** Fold every resource Modify onto each game variable at blind start (Burglar's "no discards" last). */
    private void applyResourceMods() {
        List<Modify> mods = resourceMods();
        var ctx = runLoopContext(); // some mods are dynamic Values now (Skip-Off reads the PvP state)
        state.handsLeft = Math.max(1, (int) com.balatro.engine.eval.ModifyFolder.fold(ruleset.hands(), Hand.PLAYS, mods, ctx));
        // Burglar's "no discards" is a Modify.min(Hand.DISCARDS, 0) — the fold's MIN beats any discard-adder.
        int discards = (int) com.balatro.engine.eval.ModifyFolder.fold(baseDiscards(), Hand.DISCARDS, mods, ctx);
        state.discardsLeft = Math.max(0, discards);
        state.handSize = Math.max(1, (int) com.balatro.engine.eval.ModifyFolder.fold(ruleset.handSize(), Hand.SIZE, mods, ctx));
        // Oops! All 6s scales every "1 in X" threshold: each copy is a Modify.multiply(PROBABILITY_MULTIPLIER, 2),
        // so the fold from base 1 gives 2^copies — replacing the old bespoke 1<<oopsCount capability count.
        state.probabilityNumerator = Math.max(1, (int) com.balatro.engine.eval.ModifyFolder.fold(1, Value.Var.PROBABILITY_MULTIPLIER, mods, ctx));
    }

    /** Mark hand cards debuffed per the active boss (recomputed each deal/draw). */
    private void refreshDebuffs() {
        boolean disabled = bossDisabled(); // Chicot turns off the boss's debuffs
        com.balatro.grammar.Condition debuff = (boss != null) ? boss.debuff() : null;
        for (Card c : state.hand) {
            c.debuffed = !disabled && debuff != null && testCardDebuff(debuff, c);
        }
        // Keep Matador's trigger condition current (recomputed when the boss is disabled mid-blind too).
        state.bossHasActiveAbility = boss != null && !disabled && bossHasAbility();
    }

    /** Evaluate a boss debuff condition for one card (reuses the shared {@link com.balatro.grammar.Condition}
     *  vocabulary — "Clubs don't score" is {@code card().suit(CLUBS)}). */
    private boolean testCardDebuff(com.balatro.grammar.Condition cond, Card c) {
        com.balatro.engine.joker.EvaluationContext ctx = new com.balatro.engine.joker.EvaluationContext();
        ctx.scoredCard = c;
        ctx.run = state;
        return com.balatro.engine.eval.ConditionEvaluator.test(cond, ctx);
    }

    /** Which deal put a card into hand — selects the boss face-down rules that fire (The House/Fish). */
    private enum DrawContext { INITIAL, AFTER_PLAY, AFTER_DISCARD }

    /** Apply the boss's draw-time face-down rule (The House/Wheel/Mark/Fish) to freshly-dealt cards.
     *  faceDown is (re)assigned explicitly per candidate — deck cards recycle across blinds, so a card
     *  that was hidden last blind must come back face up unless the rule says otherwise. */
    private void markFaceDown(java.util.List<Card> candidates, DrawContext context) {
        BossBlind.FaceDownRule rule = (boss != null && !bossDisabled()) ? boss.faceDown() : null;
        for (Card c : candidates) {
            if (rule == null || !faceDownFires(rule.when(), context)) {
                c.faceDown = false;
                continue;
            }
            boolean cardMatches = rule.card() == null || testCardDebuff(rule.card(), c);
            c.faceDown = cardMatches
                    && (rule.chance() <= 0 || roll(RngSources.BOSS_FACE_DOWN) < rule.chance());
        }
    }

    private static boolean faceDownFires(BossBlind.When when, DrawContext context) {
        return switch (when) {
            case ALWAYS -> true;
            case INITIAL_DEAL -> context == DrawContext.INITIAL;
            case AFTER_PLAY -> context == DrawContext.AFTER_PLAY;
        };
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
        // Boss legality: a constraint the active boss places on this intent (The Psychic/Mouth/Eye reject an
        // illegal play; Cerulean Bell forces a card in/out). One checkpoint — the boss is the read-side source.
        String illegal = bossLegality(intent);
        if (illegal != null) return IntentResult.rejected(illegal);
        // Crimson Heart: disable one random Joker for this hand, before it scores.
        if (intent instanceof Intent.PlayHand) { raiseBossRules(Trigger.PRE_HAND, null); resolveObservatory(); }
        // Snapshot the hand so we can tell which cards the upcoming refill freshly drew (boss face-down).
        java.util.Set<java.util.UUID> handBefore = new java.util.HashSet<>();
        for (Card c : state.hand) handBefore.add(c.uid);
        // Capture the played cards before scoring removes them — the boss's ON_HAND_PLAYED rules read them
        // (The Tooth counts cards played). Resolved here while the indices still point into the live hand.
        List<Card> playedCards = new ArrayList<>();
        if (intent instanceof Intent.PlayHand phPlayed) {
            for (int i : phPlayed.cardIndices()) {
                if (i >= 0 && i < state.hand.size()) playedCards.add(state.hand.get(i));
            }
        }
        // Purple Seal: count the sealed cards in this discard before they leave the hand.
        int purpleDiscards = 0;
        if (intent instanceof Intent.Discard dscP) {
            for (int i : dscP.cardIndices()) {
                if (i >= 0 && i < state.hand.size() && state.hand.get(i).seal == Seal.PURPLE) purpleDiscards++;
            }
        }
        IntentResult result = intents.handle(state, rng, intent);
        if (!result.ok()) return result;
        if (purpleDiscards > 0) applyPurpleSeals(purpleDiscards); // each makes a Tarot
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
        // The Fish (after a play) / Wheel / Mark: the refill's brand-new cards may arrive face down.
        if (intent instanceof Intent.PlayHand || intent instanceof Intent.Discard) {
            java.util.List<Card> drawn = new ArrayList<>();
            for (Card c : state.hand) if (!handBefore.contains(c.uid)) drawn.add(c);
            DrawContext ctx = (intent instanceof Intent.PlayHand) ? DrawContext.AFTER_PLAY : DrawContext.AFTER_DISCARD;
            markFaceDown(drawn, ctx);
        }
        ensureForcedSelection(); // Cerulean Bell: re-lock a card after the forced one was played

        if (intent instanceof Intent.PlayHand ph) {
            state.handsPlayedThisRound++; // after scoring, so DNA's "first hand" check saw 0
            state.handsPlayedTotal++;
            applyBossOnHandPlayed(playedCards, result.score()); // Tooth / Ox / Arm / Hook per-hand boss effects
            // (Matador's $8 vs an active boss ability is now a data Rule: DOLLARS gated on bossAbilityActive.)
            if (pvpActive) {
                // PvP blind: play all hands, then await the head-to-head comparison.
                if (state.handsLeft <= 0) phase = Phase.PVP_PENDING;
            } else if (state.roundScore >= requirement) {
                winBlind();
            } else if (state.handsLeft <= 0) {
                if (survivesLostBlind()) {
                    winBlind(); // Mr Bones prevents the death (and is consumed)
                } else {
                    // Attrition: dying to a blind costs a life (match handles it), not the run.
                    phase = (pvpFromAnte > 0) ? Phase.BLIND_FAILED : Phase.RUN_LOST;
                }
            }
        }
        return result;
    }

    /**
     * Raise a lifecycle {@link Trigger} over the boss's rules, applying each matching effect through the
     * run-loop interpreter. The boss is "a joker the blind owns" — its abilities are data {@link Rule}s fired
     * at BLIND_SELECTED (Amber Acorn) / PRE_HAND (Crimson Heart) / ON_HAND_PLAYED (Tooth/Ox/Arm/Hook) /
     * SELL_CARD (Verdant Leaf). One condition language, one effect vocabulary, one dispatch.
     */
    private void raiseBossRules(Trigger trigger, java.util.function.Consumer<com.balatro.engine.joker.EvaluationContext> setup) {
        if (boss == null || bossDisabled() || boss.rules().isEmpty()) return;
        com.balatro.engine.joker.EvaluationContext ctx = runLoopContext();
        ctx.phase = trigger;
        if (setup != null) setup.accept(ctx);
        for (Rule r : boss.rules()) {
            if (r.when() != trigger) continue;
            if (r.condition() != null && !com.balatro.engine.eval.ConditionEvaluator.test(r.condition(), ctx)) continue;
            for (Effect e : r.effects()) applyRunLoopEffect(e, ctx);
        }
    }

    /** Boss per-hand effects (Tooth / Ox / Arm / Hook), fired after a hand is played + scored. */
    private void applyBossOnHandPlayed(List<Card> playedCards, ScoreResult score) {
        raiseBossRules(Trigger.ON_HAND_PLAYED, ctx -> {
            ctx.playedCards = playedCards;
            ctx.handType = (score != null) ? score.handType() : null;
        });
    }

    /** The run-loop effect interpreter (the action-side twin of {@link #applyConsumableEffect}): resolve a
     *  {@link Value} against the run and mutate authoritative state. Shared by bosses (per-hand/blind/sell
     *  rules) and tags (Speed/Handy/Garbage money gains) — any effect fired outside the scoring pipeline. */
    private void applyRunLoopEffect(Effect e, com.balatro.engine.joker.EvaluationContext ctx) {
        switch (e) {
            case Effect.AdjustMoney am ->  // interpret → resolved Money command → one apply+trace path
                apply(new com.balatro.engine.exec.Command.Money(am.op(),
                        com.balatro.engine.eval.ValueResolver.resolve(am.amount(), ctx)));
            case Effect.DiscardRandomHeld d -> hookDiscardAndRefill(d.count()); // The Hook
            case Effect.FlipAndShuffleJokers ignored -> { // Amber Acorn (blind start)
                jokersHidden = true;
                List<Joker> js = state.jokers();
                for (int i = js.size() - 1; i > 0; i--) { // deterministic Fisher–Yates from the seeded stream
                    int k = (int) (roll(RngSources.BOSS_ACORN) * (i + 1));
                    if (k > i) k = i;
                    java.util.Collections.swap(js, i, k);
                }
            }
            case Effect.DisableRandomJoker ignored -> { // Crimson Heart (pre-hand)
                List<Joker> js = state.jokers();
                for (Joker j : js) state.jokerState(j).put("bossDisabled", false); // re-arm the rest
                if (!js.isEmpty()) {
                    int idx = (int) (roll(RngSources.BOSS_CRIMSON) * js.size());
                    if (idx >= js.size()) idx = js.size() - 1;
                    state.jokerState(js.get(idx)).put("bossDisabled", true);
                }
            }
            case Effect.DisableBoss ignored -> { // Verdant Leaf (boss) / Luchador (sold during a boss)
                if (boss != null) { luchadorDisabledBoss = true; refreshDebuffs(); }
            }
            case Effect.SurviveBlind ignored -> { // Mr Bones: this joker (ctx.selfIndex) saves the lost blind
                if (survivingJoker < 0) survivingJoker = ctx.selfIndex; // first saver wins; consumed by caller
            }
            case Effect.AddPack ap -> // pack tags (Charm/Meteor/Buffoon/Standard/Ethereal)
                shopPacks.add(new PackCatalog.Pack(PackCatalog.Kind.valueOf(ap.kind()), PackCatalog.Size.valueOf(ap.size())));
            case Effect.Create cr -> { // run-loop create: destination decides where it lands
                CreateSpec s = cr.spec();
                if (s.destination() == CreateSpec.Destination.SHOP) { // free-joker tags into the next shop
                    if (s.edition() == Edition.NONE) addFreeJoker(s.rarity()); // Rare/Uncommon by rarity
                    else addFreeEditionedJoker(s.edition());                   // Foil/Holo/Poly/Negative
                } else { // PLAYER: jokers/consumables/cards straight into the run's slots (Top-Up tag)
                    apply(new com.balatro.engine.exec.Command.Create(s));
                }
            }
            case Effect.LevelHands lh -> { // Orbital tag (MOST_PLAYED) / The Arm (PLAYED, -1 = delevel)
                if (lh.scope() == Effect.LevelHands.Scope.PLAYED) {
                    if (ctx.handType != null) {
                        int n = (int) Math.round(com.balatro.engine.eval.ValueResolver.resolve(lh.levels(), ctx));
                        apply(new com.balatro.engine.exec.Command.LevelHand(ctx.handType, n));
                    }
                } else {
                    applyLevelHands(lh);
                }
            }
            case Effect.AddShopVoucher ignored -> addTagVoucher();          // Voucher tag
            case Effect.ShopFlag sf -> {                                    // Coupon / D6 tags
                if ("COUPON".equals(sf.flag())) couponActive = true;
                else if ("D6".equals(sf.flag())) d6Active = true;
            }
            case Effect.AdjustHandSize ah -> apply(new com.balatro.engine.exec.Command.HandSize(ah.delta()));  // Juggle tag (+3 this round)
            case Effect.Copy cp -> { // run-loop copies (the rounds-owned gate is now a Condition on the rule)
                if (cp.selector() instanceof Selector.RandomConsumable && !state.consumables.isEmpty()) {
                    // Perkeo (shop exit): a slot-cap-ignoring Negative copy of a random held consumable.
                    String dup = state.queues.pick(state.consumables, RngSources.PERKEO_DUP, rngCtx(), s -> s, (a, b) -> 0);
                    state.consumables.add(dup);
                } else if (cp.selector() instanceof Selector.RandomJoker
                        && !state.jokers().isEmpty() && state.jokers().size() < Shop.JOKER_SLOT_LIMIT) {
                    // Invisible Joker (on sell): copy a joker — the rightmost in MP (deterministic), else a
                    // random one by identity (INVISIBLE_DUP).
                    Joker source = ruleset.capabilities().duplicateRightmost()
                            ? state.jokers().get(state.jokers().size() - 1)
                            : state.queues.pick(state.jokers(), RngSources.INVISIBLE_DUP, rngCtx(), Joker::key, JOKER_QUALITY);
                    state.addJoker(JokerLibrary.create(source.key()));
                }
            }
            case Effect.CreateTag ct -> grantTag(ct.tag());                 // Diet Cola (on sell)
            default -> throw new IllegalStateException("not a run-loop effect: " + e);
        }
    }

    /** The Hook: discard {@code count} random held cards, then refill (honouring a Serpent draw override). */
    private void hookDiscardAndRefill(int count) {
        for (int i = 0; i < count && !state.hand.isEmpty(); i++) {
            int idx = (int) (roll(RngSources.BOSS_HOOK) * state.hand.size());
            if (idx >= state.hand.size()) idx = state.hand.size() - 1;
            state.hand.remove(idx);
        }
        if (state.deck != null) {
            if (state.drawCountOverride > 0) state.deck.drawCount(state.hand, count);
            else state.deck.drawTo(state.hand, state.handSize);
        }
    }

    /** Cerulean Bell: keep exactly one held card force-selected. Re-picks when the previously-forced
     *  card has left the hand (it was just played); clears the flag entirely when the boss is inactive. */
    private void ensureForcedSelection() {
        boolean active = boss != null && !bossDisabled() && boss.forcesCardSelection();
        long forced = state.hand.stream().filter(c -> c.forcedSelected).count();
        if (active && forced == 1) return; // already exactly one — keep it stable across hands
        for (Card c : state.hand) c.forcedSelected = false;
        if (active && !state.hand.isEmpty()) {
            int idx = (int) (roll(RngSources.BOSS_BELL) * state.hand.size());
            if (idx >= state.hand.size()) idx = state.hand.size() - 1;
            state.hand.get(idx).forcedSelected = true;
        }
    }

    /** The index in hand of the Cerulean Bell force-selected card, or -1 if none. */
    private int forcedCardIndex() {
        for (int i = 0; i < state.hand.size(); i++) if (state.hand.get(i).forcedSelected) return i;
        return -1;
    }

    /**
     * The active boss's legality constraint on this intent: the rejection reason (the boss's description),
     * or {@code null} if the intent is allowed. The read-side mirror of a scoring rule — the boss is the
     * source, {@code requires()} is a {@link com.balatro.grammar.Condition} over the played hand,
     * and Cerulean Bell forces its selected card into every play / out of every discard.
     */
    private String bossLegality(Intent intent) {
        if (boss == null || bossDisabled()) return null;
        // Hand-legality bosses (The Psychic / Mouth / Eye): the play must satisfy the boss's requires().
        if (intent instanceof Intent.PlayHand ph && boss.requires() != null) {
            java.util.List<Card> playedCards = new ArrayList<>();
            for (int i : ph.cardIndices()) {
                if (i >= 0 && i < state.hand.size()) playedCards.add(state.hand.get(i));
            }
            com.balatro.engine.joker.EvaluationContext ctx = new com.balatro.engine.joker.EvaluationContext();
            ctx.playedCards = playedCards;
            ctx.run = state;
            // Hand-type-aware legality (The Mouth / The Eye) needs the poker hand being played.
            if (!playedCards.isEmpty()) {
                ctx.handType = com.balatro.engine.hand.HandEvaluator.evaluate(playedCards).type();
            }
            if (!com.balatro.engine.eval.ConditionEvaluator.test(boss.requires(), ctx)) return boss.effect();
        }
        // Cerulean Bell: the force-selected card must be in every played hand, and can't be discarded.
        if (boss.forcesCardSelection()) {
            int fi = forcedCardIndex();
            if (fi >= 0) {
                if (intent instanceof Intent.PlayHand ph && !ph.cardIndices().contains(fi)) return boss.effect();
                if (intent instanceof Intent.Discard dsc && dsc.cardIndices().contains(fi)) return boss.effect();
            }
        }
        return null;
    }

    /** Crimson Heart: before a hand scores, switch off one random Joker for that hand (clearing any
     *  prior pick). The scorer skips a Joker flagged {@code bossDisabled}, so it contributes nothing. */
    /** Observatory: resolve the held-Planet mult and which hand types you currently hold a Planet for,
     *  so the scorer can apply x1.5 to a played hand whose Planet you hold. */
    private void resolveObservatory() {
        state.heldPlanetMult = voucherFoldD(Value.Var.HELD_PLANET_MULT, 1.0);
        state.heldPlanetHands.clear();
        if (state.heldPlanetMult <= 1.0) return;
        for (String c : state.consumables) {
            PlanetCatalog.Planet p = PlanetCatalog.get(c);
            if (p != null) state.heldPlanetHands.add(p.hand());
        }
    }

    /** Run-loop hand leveling — the ALL (Black Hole) and MOST_PLAYED (Orbital) scopes of {@link
     *  Effect.LevelHands}; the PLAYED scope is scoring-time (the levelUpHand flag), never reaches here. */
    private void applyLevelHands(Effect.LevelHands lh) {
        int n = Math.max(1, (int) Math.round(com.balatro.engine.eval.ValueResolver.resolve(lh.levels(), runLoopContext())));
        switch (lh.scope()) {
            case ALL -> { for (HandType t : HandType.values()) apply(new com.balatro.engine.exec.Command.LevelHand(t, n)); }
            case MOST_PLAYED -> {
                HandType best = mostPlayedHand();
                if (best == null) best = HandType.HIGH_CARD;
                apply(new com.balatro.engine.exec.Command.LevelHand(best, n));
            }
            case PLAYED -> { /* scoring-time; applied by the scorer via the levelUpHand flag */ }
        }
    }

    /** The poker hand type played most this run (The Ox's trigger), or null if none yet. */
    private com.balatro.engine.hand.HandType mostPlayedHand() {
        return state.handTypePlays.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(null);
    }

    /** The Planet card key for your most-played hand (Telescope), or null if nothing's been played. */
    private String mostPlayedPlanetKey() {
        com.balatro.engine.hand.HandType mp = mostPlayedHand();
        return mp == null ? null : PlanetCatalog.forHand(mp);
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
        state.grantTempDiscards(amount, blinds);
    }

    /** The Match raises this when a PvP blind resolves, with the Nemesis's run as context, so a joker reacts
     *  as data (Pizza: {@code on(PVP_BLIND_ENDED).effect(Destroy(Self), GrantDiscards(self), GrantDiscards(opp))}). */
    public void pvpEnded(RunState opponent) {
        GameEvents.raise(Trigger.PVP_BLIND_ENDED, state, rng, ctx -> ctx.opponentRun = opponent);
    }

    /** The Match raises this when the run enters a PvP blind; {@code first} = before the Nemesis arrived.
     *  Jokers react as data (Speedrun: {@code on(PVP_BLIND_REACHED).when(reachedPvpFirst).create(SPECTRAL)}). */
    public void pvpReached(boolean first) {
        GameEvents.raise(Trigger.PVP_BLIND_REACHED, state, rng, ctx -> ctx.reachedPvpFirst = first);
    }

    /** Attrition: after the match deducts a life for a failed blind, continue to the shop. */
    public void continueAfterFail() {
        if (phase != Phase.BLIND_FAILED) return;
        pvpActive = false;
        shop = generateShop(); // no reward for a failed blind
        shopPacks.clear(); // no packs after a failed blind
        openPack = null;
        enterShopTags();
        phase = Phase.SHOP;
    }

    /** End a Nemesis blind once the match decided it (works for the locked loser AND
     *  the ahead winner who may still have hands): award economy, proceed to the shop. */
    public void endPvp() {
        if (!pvpActive) return;
        pvpActive = false;
        state.cardsSoldSinceLastPvp = 0; // Taxes counts sells between PvP blinds
        state.money += NEMESIS.reward() + endOfRoundMoney();
        applyBossDefeatTags(); // Investment Tag pays out after the PvP (Boss) blind too
        GameEvents.endOfRound(state, rng, true); // Nemesis is a Boss blind (Gift Card's sell-value bump rides this)
        raiseJokerRules(Trigger.SHOP_ENTER);
        refreshShopStock();
        phase = Phase.SHOP;
    }

    /** Reset per-shop tag flags and resolve any held ON_SHOP tags as the shop opens. */
    private void enterShopTags() {
        couponActive = false;
        d6Active = false;
        applyShopTags();
    }

    /** Regenerate shop stock, reset the free reroll, roll booster packs, and resolve shop tags —
     *  the common "open the post-blind shop" sequence shared by {@link #winBlind} and {@link #endPvp}.
     *  (The caller sets {@code phase} itself, preserving each path's exact ordering.) */
    private void refreshShopStock() {
        shop = generateShop();
        freeRerollsUsedThisShop = 0;
        rerollsThisShop = 0; // reroll cost escalation resets each new shop
        rollShopPacks();
        enterShopTags();
    }

    private void winBlind() {
        // Economy: blind reward + end-of-round bonus (per-hand/discard money + interest) + joker payouts.
        int bonus = endOfRoundMoney();
        int reward = (boss != null) ? boss.reward() : blind.reward;
        // Red stake (+): the Small Blind pays no reward (game.lua:2050, blind.lua:84).
        if (boss == null && blind == BlindType.SMALL && stake.smallBlindNoReward()) reward = 0;
        state.money += reward + bonus;
        state.lastBlindReward = reward; // cash-out breakdown for the end-of-round screen
        state.lastInterest = bonus;     // the non-reward bonus (per-hand/discard money + interest)
        if (boss != null) applyBossDefeatTags(); // Investment Tag pays out after a boss
        applyBlueSeals(); // Blue Seal: held cards create the Planet for this round's last hand
        state.roundsPlayedTotal++;
        GameEvents.endOfRound(state, rng, boss != null);
        applyJokerStickerEffects(); // perishable countdown + rental rent (stakes)
        raiseJokerRules(Trigger.SHOP_ENTER);
        phase = Phase.SHOP;
        refreshShopStock();
    }

    /** Blue Seal: each card still held at end of round creates the Planet for the round's last-played
     *  hand, if there's a consumable slot free (Balatro's "Must have room"). */
    private void applyBlueSeals() {
        if (state.lastPlayedHandType == null) return; // nothing played this round (can't clear a blind anyway)
        String planet = PlanetCatalog.forHand(state.lastPlayedHandType);
        if (planet == null) return;
        for (Card c : state.hand) {
            if (c.seal != Seal.BLUE) continue;
            if (state.consumables.size() >= state.consumableSlots) return; // out of room: the rest fizzle
            state.consumables.add(planet);
        }
    }

    /** Purple Seal: each purple-sealed card discarded creates a random Tarot, room permitting. */
    private void applyPurpleSeals(int count) {
        for (int i = 0; i < count; i++) {
            apply(new com.balatro.engine.exec.Command.Create(
                    new com.balatro.grammar.CreateSpec(com.balatro.grammar.CreateSpec.Kind.TAROT)));
        }
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
                applyStickersToJoker(bought, item.eternal(), item.perishable(), item.rental());
            }
            case TAROT, PLANET -> {
                if (state.consumables.size() >= state.consumableSlots) return "no consumable slots";
                // Astronomer makes Planets free.
                int cost = (item.kind() == Shop.Kind.PLANET && shopConfig().planetsFree()) ? 0 : price(item.cost());
                if (!canAfford(cost)) return "not enough money";
                spend(cost);
                state.consumables.add(item.key());
            }
            case PLAYING_CARD -> { // Magic Trick / Illusion: the card joins your deck permanently
                if (!canAfford(price(item.cost()))) return "not enough money";
                spend(price(item.cost()));
                composition.add(item.card().copy());
            }
            default -> { /* SPECTRAL shop items (Ghost Deck) are handled by their own path */ }
        }
        shop.items().remove(index);
        return null;
    }

    /** Buy the first offered voucher (the per-ante one). Returns null on success, else a reason. */
    public String buyVoucher() {
        return buyVoucher(0);
    }

    /** Buy the offered voucher at {@code index} (the shop can hold several when a Voucher Tag added
     *  one). Returns null on success, else a reason. */
    public String buyVoucher(int index) {
        if (phase != Phase.SHOP || shop == null) return "no voucher offered";
        java.util.List<String> offered = shop.vouchers();
        if (index < 0 || index >= offered.size()) return "no voucher offered";
        String key = offered.get(index);
        var v = VoucherCatalog.get(key);
        if (!canAfford(price(v.cost()))) return "not enough money";
        spend(price(v.cost()));
        grantVoucher(v.key());
        shop.removeVoucher(key);
        tagVouchers.remove(key);
        if (key.equals(anteVoucher)) anteVoucher = null; // bought — don't re-offer in this ante's later shops
        return null;
    }

    /** Add a voucher to the owned set; passive effects (slots, interest cap, rates) are read reactively
     *  by folding the owned vouchers' Modifys — nothing imperative to apply here. */
    private void grantVoucher(String key) {
        state.vouchers.add(key);
        recomputeSlots(); // Crystal Ball/Omen Globe (consumable) + Antimatter (joker), folded from ownership
    }

    /** Apply the Clearance Sale (25% off) / Liquidation (50% off) shop discount, rounded down.
     *  Coupon Tag makes this shop's initial cards/packs free until the first reroll. */
    private int price(int cost) {
        if (couponActive) return 0;
        return (int) (cost * shopEconomy().priceMultiplier()); // Clearance/Liquidation, derived
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
        if (state.jokerFlag(state.jokers().get(index), "eternal")) {
            return "eternal jokers cannot be sold";
        }
        Joker sold = state.jokers().remove(index);
        state.cardsSoldSinceLastPvp++; // feeds the opponent's Taxes joker
        int bonus = state.jokerInt(sold, "sellBonus", 0);
        state.money += Math.max(1, sold.info().cost() / 2) + bonus; // sell value (+ Egg/Gift bonus)
        // The sold joker's own SELL_SELF rules: Invisible (duplicate a joker), Luchador (disable boss),
        // Diet Cola (create a tag) — all data now, fired through the run-loop interpreter.
        raiseSelfSellRules(sold);
        // A card was sold: remaining jokers react (Campfire gains x0.25 each), and the boss reacts
        // (Verdant Leaf: DisableBoss) — both through the same SELL_CARD trigger.
        GameEvents.raise(Trigger.SELL_CARD, state, rng, null);
        raiseBossRules(Trigger.SELL_CARD, null);
        return null;
    }

    /** Current reroll cost: a base ($5, or $0 under the D6 Tag), reduced $2 each by Reroll Surplus/Glut
     *  (floor $0), then escalated +$1 for every reroll already done this shop (game.lua's inc_reroll_cost). */
    private int rerollCost() {
        int base = d6Active ? 0 : Shop.REROLL_COST; // D6 Tag: $0 base for the shop (still escalates)
        return Math.max(0, base - shopEconomy().rerollDiscount()) + rerollsThisShop;
    }

    /** Free rerolls granted this shop, folded from everything owned (Chaos = +1 via its FREE_REROLLS Modify). */
    private int freeRerollsThisShop() {
        return (int) com.balatro.engine.eval.ModifyFolder.fold(0, Value.Var.FREE_REROLLS, resourceMods());
    }

    /** Reroll the shop offerings. Returns null on success, else a reason. */
    public String reroll() {
        if (phase != Phase.SHOP || shop == null) return "not in shop";
        // Chaos the Clown (and any future free-reroll source): the first N rerolls each shop are free.
        boolean free = freeRerollsUsedThisShop < freeRerollsThisShop();
        int cost = free ? 0 : rerollCost();
        if (!canAfford(cost)) return "not enough money";
        spend(cost);
        if (free) {
            freeRerollsUsedThisShop++; // a free reroll does NOT escalate the next cost
        } else {
            rerollsThisShop++;         // each PAID reroll raises the next one's cost by $1 (D6's $0 rerolls included)
        }
        couponActive = false;      // the free "initial" cards are gone once you reroll
        shop = generateShop();     // advances the same game-long queue, skipping owned
        return null;
    }

    /** Use a held Planet (no card targets). */
    public String useConsumable(int index) {
        return useConsumable(index, new java.util.UUID[0]);
    }

    /**
     * Use a held consumable. Planets level their hand; Tarots/Spectrals act on the
     * cards the client selected (resolved by unique id) — enhance, destroy, or
     * create. Returns null on success, else a rejection reason.
     */
    public String useConsumable(int index, java.util.UUID[] targetUids) {
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
            for (java.util.UUID uid : targetUids) {
                for (Card card : state.hand) {
                    if (card.uid.equals(uid)) targets.add(card);
                }
            }
            if (targets.size() > c.maxTargets()) return "too many targets";
            // Free this consumable's slot BEFORE applying, so a generative effect's created
            // consumables can occupy the slot it just vacated (Balatro's ordering).
            state.consumables.remove(index);
            applyConsumable(c, targets);
            // The Fool copies the last Tarot/Planet used — track Tarots here (but never The Fool itself).
            if (c.type() == com.balatro.engine.consumable.ConsumableType.TAROT && !key.equals("c_fool")) {
                state.lastTarotPlanetUsed = key;
            }
            GameEvents.useConsumable(state, rng, c.type().label());
            return null;
        }
        return "unknown consumable: " + key;
    }

    private void applyConsumable(Consumable c, List<Card> targets) {
        // The binding map is action-scoped: it lives only for this one consumable's effect list, so a
        // Bind/Bound/Others reference can't leak between actions. It names a joker, never a value.
        java.util.Map<String, Joker> bindings = new java.util.HashMap<>();
        for (Effect e : c.effects()) applyConsumableEffect(c, e, targets, bindings);
    }

    /** The action interpreter: apply one unified {@link Effect} to the run, resolving its {@link Selector}
     *  against the live hand. The joker scoring verbs (Score/MutateState/…) never appear on a consumable. */
    private void applyConsumableEffect(Consumable c, Effect e, List<Card> targets, java.util.Map<String, Joker> bindings) {
        switch (e) {
            case Effect.Bind b -> { // resolve the selector ONCE and remember it for later effects in this list
                if (b.selector() instanceof Selector.RandomJoker && !state.jokers().isEmpty()) {
                    bindings.put(b.name(), state.queues.pick(state.jokers(),
                            RngSources.consumable(c.key()).sub("joker").composition(), rngCtx(), Joker::key, JOKER_QUALITY));
                }
            }
            case Effect.When w -> { // the consumable's IF — apply inner effects only if the gate holds (Wheel)
                if (consumableConditionHolds(c, w.condition())) {
                    for (Effect inner : w.effects()) applyConsumableEffect(c, inner, targets, bindings);
                }
            }
            case Effect.MutateCard mc -> apply(new com.balatro.engine.exec.Command.MutateCards(
                    resolveTargets(c, mc.selector(), targets), mc.mod()));
            case Effect.Create cr -> // pure-create consumable (Emperor/High Priestess/Judgement/Soul)
                apply(new com.balatro.engine.exec.Command.Create(cr.spec()));
            case Effect.Destroy d -> { // consumable-context destroy
                if (d.selector() instanceof Selector.Others o) { // Hex/Ankh: destroy every joker but the bound one
                    Joker keep = bindings.get(o.name());
                    if (keep != null) state.jokers().removeIf(j -> j != keep);
                } else { // card selectors (Hanged Man, Immolate): select the victims, then destroy them
                    apply(new com.balatro.engine.exec.Command.DestroyCards(resolveTargets(c, d.selector(), targets)));
                }
            }
            case Effect.CreateCards cr -> {
                var r = rng.stream("create:" + c.key());
                Rank[] ranks = Rank.values();
                Suit[] suits = Suit.values();
                List<Card> made = new ArrayList<>();
                for (int i = 0; i < cr.count(); i++) {
                    Rank rank;
                    do { rank = ranks[r.nextInt(ranks.length)]; } while (rank.id > 10); // numbered cards
                    made.add(new Card(rank, suits[r.nextInt(suits.length)], cr.enhancement(), Edition.NONE, Seal.NONE));
                }
                apply(new com.balatro.engine.exec.Command.AddCardsToDeck(made));
            }
            case Effect.LevelHands lh -> applyLevelHands(lh); // Black Hole (ALL)
            case Effect.AddCards a -> addRankClassCards(c, a);                  // Familiar / Grim rank-class adds
            case Effect.AdjustMoney am -> applyRunLoopEffect(am, runLoopContext()); // Immolate/Hermit/Temperance/Wraith
            case Effect.EditionJoker ej -> { // Ectoplasm/Hex: edition the bound joker (Wheel keeps JokerEdition)
                Joker target = ej.selector() instanceof Selector.Bound bnd ? bindings.get(bnd.name())
                        : (!state.jokers().isEmpty() ? state.queues.pick(state.jokers(),
                                RngSources.consumable(c.key()).sub("joker").composition(), rngCtx(), Joker::key, JOKER_QUALITY)
                           : null);
                if (target != null) apply(new com.balatro.engine.exec.Command.EditionJoker(target, resolveEdition(c, ej.edition())));
            }
            case Effect.AdjustHandSize ah -> apply(new com.balatro.engine.exec.Command.HandSize(ah.delta()));      // Ectoplasm -1
            case Effect.ConvertHand ch -> applyConvertHand(c, ch);
            case Effect.Copy cp -> { // consumable-context copy
                if (cp.selector() instanceof Selector.Selected && !targets.isEmpty()) { // Cryptid: copy the card cp.count()×
                    Card src = targets.get(0);
                    for (int i = 0; i < cp.count(); i++) {
                        Card dup = src.copy(); // fresh uid, same rank/suit/enh/edition/seal
                        composition.add(dup);
                        state.hand.add(dup);
                    }
                } else if (cp.selector() instanceof Selector.LastConsumable      // The Fool: copy the last Tarot/Planet used
                        && state.lastTarotPlanetUsed != null && state.consumables.size() < state.consumableSlots) {
                    state.consumables.add(state.lastTarotPlanetUsed);
                } else if (cp.selector() instanceof Selector.Bound bnd) {         // Ankh: copy the bound joker
                    Joker src = bindings.get(bnd.name());
                    if (src != null && state.jokers().size() < state.jokerSlots) {
                        state.addJoker(JokerLibrary.create(src.key(), ruleset.jokerVariant())); // copy has no edition
                    }
                }
            }
            case Effect.OverwriteSelected ignored -> {
                if (targets.size() == 2) { // Death: the left (first) card becomes a copy of the right
                    Card left = targets.get(0), right = targets.get(1);
                    left.rank = right.rank;
                    left.suit = right.suit;
                    left.enhancement = right.enhancement;
                    left.edition = right.edition;
                    left.seal = right.seal;
                }
            }
            case Effect.NemesisDelevel ignored -> state.nemesisDelevelPending++; // Match applies it to the opponent
            default -> throw new IllegalStateException("not a consumable effect: " + e);
        }
    }

    /** Resolve which held cards an effect targets. Selected = the player's chosen cards; the others let a
     *  consumable reach the whole hand or a random subset without bespoke code. */
    private List<Card> resolveTargets(Consumable c, Selector sel, List<Card> selected) {
        return switch (sel) {
            case Selector.Selected ignored -> selected;
            case Selector.Focus ignored ->
                    throw new IllegalStateException("Focus targets the scored card — meaningless outside scoring");
            case Selector.AllInHand ignored -> new ArrayList<>(state.hand);
            case Selector.RandomInHand r -> {
                List<Card> out = new ArrayList<>();
                for (int i = 0; i < r.count(); i++) {
                    List<Card> live = state.hand.stream().filter(x -> !x.destroyed && !out.contains(x)).toList();
                    if (live.isEmpty()) break;
                    // "destroy:i" sub-key matches the old Generate destroy stream, so Immolate/Familiar/Grim
                    // (now Destroy(RandomInHand) in their effect list) draw the same cards — byte-identical.
                    out.add(state.queues.pick(live, RngSources.consumable(c.key()).sub("destroy:" + i).composition(),
                            rngCtx(), Deck.CARD_GROUP, Deck.CARD_QUALITY));
                }
                yield out;
            }
            case Selector.RandomJoker ignored ->
                    throw new IllegalStateException("RandomJoker selects jokers, not cards");
            case Selector.Discarded ignored ->
                    throw new IllegalStateException("Discarded targets the discard event — scoring-time only");
            case Selector.Self ignored ->
                    throw new IllegalStateException("Self targets the emitting joker, not cards");
            case Selector.OtherJoker ignored ->
                    throw new IllegalStateException("OtherJoker selects a joker, not cards");
            case Selector.LastConsumable ignored ->
                    throw new IllegalStateException("LastConsumable selects a consumable, not cards");
            case Selector.RandomConsumable ignored ->
                    throw new IllegalStateException("RandomConsumable selects a consumable, not cards");
            case Selector.Bound ignored ->
                    throw new IllegalStateException("Bound names a joker, not cards");
            case Selector.Others ignored ->
                    throw new IllegalStateException("Others names jokers, not cards");
        };
    }

    /** Sigil (all cards to one random suit) / Ouija (all to one random rank, -1 hand size). */
    private void applyConvertHand(Consumable c, Effect.ConvertHand ch) {
        // MP Ouija rework: destroy 3 random cards first, convert the rest to one rank, and DON'T
        // reduce hand size (vanilla Ouija converts the whole hand and loses 1 hand size).
        boolean mpOuija = "c_ouija".equals(c.key()) && ruleset.capabilities().ouijaRework();
        if (mpOuija) {
            for (int i = 0; i < 3; i++) {
                List<Card> live = state.hand.stream().filter(x -> !x.destroyed).toList();
                if (live.isEmpty()) break;
                Card victim = state.queues.pick(live, RngSources.consumable(c.key()).sub("destroy:" + i).composition(),
                        rngCtx(), Deck.CARD_GROUP, Deck.CARD_QUALITY);
                victim.destroyed = true;
            }
            composition.removeIf(x -> x.destroyed);
            state.hand.removeIf(x -> x.destroyed);
            Rank r = pick(Rank.values(), RngSources.consumable(c.key()).sub("rank"));
            for (Card card : state.hand) card.rank = r;
            return; // no hand-size reduction in MP
        }
        if (ch.axis() == Effect.ConvertHand.Axis.SUIT) {
            Suit s = pick(Suit.values(), RngSources.consumable(c.key()).sub("suit"));
            for (Card card : state.hand) card.suit = s;
        }
        if (ch.axis() == Effect.ConvertHand.Axis.RANK) {
            Rank r = pick(Rank.values(), RngSources.consumable(c.key()).sub("rank"));
            for (Card card : state.hand) card.rank = r;
        }
        if (ch.handSizeDelta() != 0) state.handSize = Math.max(1, state.handSize + ch.handSizeDelta());
    }

    /** Ankh: copy a random owned joker (edition-free) and, if set, destroy all other jokers. */
    // Enhancements a "random Enhanced" created card (Familiar/Grim) may roll — every type but NONE.
    private static final Enhancement[] RANDOM_ENHANCEMENTS = {
        Enhancement.BONUS, Enhancement.MULT, Enhancement.GLASS, Enhancement.STEEL,
        Enhancement.STONE, Enhancement.GOLD, Enhancement.WILD, Enhancement.LUCKY
    };

    private void addRankClassCards(Consumable c, Effect.AddCards add) {
        Rank[] pool = switch (add.rankClass()) {
            case FACE -> new Rank[]{Rank.JACK, Rank.QUEEN, Rank.KING};
            case ACE -> new Rank[]{Rank.ACE};
            case NUMBER -> java.util.Arrays.stream(Rank.values()).filter(r -> r.id <= 10).toArray(Rank[]::new);
            case ANY -> Rank.values();
        };
        List<Card> made = new ArrayList<>();
        for (int i = 0; i < add.count(); i++) {
            Rank r = pick(pool, RngSources.consumable(c.key()).sub("rank:" + i));
            Suit s = pick(Suit.values(), RngSources.consumable(c.key()).sub("suit:" + i));
            Enhancement e = add.enhancement() != null ? add.enhancement()
                    : pick(RANDOM_ENHANCEMENTS, RngSources.consumable(c.key()).sub("enh:" + i));
            made.add(new Card(r, s, e, Edition.NONE, Seal.NONE));
        }
        apply(new com.balatro.engine.exec.Command.AddCardsToDeck(made));
    }

    /**
     * Add an edition to a random owned joker (Wheel of Fortune / Ectoplasm / Hex).
     * Wheel gates on a 1-in-N roll and picks a random Foil/Holo/Poly; Ectoplasm and
     * Hex always fire and carry their own side effects (hand-size / destroy-others).
     */
    /** Evaluate a consumable's {@link Effect.When} gate. A {@code Chance} rolls on the consumable's OWN
     *  stream ({@code consumable(key).sub(seedKey)}) — the same draw the hardcoded Wheel gate used — and
     *  PROBABILITY_MULTIPLIER (Oops!) scales the threshold like every other chance. Others test normally. */
    private boolean consumableConditionHolds(Consumable c, com.balatro.grammar.Condition cond) {
        if (cond instanceof com.balatro.grammar.Condition.Chance ch) {
            double r = roll(RngSources.consumable(c.key()).sub(ch.seedKey()));
            return r < (double) (ch.odds().numerator() * state.probabilityNumerator) / ch.odds().denominator();
        }
        return com.balatro.engine.eval.ConditionEvaluator.test(cond, runLoopContext());
    }

    /** {@code NONE} = roll a random Foil/Holo/Poly (the "ed" sub-stream); else the fixed edition. */
    private Edition resolveEdition(Consumable c, Edition ed) {
        if (ed != Edition.NONE) return ed;
        return pick(new Edition[]{Edition.FOIL, Edition.HOLOGRAPHIC, Edition.POLYCHROME},
                RngSources.consumable(c.key()).sub("ed"));
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
        return new com.balatro.engine.scoring.ScoringEngine().preview(played, held, state, rng);
    }

    /** Client-facing entry: validate+apply an intent, return the authoritative update. */
    public ServerUpdate submit(Intent intent) {
        IntentResult r = play(intent);
        List<ReplayEntry> replay = (r.score() != null) ? r.score().replayLog() : List.of();
        return new ServerUpdate(r.ok(), r.error(), view(), replay);
    }

    // --- The run as a fold over actions ------------------------------------------------------------
    private final List<RunAction> actionLog = new ArrayList<>(); // append-only; only accepted actions

    /**
     * The single mutation entry point: validate+apply one {@link RunAction}, append it to the action log
     * if it was accepted, and return the authoritative update. Routing everything through here is what
     * makes the run a verifiable fold — the log is the complete, replayable history.
     */
    public ServerUpdate apply(RunAction action) {
        ServerUpdate update = dispatch(action);
        if (update.accepted()) actionLog.add(action);
        return update;
    }

    /** This run's accepted actions, in order — its whole history (snapshot/verify/what-if substrate). */
    public List<RunAction> actionLog() {
        return List.copyOf(actionLog);
    }

    /** Reconstruct a run by folding {@code actions} onto a fresh run with the same identity. Deterministic:
     *  same (ruleset, seed, stake, deck, actions) → same run. This is save/restore, verification, and the
     *  base for "what if?" (replay with one action swapped). */
    public static Run replay(Ruleset ruleset, String seed, Stake stake,
                             DeckCatalog.DeckType deck, List<RunAction> actions) {
        Run run = new Run(ruleset, seed, stake, deck);
        for (RunAction a : actions) run.apply(a);
        return run;
    }

    private ServerUpdate dispatch(RunAction a) {
        return switch (a) {
            case RunAction.PlayHand p -> submit(new Intent.PlayHand(p.cards()));
            case RunAction.Discard d -> submit(new Intent.Discard(d.cards()));
            case RunAction.SelectBlind ignored -> wrap(selectBlind());
            case RunAction.SkipBlind ignored -> wrap(skipBlind());
            case RunAction.BuyShopItem b -> wrap(buyShopItem(b.index()));
            case RunAction.Reroll ignored -> wrap(reroll());
            case RunAction.BuyVoucher b -> wrap(buyVoucher(b.index()));
            case RunAction.OpenPack o -> wrap(openPack(o.index()));
            case RunAction.PickPackItem p -> wrap(pickPackItem(p.index()));
            case RunAction.SkipPack ignored -> wrap(skipPack());
            case RunAction.SellJoker s -> wrap(sellJoker(s.index()));
            case RunAction.UseConsumable u -> wrap(useConsumable(u.index(),
                    u.targets().toArray(new java.util.UUID[0])));
            case RunAction.Proceed ignored -> {
                proceed();
                yield wrap(null);
            }
        };
    }

    /** Wrap a {@code String}-returning action (null = accepted) as a {@link ServerUpdate} with the view. */
    private ServerUpdate wrap(String rejection) {
        return new ServerUpdate(rejection == null, rejection, view(), List.of());
    }

    /** The data definition backing a joker (for the client preview); every joker is data now. */
    private static JokerDef defFor(Joker j) {
        return (j instanceof DataJoker dj) ? dj.def() : null;
    }

    /** The locale the client wants its server-rendered text in (set per session); "en" by default. */
    public String viewLocale = "en";

    /** The boss's effect text in {@link #viewLocale}: the localized template filled with the boss's own data
     *  (numbers single-sourced). For "en" this equals {@code boss.effect()}. */
    private String bossText(BossBlind b) {
        double rm = b.reqMult();
        Object req = rm == Math.rint(rm) ? (Object) (long) rm : (Object) rm;
        return com.balatro.engine.i18n.Loc.fill(viewLocale, b.key(), java.util.Map.of(
                "reqMult", req, "minAnte", b.minAnte(), "reward", b.reward()));
    }

    /** A localized description for a content key in {@link #viewLocale}, falling back to {@code original}
     *  when neither the locale nor English has text (e.g. a custom joker not in localization). */
    private String locDesc(String key, String original) {
        String t = com.balatro.engine.i18n.Loc.text(viewLocale, key);
        return t.isEmpty() ? original : t;
    }

    /** The safe projection of authoritative state the client may render (spec §8). */
    public ClientView view() {
        List<CardView> handView = new ArrayList<>();
        for (Card c : state.hand) handView.add(CardView.of(c));
        Map<String, Object> handLevels = new LinkedHashMap<>();
        for (HandType t : HandType.values()) handLevels.put(t.display, state.handLevel(t));
        boolean inShop = phase == Phase.SHOP && shop != null;
        int rerollCost = inShop ? rerollCost() : 0;

        return new ClientView(ante, blind.display, requirement, state.roundScore,
                state.handsLeft, state.discardsLeft, state.money, state.handSize,
                phase.name(), handView, jokerView(), shopItemsView(), rerollCost,
                boss != null ? boss.name() : null, boss != null ? bossText(boss) : null,
                consumablesView(), handLevels, deckStatsView(), countersView(), shopVouchersView(),
                shopPacksView(), openPackView(),
                stake.display, deckType.name(), boss != null ? boss.key() : null);
    }

    /** The owned Jokers, each as a render map (or just a face-down placeholder under Amber Acorn) with the
     *  data def + live state so the client can run an instant local preview. */
    private List<Map<String, Object>> jokerView() {
        List<Map<String, Object>> jokerView = new ArrayList<>();
        for (int i = 0; i < state.jokers().size(); i++) {
            Joker j = state.jokers().get(i);
            var info = j.info();
            Map<String, Object> jv = new LinkedHashMap<>();
            if (jokersHidden) {
                // Amber Acorn: the Joker is face down — reveal nothing but a placeholder card back. The
                // server still scores it; the client just can't see which Joker sits in which (shuffled) slot.
                jv.put("faceDown", true);
                jokerView.add(jv);
                continue;
            }
            jv.put("key", info.key());
            jv.put("name", info.name());
            jv.put("description", locDesc(info.key(), info.description()));
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
        return jokerView;
    }

    /** The shop's mixed main slots (Joker / Tarot / Planet), or null when not in the shop. {@code kind}
     *  tells the client how to render/label and which buy path {@code buyShopItem} takes. */
    private List<Map<String, Object>> shopItemsView() {
        if (phase != Phase.SHOP || shop == null) return null;
        List<Map<String, Object>> shopView = new ArrayList<>();
        for (Shop.Item it : shop.items()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", it.kind().name());
            m.put("key", it.key());
            m.put("name", it.name());
            m.put("description", locDesc(it.key(), it.description()));
            m.put("cost", it.cost());
            m.put("rarity", it.rarity());
            m.put("edition", it.edition().name());
            if (it.eternal()) m.put("eternal", true);
            if (it.perishable()) m.put("perishable", true);
            if (it.rental()) m.put("rental", true);
            shopView.add(m);
        }
        return shopView;
    }

    /** Held consumables (Tarot/Planet) as render maps. */
    private List<Map<String, Object>> consumablesView() {
        List<Map<String, Object>> consumables = new ArrayList<>();
        for (String key : state.consumables) {
            PlanetCatalog.Planet p = PlanetCatalog.get(key);
            if (p != null) {
                consumables.add(Map.of("key", key, "name", p.name(), "description", locDesc(key, p.description())));
                continue;
            }
            Consumable c = TarotCatalog.get(key);
            if (c != null) {
                consumables.add(Map.of("key", key, "name", c.name(), "description", locDesc(key, c.description()),
                        "maxTargets", c.maxTargets()));
            }
        }
        return consumables;
    }

    /** Deck aggregates the client needs for deck-stat joker previews (size / remaining / enhancement counts). */
    private Map<String, Object> deckStatsView() {
        Map<String, Object> deckStats = new LinkedHashMap<>();
        deckStats.put("size", state.deckComposition.size());
        deckStats.put("remaining", state.deck != null ? state.deck.remaining() : 0);
        Map<String, Integer> enh = new LinkedHashMap<>();
        for (Card c : state.deckComposition) {
            if (c.enhancement != com.balatro.engine.card.Enhancement.NONE) {
                enh.merge(c.enhancement.name(), 1, Integer::sum);
            }
        }
        deckStats.put("enhancements", enh);
        return deckStats;
    }

    /** The run-state counters the client mirrors for previews/screens (run totals, hand-type plays, round
     *  targets, cash-out breakdown, offered/held tags, opponent state). */
    private Map<String, Object> countersView() {
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
        // Per-round targets: emit each by its id; enums (Suit/HandType) as names, rank ids as ints.
        for (com.balatro.engine.state.RoundTargets.Spec t : com.balatro.engine.state.RoundTargets.ALL) {
            Object v = state.roundTargets.get(t.id());
            counters.put(t.id(), v instanceof Enum<?> e ? e.name() : v);
        }
        counters.put("OBELISK_STREAK", state.obeliskStreak);
        counters.put("BLINDS_SKIPPED", state.blindsSkipped);
        counters.put("inPvpBlind", state.inPvpBlind);
        counters.put("bossHalveBase", state.bossHalveBase); // The Flint: preview halves base too
        counters.put("multiplayer", state.capabilities.restrictedPools());
        // Cash-out breakdown (the end-of-round screen reads these when entering the shop).
        counters.put("cashOutReward", state.lastBlindReward);
        counters.put("cashOutInterest", state.lastInterest);
        // The tag offered for skipping this blind (shown on the Select/Skip screen).
        counters.put("offeredTag", state.offeredTag == null ? "" : state.offeredTag);
        counters.put("offeredTagName", state.offeredTag == null ? ""
                : (TagCatalog.get(state.offeredTag) != null ? TagCatalog.get(state.offeredTag).name() : state.offeredTag));
        counters.put("heldTags", new ArrayList<>(state.tags));
        counters.put("OPP_LIVES_BEHIND", Math.max(0, state.opponent.lives - state.myLives));
        counters.put("OPP_HANDS_LEFT", state.opponent.handsLeft);
        counters.put("OPP_CARDS_SOLD", state.opponent.cardsSold);
        return counters;
    }

    /** The extra Voucher(s) offered in the shop (Voucher Tag / second slot), or null if none. */
    private List<Map<String, Object>> shopVouchersView() {
        if (phase != Phase.SHOP || shop == null || shop.vouchers().isEmpty()) return null;
        List<Map<String, Object>> shopVouchers = new ArrayList<>();
        for (String vk : shop.vouchers()) {
            var v = VoucherCatalog.get(vk);
            shopVouchers.add(Map.of("key", v.key(), "name", v.name(),
                    "description", locDesc(v.key(), v.description()), "cost", price(v.cost())));
        }
        return shopVouchers;
    }

    /** The shop's booster packs (kept across rerolls), or null when not in the shop. */
    private List<Map<String, Object>> shopPacksView() {
        if (phase != Phase.SHOP) return null;
        List<Map<String, Object>> packsView = new ArrayList<>();
        for (PackCatalog.Pack p : shopPacks) {
            packsView.add(Map.of("kind", p.kind().name(), "size", p.size().name(),
                    "name", p.displayName(), "cost", price(p.cost()),
                    "shown", p.shown(), "choose", p.choose()));
        }
        return packsView;
    }

    /** The currently-open booster pack's revealed items + picks left, or null if no pack is open. */
    private Map<String, Object> openPackView() {
        if (openPack == null) return null;
        List<Map<String, Object>> items = new ArrayList<>();
        for (RevealedItem it : openPack) items.add(revealedItemView(it));
        return Map.of("picksLeft", packPicksLeft, "items", items);
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
                m.put("description", locDesc(it.key(), info.description()));
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
                m.put("description", locDesc(it.key(),
                        p != null ? p.description() : (c != null ? c.description() : "")));
            }
        }
        return m;
    }

    /** Perkeo: leaving the shop, a SHOP_EXIT joker rule duplicates a random held consumable (data). */
    private void applyShopExit() {
        raiseJokerRules(Trigger.SHOP_EXIT);
    }

    /** Fire a just-sold joker's own SELL_SELF rules (Luchador disables the boss, Diet Cola makes a tag). */
    private void raiseSelfSellRules(Joker sold) {
        if (!(sold instanceof DataJoker dj)) return;
        com.balatro.engine.joker.EvaluationContext ctx = runLoopContext();
        ctx.jokers = List.of(sold); // self() = the sold joker, so its state (e.g. "rounds") is readable
        ctx.selfIndex = 0;
        for (Rule r : dj.def().rules()) {
            if (r.when() != Trigger.SELL_SELF) continue;
            if (r.condition() != null && !com.balatro.engine.eval.ConditionEvaluator.test(r.condition(), ctx)) continue;
            for (Effect e : r.effects()) applyRunLoopEffect(e, ctx);
        }
    }

    /** Fire every owned data-joker's rules for a lifecycle {@code trigger} through the run-loop interpreter —
     *  the joker-side twin of {@link #raiseBossRules}. Used for capability effects (Perkeo on shop exit) that
     *  mutate run state, which the scoring/GameEvents paths don't apply. */
    private void raiseJokerRules(Trigger trigger) {
        com.balatro.engine.joker.EvaluationContext ctx = runLoopContext();
        ctx.jokers = state.jokers();
        List<Joker> owned = state.jokers();
        for (int i = 0; i < owned.size(); i++) {
            if (!(owned.get(i) instanceof DataJoker dj)) continue;
            ctx.selfIndex = i;
            for (Rule r : dj.def().rules()) {
                if (r.when() != trigger) continue;
                if (r.condition() != null && !com.balatro.engine.eval.ConditionEvaluator.test(r.condition(), ctx)) continue;
                for (Effect e : r.effects()) applyRunLoopEffect(e, ctx);
            }
        }
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

    /** IMMEDIATE tags: resolve their data {@link Effect}s on claim (Economy/Speed/Handy/Garbage/Orbital/Top-Up). */
    private void applyImmediateTag(String key) {
        applyTagEffects(TagCatalog.get(key));
    }

    /** A base run-loop {@link com.balatro.engine.joker.EvaluationContext}: the run + its RNG, nothing more.
     *  Every run-loop dispatcher (boss/joker/tag effects) starts from this and adds what it needs. */
    private com.balatro.engine.joker.EvaluationContext runLoopContext() {
        com.balatro.engine.joker.EvaluationContext ctx = new com.balatro.engine.joker.EvaluationContext();
        ctx.run = state;
        ctx.rng = rng;
        return ctx;
    }

    /** Apply a tag's data effects through the run-loop interpreter (null/effectless tags are a no-op). */
    private void applyTagEffects(TagCatalog.Tag tag) {
        if (tag == null) return;
        com.balatro.engine.joker.EvaluationContext ctx = runLoopContext();
        for (Effect e : tag.effects()) applyRunLoopEffect(e, ctx);
    }

    /** NEXT_BLIND tags, applied at blind start (Juggle: +3 hand size — a data AdjustHandSize effect). */
    private void applyBlindTags() {
        applyHeldTagsOfTiming(TagCatalog.Timing.NEXT_BLIND);
    }

    /** ON_SHOP tags resolve as data when the shop opens (packs / free-jokers / voucher / coupon / d6). */
    private void applyShopTags() {
        if (shop == null) return;
        applyHeldTagsOfTiming(TagCatalog.Timing.ON_SHOP);
    }

    /** Resolve and consume every held tag of {@code timing}, applying its data {@link Effect}s through the
     *  shared run-loop interpreter. The one place held tags turn into behaviour. */
    private void applyHeldTagsOfTiming(TagCatalog.Timing timing) {
        List<String> held = state.tags.stream().filter(t -> TagCatalog.timing(t) == timing).toList();
        for (String key : held) {
            state.tags.remove(key); // consume one instance
            applyTagEffects(TagCatalog.get(key));
        }
    }

    /** Voucher Tag: draw the next voucher from the same game-long voucher queue and add it as an extra
     *  shop slot (BMP: get_next_voucher_key(true) + card_limit + 1). Persists across rerolls this visit. */
    private void addTagVoucher() {
        String key = nextShowableVoucher();
        if (key == null) return;
        tagVouchers.add(key);
        shop.addVoucher(key);
    }

    /** Add a free Joker of the given rarity to the shop, drawn from a tag-only off-shop queue. */
    private void addFreeJoker(String rarity) {
        List<String> pool = JokerLibrary.keysByRarity(rarity);
        if (pool.isEmpty()) return;
        String key = state.queues.queue(RngSources.tagJoker(rarity), r -> pool.get(r.nextInt(pool.size()))).next();
        var info = JokerLibrary.create(key).info();
        shop.items().add(new Shop.Item(Shop.Kind.JOKER, key, info.name(), info.description(), 0, info.rarity(), Edition.NONE));
    }

    /** Add a free base Joker with a forced edition (Foil/Holo/Poly/Negative Tag). */
    private void addFreeEditionedJoker(Edition ed) {
        String key = Shop.drawJoker(state.queues, jokerPoolForShop(), java.util.Set.of(), new java.util.HashSet<>(), false);
        var info = JokerLibrary.create(key).info();
        shop.items().add(new Shop.Item(Shop.Kind.JOKER, key, info.name(), info.description(), 0, info.rarity(), ed));
    }

    /** ON_BOSS_DEFEAT tags resolve as data after a boss is beaten (Investment: +$25 each). */
    private void applyBossDefeatTags() {
        applyHeldTagsOfTiming(TagCatalog.Timing.ON_BOSS_DEFEAT);
        // Anaglyph Deck: gain a Double Tag after defeating each Boss Blind (back.lua:111-120).
        state.tags.addAll(deckType.onBossDefeatTags());
    }

    /** Roll this shop's two booster packs from the game-long packs queue (kept across rerolls). */
    private void rollShopPacks() {
        shopPacks.clear();
        openPack = null;
        packPicksLeft = 0;
        var q = state.queues.queue(RngSources.PACKS, r -> PackCatalog.roll(r.nextDouble()));
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
            case ARCANA -> fillConsumables(out, n, RngSources.packContent("pack:tarot"),
                    TarotCatalog.tarotKeys(), "c_the_soul", "Tarot", k -> true);
            case SPECTRAL -> fillConsumables(out, n, RngSources.packContent("pack:spectral"),
                    TarotCatalog.spectralKeys(), "c_the_soul", "Spectral", k -> true);
            case CELESTIAL -> {
                int fill = n;
                // Telescope: guarantee your most-played hand's Planet is in the pack.
                if (voucherFold(Value.Var.CELESTIAL_MOST_PLAYED, 0) >= 1) {
                    String mp = mostPlayedPlanetKey();
                    if (mp != null) { out.add(new RevealedItem("Planet", mp, null)); fill--; }
                }
                fillConsumables(out, fill, RngSources.packContent("pack:planet"),
                        PlanetCatalog.keys(), "c_black_hole", "Planet", k -> PlanetCatalog.available(k, playedHands()));
            }
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
                var q = state.queues.queue(RngSources.PACK_CARD,
                        r -> new Card(ranks[r.nextInt(ranks.length)], suits[r.nextInt(suits.length)],
                                Enhancement.NONE, Edition.NONE, Seal.NONE));
                for (int i = 0; i < n; i++) out.add(new RevealedItem("CARD", null, q.next()));
            }
        }
        return out;
    }

    /** Fill {@code n} consumable slots from the pack queue, rolling the Soul queue per slot;
     *  on a Soul hit the content queue is NOT advanced (it's pushed back) and the Soul is inserted. */
    private void fillConsumables(java.util.List<RevealedItem> out, int n, RngSource contentSrc,
            List<String> pool, String soulKey, String soulType, java.util.function.Predicate<String> available) {
        var q = state.queues.queue(contentSrc, r -> pool.get(r.nextInt(pool.size())));
        // Per-content-type soul stream (BMP keys it 'soul_'..type), checked per slot at ~0.3%.
        var soulQ = state.queues.queue(RngSources.SOUL.sub(soulType), r -> r.nextDouble() < 0.003);
        for (int i = 0; i < n; i++) {
            if (soulQ.next()) out.add(new RevealedItem("CONSUMABLE", soulKey, null));
            else out.add(new RevealedItem("CONSUMABLE", q.nextWhere(available), null)); // skip softlocked planets
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
                int winAnte = voucherFold(Value.Var.WIN_ANTE, ruleset.winAnte()); // Hieroglyph/Petroglyph: -1 each
                if (pvpFromAnte == 0 && ruleset.winAnte() > 0 && ante >= winAnte) {
                    phase = Phase.RUN_WON;
                    return;
                }
                ante++;
                blind = BlindType.SMALL;
                state.shopSpentLastAnte = state.shopSpentThisAnte; // snapshot for Penny Pincher
                state.shopSpentThisAnte = 0;
                state.bossRerollsThisAnte = 0; // Director's Cut/Retcon: rerolls refresh each ante
                for (Card c : composition) c.playedThisAnte = false; // The Pillar: fresh ante, fresh cards
            }
        }
        startBlind();
    }
}
