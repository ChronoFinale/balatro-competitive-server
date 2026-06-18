# 01 — Work Breakdown (implementation packages)

Discrete, independently-assignable work packages for implementation agents,
grouped into the milestones of `00-MASTER-DESIGN.md` §6. Each package lists: scope,
primary files to touch (real paths under `D:/NewServer/src`), dependencies,
acceptance criteria (incl. tests), and rough size (S/M/L).

Conventions:
- Paths are real and current unless prefixed **NEW** (file to be created).
- Source root: `src/main/java/com/balatro/engine/`; tests: `src/test/java/com/balatro/engine/`.
- "AC" = acceptance criteria. Every package must keep the existing suite green
  (`./gradlew test`) in addition to its own new tests.
- Build with `./gradlew test`; play-verify with `./gradlew run` / `play`.

Dependency IDs reference other packages by `id`.

---

## Milestone M0 — Scoring-pipeline correctness & ruleset-driven core

### WP-M0-1 — `Score` big-number type
- **Scope.** Introduce a big-number score (port of BMP `InsaneInt`, or `BigDecimal`→
  floor→`BigInteger` wrapper) with `floor(chips*mult)`, `compareTo`, `fromString`,
  `toString`. Replace the `double` score in scoring results and the `long`
  roundScore in PvP. (`30` G8, `34` §4.3.)
- **Primary files.** **NEW** `scoring/Score.java`; `scoring/ScoreResult.java`;
  `scoring/ScoringEngine.java`; `game/Match.java` (roundScore compare).
- **Dependencies.** none.
- **AC.** Unit tests: `floor` semantics, overflow beyond `double` range, ordered
  compare, round-trip `toString`/`fromString`. `ScoreResult.score` is `Score`.
  Existing scoring tests adapted and green.
- **Size.** M.

### WP-M0-2 — `JokerEffect` field model: `xChips`, `extra`-chain, `swap`/`balance`
- **Scope.** Add `xChips` (boxed Double) and a nested `extra` (`JokerEffect`) plus
  `swap`/`balance` flags; an `applyEffect` that recurses `extra` in the SMODS order
  `chips → mult → xchips → xmult` (dollars/messages after). (`30` G4.)
- **Primary files.** `joker/JokerEffect.java`; `scoring/ScoringEngine.java` (the
  `apply` method, currently `chips→mult→dollars→hMult→xMult`).
- **Dependencies.** none.
- **AC.** Unit test proving per-source order `chips→mult→xchips→xmult`; `extra`
  recursion (`{mult, extra:{x_mult}}` applies +mult before ×mult); `xChips` multiplies
  chips; `swap`/`balance` behave per `30` §1c.
- **Size.** M.

### WP-M0-3 — New scoring Triggers + phase seams
- **Scope.** Extend `Trigger` with `MODIFY_SCORING_HAND`, `INITIAL_SCORING_STEP`,
  `FINAL_SCORING_STEP`, `DEBUFFED_HAND`, `DESTROYING_CARD`, `REMOVE_PLAYING_CARDS`.
  Add the matching no-op phase calls in the pipeline skeleton (`30` §4.1). (`30`
  G1/G3/G10.)
- **Primary files.** `joker/Trigger.java`; `scoring/ScoringEngine.java`;
  `joker/EvaluationContext.java` (carry `removedCards`).
- **Dependencies.** none.
- **AC.** Pipeline calls each phase in the documented order; a probe joker firing on
  each new trigger is observed exactly once at the right point (test). Triggers with
  no listeners are no-ops.
- **Size.** S.

### WP-M0-4 — `ScoringSetBuilder` (`modify_scoring_hand`) + Splash
- **Scope.** Build the scoring set via a `modify_scoring_hand` pass (Splash adds all
  played cards; `always_scores`/Stone always; joker add/remove) instead of the
  current `HandEvaluator`+Stone-only set. (`30` G1.)
- **Primary files.** **NEW** `scoring/ScoringSetBuilder.java`;
  `scoring/ScoringEngine.java`; `hand/HandResult.java` (expose detected set).
- **Dependencies.** WP-M0-3.
- **AC.** Splash makes all played cards score (test); add/remove flags honored;
  played-order preserved; Stone always included.
- **Size.** M.

### WP-M0-5 — `Blind` scoring participant (`debuff_hand` / `modify_hand` / `final_scoring_step`)
- **Scope.** A `Blind` interface with `debuffHand(scoring, ctx)→boolean` (Not-Allowed
  veto → 0×0, fire `DEBUFFED_HAND`), `modifyHand(acc, ctx)` (The Flint halve, The Arm
  delevel, The Ox/Tooth money), `finalScoringStep`. Wire into the pipeline between
  base and per-card scoring. (`30` G2/G10; effect catalogue in `21`.)
- **Primary files.** **NEW** `scoring/Blind.java` (or `game/BossBlind.java` extension);
  `game/BossBlind.java`; `game/BossCatalog.java`; `scoring/ScoringEngine.java`
  (add a blind to the play context).
- **Dependencies.** WP-M0-2, WP-M0-3.
- **AC.** Debuffing boss zeros the hand and fires `DEBUFFED_HAND` (Matador test);
  The Flint halves base chips+mult; The Arm delevels played hand by 1 (min 1). Per
  `21` blind.lua references.
- **Size.** L.

### WP-M0-6 — `PlayContext` + per-card intrinsic order + retrigger area-awareness
- **Scope.** Replace `score(played, held, run, rng)` with a `PlayContext` record
  (`played, held, run, ruleset, blind, queues`). Fix per-card intrinsic order
  (`30` §4.3) and make `collectReps` area-aware (PLAY vs HAND), reading Red-seal/Echo
  rep counts from the ruleset, each rep re-running intrinsic + per-card joker pass.
  (`30` G4/G6.)
- **Primary files.** **NEW** `scoring/PlayContext.java`; `scoring/ScoringEngine.java`
  (`score`, `applyCardScored`, `applyCardHeld`, `retriggers`); `intent/IntentHandler.java`
  (call site).
- **Dependencies.** WP-M0-2, WP-M0-5.
- **AC.** Played-card order `chips→mult→xmult→dollars→edition`; held `h_mult→h_xmult`;
  retrigger re-applies both intrinsic and per-card jokers; reps distinguish PLAY/HAND
  (test with Mime held vs Hack played).
- **Size.** L.

### WP-M0-7 — Joker-edition wrap + destruction pass
- **Scope.** In the main joker pass, apply each joker's Foil/Holo (+chips/+mult) before
  its `JOKER_MAIN` and Polychrome (×mult) after; run `ON_OTHER_JOKER` between. Move Glass
  break + all destruction into a single post-scoring `DESTROYING_CARD` /
  `REMOVE_PLAYING_CARDS` pass (a card destroyed at most once regardless of retriggers).
  (`30` G3/G5.)
- **Primary files.** `scoring/ScoringEngine.java`; **NEW** `scoring/DestructionPass.java`;
  `joker/EvaluationContext.java`.
- **Dependencies.** WP-M0-6.
- **AC.** Joker Foil/Holo applies pre-main, Poly post-main (ordering test); a
  retriggered Glass card is destroyed once (test); destruction emits `REMOVE_PLAYING_CARDS`.
- **Size.** M.

### WP-M0-8 — `Ruleset`-driven scoring values
- **Scope.** Introduce a scoring `Ruleset` view holding all magic numbers (redSealReps,
  lucky odds, glassXmult/breakOdds, steelXmult, bonus/stone chips, edition values,
  goldSeal $, per-joker overrides) injected per match; pipeline reads `ruleset.*`
  instead of literals. (`30` G9.) NB: distinct from the *content* `state/Ruleset.java`;
  this is the tuning surface (may live on it).
- **Primary files.** `state/Ruleset.java`; **NEW** `scoring/ScoringRuleset.java` (or
  fields on `Ruleset`); `scoring/ScoringEngine.java`; `state/RulesetCatalog.java`.
- **Dependencies.** WP-M0-6.
- **AC.** No scoring literals remain in `ScoringEngine`/`applyCardScored`; a test
  ruleset with altered glassXmult/redSealReps changes scoring; `HandType` Straight/
  Straight Flush/Flush House/Flush Five increments corrected to BMP-release values
  (`15`).
- **Size.** M.

---

## Milestone M1 — Queue topology & shop/pack/voucher generation

### WP-M1-1 — `QueueKeys` registry + ante-independence discipline
- **Scope.** Central constants + javadoc registry of every queue key (`31` §4.1),
  each cited to its BMP source. Ensure no ante is baked into keys.
- **Primary files.** **NEW** `rng/QueueKeys.java`; `rng/QueueSet.java` (namespacing).
- **Dependencies.** none.
- **AC.** All queue keys referenced through `QueueKeys`; test asserts determinism
  (same seed → identical sequence per key) and independence (consuming key A doesn't
  perturb key B).
- **Size.** S.

### WP-M1-2 — `Pool` + `UNAVAILABLE` block/skip pools
- **Scope.** Ruleset-supplied ordered, **culled** candidate lists (analog of
  `get_current_pool`): stable sort, `UNAVAILABLE` marker for owned/soft-locked/banned/
  gated entries, recomputed per draw from current ownership; drive `GameQueue.nextWhere`.
  (`31` §4.2.)
- **Primary files.** **NEW** `rng/Pool.java`, **NEW** `rng/PoolEntry.java`;
  `rng/GameQueue.java` (confirm `nextWhere`).
- **Dependencies.** WP-M1-1.
- **AC.** `nextWhere` skips `UNAVAILABLE` advancing the *same* stream; pool recompute
  between draws reflects ownership without resetting the cursor (test).
- **Size.** M.

### WP-M1-3 — Rarity-split joker queues + `RarityRoller` + `cdt` category stream
- **Scope.** Replace the uniform `jokers` queue with `joker.common/uncommon/rare/legendary`
  + a `joker.rarity` stream (`bucketize`: >.95 Rare, >.7 Uncommon else Common) + the
  `shop.category` (`cdt`) rate-walk in fixed order. (`31` §1.3/§4.3.)
- **Primary files.** **NEW** `rng/RarityRoller.java`; `rng/QueueSet.java`;
  `game/Shop.java`.
- **Dependencies.** WP-M1-2.
- **AC.** Category + rarity reproducible across players on a seed; rate-0 categories
  never selected; reroll advances same streams (both players further along same list).
- **Size.** M.

### WP-M1-4 — `ShopGenerator` (per-slot pipeline + reroll)
- **Scope.** Owns the per-slot pipeline (`31` §4.3): category → (rarity → rarity pool) |
  uptop consumable | playing card. Reroll = next N slots. Free-reroll grant hook.
- **Primary files.** **NEW** `rng/ShopGenerator.java`; `game/Shop.java`;
  `game/Run.java` (reroll path).
- **Dependencies.** WP-M1-3.
- **AC.** Two players on a seed get identical shop slots and identical reroll batches
  (test); rerolling more just advances further; reroll cost from ruleset.
- **Size.** M.

### WP-M1-5 — Up-Top vs Pack consumable queues + `PackQueue` + `PackOpener` + Soul insertion
- **Scope.** Six consumable streams (`tarot/planet/spectral` × `uptop`/`pack`); a
  `packs` booster-type queue **advanced on shop view** (skip-offset, first-shop Buffoon
  pin); a `PackOpener` fill loop advancing `soul.c_soul`/`soul.c_black_hole` per produced
  card and **inserting on hit** (push-back-one, Soul-beats-Black-Hole). (`31` §1.4–§1.6/§4.4–§4.5; pack data in `19`.)
- **Primary files.** **NEW** `rng/PackQueue.java`, **NEW** `rng/PackOpener.java`;
  `rng/QueueSet.java`; `game/Shop.java`.
- **Dependencies.** WP-M1-3.
- **AC.** Packs queue advances only on shop reveal (skip shifts packs a blind later,
  test); soul hit inserts and defers the consumable; both players see soul at the same
  index; mutual exclusion holds.
- **Size.** L.

### WP-M1-6 — `VoucherQueue` (1–16 culled pairs)
- **Scope.** Single `voucher` stream over culled Tier-1/Tier-2 pairs; tier resolution
  (T1 unowned → T1, else T2, else skip); voucher-tag advance; back-to-back duplicate
  skip; resample-same-stream on UNAVAILABLE. (`31` §1.7/§4.6; voucher data in `17`.)
- **Primary files.** **NEW** `rng/VoucherQueue.java`; `rng/QueueSet.java`.
- **Dependencies.** WP-M1-2.
- **AC.** 16-family queue resolves T1/T2 by ownership; banned vouchers substituted via
  next-key; voucher tag advances identically to an ante reveal; dup-skip works (tests).
- **Size.** M.

### WP-M1-7 — `DeterministicSelector` + `shuffleByValue`
- **Scope.** Sort-then-pick utility (canonical sort + weighted/positional pick) for
  Idol/Mail/To-Do/Invisible, plus `RandomStreams.shuffleByValue` (per-card stream value,
  sort) so picks are insensitive to internal list order. (`31` §1.10/§4.9.)
- **Primary files.** **NEW** `rng/DeterministicSelector.java`; `rng/RandomStreams.java`.
- **Dependencies.** WP-M1-1.
- **AC.** Element pick identical regardless of input list order (test with permuted
  inputs); weighted/positional variants covered.
- **Size.** M.

---

## Milestone M2 — Run lifecycle, gamemodes & Attrition end-to-end

### WP-M2-1 — `BlindSlot`/`BlindState` + `BLIND_SELECT`/`ROUND_EVAL` phases
- **Scope.** Per-blind state (`UPCOMING/SELECT/CURRENT/SELECTED/DEFEATED/SKIPPED/HIDDEN`),
  a `BlindBoard` of 3 slots/ante replacing the scalar blind, and explicit `BLIND_SELECT`
  + `ROUND_EVAL` phases + transitions. (`33` §4.1.)
- **Primary files.** `game/Run.java`; **NEW** `game/BlindSlot.java`, **NEW**
  `game/BlindBoard.java`; `net/ClientView.java` (expose blindStates/choices).
- **Dependencies.** WP-M0-5.
- **AC.** Run enters BLIND_SELECT each ante with 3 slots; select→CURRENT→play;
  state machine test covers SMALL→BIG→BOSS→ante++.
- **Size.** L.

### WP-M2-2 — Skip + tag plumbing + `skips` counter
- **Scope.** `SkipBlind` intent (legal only SMALL/BIG, non-PvP, not banned); run-long
  tag inventory; a deterministic `tag` queue in `QueueSet` (ban-gated); `skips` counter
  on `RunState`. Tag *effects* deferred to M4; this is the slot/inventory/queue plumbing.
  (`33` §4.2; tag catalogue/queues in `20`.)
- **Primary files.** `intent/Intent.java`, `intent/IntentHandler.java`; `game/Run.java`;
  `state/RunState.java`; **NEW** `game/TagInstance.java`; `rng/QueueSet.java`.
- **Dependencies.** WP-M2-1, WP-M1-2.
- **AC.** Skipping a Small/Big claims its tag and advances to next slot; Boss un-skippable;
  banned tag rejected; `skips` increments (tests).
- **Size.** M.

### WP-M2-3 — `Gamemode` abstraction + `BlindSchedule` + `LobbyConfig`
- **Scope.** `Gamemode` interface + Attrition/Showdown/Survival impls supplying
  `startingLives`, `blindsForAnte(ante,cfg)` (`get_blinds_by_ante` ports), `endless`,
  bans; a `GamemodeRegistry`; a `LobbyConfig` record with BMP defaults
  (starting_lives=4, pvp_start_round=2, showdown_starting_antes=3, normal_bosses, timers…).
  (`33` §4.3, `34` §4.1.)
- **Primary files.** **NEW** `game/Gamemode.java`, **NEW** `game/AttritionMode.java`,
  **NEW** `game/ShowdownMode.java`, **NEW** `game/SurvivalMode.java`, **NEW**
  `game/GamemodeRegistry.java`, **NEW** `game/LobbyConfig.java`; `game/Match.java`
  (select mode instead of constants).
- **Dependencies.** WP-M2-1.
- **AC.** Attrition: boss=Nemesis from ante≥pvpStart; Showdown: all-3 PvP from
  ante≥showdownStart; Survival: always normal, endless, 1 life (tests on schedules).
- **Size.** L.

### WP-M2-4 — `PvpBlind` (Nemesis) type + `BanSet` application
- **Scope.** `bl_mp_nemesis`: reward 5, no chip target (target = opponent score),
  `inPool=false`, `isPvpBoss()` predicate. A resolved `BanSet` (six categories) gating
  boss/tag/voucher/shop pools (`bl_wall`/`bl_final_vessel` banned in PvP; `tag_boss`
  banned). (`34` §4.2, `33` §4.6; bans in `17`/`20`/`21`.)
- **Primary files.** **NEW** `game/PvpBlind.java` (or `pvp` flag on `game/BossBlind.java`);
  **NEW** `state/BanSet.java`; `game/BossCatalog.java`; `state/Ruleset.java` (banned* fields).
- **Dependencies.** WP-M2-3, WP-M1-3, WP-M1-6.
- **AC.** Nemesis never appears in natural boss pool; `isPvpBoss()` true on it; banned
  boss/voucher/tag keys excluded from their pools (tests).
- **Size.** M.

### WP-M2-5 — `OpponentView` nemesis snapshot + PvP-loss context
- **Scope.** `OpponentView` carrying the exact `MP.GAME.enemy` field set (score,
  highestScore, handsLeft, skips, lives, sells, sellsPerAnte, spentInShop[], location),
  refreshed every action; self-side PvP scalars on `RunState` (pincherIndex=-3,
  pincherUnlock, lives, pvpReached, comebackBonus); `mpPvpLoss`/`mpHandsLeft` context
  on a timer loss. (`34` §4.5/§1.7.)
- **Primary files.** **NEW** `game/OpponentView.java`; `state/RunState.java`;
  `joker/EvaluationContext.java`; `game/Match.java` (`opponentSummary` → populate
  OpponentView).
- **Dependencies.** WP-M2-4.
- **AC.** Opponent snapshot exposes all listed fields and refreshes after each action
  (test); `mp_pvp_loss`/`mp_hands_left` fired only on timer loss with handsLeft>0.
- **Size.** M.

### WP-M2-6 — Server-exact PvP resolution + lives + Comeback Gold + ante/endless
- **Scope.** Align `resolveNemesisIfDecided` to the server predicate (`(opp.handsLeft<1
  && opp.score<me) || (me.handsLeft<1 && me.score<opp) || both<1`; tie = no life lost);
  lives from gamemode/lobby; Comeback Gold on **any** life loss; ante progression with
  endless-continue. PvP timer + `failPvpTimer` life-loss path. Survival furthest-blind
  resolver. (`34` §4.4/§4.6/§4.7, `33` §4.4/§4.5.)
- **Primary files.** `game/Match.java`; `game/Run.java` (proceed/endless);
  **NEW** `game/WinResolver.java`; **NEW** `game/PvpTimer.java`; `game/GameEvents.java`
  (comeback gold hook).
- **Dependencies.** WP-M2-5, WP-M0-1.
- **AC.** Tie costs no life; out-of-hands-and-behind loses immediately; 0 lives →
  win/lose; timer expiry → life loss; Survival decides by furthest blind with both-at-0
  draw; Comeback Gold awarded on every life loss (tests; extend `AttritionTest`/`MatchTest`).
- **Size.** L.

---

## Milestone M3 — JokerDef algebra v2 (building blocks)

> Land in leverage order. Each WP adds sealed-interface variants + interpreter
> support in `DataJoker` + `BuilderSchema` exposure + the schema endpoint, and a
> jqwik/AssertJ test proving the representative jokers it unlocks. Primary files
> common to most: `joker/def/DataJoker.java`, `joker/def/BuilderSchema.java`,
> `joker/def/JokerDef.java`, `joker/Trigger.java`, plus the specific variant file.

### WP-M3-1 — Tier-1 value sources: `Stat` aggregates + RunState counters
- **Scope.** `Value.Stat(which,...)` for deck/run aggregates (DECK_SIZE, DECK_ENH_COUNT,
  DECK_RANK_COUNT, STARTING_DECK_DELTA, ENHANCED_CARD_COUNT, OWNED_JOKERS,
  OWNED_JOKERS_SELL_SUM, EMPTY_JOKER_SLOTS, HAND_TYPE_PLAYED(+thisRound),
  CONSUMABLE_USED, UNIQUE_PLANETS_USED, CARDS_SOLD_THIS_RUN) + the supporting RunState
  fields/hooks (`40` block 2 + 17). (~17 jokers.)
- **Primary files.** `joker/def/Value.java`; `state/RunState.java`; `state/Deck.java`;
  `game/GameEvents.java` (counter hooks); `joker/def/DataJoker.java`.
- **Dependencies.** WP-M0-6.
- **AC.** Blue Joker (DECK_REMAINING), Steel/Stone Joker (DECK_ENH_COUNT), Abstract
  (OWNED_JOKERS), Driver's License (ENHANCED≥16) score correctly (tests); counters
  maintained by lifecycle hooks; `OWNED_JOKERS_SELL_SUM` excludes self.
- **Size.** L.

### WP-M3-2 — Tier-1 `CREATE` effect-type (queue-backed)
- **Scope.** `Create(what, spec, count, sourceKey)` over PLAYING_CARD/TAROT/PLANET/
  SPECTRAL/JOKER/TAG, drawing from named `QueueSet` queues, slot-gated server-side;
  "copy of card X" RNG-free form. (`40` block 1; ~38 items.)
- **Primary files.** **NEW** `joker/def/effect/CreateEffect.java` (or variant in
  `EffectTemplate.java`); `joker/def/DataJoker.java`; `rng/QueueSet.java`; `game/Run.java`
  (apply creation to run state).
- **Dependencies.** WP-M1-5 (uptop/pack/soul queues), WP-M1-3 (joker queues).
- **AC.** Riff-raff creates 2 Commons; Emperor creates 2 Tarots; DNA copies the played
  card; slot checks block creation when full; creation deterministic across players (tests).
- **Size.** L.

### WP-M3-3 — Tier-1 `DESTROY` effect-type
- **Scope.** `Destroy(target, selector, condition)` for SELF/OTHER_JOKER/PLAYING_CARD
  with selectors + at-zero `when`; honors eternal/phantom; emits CARDS_REMOVED. (`40`
  block 3; ~15 items.)
- **Primary files.** **NEW** `joker/def/effect/DestroyEffect.java`; `joker/def/DataJoker.java`;
  `scoring/DestructionPass.java`; `game/Run.java`.
- **Dependencies.** WP-M0-7, WP-M3-2 (shared selector infra).
- **AC.** Ice Cream self-destroys at 0; Hex destroys all other non-eternal jokers;
  Hanged Man destroys selected hand cards emitting CARDS_REMOVED (tests).
- **Size.** M.

### WP-M3-4 — Tier-1 `Chance` condition + `Random` value
- **Scope.** `Chance(num,den,seedKey)` gate (scaled by `probabilityNumerator`) and
  `Random(min,max,seedKey)` magnitude, both via `RandomStreams`/`QueueSet`. (`40` block 4;
  ~13 items.)
- **Primary files.** `joker/def/Condition.java`; `joker/def/Value.java`;
  `joker/def/DataJoker.java`; `rng/RandomStreams.java`.
- **Dependencies.** WP-M0-6.
- **AC.** Business Card 1/2, 8 Ball 1/4, Misprint 0–23 reproduce identically per seed;
  Oops! All 6s halves thresholds without disturbing sequence (tests).
- **Size.** M.

### WP-M3-5 — Tier-1 `Nemesis` reads + PvP conditions
- **Scope.** `Value.Nemesis(which,...)` (NEMESIS_LIVES/HANDS_LEFT/SKIPS/SELLS_SINCE_PVP/
  SHOP_SPEND + own LIVES/SKIPS) reading `OpponentView`; `[C] InPvPBlind`, `BlindKeyIs`.
  (`40` blocks 5,6; 6 + 5 jokers.)
- **Primary files.** `joker/def/Value.java`; `joker/def/Condition.java`;
  `joker/EvaluationContext.java`; `joker/def/DataJoker.java`.
- **Dependencies.** WP-M2-5.
- **AC.** Defensive Joker chip formula, Conjoined (only in PvP), Pacifist (only not in
  PvP) evaluate against the snapshot (tests).
- **Size.** M.

### WP-M3-6 — Tier-2 effect-types: resource modifiers, level-up, copy-joker, mutate-card
- **Scope.** `ModifyResource` (hand-size/hands/discards/debt/interest/slots, +grantNemesis),
  `LevelUpHand(target)` + `IsMostPlayedHand`, `CopyJoker(selector)` (reuse `forCopy`),
  `MutateCard(target,change)` (enhancement/seal/edition/suit/rank/perma-chip) +
  `DestroyedCardWas`/`HeldCardsAllSuit`. (`40` blocks 7,10,11,12; ~13+7+4+25 items.)
- **Primary files.** **NEW** `joker/def/effect/{ResourceEffect,LevelUpEffect,CopyJokerEffect,MutateCardEffect}.java`;
  `joker/def/DataJoker.java`; `joker/def/Condition.java`; `state/RunState.java`;
  `card/Card.java`; `game/Run.java`.
- **Dependencies.** WP-M3-1, WP-M3-2, WP-M3-3.
- **AC.** Blueprint copies right neighbor (depth-guarded); Space levels played hand;
  Vampire removes enhancement; Midas Mask sets Gold; Juggler +hand size (tests).
- **Size.** L.

### WP-M3-7 — Tier-2 value combinators + `ADD_CLAMP` + set/positional conditions + round-seeded targets
- **Scope.** `Value.Clamp`/`FloorDiv`; `Mutation.ADD_CLAMP`; `SET_RANDOM(domain,seedKey)`
  + `ScoredMatchesStateTarget`; set-membership/positional conditions (`ScoredRankIn`,
  `ScoredPositionIs`/`FirstScoredMatching`, `HandContainsAnyOf`, `ScoringContainsAllSuits`,
  `ScoringContainsClubPlusOther`). (`40` blocks 8,9,14,15; ~24 items.)
- **Primary files.** `joker/def/Value.java`; `joker/def/Mutation.java`;
  `joker/def/Condition.java`; `joker/def/DataJoker.java`.
- **Dependencies.** WP-M3-1.
- **AC.** Bootstraps floor(MONEY/5)×2; Conjoined clamp 1..3; Green Joker −1 floor 0;
  Fibonacci rank-set; Photograph first-scored-face; Castle round-suit synced across
  players (tests).
- **Size.** L.

### WP-M3-8 — Tier-3: lifecycle triggers, economy ops, global modifiers, edition-on-create + NATIVE carve-outs
- **Scope.** Triggers `SKIP_BOOSTER`/`END_OF_PVP`/`MP_SPEEDRUN`/`BOSS_DEFEATED`/`CARDS_REMOVED`;
  `Economy` ops; `GlobalModifier` flags (Four Fingers/Shortcut/Smeared/Pareidolia/Splash/
  Showman/Astronomer/DisableBoss/PreventDeath); `StakeAtLeast` + edition-on-create. Define
  the **NATIVE joker interface** for the 7 carve-outs (hand-eval changes, Order RNG shims,
  soul spawn, Invisible positioning, Blueprint engine, boss-disable, phantom networking).
  (`40` blocks 13,18,19,20 + Native section.)
- **Primary files.** `joker/Trigger.java`; `joker/def/Condition.java`;
  **NEW** `joker/def/effect/{EconomyEffect,GlobalModifierEffect}.java`; `joker/def/DataJoker.java`;
  **NEW** `joker/NativeJoker.java`; `hand/HandEvaluator.java` (rule flags); `game/GameEvents.java`.
- **Dependencies.** WP-M3-6, WP-M2-6 (END_OF_PVP/BOSS_DEFEATED hooks).
- **AC.** Red Card on SKIP_BOOSTER; Four Fingers flips 4-card flush detection;
  Mr. Bones prevents death once; Chicot disables boss; a NATIVE joker registered by key
  evaluates without being data-defined (tests).
- **Size.** L.

---

## Milestone M4 — Content transcription

> Each WP transcribes a catalogue into data using the M3 algebra, with a per-item
> parity test (the detail doc supplies exact numbers/keys/RNG seeds). These are
> parallelizable once their subsystem exists.

### WP-M4-1 — Consumable subsystem (Tarot/Planet/Spectral + Black Hole)
- **Scope.** A `Consumable` type + `ConsumableType{TAROT,PLANET,SPECTRAL}`, a use-intent
  carrying selected card ids with server-side `can_use` validation, the Tarot operation
  classes (`14`), Planet→HandType leveling + secret-planet softlock (`15`), and the
  Spectral catalogue incl. Black Hole hand-level hook (`16`).
- **Primary files.** **NEW** `consumable/Consumable.java`, **NEW** `consumable/ConsumableType.java`,
  **NEW** `consumable/TarotCatalog.java`, **NEW** `consumable/SpectralCatalog.java`;
  `game/PlanetCatalog.java`; `intent/Intent.java`/`IntentHandler.java`; `state/RunState.java`
  (lastTarotOrPlanetUsed, ecto_minus).
- **Dependencies.** WP-M3-2, WP-M3-6, WP-M1-5.
- **AC.** Each Tarot/Planet/Spectral matches its detail-doc effect (per-item tests);
  min/max highlight enforced; Fool copies last consumable; Black Hole levels all 12 hands.
- **Size.** L.

### WP-M4-2 — Joker pool transcription (159 jokers)
- **Scope.** Author all Common/Uncommon/Rare/Legendary/BMP-exclusive jokers as `JokerDef`
  data (or NATIVE where carved out), with the exact numbers from `10`–`13`. Sub-split by
  rarity for parallel assignment.
- **Primary files.** `joker/JokerLibrary.java`; `web-assets/` joker defs (or built-in
  registrations); `joker/NativeJoker.java` (carve-outs).
- **Dependencies.** WP-M3-* (all algebra blocks), WP-M2-5 (MP jokers).
- **AC.** Each joker reproduces its detail-doc effect (golden tests grouped by rarity);
  ranked-banned vanilla keys replaced by MP keys where `10`/`13` specify
  (`j_mp_hanging_chad`, `j_mp_ticket`); MP-exclusive jokers read OpponentView correctly.
- **Size.** L (split into 4–5 sub-packages by rarity).

### WP-M4-3 — Vouchers (32) + effect resolver + tier gating
- **Scope.** Voucher registry (32, T1/T2, `requires`), effect resolver mapping each key
  to its run-state mutation (additive vs replacement), per-gamemode ban set. (`17`.)
- **Primary files.** **NEW** `state/VoucherCatalog.java`, **NEW** `state/Voucher.java`;
  `state/RunState.java`; `rng/VoucherQueue.java`.
- **Dependencies.** WP-M1-6, WP-M2-4 (bans).
- **AC.** Each voucher mutates the right run param; Tier-2 redeemable only after Tier-1;
  4 ranked-banned vouchers excluded; additive vs replacement semantics correct (tests).
- **Size.** M.

### WP-M4-4 — Decks (23) + starting-params + apply-hooks
- **Scope.** `StartingParams` model + deck "apply hook" interface (onRunStart/onShopEnd/
  onBossDefeated/onBoosterOpen/rank-shift/retrigger/dollar-cap/voucher-cost) for the 15
  vanilla + 8 active BMP decks. (`18`.)
- **Primary files.** **NEW** `state/Deck`+`state/DeckCatalog.java` (extend existing
  `state/Deck.java`); `state/RunState.java`; `game/Run.java` (hook calls).
- **Dependencies.** WP-M4-1 (starting consumables), WP-M4-3 (starting vouchers).
- **AC.** Each deck's starting config + hooks behave per detail doc (tests: Echo
  retrigger-all, Plasma ×2 blind, Anaglyph Double Tag on boss defeat, Orange Giga pack).
- **Size.** L.

### WP-M4-5 — Skip tags (24) + tag effects
- **Scope.** Tag trigger-context model (immediate/new_blind_choice/eval/store_joker_*/
  voucher_add/tag_add/round_start_bonus/shop_start/shop_final_pass), per-tag effects,
  separate rarity queues (uta/rta/common) + cross-source shared cursors, pack-tag→pack-queue
  routing, run-long counters, Double-Tag copy. (`20`.)
- **Primary files.** **NEW** `game/TagCatalog.java`; `game/TagInstance.java`;
  `rng/QueueSet.java` (rarity/common queues); `game/Run.java`.
- **Dependencies.** WP-M2-2, WP-M1-3, WP-M1-5.
- **AC.** Each tag fires at its context with correct effect; Rare-tag and Wraith advance
  the same `rta` cursor; Investment $15 (BMP delta); Boss tag banned in PvP (tests).
- **Size.** L.

### WP-M4-6 — Boss blinds (28) + per-blind effect enum + RNG keys
- **Scope.** `BossBlind` model with typed effect enum mapping to the blind.lua hook
  (PRESS_PLAY/MODIFY_HAND/DEBUFF_HAND/DRAWN_TO_HAND/STAY_FLIPPED/DEBUFF_CARD/SET_BLIND/
  DISABLE/DEFEAT), per-blind RNG keys, finisher selection, gamemode boss-override +
  ban filter. (`21`.)
- **Primary files.** `game/BossBlind.java`; `game/BossCatalog.java`; `scoring/Blind.java`;
  `game/Run.java`.
- **Dependencies.** WP-M0-5, WP-M2-3/WP-M2-4.
- **AC.** Each of the 22 regular + 5 finisher bosses applies its effect (tests:
  Hook discards 2, Flint halves, Eye no-repeat, Crimson Heart rotates); Nemesis force-assigned;
  Wall/Vessel banned in PvP.
- **Size.** L.

---

## Milestone M5 — Ruleset bundles, layers, reworks & curation

### WP-M5-1 — `ContentLayer` + `RulesetResolver` (layered composition)
- **Scope.** `ContentLayer` record + recast `Ruleset` as a layered composition with
  merge semantics (arrays concat / scalars last-layer-wins / ruleset-own beats layer) +
  `active_layer_chain` dedup/order. (`36` §4.1.)
- **Primary files.** **NEW** `state/ContentLayer.java`, **NEW** `state/RulesetResolver.java`;
  `state/Ruleset.java`; `state/RulesetCatalog.java`.
- **Dependencies.** WP-M2-4 (BanSet).
- **AC.** "standard + ranked" composition resolves to the expected merged ban/rework set;
  ruleset-own value beats a layer; dedup prevents double-apply (tests).
- **Size.** M.

### WP-M5-2 — `BanSet` (six categories + silent + dynamic) + `applyBans`
- **Scope.** Six explicit key sets + `silent` + named seed-driven `dynamicBanHooks`
  (registry-gated, no author code), writing a per-run `bannedKeys` consulted by every
  pool draw. (`36` §4.2.)
- **Primary files.** `state/BanSet.java`; **NEW** `state/DynamicBanHookRegistry.java`;
  `rng/Pool.java`; `game/Run.java`.
- **Dependencies.** WP-M5-1, WP-M1-2.
- **AC.** Bans gate shop/pack/tag/voucher draws across all six categories; smallworld-style
  75% cull is deterministic and identical across players from `RandomStreams` (test).
- **Size.** M.

### WP-M5-3 — `Rework` deterministic overlay + `ReworkResolver`
- **Scope.** `Rework` (targetKey, overrides, ruleDeltas, balanced) + `ReworkResolver`
  producing the effective joker per active layer chain; override-able `config` map on
  hand-coded/native jokers (or re-author ranked-pool jokers as defs). (`36` §4.3.)
- **Primary files.** **NEW** `state/Rework.java`, **NEW** `state/ReworkResolver.java`;
  `joker/def/JokerDef.java`; `joker/JokerLibrary.java`.
- **Dependencies.** WP-M5-1, WP-M4-2.
- **AC.** Same `j_to_do_list` is $4 in one ruleset and $5 in another with no forked key
  (test); `balanced` surfaces a reworked flag on `JokerInfo`.
- **Size.** M.

### WP-M5-4 — Forced gamemode + lobby locks + curation + content hash + versioning
- **Scope.** `forcedGamemode` + `LobbyLock` (server-revalidated); `RankedTier` enum +
  `CurationService` (list/propose/promote/diff) + version-pinned `ranked-content/` store;
  `ContentHasher` (canonical-JSON SHA-256 over resolved ruleset+defs+sprite hashes) +
  per-match record `{ruleset, formatVersion, contentHash, seed, modifiers}`; per-tier
  numeric bounds + all-category cross-ref validation. (`36` §4.4–§4.7.)
- **Primary files.** **NEW** `state/LobbyLock.java`, **NEW** `state/RankedTier.java`,
  **NEW** `state/CurationService.java`, **NEW** `state/ContentHasher.java`;
  `state/RulesetStore.java`, `joker/def/CustomJokerStore.java` (validation + provenance).
- **Dependencies.** WP-M5-1, WP-M5-3.
- **AC.** Only CURATED_RANKED rulesets selectable in ranked; two players with matching
  contentHash are guaranteed the same surface; ranked rejects an XMULT beyond the tier cap;
  unknown banned/reworked key rejected pre-match (tests).
- **Size.** L.

---

## Milestone M6 — Networking hardening

### WP-M6-1 — `LobbyManager` + `Lobby`/`Seat` + ready-flow
- **Scope.** Extract lobby ownership from `GameServer`'s ad-hoc maps into a `LobbyManager`
  (code↔lobby, session↔lobby), `Lobby`/`Seat` types, lobby-ready + per-blind ready,
  host-only start requiring guest ready, `startBlind {firstPlayer}` + speedrun 30s window.
  (`35` §4.4.)
- **Primary files.** **NEW** `net/LobbyManager.java`, **NEW** `net/Lobby.java`,
  **NEW** `net/Seat.java`; `net/GameServer.java`; `game/Match.java`.
- **Dependencies.** WP-M2-3.
- **AC.** Two-seat lobby create/join/leave; both-ready → startBlind with firstPlayer;
  speedrun granted to 2nd within 30s (tests; extend `NetworkTest`).
- **Size.** L.

### WP-M6-2 — Liveness (ping/pong) + idle/retry timers
- **Scope.** WS Jetty ping after 15s idle, 5s retry ×4 then close (BMP semantics);
  client answers immediately. (`35` §4.3.)
- **Primary files.** `net/GameServer.java`; **NEW** `net/KeepAlive.java`.
- **Dependencies.** none (can parallel M6-1).
- **AC.** Idle connection pinged at 15s; closed after 4 unanswered 5s retries; inbound
  data refreshes the timer (test with a fake clock).
- **Size.** M.

### WP-M6-3 — Reconnect with full authoritative resync + `MatchLog`
- **Scope.** On disconnect, keep the `Run`/`Match` alive, reserve the seat for a grace
  period, push `enemyDisconnected`; on `rejoinLobby{code,token}` rebind the new session
  and resend the full authoritative `ClientView` + opponent summary; grace expiry →
  authoritative result. Append-only `MatchLog` (powers resync + ghost export). (`35` §4.5.)
- **Primary files.** **NEW** `net/ReconnectManager.java`, **NEW** `net/MatchLog.java`;
  `net/GameServer.java` (don't purge on close); `net/AuthService.java` (token TTL ≥ grace);
  `net/ClientView.java`.
- **Dependencies.** WP-M6-1.
- **AC.** A dropped player rejoins within grace and receives the exact current view (no
  client-held run state needed); grace expiry yields a clean matchResult; MatchLog
  reproduces the match (tests).
- **Size.** L.

### WP-M6-4 — Cross-player MP-joker intents + version/content handshake
- **Scope.** Validated cross-player intents (`SendPhantom`/`RemovePhantom`, `Magnet`,
  derived `eatPizza`/`asteroid`/`letsGoGamblingNemesis`/`soldJoker`/`spentLastShop`,
  `skipBlind`) checked against the sender's authoritative state, then a derived push;
  protocol-version + `contentHash` handshake on auth (hard-reject for ranked). (`35`
  §4.6/§4.7.)
- **Primary files.** `intent/Intent.java`, `intent/IntentHandler.java`; `game/Match.java`;
  `net/GameServer.java`; `joker/EvaluationContext.java` (opponent handle).
- **Dependencies.** WP-M2-5, WP-M6-1, WP-M5-4 (contentHash).
- **AC.** A phantom intent only affects the nemesis if the joker is in the sender's real
  board; ranked rejects mismatched contentHash; no arbitrary `moddedAction` path exists (tests).
- **Size.** M.

---

## Milestone M7 — Matchmaking & compat (optional)

### WP-M7-1 — `RankedQueue` + MMR store
- **Scope.** `enqueue(playerId, mode/ruleset pref)` → MMR-bucketed pairing → synthesize a
  server-side lobby (no code) with a curated ruleset and start; persist results. (`35`
  §4.4.)
- **Primary files.** **NEW** `net/RankedQueue.java`, **NEW** `net/MmrStore.java`;
  `net/LobbyManager.java`.
- **Dependencies.** WP-M6-1, WP-M5-4.
- **AC.** Two enqueued players within an MMR band are paired into a curated-ruleset match
  without a code; result updates ratings (tests).
- **Size.** L.

### WP-M7-2 — BMP-compat TCP gateway (casual, unverified)
- **Scope.** A `BmpCompatGateway` speaking BMP 0.4.0's TCP+`\n`-JSON action vocabulary,
  mapping onto `LobbyManager`/`Match`, **dropping** client-supplied scores (flagged
  unranked/relay-faithful). Bounded line length. (`35` §4.2.)
- **Primary files.** **NEW** `net/BmpCompatGateway.java`; `net/LobbyManager.java`.
- **Dependencies.** WP-M6-1.
- **AC.** A stock-shaped TCP client can create/join a lobby and play casually; scores it
  sends are ignored/recomputed where possible; matches flagged unverified (tests).
- **Size.** L.

### WP-M7-3 — Admin gateway (optional, ops)
- **Scope.** Signature-verified admin TCP surface (`message`, `listLobbies`), mirroring
  BMP's :8789. (`35` §4 / open Q7.)
- **Primary files.** **NEW** `net/AdminGateway.java`.
- **Dependencies.** WP-M6-1.
- **AC.** Signed commands accepted, unsigned rejected; `listLobbies` returns live lobbies (test).
- **Size.** S.

---

## Summary

- **Work packages: 38** across 8 milestones (M0: 8, M1: 7, M2: 6, M3: 8, M4: 6,
  M5: 4, M6: 4, M7: 3 — sub-splits of WP-M4-2 by rarity add parallelizable child tasks).
- Critical path to "Attrition done well" (the README focus): **M0 → M1 → M2**, then
  the highest-leverage M3 blocks (CREATE, Stat, Nemesis) feeding the M4-2 joker pool.
- M5–M7 are governance/scale and can begin once M2 (gamemode + bans) and a content
  slice (M4) exist.
