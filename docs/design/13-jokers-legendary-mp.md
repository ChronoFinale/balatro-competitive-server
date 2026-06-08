# 13 — Legendary Jokers + Balatro-Multiplayer 0.4.0 Exclusive Jokers

Definitive catalogue for **Balatro Multiplayer (BMP) 0.4.0** (the version currently on disk at
`C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/`). Every claim is grounded in a
real source file (path cited inline). Where an exact number could not be found on disk it is
flagged **(unverified)**.

This section assesses each joker against **our** `com.balatromp.engine.joker` / `joker/def` algebra
(triggers / conditions / values / effect-ops / state-mutations) and marks it **EXPRESSIBLE** or
**NEEDS: \<new building blocks\>**.

---

## Part A — Vanilla Legendary Jokers (rarity 4, Soul-card pool)

The five Legendaries are **pure vanilla** in BMP 0.4.0. BMP does **not** redefine, rebalance, or
ban them; the only BMP touch-point is the score-preview *simulation* layer
(`compatibility/Preview/Jokers/_Vanilla.lua`), which mirrors vanilla behavior and explicitly marks
Chicot/Perkeo as "Effect not relevant (Meta/Blind)" — i.e. the preview simulator ignores them, but
the real run uses stock vanilla logic.

Center definitions (rarity, cost, config) confirmed from the decompiled base game:
`C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua:531-535`. All five are
`rarity = 4, cost = 20`, `unlocked=false, discovered=false` (Soul/Legendary pool only).

| Name | Key | Rarity / Cost | Exact effect | Config (dump) | Trigger(s) | PvP / nemesis behavior |
|------|-----|---------------|--------------|---------------|------------|------------------------|
| Canio | `j_caino` (note: internal key is misspelled **caino**) | Legendary / $20 | This Joker gains **X1 Mult** when a face card is destroyed (no upper limit). | `config = {extra = 1}`; live counter `ability.caino_xmult` | `ON_SCORED`/`global` X-mult application; gain on **CARD_DESTROYED** (face card) | None — purely local. Same in 1v1. |
| Triboulet | `j_triboulet` | Legendary / $20 | Played **Kings and Queens** each give **X2 Mult** when scored. | `config = {extra = 2}` | `ON_SCORED` (per scoring card), condition rank ∈ {K, Q} | None — local. |
| Yorick | `j_yorick` | Legendary / $20 | This Joker gains **X1 Mult** per **23 cards discarded**; counter resets to 23 each trigger. | `config = {extra = {xmult = 1, discards = 23}}`; live `ability.yorick_discards` | `ON_DISCARD` decrements counter; at 0 grants X-mult (`JOKER_MAIN`/global X-mult thereafter) | None — local. Counts the player's own discards only. |
| Chicot | `j_chicot` | Legendary / $20 | **Disables the effect of every Boss Blind.** `blueprint_compat = false`. | `config = {}` | `BLIND_SELECTED` (disable boss-blind effect) | **PvP-relevant indirectly:** the BMP PvP/nemesis blind is `bl_mp_nemesis` (see `objects/blinds/nemesis.lua`). Whether Chicot nullifies the nemesis-blind's debuff is **unverified** — `bl_mp_nemesis` is a custom blind; needs a live test. Flag for QA. |
| Perkeo | `j_perkeo` | Legendary / $20 | At end of shop, creates a **Negative** copy of **1 random consumable** in your possession. | `config = {}` | `SHOP_EXIT` (create negative consumable copy) | None — local. |

**Sources:** `lovely/dump/game.lua:531-535` (centers); BMP sim
`compatibility/Preview/Jokers/_Vanilla.lua:919-949` (`simulate_caino/triboulet/yorick/chicot/perkeo`).
Effect numbers (X1/face, X2 K&Q, X1/23 discards, boss-disable, negative consumable) are stock
vanilla and corroborated by the dump config.

### Legendary expressibility against our algebra
- **Canio** → NEEDS: `CARD_DESTROYED`-driven state gain reading destroyed-card face-ness (we have
  `CARD_DESTROYED` trigger + `ScoredIsFace`, but the destroyed card is not the *scored* card — need a
  **destroyed-card property condition**), plus persistent `XMULT` from `State(var)`. Partially
  expressible: `ADD` on `CARD_DESTROYED` + condition on destroyed card's face status (**new: condition reads the destroyed card**) → `XMULT = State(caino_xmult)` via `JOKER_MAIN`.
- **Triboulet** → **EXPRESSIBLE**: `ON_SCORED` + `ScoredRankBetween(12,13)` (or `ScoredIsFace` filtered to K/Q) → `XMULT = Const(2)` per scoring card.
- **Yorick** → **EXPRESSIBLE** (modulo cosmetics): `ON_DISCARD` state `ADD 1`; a `StateAtLeast(23)`
  condition gates `XMULT` gain + `RESET`. Our counter direction differs (we count up to 23 vs vanilla counting down) but is equivalent.
- **Chicot** → NEEDS: **disable/modify-blind effect** (we have no op to nullify a blind/debuff).
- **Perkeo** → NEEDS: **create card/consumable** + **edition/negative effects** (create a Negative copy of a random consumable on `SHOP_EXIT`).

---

## Part B — BMP 0.4.0 Exclusive Jokers ("Multiplayer Jokers")

All live in `objects/jokers/*.lua` (top level = the canonical MP exclusive set; subfolders
`sandbox/`, `standard/`, `experimental/` are reworked-vanilla variants, not original MP jokers).
Loaded via `core.lua:310` (`MP.load_mp_dir("objects/jokers")`).

Common scaffolding shared by all (verified in each file):
- `mp_include = function(self) return MP.LOBBY.code and MP.LOBBY.config.multiplayer_jokers end`
  — only enter the pool in an online lobby with the **Multiplayer Jokers** lobby option on
  (`config.lua` default `multiplayer_jokers = true`, `core.lua:194`).
- Internal keys are prefixed **`j_mp_`** by SMODS (e.g. `key = "defensive_joker"` → `j_mp_defensive_joker`).
- "Nemesis" = the opponent. Tooltip injected by `MP.UTILS.add_nemesis_info` (`lib/ui.lua:138`),
  which reads `MP.LOBBY.guest.username` / `MP.LOBBY.host.username`.
- "PvP Blind" detection = `MP.is_pvp_boss()` → true iff `G.GAME.blind` is `bl_mp_nemesis` or has
  `blind.pvp` (`objects/blinds/nemesis.lua:32-35`).
- Several jokers ship a **phantom** mirror to the opponent via `MP.ACTIONS.send_phantom(key)` /
  `remove_phantom` (`networking/action_handlers.lua:1160-1172`). Phantom copies are skipped in
  their own `calculate` via the `card.edition.type ~= "mp_phantom"` guard.

### B.1 Full table

| Name | Key | Rarity / Cost | Exact effect (real numbers) | Trigger(s) | PvP / nemesis mechanic |
|------|-----|---------------|------------------------------|-----------|------------------------|
| **Defensive Joker** | `j_mp_defensive_joker` | Common (r1) / $4 | **+125 Chips** for every life you have *fewer* than your Nemesis (**+75** on Stake ≥ 6 / Gold+). `t_chips = max((enemy.lives - your.lives) * chips, 0)`. | `update` recomputes `t_chips`; `JOKER_MAIN` applies `chip_mod`. | Reads `MP.GAME.enemy.lives` vs `MP.GAME.lives`. Scales with you being *behind* in lives. |
| **Conjoined Joker** | `j_mp_conjoined_joker` | Uncommon (r2) / $6 | While in a PvP Blind: **X0.5 Mult per Hand the Nemesis has left**, `x_mult = clamp(1 + enemy.hands*0.5, 1, 3)` → **max X3**. | `update` recomputes; `JOKER_MAIN` applies `x_mult` **only when `MP.is_pvp_boss()`**. | Reads `MP.GAME.enemy.hands`. Sends a **phantom** to opponent (`send_phantom("j_mp_conjoined_joker")`). Excluded from pool when `sandbox` layer active. |
| **Pizza** | `j_mp_pizza` | Common (r1) / $4 | At **end of the next PvP Blind**: consume itself, grant **+2 discards to you** and **+1 discard to your Nemesis** for the ante. `blueprint_compat=false, eternal_compat=false`. | `calculate` on `context.mp_end_of_pvp`: bumps `round_resets.discards`, `ease_discard(2)`, `MP.ACTIONS.eat_pizza(1)` to nemesis, then `remove_from_deck` + dissolve. | Self-destructs; sends `eatPizza` action giving the opponent **+1** discard (`action_handlers.lua:632-637`). `mp_end_of_pvp` raised via the `after_pvp` hack (`ui/game/game_state.lua:15`). |
| **Let's Go Gambling** | `j_mp_lets_go_gambling` | Uncommon (r2) / $6 | **1 in 4** chance: **X4 Mult and +$10**. Additionally, in a PvP Blind, **1 in 4** chance to give your **Nemesis $10** (the "misfire", `k_oops`). | `JOKER_MAIN`: `SMODS.pseudorandom_probability(card, "j_mp_lets_go_gambling", 1, 4)` for the X4/$10; second roll gated on `MP.is_pvp_boss()`. | Misfire → `MP.ACTIONS.lets_go_gambling_nemesis()` gives nemesis `nemesis_dollars=10` (`action_handlers.lua:626-630`, juices the phantom). Sends phantom. |
| **Penny Pincher** | `j_mp_penny_pincher` | Common (r1) / $4 | At end of round, earn **$1 for every $3** your Nemesis spent in the *corresponding* shop last ante. `calc_dollar_bonus = floor(enemy_spent / 3)`. `blueprint_compat=false`. | `calc_dollar_bonus` (END_OF_ROUND dollar payout). | Reads `MP.GAME.enemy.spent_in_shop[MP.GAME.pincher_index]`. **`in_pool` gated on `MP.GAME.pincher_unlock`** (set true once a PvP blind is reached — effectively ante ≥ pvp_start_round; comment warns not to use `ante>=3` because The Order zeroes ante). `pincher_index` advances each end-of-round (`lovely/end_round.toml:137`). |
| **SPEEDRUN** | `j_mp_speedrun` | Uncommon (r2) / $6 | If you reach a PvP Blind **within 30s of your Nemesis**, create a **random Spectral card** (must have consumable room). | `calculate` on `context.mp_speedrun` → `create_card("Spectral",…,"mp_speedrun")`. | `mp_speedrun` raised server-side via `action_speedrun()` (`action_handlers.lua:575-577`). Sends phantom. **0.4.0 DELTA:** per `CHANGELOG.md:11`, Speedrun is **"Out of rotation"** in Standard Ranked (still in code, but not pooled in the Standard ruleset). |
| **Pacifist** | `j_mp_pacifist` | Common (r1) / $4 | **X10 Mult while NOT in a PvP Blind.** | `JOKER_MAIN` applies `x_mult=10` only when `not MP.is_pvp_boss()`. | Inverse of Conjoined — turns OFF during PvP. No phantom. |
| **Taxes** | `j_mp_taxes` | Common (r1) / $5 | Gains **+4 Mult per card the Nemesis sold** since the last PvP Blind; the gain is **committed when the PvP (nemesis) Blind is selected**. `perishable_compat=false`. | `JOKER_MAIN` applies running `mult`; `setting_blind` + `blind.key=="bl_mp_nemesis"` commits `mult += sells*4`. | Reads `MP.GAME.enemy.sells_per_ante[ante]` (before first PvP, accumulates antes 1..ante-1). Enemy sells tracked by `action_sold_joker` (`action_handlers.lua:618-624`). **0.4.0 note:** the sold-joker action now fires on *any* card sold (HACK comment, line 619). |
| **Skip-Off** | `j_mp_skip_off` | Uncommon (r2) / $5 | **+1 Hand and +1 Discard per additional Blind skipped vs your Nemesis.** `hands = max(your_skips - enemy_skips,0)*1`, same for discards. `blueprint_compat=false`. | `update` recomputes from skip diff; `setting_blind` → `ease_hands_played(hands)` + `ease_discard(discards)`. | Reads `G.GAME.skips` vs `MP.GAME.enemy.skips`; tooltip shows ahead/tied/behind (`a_mp_skips_ahead/tied/behind`). |

### B.2 Per-joker exact config (from source, for our number tables)

- Defensive: `config.extra = { extra = 125, highstake = 75 }`, `t_chips` live. (`defensive_joker.lua:18`)
- Conjoined: `extra = { x_mult_gain = 0.5, max_x_mult = 3, x_mult = 1 }`. (`conjoined_joker.lua:18`)
- Pizza: `extra = { discards = 2, discards_nemesis = 1 }`. (`pizza.lua:18`)
- Let's Go Gambling: `extra = { odds = 4, xmult = 4, dollars = 10, nemesis_odds = 4, nemesis_dollars = 10 }`. (`lets_go_gambling.lua:18`)
- Penny Pincher: `extra = { dollars = 1, nemesis_dollars = 3 }` (note `nemesis_dollars` is the **divisor**, $3). (`penny_pincher.lua:18`)
- SPEEDRUN: no `config.extra` (binary proc). (`speedrun.lua`)
- Pacifist: `extra = { x_mult = 10 }`. (`pacifist.lua:18`)
- Taxes: `extra = { mult_gain = 4, mult = 0 }`. (`taxes.lua:31`)
- Skip-Off: `extra = { hands = 0, discards = 0, extra_hands = 1, extra_discards = 1 }`. (`skip_off.lua:18`)

### B.3 0.4.0 vs 0.3.3 deltas (from `CHANGELOG.md`)

The 0.4.0 changelog touches **none** of the MP-exclusive joker *numbers* except:
- **SPEEDRUN — "Out of rotation"** in Standard Ranked (`CHANGELOG.md:11`). Code/effect unchanged;
  it is simply not pooled in the Standard ruleset. (0.3.0 had given it the 30s/2-player behavior,
  `CHANGELOG.md:43`.)
- Everything else in 0.4.0's joker section is **vanilla** rework (To Do List, Golden Ticket,
  Ouija/Ectoplasm cost fix, Hanging Chad in Legacy) — none of the nine exclusives above.
- The **ranked spreadsheet** (`xlsx_out2/05_Jokers.txt`) is a **0.3.3 baseline**; where it conflicts
  with the 0.4.0 source files above, the 0.4.0 source wins (e.g. Defensive +125/+75, Conjoined
  X0.5→max X3, Taxes +4/sold). No 0.3.3→0.4.0 numeric change to the exclusives was found beyond
  Speedrun rotation.

### B.4 Expressibility against our JokerDef algebra

| Joker | Verdict | Notes |
|-------|---------|-------|
| Defensive Joker | **NEEDS:** read-nemesis (opponent lives), stake-dependent constant | `JOKER_MAIN` + `CHIPS = Const(125)*…` is shape-fine, but the multiplier source is `enemy.lives - my.lives` → requires a **NEMESIS run-var** (`NEMESIS_LIVES`) and **our `LIVES`** run-var (we have neither). Also a `StakeAtLeast(6)` switch (125 vs 75) → **new condition**. |
| Conjoined Joker | **NEEDS:** read-nemesis (`NEMESIS_HANDS_LEFT`), value clamp (min/max), PvP-only condition | `XMULT = clamp(1 + 0.5*NEMESIS_HANDS, 1, 3)` gated on `InPvP`. We have `XMULT` op but no nemesis read, no clamp, no `InPvP` condition. |
| Pizza | **NEEDS:** self-destruct, modify-deck (discards-this-ante), grant-resource-to-nemesis, `END_OF_PVP` trigger | `+2 discards self / +1 nemesis`, then `SELL_SELF`/destroy. None of {grant discards, push effect to opponent, end-of-pvp trigger} exist. |
| Let's Go Gambling | **NEEDS:** probabilistic proc, grant-money-to-nemesis, `InPvP` condition | We can express deterministic `XMULT/DOLLARS`, but **probabilistic procs** and the nemesis-money side-effect are new. |
| Penny Pincher | **NEEDS:** read-nemesis (per-ante shop spend), end-of-round dollar payout from external source | `DOLLARS = floor(NEMESIS_SPENT_LAST_SHOP / 3)`. Needs a **nemesis-spend run-var indexed by ante** + integer-division value. |
| SPEEDRUN | **NEEDS:** create-consumable, timing/queue-driven proc (`mp_speedrun`), consumable-room check | Create random Spectral on a server-pushed timing event. All new. |
| Pacifist | **NEEDS:** PvP-only (negated) condition | Otherwise trivial: `JOKER_MAIN` + `XMULT = Const(10)` gated on `Not(InPvP)`. Only the `InPvP` condition is missing. |
| Taxes | **NEEDS:** read-nemesis (sold count since last PvP), commit-on-PvP-blind trigger, persistent state | `MULT += 4*NEMESIS_SELLS` committed on `BLIND_SELECTED(bl_mp_nemesis)`. We have state `ADD` + `MULT` from `State(var)`, but the source (nemesis sells per ante) and the nemesis-blind-specific `BLIND_SELECTED` condition are new. |
| Skip-Off | **NEEDS:** read-nemesis (`NEMESIS_SKIPS`) + our `SKIPS` run-var, grant hands/discards on blind-select | `+1 hand / +1 discard per (my_skips - enemy_skips)`. We track neither skips nor the resource-grant op. |

**Pattern:** the entire MP-exclusive set is **PvP/nemesis-coupled**. The single largest missing
primitive is **"read the nemesis"** (opponent lives, hands-left, skips, shop-spend, sold-count),
followed by **"push an effect onto the nemesis"** (give money/discards), **`InPvP` / nemesis-blind
conditions**, **probabilistic procs**, and **create-consumable / self-destruct**.

---

## Open questions

1. **Chicot vs `bl_mp_nemesis`** — Does Chicot ("disable Boss Blind effect") nullify the BMP PvP
   nemesis blind's debuff/score-target? `bl_mp_nemesis` is a custom blind (`objects/blinds/nemesis.lua`);
   vanilla Chicot only disables `boss` blinds. Needs a live test. **(unverified)**
2. **Perkeo / SPEEDRUN consumable creation in PvP** — does the consumable-buffer/room check
   (`speedrun.lua:29`) interact with the disabled-overlay `poll_edition` override
   (`action_handlers.lua:570-573`)? Confirm Negative editions can still roll mid-PvP.
3. **Taxes "sold" semantics (0.4.0)** — the `soldJoker` action now fires on *any* card sale
   (HACK, `action_handlers.lua:619`). Confirm consumables/playing-cards sold by the nemesis count
   toward Taxes mult, not just jokers — the name implies jokers but the code counts all sales.
4. **Penny Pincher indexing** — `pincher_index` starts at `-3` (`core.lua:248`) and increments at
   end of round (two sites: `lovely/end_round.toml:137` and `ui/game/functions.lua:99`). Verify the
   index lines up the *corresponding* shop with the nemesis's spend array; the double-increment
   path needs confirmation it isn't double-counting.
5. **Stake threshold for Defensive** — confirmed `G.GAME.stake >= 6` (Gold Stake) switches +125→+75.
   Verify BMP's `alt_stakes` experimental flag (`core.lua:66`) doesn't reorder stake indices.
6. **Phantom self-exclusion** — every phantom-bearing joker guards `calculate` with
   `card.edition.type ~= "mp_phantom"`. Confirm none of the nine double-fire on the owner when a
   Blueprint copies them (Pizza/Penny Pincher/Skip-Off set `blueprint_compat=false`; the others are
   `true`).

## New building blocks needed

To express the Legendary + MP-exclusive set in our `JokerDef` algebra, add:

1. **NEMESIS reads (PvP run-vars):** `NEMESIS_LIVES`, `NEMESIS_HANDS_LEFT`, `NEMESIS_SKIPS`,
   `NEMESIS_SELLS_SINCE_PVP`, `NEMESIS_SHOP_SPEND(ante)`, plus our own `LIVES` and `SKIPS` run-vars.
2. **Push-effect-to-nemesis op:** grant the opponent money / discards (Pizza +1 discard, Let's Go
   Gambling +$10).
3. **`InPvP` / blind-key conditions:** `InPvPBlind`, `Not(InPvPBlind)`, and a
   `BlindKeyIs("bl_mp_nemesis")` condition for `BLIND_SELECTED`/`setting_blind` triggers.
4. **`END_OF_PVP` trigger** (the `mp_end_of_pvp` / `after_pvp` hook) and a server-timing
   **`SPEEDRUN`/`mp_speedrun` proc trigger**.
5. **Probabilistic procs:** `Chance(numerator, denominator, seed_key)` value/condition wrapping
   `SMODS.pseudorandom_probability` (Let's Go Gambling, and vanilla luck jokers generally).
6. **Value clamp:** `Clamp(value, min, max)` (Conjoined X-mult cap at 3).
7. **Stake condition:** `StakeAtLeast(n)` (Defensive +125 vs +75).
8. **Integer-division value:** `Floor(Div(a, b))` (Penny Pincher floor(spend/3)).
9. **Create-card / create-consumable op** with **edition control (Negative)** — Perkeo (Negative
   consumable copy), SPEEDRUN (random Spectral), incl. consumable-room precondition.
10. **Self-destruct / SELL_SELF-as-consume op** — Pizza removes itself after firing.
11. **Disable/modify-blind op** — Chicot (nullify boss-blind effect).
12. **Resource-grant on blind-select** (`ease_hands_played` / `ease_discard`) — Skip-Off, Pizza,
    SPEEDRUN-adjacent.
13. **Destroyed-card property condition** — Canio (gain X-mult when a *face* card is destroyed; the
    destroyed card is not the scored card).
