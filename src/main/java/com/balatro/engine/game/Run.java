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
    int freeRerollsUsedThisShop = 0; // free rerolls spent this shop (vs the FREE_REROLLS fold: Chaos)
    int rerollsThisShop = 0;         // reroll cost escalates +$1 per reroll, reset each shop
    final java.util.List<PackCatalog.Pack> shopPacks = new ArrayList<>(); // 2 packs/shop, kept across rerolls
    java.util.List<RevealedItem> openPack = null; // the currently-open pack's revealed cards (null = none open)
    int packPicksLeft = 0;

    /** A revealed pack card — enough to render it and to resolve a pick. type: CONSUMABLE | JOKER | CARD. */
    record RevealedItem(String type, String key, Card card) {}
    String anteVoucher = null;        // the single voucher offered this ante (persists across its shops)
    int anteVoucherAnte = -1;         // which ante anteVoucher was rolled for (-1 = none yet)
    String lastVoucherShown = null;   // last resolved voucher (for the queue's dup-skip rule)
    final List<String> tagVouchers = new ArrayList<>(); // extra vouchers a Voucher Tag added this shop visit
    boolean couponActive = false;     // Coupon Tag: this shop's initial cards/packs are free
    boolean d6Active = false;         // D6 Tag: rerolls start at $0 this shop
    boolean luchadorDisabledBoss = false; // Luchador: boss disabled for the current blind
    boolean jokersHidden = false;         // Amber Acorn: Jokers flipped face down (hidden in the view)
    final List<Card> composition = state.deckComposition; // the full deck (lives on RunState)

    // Identity tiebreaks for composition picks. Same-key jokers/consumables are interchangeable for a
    // "random" pick, so the comparator is neutral; the grouping (by key) is what makes the choice
    // depend on the set held rather than its left-to-right order.
    static final java.util.Comparator<Joker> JOKER_QUALITY = (a, b) -> 0;

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
        for (String v : deckType.startingVouchers()) RunShop.grantVoucher(this, v);
        state.consumables.addAll(deckType.startingConsumables());
        recomputeSlots();
    }

    private static final int BASE_CONSUMABLE_SLOTS = 2; // before deck/voucher CONSUMABLE_SLOTS Modifys
    private static final int BASE_JOKER_SLOTS = 5;       // before deck/voucher JOKER_SLOTS Modifys

    /** Fold {@code var} from every owned voucher's Modifys over {@code base} — the derived-from-ownership
     *  pattern for voucher-driven variables (win ante, boss rerolls, held-planet mult, ...). */
    double voucherFoldD(Value.Var var, double base) {
        List<Modify> mods = new ArrayList<>();
        for (String v : state.vouchers) {
            VoucherCatalog.Voucher def = VoucherCatalog.get(v);
            if (def != null) mods.addAll(def.mods());
        }
        return com.balatro.engine.eval.ModifyFolder.fold(base, var, mods);
    }

    int voucherFold(Value.Var var, int base) {
        return (int) voucherFoldD(var, base);
    }

    /** Slot counts = base + every CONSUMABLE_SLOTS / JOKER_SLOTS Modify owned (deck + Crystal Ball/Omen
     *  Globe / Antimatter / Black/Painted decks), folded fresh — a pure function of what you own,
     *  recomputed whenever ownership changes. */
    void recomputeSlots() {
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
        RunLoopRules.applyJokerDestroyers(this); // Ceremonial Dagger / Madness eat a joker at blind select
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
        RunTags.applyBlindTags(this); // NEXT_BLIND tags (Juggle: +3 hand size) before the hand is dealt
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
        RunLoopRules.raiseBossRules(this,Trigger.BLIND_SELECTED, null);
        ensureForcedSelection(); // Cerulean Bell: lock one opening-hand card as force-selected
        RunLoopRules.refreshDebuffs(this);
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
        if (!RunShop.canAfford(this, BOSS_REROLL_COST)) return "not enough money";
        RunShop.spend(this, BOSS_REROLL_COST);
        state.bossRerollsThisAnte++;
        boss = BossCatalog.pick(ante, rng, state.bossRerollsThisAnte);
        boolean disabled = bossDisabled();
        state.bossHalveBase = !disabled && boss.halveBase(); // The Flint
        requirement = Math.round(Blinds.getBlindAmount(ante, ruleset, stake.scaling())
                * boss.reqMult() * ruleset.anteScaling()) * deckType.blindSizeMult();
        applyResourceMods();                            // recompute hands/discards/size for the new boss
        markFaceDown(state.hand, DrawContext.INITIAL);  // re-mark face-down for the new boss
        RunLoopRules.refreshDebuffs(this);                               // re-mark card debuffs for the new boss
        return null;
    }

    private static final int BOSS_REROLL_COST = 10;

    /** Jokers that consume another joker at blind select: Ceremonial Dagger (right neighbour ->
     *  +Mult from 2x its sell value) and Madness (a random other joker -> +x0.5 Mult, non-boss only). */
    /** Whether the active boss has an ability (a debuff or a hand/discard/size override). */
    boolean bossHasAbility() {
        return boss != null && (boss.debuff() != null || boss.halveBase()
                || !boss.mods().isEmpty() // hand/discard/size resource modifiers
                || !boss.rules().isEmpty() // rule-driven effects (per-hand, blind-start, pre-hand, on-sell)
                || boss.requires() != null || boss.faceDown() != null
                || boss.forcesCardSelection());
    }

    /** The boss blind's ability is off — Chicot (always) or Luchador (sold this blind). */
    boolean bossDisabled() {
        // Chicot is a dynamic boolean policy now (mods(max(BOSS_ABILITY_DISABLED, 1))), folded from the
        // owned jokers — same pattern as Showman/Astronomer — so it re-arms the boss if Chicot is sold.
        return luchadorDisabledBoss
                || com.balatro.engine.joker.def.DataJoker.policyEnabled(
                        state.jokers(), Value.Var.BOSS_ABILITY_DISABLED);
    }

    // Jokers that did Destroy(Self) during a run-loop rule pass — removed after the pass (no concurrent mod).
    final java.util.List<Joker> pendingSelfDestruct = new java.util.ArrayList<>();

    /** A failed blind, as a hookable Blind-lifecycle event: raise {@code BLIND_LOST} over the joker rules.
     *  Mr Bones (gated on {@code BLIND_PROGRESS} >= 0.25) reacts by {@code Write(BLIND_SURVIVED)} + {@code Destroy(Self)}
     *  — the survive is just a write, the consume is the shared self-destruct primitive. No bespoke capability. */
    private boolean survivesLostBlind() {
        if (requirement <= 0) return false;
        state.blindProgress = (double) state.roundScore / requirement;
        state.blindSurvived = false;
        RunLoopRules.raiseJokerRules(this, Trigger.BLIND_LOST); // Mr Bones: Write(BLIND_SURVIVED) + Destroy(Self), both applied in the pass
        return state.blindSurvived;
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
                    for (Effect e : s.endOfRound()) RunLoopRules.applyRunLoopEffect(this, e, ctx);
                }
            }
        }
    }

    /** The joker pool the shop/packs draw from — in multiplayer the boss-interacting jokers
     *  are excluded from the pool entirely (not merely skipped). */
    List<String> jokerPoolForShop() {
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
     * Draw the next showable voucher from the game-long voucher queue, advancing it. Resolution per
     * drawn position: Tier 1 until bought, then Tier 2, then skip the position once both tiers are
     * owned; a position resolving to the same voucher as the last shown is skipped (dup-skip). Shared
     * by the per-ante voucher and the Voucher Tag — the BMP {@code get_next_voucher_key(_from_tag)}
     * model, where a tag draws the next voucher from the very same queue. Returns null if exhausted.
     */
    String nextShowableVoucher() {
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
    int minMoney() {
        return EconomyConfig.resolve(deckType, state.vouchers, state.jokers()).minMoney();
    }

    /** Apply one resolved {@link com.balatro.engine.exec.Command} — the single mutation path that the
     *  action-effect families migrate onto (replacing the direct mutation in the interpreter switches). */
    void apply(com.balatro.engine.exec.Command cmd) {
        CommandApply.apply(this, cmd);
    }


    /** Re-roll the per-round dynamic targets (The Idol's card, Ancient's suit). */
    /** Re-roll each owned joker's per-round targets (the domains its rules reference): generic + per-joker,
     *  so no joker name is baked anywhere — Ancient/Castle/Idol/To Do/Rebate are just jokers that use a
     *  suit/rank/hand target. Keyed by {@link RoundTargets#key}; read back by each joker's {@code *IsTarget}. */
    private void rollRoundTargets() {
        state.roundTargets.clear(); // fresh each blind; per-joker keys (stale sold-joker entries don't linger)
        // Multiplayer rolls the Idol target a different way (deck position) — skip its generic roll below.
        boolean mpIdol = ruleset.capabilities().idolDeckPosition();
        for (Joker j : state.jokers()) {
            if (!(j instanceof DataJoker dj)) continue;
            for (com.balatro.engine.state.RoundTargets.Domain d : targetDomains(dj)) {
                if (mpIdol && dj.key().equals(IDOL_KEY)
                        && (d == com.balatro.engine.state.RoundTargets.Domain.RANK
                            || d == com.balatro.engine.state.RoundTargets.Domain.SUIT)) continue;
                String rngKey = dj.key() + ":" + d.name();
                Object v = switch (d) {
                    case SUIT -> pick(com.balatro.engine.card.Suit.values(), RngSources.TARGET.sub(rngKey));
                    case RANK -> 2 + (int) (roll(RngSources.TARGET.sub(rngKey)) * 13) % 13;
                    case HAND_TYPE -> pick(com.balatro.engine.hand.HandType.values(), RngSources.TARGET.sub(rngKey));
                };
                state.roundTargets.put(com.balatro.engine.state.RoundTargets.key(dj.key(), d), v);
            }
        }
        if (mpIdol) rollMpIdol(); // MP: deck-position roll (shared number, each player's own deck)
    }

    /** The Idol is the one joker whose target the MP ruleset rolls specially (deck position). */
    private static final String IDOL_KEY = "j_idol";

    private static final com.fasterxml.jackson.databind.ObjectMapper TARGET_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private final java.util.Map<String, java.util.EnumSet<com.balatro.engine.state.RoundTargets.Domain>>
            targetDomainCache = new java.util.HashMap<>();

    /** Which target domains a joker's rules reference (cached per key). Walks the def's JSON so a target
     *  condition is found wherever it nests (a rule's {@code when}, a per-card predicate, a Value.Count). */
    private java.util.EnumSet<com.balatro.engine.state.RoundTargets.Domain> targetDomains(DataJoker dj) {
        return targetDomainCache.computeIfAbsent(dj.key(), k -> {
            var set = java.util.EnumSet.noneOf(com.balatro.engine.state.RoundTargets.Domain.class);
            scanTargetDomains(TARGET_MAPPER.valueToTree(dj.def().rules()), set);
            return set;
        });
    }

    private static void scanTargetDomains(com.fasterxml.jackson.databind.JsonNode node,
            java.util.EnumSet<com.balatro.engine.state.RoundTargets.Domain> out) {
        if (node.isObject()) {
            switch (node.path("type").asText("")) {
                case "scoredSuitIsTarget" -> out.add(com.balatro.engine.state.RoundTargets.Domain.SUIT);
                case "scoredRankIsTarget" -> out.add(com.balatro.engine.state.RoundTargets.Domain.RANK);
                case "handIsTarget" -> out.add(com.balatro.engine.state.RoundTargets.Domain.HAND_TYPE);
                default -> { }
            }
            node.forEach(c -> scanTargetDomains(c, out));
        } else if (node.isArray()) {
            node.forEach(c -> scanTargetDomains(c, out));
        }
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
        state.roundTargets.put(com.balatro.engine.state.RoundTargets.key(IDOL_KEY,
                com.balatro.engine.state.RoundTargets.Domain.SUIT), target.suit);
        state.roundTargets.put(com.balatro.engine.state.RoundTargets.key(IDOL_KEY,
                com.balatro.engine.state.RoundTargets.Domain.RANK), target.rank.id);
    }

    /** The RNG resolution context for this run right now (ante, blind, PvP state, order flag). */
    public RngContext rngCtx() {
        return new RngContext(ante, blind.name(), state.inPvpBlind, state.order);
    }

    /** A roll in [0,1) from a declared source, resolved against the current context. */
    double roll(RngSource src) {
        return state.queues.roll(src, rngCtx());
    }

    /** Uniformly pick one element of {@code arr} using a roll from {@code src}. */
    <T> T pick(T[] arr, RngSource src) {
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
    List<Modify> resourceMods() {
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
            boolean cardMatches = rule.card() == null || RunLoopRules.testCardDebuff(this, rule.card(), c);
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
        if (intent instanceof Intent.PlayHand) { RunLoopRules.raiseBossRules(this,Trigger.PRE_HAND, null); RunLoopRules.resolveObservatory(this); }
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
        if (purpleDiscards > 0) RunTags.applyPurpleSeals(this, purpleDiscards); // each makes a Tarot
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
        RunLoopRules.refreshDebuffs(this); // re-mark the freshly drawn hand
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
            RunLoopRules.applyBossOnHandPlayed(this, playedCards, result.score()); // Tooth / Ox / Arm / Hook per-hand boss effects
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

    /** Run-loop hand leveling — the ALL (Black Hole) and MOST_PLAYED (Orbital) scopes of {@link
     *  Effect.LevelHands}; the PLAYED scope is scoring-time (the levelUpHand flag), never reaches here. */
    void applyLevelHands(Effect.LevelHands lh, com.balatro.engine.joker.EvaluationContext ctx) {
        if (lh.target() == com.balatro.grammar.Side.OPPONENT) { // Asteroid: route to the Nemesis (Match consumes the pending)
            state.nemesisDelevelPending += Math.abs((int) Math.round(com.balatro.engine.eval.ValueResolver.resolve(lh.levels(), ctx)));
            return;
        }
        int n = Math.max(1, (int) Math.round(com.balatro.engine.eval.ValueResolver.resolve(lh.levels(), ctx)));
        switch (lh.scope()) {
            case ALL -> { for (HandType t : HandType.values()) apply(new com.balatro.engine.exec.Command.LevelHand(t, n)); }
            case MOST_PLAYED -> {
                HandType best = mostPlayedHand();
                if (best == null) best = HandType.HIGH_CARD;
                apply(new com.balatro.engine.exec.Command.LevelHand(best, n));
            }
            case PLAYED -> { /* SELF+PLAYED is scoring-time (the scorer) or The Arm (run-loop applies it inline) */ }
        }
    }

    /** The poker hand type played most this run (The Ox's trigger), or null if none yet. */
    private com.balatro.engine.hand.HandType mostPlayedHand() {
        return state.handTypePlays.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(null);
    }

    /** The Planet card key for your most-played hand (Telescope), or null if nothing's been played. */
    String mostPlayedPlanetKey() {
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
        shop = RunShop.generateShop(this); // no reward for a failed blind
        shopPacks.clear(); // no packs after a failed blind
        openPack = null;
        RunShop.enterShopTags(this);
        phase = Phase.SHOP;
    }

    /** End a Nemesis blind once the match decided it (works for the locked loser AND
     *  the ahead winner who may still have hands): award economy, proceed to the shop. */
    public void endPvp() {
        if (!pvpActive) return;
        pvpActive = false;
        state.cardsSoldSinceLastPvp = 0; // Taxes counts sells between PvP blinds
        state.money += NEMESIS.reward() + endOfRoundMoney();
        RunTags.applyBossDefeatTags(this); // Investment Tag pays out after the PvP (Boss) blind too
        GameEvents.endOfRound(state, rng, true); // Nemesis is a Boss blind (Gift Card's sell-value bump rides this)
        RunLoopRules.raiseJokerRules(this, Trigger.SHOP_ENTER);
        RunShop.refreshShopStock(this);
        phase = Phase.SHOP;
    }

    /** Reset per-shop tag flags and resolve any held ON_SHOP tags as the shop opens. */
    private void winBlind() {
        // Economy: blind reward + end-of-round bonus (per-hand/discard money + interest) + joker payouts.
        int bonus = endOfRoundMoney();
        int reward = (boss != null) ? boss.reward() : blind.reward;
        // Red stake (+): the Small Blind pays no reward (game.lua:2050, blind.lua:84).
        if (boss == null && blind == BlindType.SMALL && stake.smallBlindNoReward()) reward = 0;
        state.money += reward + bonus;
        state.lastBlindReward = reward; // cash-out breakdown for the end-of-round screen
        state.lastInterest = bonus;     // the non-reward bonus (per-hand/discard money + interest)
        if (boss != null) RunTags.applyBossDefeatTags(this); // Investment Tag pays out after a boss
        RunTags.applyBlueSeals(this); // Blue Seal: held cards create the Planet for this round's last hand
        state.roundsPlayedTotal++;
        GameEvents.endOfRound(state, rng, boss != null);
        applyJokerStickerEffects(); // perishable countdown + rental rent (stakes)
        RunLoopRules.raiseJokerRules(this, Trigger.SHOP_ENTER);
        phase = Phase.SHOP;
        RunShop.refreshShopStock(this);
    }

    /**
     * Buy the shop slot at {@code index}, whatever its type: a Joker is added to the
     * joker row (carrying any rolled edition), a Tarot/Planet is added to your
     * consumables. Returns null on success, else a reason.
     */
    public String buyShopItem(int index) {
        return RunShop.buyShopItem(this, index);
    }

    /** Buy the first offered voucher (the per-ante one). Returns null on success, else a reason. */
    public String buyVoucher() {
        return buyVoucher(0);
    }

    /** Buy the offered voucher at {@code index} (the shop can hold several when a Voucher Tag added
     *  one). Returns null on success, else a reason. */
    public String buyVoucher(int index) {
        return RunShop.buyVoucher(this, index);
    }

    /** Apply the Clearance Sale (25% off) / Liquidation (50% off) shop discount, rounded down.
     *  Coupon Tag makes this shop's initial cards/packs free until the first reroll. */
    int price(int cost) {
        if (couponActive) return 0;
        return (int) (cost * RunShop.shopEconomy(this).priceMultiplier()); // Clearance/Liquidation, derived
    }

    /** Sell the joker at the given slot (shop or during a blind). Returns null on success. */
    public String sellJoker(int index) {
        return RunShop.sellJoker(this, index);
    }

    /** Current reroll cost: a base ($5, or $0 under the D6 Tag), reduced $2 each by Reroll Surplus/Glut
     *  (floor $0), then escalated +$1 for every reroll already done this shop (game.lua's inc_reroll_cost). */
    int rerollCost() {
        int base = d6Active ? 0 : Shop.REROLL_COST; // D6 Tag: $0 base for the shop (still escalates)
        return Math.max(0, base - RunShop.shopEconomy(this).rerollDiscount()) + rerollsThisShop;
    }

    /** Reroll the shop offerings. Returns null on success, else a reason. */
    public String reroll() {
        return RunShop.reroll(this);
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
        return ConsumableApply.use(this, index, targetUids);
    }

    /** Apply a {@link com.balatro.grammar.Modify} property-write at action time — the spine the bespoke
     *  property verbs collapse onto. Hand.SIZE routes to the existing {@code HandSize} command (byte-identical
     *  to the old {@code AdjustHandSize}); other properties are added here as their verbs are folded in. */
    void applyWrite(com.balatro.grammar.Modify m, com.balatro.engine.joker.EvaluationContext ctx) {
        double v = com.balatro.engine.eval.ValueResolver.resolve(m.value(), ctx);
        if (m.variable() == com.balatro.grammar.Hand.SIZE && m.op() == com.balatro.grammar.Effect.Operation.ADD) {
            apply(new com.balatro.engine.exec.Command.HandSize((int) Math.round(v)));   // Juggle / Ectoplasm / Ouija
            return;
        }
        if (m.variable() == com.balatro.grammar.Value.Var.MONEY) {                       // Tooth/Ox/Rental/Immolate/tags
            apply(new com.balatro.engine.exec.Command.Money(m.op(), v));
            return;
        }
        if (m.variable() == com.balatro.grammar.Value.Var.BLIND_SURVIVED) {              // Mr Bones: save the lost blind
            state.blindSurvived = v != 0;
            return;
        }
        throw new IllegalStateException("unsupported action Write: " + m);
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
    /** The locale the client wants its server-rendered text in (set per session); "en" by default. */
    public String viewLocale = "en";

    /** The safe projection of authoritative state the client may render (spec §8) — built by {@link RunView}. */
    public ClientView view() {
        return RunView.build(this);
    }

    /** Perkeo: leaving the shop, a SHOP_EXIT joker rule duplicates a random held consumable (data). */
    private void applyShopExit() {
        RunLoopRules.raiseJokerRules(this, Trigger.SHOP_EXIT);
    }

    /** Fire a just-sold joker's own SELL_SELF rules (Luchador disables the boss, Diet Cola makes a tag). */
    /** A base run-loop {@link com.balatro.engine.joker.EvaluationContext}: the run + its RNG, nothing more.
     *  Every run-loop dispatcher (boss/joker/tag effects) starts from this and adds what it needs. */
    com.balatro.engine.joker.EvaluationContext runLoopContext() {
        com.balatro.engine.joker.EvaluationContext ctx = new com.balatro.engine.joker.EvaluationContext();
        ctx.run = state;
        ctx.rng = rng;
        return ctx;
    }

    /** Open a shop pack: pay, remove it, reveal its contents (from the Pack queues + Soul queue). */
    public String openPack(int index) {
        return RunShop.openPack(this, index);
    }

    /** Take one revealed card from the open pack into your inventory/deck. */
    public String pickPackItem(int index) {
        return RunShop.pickPackItem(this, index);
    }

    /** Skip the rest of the open pack (counts as a skip for Red Card). */
    public String skipPack() {
        return RunShop.skipPack(this);
    }

    /** Skip the current Small/Big blind (forfeit its reward, bypass the shop). Returns null on success. */
    public String skipBlind() {
        if (phase != Phase.BLIND_SELECT && phase != Phase.BLIND_ACTIVE) return "not at a blind";
        if (blind == BlindType.BOSS || pvpActive) return "cannot skip this blind";
        state.blindsSkipped++;
        GameEvents.raise(Trigger.SKIP_BLIND, state, rng, null); // Throwback / skip-tag jokers
        RunTags.grantTag(this, state.offeredTag != null ? state.offeredTag : "tag_investment"); // claim the offered tag
        blind = (blind == BlindType.SMALL) ? BlindType.BIG : BlindType.BOSS;
        startBlind();
        return null;
    }

    /** Leave the (stubbed) shop and advance to the next blind / ante / win. */
    public void proceed() {
        if (phase != Phase.SHOP) return;
        RunShop.applyShopExit(this); // Perkeo duplicates a held consumable on the way out
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
