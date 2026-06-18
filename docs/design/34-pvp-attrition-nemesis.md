# 34 — PvP, Attrition, Showdown, Survival & the Nemesis Blind

Target parity: **Balatro Multiplayer (BMP) 0.4.0** (on disk at
`C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/`).

This doc specs the gamemode/PvP layer: exact rules per gamemode, lives, the
Nemesis blind, the win/loss resolution, and — critically — **the exact opponent
("nemesis") state our jokers must read**, grounded in the real joker source.

---

## 1. How the REAL game / BMP 0.4.0 does it

### 1.1 Gamemode object model

Gamemodes are an SMODS `GameObject` subclass. Each gamemode supplies only a
**blind-selection override** plus banned/reworked pools and a UI info menu.
Source: `gamemodes/_gamemodes.lua`.

```lua
MP.Gamemode = SMODS.GameObject:extend({
  required_params = { "key", "get_blinds_by_ante",
    "banned_jokers","banned_consumables","banned_vouchers",
    "banned_enhancements","banned_tags","banned_blinds",
    "reworked_jokers", ... , "create_info_menu" },
  class_prefix = "gamemode",
})
```

`get_blinds_by_ante(self, ante) -> small, big, boss` returns blind keys (or
`nil` to mean "use the normal vanilla blind"). This is the **only** rules hook
that differs the three gamemodes; everything else is bans/UI.

The three gamemodes are loaded via `MP.load_mp_dir("gamemodes")` (`core.lua:300`).

### 1.2 Attrition (`gamemodes/attrition.lua`)

- **Lives: 4** (info menu literal `"4"`, attrition.lua:196; default also in
  `MP.LOBBY.config.starting_lives = 4`, core.lua:177; server `startingLives: 4`,
  `GameMode.ts:22`).
- **Blind schedule** (attrition.lua:3-12):
  - Ante < `pvp_start_round` (default **2**, core.lua:178): normal Small / Big /
    Boss (returns `nil,nil,nil`).
  - Ante >= `pvp_start_round`:
    - if `normal_bosses` is **false** (default): boss slot becomes
      `"bl_mp_nemesis"` (returns `nil, nil, "bl_mp_nemesis"`). Small & Big stay
      normal.
    - if `normal_bosses` is **true**: keeps a real boss but flags
      `G.GAME.round_resets.pvp_blind_choices.Boss = true`.
  - So Attrition = solo through the antes, **PvP only on the boss slot** from
    ante 2 onward. The info menu reflects this: Ante 1 = Small/Big/random-boss,
    Ante 2+ = Small/Big/**Nemesis** (attrition.lua:85-175).
- **Banned jokers**: `j_mr_bones`, `j_luchador`, `j_matador`, `j_chicot`
  (anti-death/boss-disabling jokers — they would trivialize the Nemesis loss).
- **Banned vouchers**: `v_hieroglyph`, `v_petroglyph`, `v_directors_cut`,
  `v_retcon` (ante-skip / boss-reroll vouchers).
- **Banned tags**: `tag_boss`. **Banned blinds**: `bl_wall`, `bl_final_vessel`.

### 1.3 Showdown (`gamemodes/showdown.lua`)

- **Lives: 4** in the mod info menu (showdown.lua:191) **but server says 2**
  (`GameMode.ts:28 startingLives: 2`). **DELTA / conflict** — the lobby
  `starting_lives` default (4) overrides at runtime via `lobbyOptions`
  (action_handlers.lua:507 syncs `starting_lives`), and the client info menu is
  cosmetic. Treat **lives as a lobby option**, default 4 from
  `MP.reset_lobby_config`; the server's hard-coded 2 is only a fallback when the
  client never sends `starting_lives`. **Flag: verify which wins in practice.**
- **Blind schedule** (showdown.lua:3-8): once ante >= `showdown_starting_antes`
  (default **3**, core.lua:182), **ALL three blinds** become `"bl_mp_nemesis"`
  (`return "bl_mp_nemesis","bl_mp_nemesis","bl_mp_nemesis"`). Before that, normal
  blinds. So Showdown = solo antes 1-2, then **every blind is PvP**.
  - Server mirror (`GameMode.ts:29-33`) uses key `bl_pvp` and condition
    `ante <= starting_antes -> {}` else all three `bl_pvp`. **DELTA**: server key
    is legacy `bl_pvp`; 0.4.0 client key is `bl_mp_nemesis`. The 0.4.0 source
    wins — our engine should use a single internal PvP-blind concept and map.
- Same bans as Attrition.

### 1.4 Survival (`gamemodes/survival.lua`)

- **Lives: 1** (info menu `"1"`, survival.lua:133; server `startingLives: 1`,
  `GameMode.ts:36`).
- **No PvP blind ever**: `get_blinds_by_ante` returns `nil,nil,nil` always
  (survival.lua:3-5). It is a pure **same-seed solo race** — "go as far as you
  can on 1 life."
- **Win/loss is by furthest blind reached, not by score-vs-score.** Resolved on
  `failRound` server-side (`actionHandlers.ts:344-385`):
  - With `death_on_round_loss` (default true, core.lua:174) failing a round costs
    the life; at `lives === 0`:
    - both at 0 lives → compare `furthestBlind`: equal = **both win** (draw),
      else lower loses.
    - one at 0 → if the dying player's `furthestBlind < enemy.furthestBlind` they
      lose, otherwise nothing happens yet (enemy keeps going).
- **Banned jokers** (survival.lua:6-17): the MP nemesis-interaction jokers
  (`conjoined_joker`, `defensive_joker`, `lets_go_gambling`, `magnet_sandbox`,
  `pacifist`, `penny_pincher`, `pizza`, `skip_off`, `speedrun`, `taxes`) — i.e.
  everything that reads opponent state is banned because there is no live PvP
  duel. **Banned consumable**: `c_mp_asteroid`.

### 1.5 The Nemesis blind (`objects/blinds/nemesis.lua`)

```lua
SMODS.Blind({
  key = "nemesis",          -- full key "bl_mp_nemesis"
  dollars = 5,              -- reward $5 on win
  mult = 1,                 -- NOT a chip target; 1 because 0 crashes Jen's Almanac
  boss_colour = G.C.MULTIPLAYER,
  boss = { min = 1, max = 10 },
  atlas = "player_blind_chip",
  discovered = true,
  in_pool = function(self) return false end,  -- never rolls naturally
})
function MP.is_pvp_boss()
  if not G.GAME or not G.GAME.blind or not G.GAME.blind.config.blind then return false end
  return G.GAME.blind.config.blind.key == "bl_mp_nemesis" or G.GAME.blind.pvp
end
```

Key facts:
- The Nemesis blind has **no fixed chip score requirement** — `mult = 1` is a
  placeholder. The real "target" is **the opponent's score** (a race), resolved
  by the server, not by the blind's chip requirement.
- `MP.is_pvp_boss()` is the canonical "am I in a PvP duel right now" check used
  throughout (jokers, timers). It matches either the `bl_mp_nemesis` key **or**
  a generic `G.GAME.blind.pvp` flag.
- Reward on winning the duel is **$5** (`dollars = 5`).

### 1.6 PvP duel lifecycle (ready timing, score race, lives)

Client → server actions (`networking/action_handlers.lua`):
- `readyBlind` (`MP.ACTIONS.ready_blind`, :1019) — sent when a player presses
  Ready on the blind-select screen; stores `MP.GAME.next_blind_context`.
- `playHand(score, handsLeft)` (:1087-1118) — sent **every hand** on a PvP boss:
  pushes the player's running `score` and remaining `handsLeft`. The lovely hook
  that calls it: `lovely/game.toml` around line 130 also sends `spentLastShop`.

Server (`actionHandlers.ts`):
- `readyBlindAction` (:161-198): sets `client.isReady`. When **both** host and
  guest are ready → reset both scores to 0, reset both `handsLeft = 4`, compute
  `firstPlayer` from whoever set `firstReady`, broadcast
  `{ action: "startBlind", firstPlayer }`. **First-ready also grants Speedrun**
  (:166-177): the first player to ready gets `speedrun`; the second player also
  gets it if they ready **within 30s** (`firstReadyAt`, 30000 ms). This is the
  exact "ready timing" mechanic.
- Client `action_start_blind` (:286-296): resets PvP timers, sets
  `MP.GAME.pvp_reached_first = (am I the firstPlayer)`, starts the countdown
  (`pvp_countdown_seconds`, default **3**, core.lua:181), then calls
  `begin_pvp_blind` → `select_blind`.
- `playHandAction` (:204-275): records score + handsLeft, relays to opponent as
  `enemyInfo`. **Round decided when**: a player is out of hands (`handsLeft < 1`)
  **and behind**, or both are out of hands. Then:
  - tie (`host.score == guest.score`) → **no life lost**, both `endPvP`.
  - else `roundLoser.loseLife()`. If anyone hits `lives <= 0` →
    `winGame`/`loseGame`; else both `endPvP` (loser gets `lost: true`).
- **PvP timer loss** (`failPvPTimerAction`, :277-322): the player whose timer
  expired `loseLife()`; same win/round resolution, but `endPvP` carries
  `pvpTimerLost: true`.

### 1.7 The exact opponent ("enemy") state — what jokers read

This is the authoritative shape our engine must replicate. Initialized in
`core.lua:216-227` (`MP.GAME.enemy`):

```lua
MP.GAME.enemy = {
  score          = INSANE_INT,   -- opponent live score this blind
  score_text     = "0",
  hands          = 4,            -- opponent hands LEFT this blind  (key: "hands")
  location       = "Selecting",  -- where they are (shop/blind/etc.)
  skips          = 0,            -- total blinds skipped
  lives          = starting_lives,
  sells          = 0,            -- total cards sold
  sells_per_ante = {},           -- [ante] -> count sold that ante
  spent_in_shop  = {},           -- per-shop-visit $ spent, appended each shop
  highest_score  = INSANE_INT,
}
```

Plus `MP.GAME` PvP scalars used by jokers/timers:
`lives` (mine), `pincher_index` (starts **-3**, core.lua:247),
`pincher_unlock` (false until first PvP reached, set in
`ui/game/game_state.lua:14`), `score`, `highest_score`, `pvp_reached`,
`pvp_reached_first`, `nemesis_timer_started`, `comeback_bonus(_given)`.

Updated by `action_enemy_info` (action_handlers.lua:298-395):
sets `MP.GAME.enemy.hands/skips/lives` and eases the displayed score; plays a
sound when `enemy.lives` drops or `enemy.skips` increases.
`action_spent_last_shop` (:639-640) appends `tonumber(p.amount)` to
`enemy.spent_in_shop`. `enemy.sells_per_ante` is filled by the sell relay
(:620-622). `enemy.location` by `enemyLocation` (:579-606).

`pincher_index` is incremented each end-of-round (`end_round.toml:137`,
`ui/game/functions.lua:99`) so it indexes the correct historical shop-spend entry
when Penny Pincher fires.

### 1.8 The MP jokers and the EXACT nemesis state each reads

(`objects/jokers/`). These are the contracts our joker engine must honor:

| Joker | Reads | Exact effect (verbatim from source) |
|---|---|---|
| **Defensive Joker** | `MP.GAME.enemy.lives`, `MP.GAME.lives` | `t_chips = max((enemy.lives - my.lives) * chips, 0)`, `chips = 75 if stake>=6 else 125`. Adds `chip_mod` on `joker_main`. (defensive_joker.lua:35,18) |
| **Conjoined Joker** | `MP.GAME.enemy.hands` | `x_mult = clamp(1 + enemy.hands * 0.5, 1, 3)`. Applies `x_mult` **only when `MP.is_pvp_boss()`** and not a phantom. (conjoined_joker.lua:40,48-58) |
| **Penny Pincher** | `MP.GAME.enemy.spent_in_shop[MP.GAME.pincher_index]` | `calc_dollar_bonus = floor(spent / 3)` ($3 per). Unlocks (`in_pool`) only when `MP.GAME.pincher_unlock` (first PvP reached). (penny_pincher.lua:30-33,24) |
| **Taxes** | `MP.GAME.enemy.sells_per_ante` | On `setting_blind` of `bl_mp_nemesis`: `mult += sells_this_ante * 4` (accumulates earlier antes' sells if PvP not yet reached). Gives `mult` on `joker_main`. (taxes.lua:1-12,39-54) |
| **Skip Off** | `G.GAME.skips`, `MP.GAME.enemy.skips` | `skip_diff = max(my.skips - enemy.skips, 0)`; grants `skip_diff` extra hands and discards on `setting_blind` via `ease_hands_played`/`ease_discard`. (skip_off.lua:42-55) — this is the **"unlocked hands"** mechanic: hands/discards above the round default granted from the skip differential. |
| **Let's Go Gambling** | (nemesis relay) | `action_lets_go_gambling_nemesis` gives opponent `ease_dollars(nemesis_dollars or 5)` (action_handlers.lua:626-629). |

Jokers that **react to losing a PvP round** read the calculate context, not
enemy state directly:
- `context.mp_pvp_loss` + `context.mp_hands_left` is fired by
  `action_end_pvp` (action_handlers.lua:407-413) **only when**
  `lost and pvpTimerLost` and `hands_left > 0`:
  `SMODS.calculate_context({ mp_pvp_loss = true, mp_hands_left = hands_left })`.
- Ice Cream / Seltzer consume `mp_hands_left` as the number of hands to decrement
  (ice_cream.lua:7-8, seltzer.lua:19-47) — only `if MP.is_layer_active("pvp_timer")`.

`MP.UTILS.add_nemesis_info(info_queue)` (lib/ui.lua:138) attaches the "reads
nemesis state" tooltip; Conjoined/Penny/Taxes/Skip-Off all call it.

### 1.9 Lobby/config knobs that drive PvP (all syncable, action_handlers.lua:505-512)

`starting_lives` (4), `pvp_start_round` (2), `showdown_starting_antes` (3),
`pvp_countdown_seconds` (3), `timer_base_seconds` (150),
`timer_increment_seconds` (60), `normal_bosses`, `different_seeds` (false),
`death_on_round_loss` (true), `gold_on_life_loss` (true),
`no_gold_on_round_loss` (false), `multiplayer_jokers` (true).
Life-loss → Comeback Gold: `action_player_info` (:426-439) bumps `comeback_bonus`
when `gold_on_life_loss` and a life was actually lost.

### 1.10 0.3.3 → 0.4.0 deltas (CHANGELOG.md) relevant to PvP

- **Comeback Gold** — "Now awarded on **any life loss again**, not just PvP boss
  losses" ($4, or $2 on higher stakes). This matches `action_player_info`'s
  unconditional `gold_on_life_loss` bump. **Our engine must award comeback gold on
  every life loss, not only Nemesis losses.**
- **Speedrun** — "Out of rotation" in Standard Ranked (still exists; the 30s
  second-player grant logic is unchanged server-side).
- Other 0.4.0 deltas (To Do List, Golden Ticket, Justice, Gold Card, Hanging Chad,
  Match Replays) are not PvP-resolution rules; tracked in the joker/economy docs.
- **Server is still 0.3.x-shaped**: uses blind key `bl_pvp`, hard-codes Showdown
  lives = 2, has no `bl_mp_nemesis` mapping. The 0.4.0 client source wins.

---

## 2. How OUR engine does it today

Source: `D:/NewServer/src/main/java/com/balatro/engine/game/Match.java`.

- **`Match`** is a 2-player same-seed coordinator. Each `Side` holds its own
  authoritative `Run` and an `int lives`. Phases: WAITING → AGREEING (host
  proposes a `Ruleset`, guest accepts) → PLAYING → FINISHED (Match.java:27,
  156-208).
- **Lives**: hard-coded `STARTING_LIVES = 4` and `PVP_FROM_ANTE = 2`
  (Match.java:38-39). Set on both sides at `startPlaying` (:214-219). No gamemode
  concept at all — it is implicitly Attrition-with-fixed-constants.
- **Nemesis resolution** (`resolveNemesisIfDecided`, :112-140): when both runs
  report `inPvpBlind()`, it races `state.roundScore` (a `long`). A player who is
  `PVP_PENDING` (out of hands) and **behind** loses a life immediately; both out
  of hands → lower score loses; tie → nobody. Then `endPvp()` both sides, push
  views, finish if a side hits 0 lives.
- **Opponent summary** (`opponentSummary`, :236-246): sends `playerId, lives,
  ante, blind, roundScore, money, phase`. This is the only opponent state our
  jokers could read today.
- **Blind-fail** (non-PvP) costs a life unconditionally (:90-101).
- Transport-agnostic `(sessionId, payload)` sink; no client-supplied score.

What exists in support: `Run`, `RunState`, `Blinds`, `BossBlind`, `BossCatalog`,
`Ruleset`/`RulesetStore` (proposal flow), `ScoringEngine`. Joker eval:
`EvaluationContext`, `JokerEffect`, data-driven `JokerDef`.

---

## 3. The GAP

1. **No gamemode abstraction.** Attrition/Showdown/Survival do not exist; the
   blind schedule and lives are hard constants. We can't represent Showdown's
   "all-three-blinds PvP from ante N", Survival's "no PvP, furthest-blind win",
   or the `normal_bosses` toggle.
2. **Lives are a constant, not a lobby/gamemode option.** Need `starting_lives`,
   `pvp_start_round`, `showdown_starting_antes`, `normal_bosses` as ruleset/lobby
   params (defaults 4 / 2 / 3 / false).
3. **Score type mismatch.** We race a `long roundScore`; BMP uses `InsaneInt`
   (e/coeff/exp) because Talisman scores overflow 64-bit. PvP comparisons must use
   a big-number type.
4. **Win/loss resolution is incomplete vs server semantics:**
   - No "out of hands AND behind = immediate loss; ahead player needn't finish"
     wired to a real `handsLeft` counter (we infer from `PVP_PENDING`).
   - No **PvP timer** loss path (`failPvPTimer` → life loss + `mp_pvp_loss`/
     `mp_hands_left` context).
   - No **Survival furthest-blind** comparison or draw rule.
   - No **first-ready / 30s Speedrun** timing.
5. **No opponent ("nemesis") state model for jokers.** `opponentSummary` lacks
   the fields jokers actually read: `enemy.hands`, `enemy.skips`,
   `enemy.spent_in_shop[]`, `enemy.sells_per_ante{}`, `enemy.lives`,
   `enemy.highest_score`, plus `pincher_index`/`pincher_unlock`. Without these,
   Defensive Joker, Conjoined, Penny Pincher, Taxes, Skip Off cannot be evaluated.
6. **No Nemesis blind type.** We have `BossBlind`/`BossCatalog` but no PvP blind
   with `dollars=5`, no chip target, "target = opponent score", `in_pool=false`,
   and `isPvpBoss()` predicate.
7. **No `mp_pvp_loss` / `mp_hands_left` evaluation context** for Ice
   Cream/Seltzer-style decay on a PvP loss.
8. **Banned/reworked pools per gamemode** are not modeled.
9. **Comeback Gold on any life loss** (0.4.0) not implemented.

---

## 4. Proposed target design (authoritative, queue-shaped, ruleset-driven)

### 4.1 `Gamemode` as a first-class, data-driven object

Add `engine/game/Gamemode.java` (interface) + a registry, mirroring
`MP.Gamemode`'s required params:

```java
public interface Gamemode {
  String key();                       // "attrition" | "showdown" | "survival"
  int startingLives(LobbyConfig cfg); // 4 / 4 / 1
  /** Returns the blind keys for an ante; null entry => normal vanilla blind. */
  BlindSchedule blindsForAnte(int ante, LobbyConfig cfg);
  WinResolver winResolver();          // PvP-score race vs furthest-blind
  Set<String> bannedJokers(), bannedVouchers(), bannedTags(), bannedBlinds(), ...;
}
```

`BlindSchedule = (smallKey, bigKey, bossKey)`, each nullable; `null` = normal.
- **Attrition**: ante < pvpStart → all null; else boss = `bl_mp_nemesis`
  (unless `normalBosses`, then keep boss + flag PvP-choice).
- **Showdown**: ante <= showdownStart → all null; else all three = `bl_mp_nemesis`.
- **Survival**: always all null.

Drive bans/lives/schedule from `LobbyConfig` (new) carrying the synced knobs:
`startingLives, pvpStartRound, showdownStartingAntes, normalBosses,
deathOnRoundLoss, goldOnLifeLoss, noGoldOnRoundLoss, differentSeeds,
multiplayerJokers, pvpCountdownSeconds, timerBaseSeconds,
timerIncrementSeconds`. Defaults exactly per core.lua:171-200.

`Match` selects the `Gamemode` from `LobbyConfig.gamemode` instead of using
`PVP_FROM_ANTE`/`STARTING_LIVES` constants.

### 4.2 The Nemesis (PvP) blind

`engine/game/PvpBlind.java` (or a flag on `Blinds`): key `bl_mp_nemesis`,
`reward = 5`, **no chip target** (`target = opponentScore`), `inPool = false`,
`isPvpBoss()` predicate = `blind.key == "bl_mp_nemesis" || blind.pvp`. `Run`
exposes `inPvpBlind()` already; keep it but back it with the blind key.

### 4.3 Big-number score

Introduce `Score` (port of `InsaneInt`: sign/coeff/exp or BigDecimal+exp) and use
it for `roundScore` and all PvP comparisons. Provide `greaterThan/equalTo`.

### 4.4 Authoritative PvP duel state machine (per blind)

Per-blind, server-driven, mirroring `readyBlind`/`startBlind`/`playHand`/`endPvP`:

```
SELECTING_BLIND
  -- each side intent: READY_BLIND
  -- first ready -> grant Speedrun; firstReadyAt = now
  -- both ready  -> reset side.roundScore=0, side.handsLeft=roundHandCount,
                    firstPlayer = whoever firstReady,
                    second-ready-within-30s also gets Speedrun -> START_BLIND
IN_PVP_BLIND
  -- each PLAY_HAND advances side.roundScore and decrements side.handsLeft
  -- decide when (handsLeft<1 && behind) OR (both handsLeft<1):
       tie -> no life lost; both END_PVP
       else loser.lives--; if any lives<=0 -> matchResult; else END_PVP(loser lost)
  -- PvP timer expiry -> failPvpTimer: that side.lives--; END_PVP(pvpTimerLost)
                         and fire mp_pvp_loss/mp_hands_left to that side's jokers
SHOP / next ante
```

`handsLeft` becomes a real per-side counter (default round hand count, **modified
by Skip Off's unlocked hands** — see 4.6). The "ahead player needn't finish"
short-circuit (Match.java:122-127) is correct and stays.

Survival overrides `winResolver()` to compare `furthestBlind` on death, with the
both-at-0-equal = draw (both win) rule (actionHandlers.ts:353-370).

### 4.5 Opponent ("nemesis") state — the joker-visible contract

Add `OpponentView` carried in `EvaluationContext` and refreshed every action,
with **exactly** the BMP `MP.GAME.enemy` fields jokers read:

```java
record OpponentView(
  Score score, Score highestScore,
  int handsLeft,                 // enemy.hands
  int skips,                     // enemy.skips
  int lives,                     // enemy.lives
  int sells,                     // enemy.sells (total)
  Map<Integer,Integer> sellsPerAnte,    // enemy.sells_per_ante[ante]
  List<Integer> spentInShop,            // enemy.spent_in_shop[]  (per visit, appended)
  String location
) {}
```

Plus self-side PvP scalars on `RunState`: `pincherIndex` (init **-3**, ++ each
end-of-round), `pincherUnlock` (false until first PvP reached), `lives`,
`pvpReached`, `pvpReachedFirst`, `comebackBonus`.

`Match.opponentSummary` is extended to populate `OpponentView`. The summary must
be pushed **after every action** (already the pattern) so update-driven jokers
(Defensive, Conjoined, Skip Off) recompute against fresh values.

### 4.6 Joker effects against nemesis state (data-driven rules)

Each MP joker becomes a `JokerDef` reading `OpponentView` via new context keys.
Exact formulas (Section 1.8) to encode:
- **Defensive Joker**: `chipMod = max((opp.lives - self.lives) * (stake>=6?75:125), 0)`.
- **Conjoined**: `xMult = clamp(1 + opp.handsLeft*0.5, 1, 3)`, applied only when
  `isPvpBoss()`.
- **Penny Pincher**: `dollarBonus = floor(opp.spentInShop[pincherIndex] / 3)`;
  in-pool only when `pincherUnlock`.
- **Taxes**: on entering `bl_mp_nemesis`, `mult += sellsThisAnte*4`
  (sum earlier antes if PvP not yet reached); apply `mult` on main.
- **Skip Off**: `skipDiff = max(self.skips - opp.skips, 0)`; grant `skipDiff`
  extra **hands and discards** for the upcoming blind ("unlocked hands"). This
  feeds `handsLeft` in 4.4.
- **mp_pvp_loss context**: on a PvP **timer** loss with `handsLeft>0`, fire
  `{ mpPvpLoss=true, mpHandsLeft=handsLeft }` to that side's jokers (Ice
  Cream/Seltzer decay).

### 4.7 Economy hooks

- **Comeback Gold on ANY life loss** (0.4.0): when `goldOnLifeLoss` and a life was
  lost, increment a comeback bonus (paid next shop). Amount $4 (or $2 on higher
  stakes) — verify exact stake threshold in economy doc.
- **Nemesis win reward**: $5 (blind `dollars`). `no_gold_on_round_loss` zeroes the
  blind reward on a round loss.

### 4.8 Server key compatibility

Internally use one PvP-blind concept; when speaking the legacy protocol, map
`bl_mp_nemesis <-> bl_pvp`. Prefer the 0.4.0 client key on the wire going forward.

---

## Open questions

1. **Showdown lives**: client info menu says 4, lobby default is 4, but the
   reference TS server hard-codes 2. Which is authoritative at runtime in 0.4.0?
   (Lobby option almost certainly wins — needs a live capture to confirm.)
2. **PvP score tie on a non-final blind**: server gives both `endPvP` with no life
   lost — confirmed. But does either side still get the $5 Nemesis reward on a
   tie? (Blind `dollars=5` is per-side; verify both are paid.)
3. **Comeback Gold exact amounts/threshold** in 0.4.0 ($4 vs $2 on "higher
   stakes" — which stake index?). CHANGELOG says unchanged from prior; confirm
   value and threshold in the economy layer source.
4. **`handsLeft` reset value**: server resets to `4` on both-ready
   (actionHandlers.ts:189-190) regardless of deck/ruleset hand count. Is 4 always
   correct, or is it overwritten by the client's actual round hand count (incl.
   Skip Off / Turtle Bean)? The number sent in `playHand` is the truth; 4 is just
   the server's optimistic reset.
5. **Timer mechanics** (`timer_base_seconds`/`increment`, pressure/no-anim/pvp
   timer layers) — out of scope here but the `failPvpTimer` life-loss path depends
   on them; specced separately.
6. **`normal_bosses` interaction** with Nemesis: when true, Attrition keeps a real
   boss AND flags `pvp_blind_choices.Boss` — does PvP still happen that ante, or
   is the boss purely solo? Needs a play-test trace.
7. **Different seeds** (`different_seeds`/`different_decks`): our Match assumes one
   shared seed. How does score-race fairness work when seeds differ?

## New building blocks needed

- `engine/game/Gamemode.java` (interface) + `AttritionMode`, `ShowdownMode`,
  `SurvivalMode`, and a `GamemodeRegistry`.
- `engine/game/LobbyConfig.java` — synced knobs with BMP defaults
  (starting_lives=4, pvp_start_round=2, showdown_starting_antes=3,
  normal_bosses=false, death_on_round_loss=true, gold_on_life_loss=true, …).
- `engine/game/BlindSchedule.java` — `(small,big,boss)` nullable keys per ante.
- `engine/game/PvpBlind.java` (or `pvp` flag + key on `Blinds`) — `bl_mp_nemesis`,
  reward 5, no chip target, `inPool=false`, `isPvpBoss()`.
- `engine/scoring/Score.java` — InsaneInt-equivalent big-number with
  `greaterThan/equalTo/fromString/toString`.
- `engine/game/WinResolver.java` — `PvpScoreRaceResolver` and
  `FurthestBlindResolver` (Survival, incl. draw rule).
- `engine/game/OpponentView.java` — the exact `MP.GAME.enemy` field set
  (score, highestScore, handsLeft, skips, lives, sells, sellsPerAnte,
  spentInShop[], location).
- `RunState` additions: `lives`, `handsLeft`, `skips`, `spentInShopHistory`,
  `sellsPerAnte`, `pincherIndex(=-3)`, `pincherUnlock`, `pvpReached`,
  `pvpReachedFirst`, `furthestBlind`, `comebackBonus`.
- `EvaluationContext` additions: `opponent` (OpponentView), `isPvpBoss`,
  `mpPvpLoss`, `mpHandsLeft`, `settingBlind`+blindKey, `pincherIndex`.
- PvP duel state machine in `Match` (READY → START → PLAY_HAND loop → END_PVP /
  matchResult), incl. first-ready Speedrun grant + 30s second-player window, and
  the `failPvpTimer` life-loss path.
- Per-gamemode **banned/reworked pools** wired into shop/pack generation.
- **Comeback-gold-on-any-life-loss** economy hook.
- Protocol mapping `bl_mp_nemesis <-> bl_pvp` for legacy server compatibility.
