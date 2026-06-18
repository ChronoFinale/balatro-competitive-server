# 33 — Run / Ante / Blind Lifecycle (server-authoritative, ruleset-driven)

Target parity: **Balatro Multiplayer (BMP) 0.4.0** (the "Ranked Update", on disk).
Scope: the run/ante/blind state machine — Small/Big/Boss selection, blind skipping + tags,
shop entry/exit, ante progression, win/loss, and endless — plus the multiplayer overlays
(Nemesis/PvP blinds, lives, gamemodes). Every claim is cited to a real source on disk.

Convention used below: `dump/game.lua` = `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua`;
`BMP/` = `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/`;
`SERVER/` = `D:/BalatroMultiplayerAPI-Server-main/`; `OURS/` = `D:/NewServer/src/main/java/com/balatro/engine/`.

---

## 1. How the REAL game / BMP 0.4.0 does it (grounded)

### 1.1 Base-game round-state container (`round_resets`)

The vanilla per-ante state lives in `G.GAME.round_resets`, initialized in `Game:init_game_object`
(`dump/game.lua:2000-2013`):

```lua
round_resets = {
    hands = 1, discards = 1, reroll_cost = 1, free_rerolls = 0,
    ante = 1, blind_ante = 1,
    blind_states  = {Small = 'Select', Big = 'Upcoming', Boss = 'Upcoming'},
    blind_choices = {Small = 'bl_small', Big = 'bl_big'},   -- Boss filled later
    boss_rerolled = false,
}
shop = { joker_max = 2 }
```

Key facts grounded here:
- **Three blinds per ante**: Small, Big, Boss, each with an independent *state* in the set
  `{Select, Upcoming, Selected, Skipped, Defeated, Hidden, Current}` (BMP enumerates the
  "done/hidden" subset in `BMP/lib/blind_utils.lua:1-5`: `Hidden`, `Defeated`, `Skipped`).
- **Boss is chosen at run start, then re-chosen each ante**. `Game:start_run` sets
  `round_resets.blind_choices.Boss = get_new_boss()` (`dump/game.lua:2241`) and `reset_blinds()`
  / `set_blind` advance it (`dump/game.lua:2559-2560`). `round_resets.blind_choices.Boss` is
  refreshed by `get_new_boss()` again at each ante reset (`dump/game.lua:2241`, plus the
  commented re-roll path at `:3464`).
- **Win condition**: `G.GAME.win_ante = 8` (`dump/game.lua:1918`). Game-over / win is gated on
  `G.GAME.round_resets.ante <= G.GAME.win_ante` (`dump/game.lua:3773`, `:3791`). Beating the
  Boss of `win_ante` wins; exceeding it without an "endless" continue is the win screen.
- **Tags are chosen per ante for Small and Big** (the skip reward). `start_run` seeds
  `round_resets.blind_tags.Small`/`.Big = get_next_tag_key()` (`dump/game.lua:2244-2245`).
  Only Small and Big can be skipped; Boss cannot be skipped.
- **Seed / RNG**: seed is set in `start_run` (`dump/game.lua:2224`), pseudohashed per-key
  (`:2228`), and `hashed_seed = pseudohash(seed)` (`:2229`). BMP forces an `"*"` prefix when
  "the_order" is on (`dump/game.lua:2227`, gated by `MP.should_use_the_order()`).

### 1.2 Blind selection / skip / ante-up flow (vanilla, the backbone BMP inherits)

The vanilla loop is: `BLIND_SELECT` state → player picks one of the three offered blinds OR
skips Small/Big (claiming its tag) → `SELECTING_HAND` (play/discard) → on clear, `ROUND_EVAL`
(economy) → `SHOP` → `select_blind` advances the pointer; after Boss, `ante_up` increments
`round_resets.ante` and `get_new_boss()` re-rolls the Boss. Skipping calls `add_tag(_tag)`
(`dump/game.lua:2556`) and flips that blind's state to `Skipped`.

### 1.3 BMP 0.4.0 multiplayer overlay

BMP layers a **gamemode** + **ruleset** on top of the vanilla loop. The two are distinct
SMODS object types:

- **Rulesets** (`BMP/rulesets/_rulesets.lua`): declare banned/reworked content
  (`banned_jokers`, `banned_consumables`, `banned_vouchers`, `banned_enhancements`,
  `banned_tags`, `banned_blinds`, and `reworked_*`), composed from **layers**
  (`MP.Ruleset({ layers = {...} })`). E.g. `BMP/rulesets/ranked.lua`:
  `key = "standard_ranked", layers = {"standard","ranked"}, forced_gamemode = "gamemode_mp_attrition"`.
  On-disk rulesets: `vanilla, standard? , ranked, legacy_ranked, blitz, chaos, badlatro,
  majorleague, minorleague, release, sandbox, smallworld, speedlatro, traditional` (+ `experimental/`).
  Bans are applied at run start via `MP.ApplyBans()` → `G.GAME.banned_keys[v] = true`
  (`_rulesets.lua:198-230`), merging ruleset + gamemode + deck bans.

- **Gamemodes** (`BMP/gamemodes/_gamemodes.lua`): the *required* hook is
  **`get_blinds_by_ante(self, ante) -> small, big, boss`** (returns blind keys or `nil` to
  keep the vanilla choice). This is exactly the run/blind state-machine seam.
  - **Attrition** (`BMP/gamemodes/attrition.lua`): `if ante >= MP.LOBBY.config.pvp_start_round
    then return nil, nil, "bl_mp_nemesis"` (unless `normal_bosses`, which instead forces a
    PvP *choice* via `round_resets.pvp_blind_choices.Boss = true`). So from `pvp_start_round`
    (default **2**), the **Boss slot becomes a Nemesis (PvP) blind**. Bans: `j_mr_bones,
    j_luchador, j_matador, j_chicot`; vouchers `v_hieroglyph, v_petroglyph, v_directors_cut,
    v_retcon`; tag `tag_boss`; blinds `bl_wall, bl_final_vessel`.
  - **Showdown** (`BMP/gamemodes/showdown.lua`): `if ante >= showdown_starting_antes
    (default 3) then return "bl_mp_nemesis","bl_mp_nemesis","bl_mp_nemesis"` — **all three
    blinds become PvP**. Same ban list.
  - **Survival** (`BMP/gamemodes/survival.lua`): `get_blinds_by_ante` always returns
    `nil,nil,nil` (pure vanilla blinds), **1 life**, endless — last player standing /
    furthest. Bans a large MP-joker list + `c_mp_asteroid`.

- **Nemesis blind** (`BMP/objects/blinds/nemesis.lua`): `SMODS.Blind{ key="nemesis",
  dollars=5, mult=1 (comment: "Jen's Almanac crashes if mult is 0"), boss={min=1,max=10},
  in_pool=function() return false end }`. It is **never in the natural boss pool** — only
  injected by the gamemode hook. `MP.is_pvp_boss()` returns true when
  `G.GAME.blind.config.blind.key == "bl_mp_nemesis"` or `G.GAME.blind.pvp`.

### 1.4 Lives, lobby config defaults (BMP 0.4.0)

`MP.reset_lobby_config()` (`BMP/core.lua:171-200`):
- `starting_lives = 4`, `pvp_start_round = 2`, `showdown_starting_antes = 3`
- `gold_on_life_loss = true`, `no_gold_on_round_loss = false`, `death_on_round_loss = true`
- `different_seeds = false`, `the_order = true`, `custom_seed = "random"`
- timers: `timer_base_seconds = 150`, `timer_increment_seconds = 60`,
  `pvp_countdown_seconds = 3`, `timer_forgiveness = 0`
- default `ruleset = "ruleset_mp_blitz"`, default `gamemode = "gamemode_mp_attrition"`
- decks/loadout: `different_decks=false`, `random_loadout=false`, `back="Red Deck"`,
  `stake = 1`, `multiplayer_jokers = true`, `legacy_smallworld = false`

`MP.reset_game_states()` (`BMP/core.lua:204-260`) tracks the live overlay: `lives`,
`enemy = { score, hands=4, skips, lives=starting_lives, ... }`, `next_blind_context`,
`pvp_reached`, `pvp_countdown`, `furthest_blind`, `comeback_bonus`, `end_pvp`, `stats`.

### 1.5 PvP blind resolution (authoritative on the BMP server)

The *server* (`SERVER/`) is the source of truth for PvP and lives — the Lua client only
reports its own score/handsLeft.

- **GameMode table** (`SERVER/src/GameMode.ts`): per-mode `startingLives` and
  `getBlindFromAnte(ante, options) -> {small?, big?, boss?}` using key `"bl_pvp"`:
  - attrition: `startingLives: 4`, `{ boss: "bl_pvp" }` (every ante's boss is PvP — note
    no `pvp_start_round` gate here; see delta §3).
  - showdown: `startingLives: 2`, returns `{}` while `ante <= showdown_starting_antes`,
    else `{small,big,boss = 'bl_pvp'}`.
  - survival: `startingLives: 1`, always `{}`.
- **Round start** (`SERVER/src/actionHandlers.ts:173-197`): when both players are
  `isReady`, server resets scores to `InsaneInt(0,0,0)`, **`handsLeft = 4`** for both,
  grants **Speedrun** to the 2nd-ready player if within **30s** of the first, and
  broadcasts `startBlind { firstPlayer }`.
- **PvP resolution on `playHand`** (`actionHandlers.ts:204-275`): a player's reported
  `{score, handsLeft}` is stored; the round is decided when
  `(guest.handsLeft<1 && guest.score < host.score) || (host.handsLeft<1 && host.score <
  guest.score) || (both handsLeft<1)`. The lower score is `roundLoser`; on non-tie,
  `roundLoser.loseLife()`. If any `lives <= 0`, the higher-lives player gets `winGame`,
  the other `loseGame`. Otherwise both get `endPvP { lost }`. Ties cost nothing.
- **PvP timeout** (`failPvPTimerAction`, `actionHandlers.ts:277+`): the timed-out client
  `loseLife()`; at 0 lives → `winGame`/`loseGame`.
- The BMP client kicks off a PvP blind via `startBlind` → `begin_pvp_blind()` →
  `G.FUNCS.select_blind(MP.GAME.next_blind_context)` after a `start_pvp_countdown`
  (`BMP/networking/action_handlers.lua:278-296`). Enemy score/hands/lives/skips stream via
  `enemyInfo` (`:298+`).

### 1.6 Blind requirement curve (vanilla `get_blind_amount`)

Ante 1..8 base chips are fixed; >8 use the formula our `Blinds.java` already ports
(`a*(b+(k*c)^d)^c`, truncated to the 2nd significant digit). Small ×1, Big ×1.5, Boss ×2;
dollar rewards $3/$4/$5. The Nemesis blind has `dollars=5, mult=1`
(`BMP/objects/blinds/nemesis.lua`).

### 1.7 0.3.3 → 0.4.0 deltas (from `BMP/CHANGELOG.md`)

The 0.4.0 "Ranked Update" changes are **content/economy**, not state-machine:
- To Do List reworked → pays **$5**, target hand from *all* hands.
- Golden Ticket payout reverted to **$4**; Speedrun **out of rotation** (Standard Ranked);
  Ouija/Ectoplasm now cost **$4** (bugfix); Justice back in rotation.
- Gold Card enhancement **$3 → $4**.
- **Comeback Gold** — awarded on **any life loss** again (not just PvP boss losses);
  amounts ($4, or $2 on higher stakes) unchanged. (Lifecycle-relevant: ties life-loss to
  an economy hook.)
- Legacy Ranked: Hanging Chad retriggers first **2** cards.
- 0.3.0 (historical): Judgment draws from own queue on Orange+ stake (else shop queue) —
  a *queue-topology* note relevant to our QueueSet design.

**No change to the Small/Big/Boss/skip/ante machinery in 0.4.0** — the lifecycle backbone is
the same as 0.3.3; what moved is balance + the practice/replay system. Flagged: the
**0.3.3 changes spreadsheet** (`xlsx_out2/09_Skip_Tags.txt` etc.) remains the baseline for
tag/economy values; where it disagrees with the 0.4.0 source, the 0.4.0 source wins.

---

## 2. How OUR engine does it today (cited)

`OURS/game/Run.java` is the single-player run spine; `OURS/game/Match.java` coordinates two
Runs into a race.

- **Phases** (`Run.java:37`): `enum Phase { BLIND_ACTIVE, SHOP, PVP_PENDING, BLIND_FAILED,
  RUN_WON, RUN_LOST }`. There is **no explicit blind-select phase** — a blind starts
  immediately in `startBlind()` (`Run.java:83`).
- **Blind types** (`Blinds.java:16-30`): `SMALL/BIG/BOSS` with mult `1/1.5/2` and reward
  `$3/$4/$5`. Requirement = `getBlindAmount(ante) * mult * anteScaling` (`Blinds.java:50`),
  a faithful port of `get_blind_amount` (`Blinds.java:33-48`).
- **Boss** is picked in `startBlind` via `BossCatalog.pick(ante, rng)` or `forcedBoss`
  (`Run.java:96`); debuffs (suit/face) applied in `refreshDebuffs()` (`Run.java:116-122`).
- **Ante progression** (`Run.java:289-305`, `proceed()`): SMALL→BIG→BOSS→(win check)→
  ante++ / SMALL. Win: `pvpFromAnte==0 && winAnte>0 && ante>=winAnte` after Boss → `RUN_WON`.
  Endless = `winAnte==0` (`Ruleset.java:46` notes "0 = endless/survival").
- **Win/loss of a blind** (`Run.java:125-180`): `play()` checks `roundScore >= requirement`
  → `winBlind()` (economy: reward + interest `min(5, money/5)`, `GameEvents.endOfRound`,
  then `Shop.generate`). Hands exhausted → `RUN_LOST` (solo) or `BLIND_FAILED` (Attrition).
- **PvP / Nemesis**: `pvpFromAnte` (`Run.java:56`) makes Boss blinds at/after that ante a
  synthetic `NEMESIS` blind (`Run.java:40-41`, `bl_pvp`, no debuff, reward 5). `startBlind`
  sets `pvpActive`, `requirement = 0` (`Run.java:86-94`). `Match.java` owns lives
  (`STARTING_LIVES = 4`, `PVP_FROM_ANTE = 2`, lines 38-39), resolves Nemesis with a
  race comparison (`resolveNemesisIfDecided`, `Match.java:112-140`), and `endPvp()`
  (`Run.java:162-170`) awards economy + opens shop.
- **Shop** (`Shop.java`): `generate(queues, slots=2, pool)` draws jokers + 1 planet from
  the run-long `QueueSet`. `Run.buyJoker/reroll/buyPlanet/useConsumable` mutate it.
  Reroll cost is a flat `REROLL_COST = 5` (`Shop.java:23`).
- **Ruleset** (`Ruleset.java`): record of `startingMoney, hands, discards, handSize,
  anteScaling, winAnte, blindBaseAmounts, jokerPool`. **No bans, no gamemode, no
  per-ante blind override, no lives, no skip/tag data.**
- **Skips / tags**: **absent entirely.** No skip action, no tag inventory, no
  `blind_states`, no Small/Big skip economy.
- **Blind select**: **absent.** No choice between the three offered blinds, no boss reroll.

---

## 3. The GAP

| Concern | BMP 0.4.0 / vanilla | OURS today | Gap |
|---|---|---|---|
| Blind-select phase | `BLIND_SELECT` state, 3 choices visible, states per blind | none — blind auto-starts | **Add a `BLIND_SELECT` phase + `blindStates`** |
| Skip Small/Big + tag | `add_tag`, state→`Skipped`, tag inventory, skip economy | none | **Add skip intent, tag inventory, skip-tag effects** |
| Boss selection | `get_new_boss()` per ante, reroll voucher, `in_pool` gating, banned blinds | `BossCatalog.pick` only; no bans, no reroll | **Boss pool must honor ruleset/gamemode bans + reroll** |
| Per-ante blind override | gamemode `get_blinds_by_ante(ante)->small,big,boss` | hardcoded `pvpFromAnte` for Boss only | **Generalize to a gamemode hook returning all 3 slots** |
| Gamemodes | attrition / showdown / survival as data objects | implicit in `Match` constants | **First-class `Gamemode` data type** |
| Lives | per-gamemode (4 / 2 / 1), `loseLife`, comeback gold | hardcoded `STARTING_LIVES=4` in Match | **Lives belong to gamemode config; survival=1, showdown=2** |
| Showdown (all-PvP) | small+big+boss all `bl_pvp` from ante N | only Boss can be PvP | **PvP must be applyable to any of the 3 slots** |
| PvP resolution | server: `score<` + `handsLeft<1`, tie=no-op, `winGame/loseGame` | `resolveNemesisIfDecided` (close) | **Mostly present; reconcile with server's exact predicate** |
| PvP timeout | `failPvPTimerAction` → loseLife | none (no timer) | **Add PvP timer + timeout life-loss** |
| Bans | ruleset+gamemode+deck → `banned_keys` | none | **Ban set must gate shop/boss/tag/voucher pools** |
| Reworks / layers | layered reworked centers per ruleset | none | (out of lifecycle scope; track separately) |
| Economy on life loss | Comeback Gold on any life loss (0.4 delta) | none | **Hook life-loss → economy** |
| `bl_pvp` reward | dollars=5, mult=1 | reward 5, req 0 | matches |
| Speedrun 2nd-ready grant | server grants within 30s | none | **Add ready-window grant hook** |
| Endless | continue past win_ante | `winAnte==0` only | **Add endless-continue after a normal win** |

**Server-vs-mod delta to flag:** `SERVER/src/GameMode.ts` attrition returns
`{ boss: "bl_pvp" }` for **every** ante (no `pvp_start_round` gate), whereas the 0.4.0 mod
(`BMP/gamemodes/attrition.lua`) gates on `ante >= pvp_start_round` (default 2). The mod is
newer/authoritative for 0.4.0; the server repo on disk is the older 0.3.x protocol. **Our
design follows the 0.4.0 mod** (gated by `pvpStartAnte`). Also: server showdown
`startingLives: 2` vs the 0.4.0 attrition/showdown UI showing **4** lives
(`BMP/gamemodes/showdown.lua:191-200` displays "4"). Flag: lives are a *lobby option*
(`starting_lives=4` default in `core.lua:177`); the server's per-mode literal is a fallback.
Treat `startingLives` as gamemode default, overridable by lobby config.

---

## 4. Proposed target design (authoritative + queue-shaped + ruleset-driven)

### 4.1 State machine

Introduce an explicit blind-select step and make PvP a property of the *current blind slot*,
not a special Boss-only path.

```
enum Phase {
    BLIND_SELECT,   // choosing/skipping the upcoming blind (NEW)
    BLIND_ACTIVE,   // play/discard until requirement met or hands out
    PVP_PENDING,    // out of hands in a PvP blind, awaiting comparison
    ROUND_EVAL,     // economy applied (NEW, explicit; was folded into winBlind)
    SHOP,
    BLIND_FAILED,   // failed a blind in a lives gamemode (life lost, continue)
    RUN_WON, RUN_LOST
}
```

Per-blind state mirrors vanilla `blind_states`:

```
enum BlindState { UPCOMING, SELECT, CURRENT, SELECTED, DEFEATED, SKIPPED, HIDDEN }
record BlindSlot(BlindType type, String blindKey, BlindState state, boolean pvp,
                 String skipTagKey)   // skipTagKey present only for SMALL/BIG
```

`Run` holds `BlindSlot[3]` per ante (`small,big,boss`), recomputed at each ante via the
gamemode hook (§4.3). Transitions:

```
ante start → gamemode.resolveBlinds(ante, lobbyOpts) fills the 3 slots
           → first non-done slot enters BLIND_SELECT
BLIND_SELECT:
   selectBlind()  → slot.state=CURRENT, deal deck, Phase=BLIND_ACTIVE
   skipBlind()    → (SMALL/BIG only, not banned, not pvp) claim slot.skipTagKey,
                    slot.state=SKIPPED, advance to next slot's BLIND_SELECT
BLIND_ACTIVE:
   playHand → if pvp && handsLeft==0 → PVP_PENDING
            → else if score>=req → slot.state=DEFEATED, ROUND_EVAL→SHOP
            → else if handsLeft==0 → RUN_LOST (solo) | BLIND_FAILED (lives mode)
SHOP.proceed() → next slot; after BOSS: ante++ or win/endless (§4.4)
```

### 4.2 Skip + tags

- `skipBlind()` legal only when `slot.type ∈ {SMALL,BIG}`, `!slot.pvp`,
  `slot.blindKey ∉ bans`, and `slot.skipTagKey` not a banned tag.
- A run-long **tag inventory** (`List<TagInstance>`); on skip, append `slot.skipTagKey`.
- Tag keys for Small/Big are drawn from a deterministic **tag queue** in the `QueueSet`
  (mirrors `get_next_tag_key()`), gated by `bannedTags`. Tag *effects* (economy/shop/blind
  modifiers) are resolved at their trigger points (shop entry, next blind, immediate) — out
  of scope for the skeleton, but the *slots/inventory/queue* are in scope. Cross-ref
  `docs/design/20-skip-tags.md` for per-tag effects; this doc owns the **state plumbing**.
- Skip count is surfaced (`skips`) — needed for the PvP timer-increment + enemy HUD
  (`SERVER` streams `skips`; `BMP/networking/action_handlers.lua:302-321`).

### 4.3 Gamemode hook (the central seam)

Add a `Gamemode` data type mirroring `BMP/gamemodes/_gamemodes.lua` + `SERVER/GameMode.ts`:

```java
public record Gamemode(
    String key,                 // "attrition" | "showdown" | "survival"
    int startingLives,          // 4 / 4(UI) / 1   (lobby-overridable)
    boolean endless,            // survival/attrition true; bounded modes false
    BlindResolver blinds,       // get_blinds_by_ante
    BanSet bans) { }

@FunctionalInterface interface BlindResolver {
    // returns blind-key override per slot, null = keep vanilla choice
    BlindOverride resolve(int ante, LobbyOptions opts);
}
record BlindOverride(String small, String big, String boss) {}
```

Concrete resolvers (faithful ports):
- **attrition**: `ante >= opts.pvpStartAnte (def 2)` → `new BlindOverride(null,null,"bl_pvp")`
  (unless `normalBosses`, which keeps a normal boss but flags a PvP *choice*).
- **showdown**: `ante >= opts.showdownStartingAntes (def 3)` →
  `new BlindOverride("bl_pvp","bl_pvp","bl_pvp")`, else all-null.
- **survival**: always all-null; `startingLives=1`, `endless=true`.

`Run` consumes the override at ante start: any non-null slot is marked `pvp=true,
blindKey="bl_pvp"`, `requirement=0`. Non-PvP slots fall through to normal blind/boss
selection (boss honoring bans).

### 4.4 Ante progression, win, endless

```
proceed() after BOSS:
   if gamemode.endless || ruleset.winAnte()==0:
       ante++; continue          // survival/attrition: only lives end it
   else if ante >= ruleset.winAnte():
       if endlessRequested: ante++; markEndless(); continue   // NEW
       else Phase=RUN_WON
   else ante++
```

This matches `dump/game.lua:3773` (`round_resets.ante <= win_ante`) and preserves vanilla
endless. `winAnte` stays in `Ruleset`; **endless-vs-bounded becomes a gamemode property**
so survival/attrition are correctly endless regardless of ruleset.

### 4.5 Lives & PvP resolution (reconcile with server)

- Lives move from `Match` constants into gamemode/lobby config:
  `lives = lobbyOpts.startingLives ?? gamemode.startingLives`.
- Keep `Match.resolveNemesisIfDecided` but align its predicate exactly to
  `SERVER/actionHandlers.ts:236-273`:
  decide when `(opp.handsLeft<1 && opp.score<me.score) || (me.handsLeft<1 &&
  me.score<opp.score) || (both handsLeft<1)`; lower score loses a life; **tie → no life
  lost, both `endPvP`**; any side at 0 lives → `winGame`/`loseGame`. Our current code is
  equivalent but should use the shared predicate to avoid drift.
- Add a **PvP timer** + `failPvPTimer` path (life loss on timeout) —
  `timerBaseSeconds=150`, `incrementSeconds=60` per skip, `pvpCountdown=3`
  (`BMP/core.lua:179-181`). Server-authoritative clock.
- **Comeback Gold on any life loss** (0.4 delta): on `loseLife()`, if `gold_on_life_loss`,
  award `$4` (`$2` higher stakes) via `GameEvents`. Hook in `Match.loseLife`.
- **Speedrun grant**: when both ready, if 2nd ready within 30s, grant Speedrun to the 2nd
  (`SERVER/actionHandlers.ts:174-177`). Add a ready-timestamp to `Side`.

### 4.6 Bans (ruleset + gamemode + deck)

Add a resolved `BanSet` = union of `ruleset.banned*` ∪ `gamemode.banned*` ∪ `deck.banned*`
(mirror `MP.ApplyBans`, `_rulesets.lua:198-230`). It gates: shop joker/consumable/voucher
pools, the boss pool (`bl_wall`, `bl_final_vessel` banned in attrition/showdown), and the
tag queue (`tag_boss` banned). `Ruleset` gains `banned*` lists; a `Gamemode` carries its own.

### 4.7 Determinism / queues

- One run-long `QueueSet` per player (already present). **Add queues**: `boss` (per-ante
  `get_new_boss`), `tag` (Small/Big skip tags), `voucher` (per-ante). Each gated by the
  `BanSet` (reject-and-redraw banned keys, deterministically).
- Same-seed both players ⇒ identical blind/boss/tag/voucher/shop sequences; the only
  variable is build/play (our existing invariant, now extended to the full lifecycle).
- Keep `the_order` `"*"`-seed prefix behavior as a ruleset/lobby flag for vanilla-seed
  parity (`dump/game.lua:2227`).

### 4.8 Authoritative intents (client never decides outcomes)

New intents routed through `IntentHandler`/`Run`:
`SelectBlind`, `SkipBlind`, `RerollBoss` (if a reroll voucher is owned), `EnterShop`
(implicit on round-eval), `LeaveShop` (→ `proceed`), plus existing play/discard/buy/use.
Each validated against phase + bans + economy server-side; `ServerUpdate` returns the new
`ClientView` (extend `view()` with `blindStates`, `tags`, `lives`, `skips`, the 3 blind
choices, and `pvp` flags).

---

## Open questions

1. **Boss reroll voucher** (`v_directors_cut`/`v_retcon`) is *banned* in attrition/showdown.
   Do we still implement `RerollBoss` for survival/vanilla rulesets, or defer until those
   vouchers are in a pool? (Bans suggest it's only live in non-PvP modes.)
2. **`normal_bosses` attrition variant** flips Boss to a *PvP choice* rather than forcing
   Nemesis (`attrition.lua:7-9`, `round_resets.pvp_blind_choices.Boss=true`). Do we model a
   "PvP-or-normal pick" UI, or treat it as out-of-scope for v1?
3. **Lives source of truth**: lobby `starting_lives` (4) vs server per-mode literal
   (showdown 2). Which wins when they conflict? Proposed: lobby option overrides gamemode
   default — confirm against current matchmaking server behavior.
4. **Showdown all-PvP economy**: with Small+Big+Boss all PvP from ante N, is there still a
   shop between PvP blinds, and what reward does a PvP Small/Big pay (Nemesis is `dollars=5`
   regardless of slot)? Need to confirm reward parity for non-boss PvP slots.
5. **Tag effect timing**: this doc plumbs tag *slots/inventory*; the exact trigger/queue
   interaction for each tag (esp. Orange-Stake "own queue" for Judgment, 0.3.0 changelog) is
   owned by `20-skip-tags.md` — confirm the seam (does the tag fire on shop entry or on the
   next blind select?).
6. **PvP timer**: is the timer purely a server clock with `failPvPTimer`, or does the client
   also enforce? For an authoritative server we should own it — confirm the client trusts
   server `pvpTimerLost`.
7. **`furthest_blind` / survival ranking**: survival is "last standing"; how is a multi-way
   or simultaneous-death tie broken — by `furthest_blind` then score? (`core.lua:246`).

## New building blocks needed

- `Gamemode` record + `BlindResolver` (attrition/showdown/survival ports of
  `get_blinds_by_ante`); registry `GamemodeCatalog`.
- `BlindSlot` + `BlindState` enum; a `BlindBoard` (3 slots/ante) on `Run`, replacing the
  scalar `blind` field.
- New `Phase.BLIND_SELECT` and `Phase.ROUND_EVAL`; intents `SelectBlind`, `SkipBlind`,
  `RerollBoss`, `LeaveShop`.
- **Tag plumbing**: `TagInstance`, run-long tag inventory, deterministic `tag` queue in
  `QueueSet`, `skips` counter on `RunState`.
- `BanSet` (resolved union) + `banned*` fields on `Ruleset`; ban-gated `boss`/`tag`/
  `voucher`/shop pools.
- `LobbyOptions` record: `pvpStartAnte=2`, `showdownStartingAntes=3`, `startingLives`,
  `timerBaseSeconds=150`, `timerIncrementSeconds=60`, `pvpCountdownSeconds=3`,
  `goldOnLifeLoss`, `deathOnRoundLoss`, `theOrder`, `differentSeeds`, `customSeed`.
- `BossCatalog` extension: `in_pool` gating, ban filtering, per-ante boss queue, reroll.
- Lives moved into a gamemode/lobby-driven field on `Match.Side`; `loseLife()` with
  **Comeback Gold** economy hook (0.4 delta).
- PvP **timer** (server clock) + `failPvPTimer` life-loss path; `pvpTimerLost` message.
- Speedrun 2nd-ready-within-30s grant hook on `Match`.
- `ClientView` extension: `blindStates`, `blindChoices`, `tags`, `lives`, `skips`, `pvp`.
- Endless-continue flag on `Run` (continue past `winAnte` when requested; auto for
  endless gamemodes).
