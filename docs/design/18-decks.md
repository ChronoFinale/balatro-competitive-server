# 18 — Decks (Backs / `b_*`)

**Target version:** Balatro Multiplayer (BMP) **0.4.0**, base game decompiled dump.

A *Deck* in Balatro is internally a **Back** center (`b_*`, `set = "Back"`). Its starting
modifiers are expressed two ways:

1. **`config = {…}`** — a declarative table of starting parameters that the engine reads in
   `Back:apply_to_run()` (e.g. `hands`, `discards`, `dollars`, `joker_slot`, `voucher`,
   `vouchers`, `consumables`, `consumable_slot`, `ante_scaling`, `spectral_rate`,
   `remove_faces`, `randomize_rank_suit`). These map onto `G.GAME.starting_params` /
   `G.GAME.round_resets` / `G.GAME.modifiers`.
2. **`apply = function(self) … end`** (SMODS Backs) — imperative code run when the deck is
   applied, used by every BMP deck for effects the vanilla `config` schema cannot express
   (custom run modifiers, banning keys, spawning packs, voucher discount hooks, etc.).

Some decks additionally use **`calculate = function(self, back, context)`** to fire ongoing
effects during a run (Heidelberg, Echo, Cocktail).

> **Sources**
> - Vanilla 15: `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` lines **637–653** (the `b_*` center block).
> - BMP decks: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/objects/decks/*.lua`.
> - BMP deck names/text: `…/multiplayer-0.4.0/localization/en-us.lua` lines **812–878**.
> - 0.3.3 baseline (Orange/Purple only): `C:/Users/micha/AppData/Local/Temp/xlsx_out2/04_Decks.txt`.
> - `…/multiplayer-0.4.0/CHANGELOG.md` — **no deck-specific entries** in the 0.3.3→0.4.0 delta (only joker/hotkey changes). See "0.4.0 vs 0.3.3" below.

---

## 1. Vanilla decks (15) — exact starting config

All 15 are `set = "Back"`, `stake = 1`. `unlocked` / `unlock_condition` shown for completeness
(irrelevant to multiplayer where decks are typically all-unlocked). Source: `game.lua:637–651`.

| # (order) | Name | Key | `pos` | `config` (exact) | Effect (exact numbers) |
|---|---|---|---|---|---|
| 1 | Red Deck | `b_red` | {0,0} | `{discards = 1}` | **+1 discard** every round. Default starting deck. |
| 2 | Blue Deck | `b_blue` | {0,2} | `{hands = 1}` | **+1 hand** every round. |
| 3 | Yellow Deck | `b_yellow` | {1,2} | `{dollars = 10}` | **Start with extra $10.** |
| 4 | Green Deck | `b_green` | {2,2} | `{extra_hand_bonus = 2, extra_discard_bonus = 1, no_interest = true}` | At end of each round: **+$2 per remaining Hand, +$1 per remaining Discard**; **earns no interest**. |
| 5 | Black Deck | `b_black` | {3,2} | `{hands = -1, joker_slot = 1}` | **+1 Joker slot**, **−1 hand** every round. |
| 6 | Magic Deck | `b_magic` | {0,3} | `{voucher = 'v_crystal_ball', consumables = {'c_fool','c_fool'}}` | Start with **Crystal Ball** voucher applied (+1 consumable slot) and **2 copies of The Fool**. |
| 7 | Nebula Deck | `b_nebula` | {3,0} | `{voucher = 'v_telescope', consumable_slot = -1}` | Start with **Telescope** voucher applied; **−1 consumable slot**. |
| 8 | Ghost Deck | `b_ghost` | {6,2} | `{spectral_rate = 2, consumables = {'c_hex'}}` | **Spectral cards may appear in the shop** (`spectral_rate = 2`); start with **Hex**. |
| 9 | Abandoned Deck | `b_abandoned` | {3,3} | `{remove_faces = true}` | Start with **no Face cards** (no J/Q/K) in the 52-card deck → 40 cards. |
| 10 | Checkered Deck | `b_checkered` | {1,3} | `{}` (special apply) | Start with **26 Spades + 26 Hearts** (Clubs→Spades, Diamonds→Hearts). Only 2 suits. |
| 11 | Zodiac Deck | `b_zodiac` | {3,4} | `{vouchers = {'v_tarot_merchant','v_planet_merchant','v_overstock_norm'}}` | Start with **Tarot Merchant + Planet Merchant + Overstock** vouchers applied. |
| 12 | Painted Deck | `b_painted` | {4,3} | `{hand_size = 2, joker_slot = -1}` | **+2 hand size**, **−1 Joker slot**. |
| 13 | Anaglyph Deck | `b_anaglyph` | {2,4} | `{}` (special apply) | After each **Boss Blind** defeated, gain **1 Double Tag** (`tag_double`). |
| 14 | Plasma Deck | `b_plasma` | {4,2} | `{ante_scaling = 2}` | **Balance Chips & Mult** in scoring screen; **X2 base Blind size** (`ante_scaling = 2`). |
| 15 | Erratic Deck | `b_erratic` | {2,3} | `{randomize_rank_suit = true}` | All **52 card ranks & suits randomized** at start. |

`b_challenge` (`{0,4}`, order 16, `omit = true`) is the hidden Challenge-mode back, not a
selectable deck.

**Notes on `{}` / special decks** (effects realized in apply logic, not declarative config):
- **Checkered**: suit conversion done imperatively on the starting deck.
- **Anaglyph**: hooks Boss-Blind defeat to grant a Double Tag.
- **Plasma**: `ante_scaling = 2` doubles the blind chip requirements per ante; the chip/mult
  balancing in the score UI is a separate Plasma flag in the base game.
- **Erratic** / **Abandoned**: realized via `randomize_rank_suit` / `remove_faces` flags read
  during deck construction.

---

## 2. BMP 0.4.0 decks — exact starting modifiers

BMP registers these via `SMODS.Back`. Shared atlas `mp_decks` (`pos {x,y}` index into
`decks.png`) except Heidelberg/Echo which have dedicated atlases. Files live in
`…/objects/decks/`. Internal keys are **`b_mp_<key>`** (SMODS prefixes the mod's `Multiplayer`
prefix → `mp`).

> **Important naming note:** The 0.3.3 changes doc (`04_Decks.txt`) lists exactly two MP decks:
> **"Orange Deck"** (Giga Standard Pack + 2 Hanged Man) and **"Purple Deck"** (+1 Voucher slot,
> Vouchers 50% off during Ante 1). In **0.4.0 the source has no `b_purple`**; that effect is now
> the **Violet Deck** (`b_mp_violet`), expanded (also 30% off Ante 2). **0.4.0 source wins** —
> treat "Purple" as renamed/evolved into "Violet". 0.4.0 also adds **6 new decks** beyond
> Orange/Violet (Indigo, Oracle, Gradient, Heidelberg, Echo, Cocktail). **White is present but
> fully commented out** → *not active in 0.4.0*.

### 2.1 Active BMP decks

| Name | Key | File / `pos` | Starting `config` | `apply` / `calculate` effect (exact) |
|---|---|---|---|---|
| **Orange Deck** | `b_mp_orange` | `02_orange.lua`, {2,0} | `{consumables = {'c_hanged_man','c_hanged_man'}}` | Start with **2 × The Hanged Man** (from config) **plus** `apply` opens a **Giga Standard Pack** (`p_mp_standard_giga`, randomly `_mega_1`/`_2` arcana-style atlas) immediately at run start (triple-nested event; uses `G.FUNCS.use_card`). |
| **Violet Deck** | `b_mp_violet` | `00_violet.lua`, {0,0} | `{}` | `apply`: `SMODS.change_voucher_limit(1)` → **+1 Voucher slot in shop**; sets `G.GAME.modifiers.mp_violet`. Patches `Card:set_cost`: when a Voucher's cost is computed, **Ante 1 → 50% off** (`0.5 *(base+extra+0.5)`, min $1), **Ante 2 → 30% off** (`0.7 * …`, min $1). Discounts stack with `discount_percent`. *(= the 0.3.3 "Purple Deck", expanded with the Ante-2 30% tier.)* |
| **Indigo Deck** | `b_mp_indigo` | `01_indigo.lua`, {1,0} | `{}` | `apply`: sets `mp_indigo`; `booster_choice_mod += 1` → **+1 additional card chosen from every Booster Pack**; **bans `j_red_card`** (`G.GAME.banned_keys`). Patches `G.FUNCS.can_skip_booster`: packs are **unskippable** while any card in the pack is *usable* (extensive usability check covering Joker space, Tarot/Spectral/Planet/Enhanced targets, negative/eternal joker slot math). |
| **Oracle Deck** | `b_mp_oracle` | `AA_oracle.lua`, {1,1} | `{vouchers = {'v_clearance_sale'}, consumables = {'c_medium'}}` | Start with **Clearance Sale** voucher applied + **1 × Medium** (Spectral). `apply`: `G.GAME.modifiers.oracle_max = 50` → **balance capped at $50 + current interest cap** (gains beyond the cap are clamped; helper `oracle_apply_dollar_cap` returns `min(max−current, mod)`; shows "MAX" alert). |
| **Gradient Deck** | `b_mp_gradient` | `BB_gradient.lua`, {0,1} | `{}` | `apply`: sets `mp_gradient`. Cards count as **one rank higher OR lower** for all Joker effects. Implemented by hooking `Card:calculate_joker`: for each scored/contextual card it temporarily shifts every playing card's `base.id` by `−1, 0, +1` (`G.MP_GRADIENT` ∈ {1,0,−1}) and re-evaluates, wrapping Ace↔King edges (id 15→2, 1→14). Also patches `Card:is_face` (10/J/Q/K-adjacent ranks treated as face for face-checks) and `Card:update` for Cloud 9's nine-tally (counts ids 8/9/10). Has blacklist (Photograph/Faceless/Ramen) and passkey (Superposition/Sixth Sense) for the hook. |
| **Heidelberg Deck** | `b_mp_heidelberg` | `CC_heidelberg.lua`, own atlas `b_heidelberg` | *(none)* | `calculate` on `context.ending_shop`: if you hold ≥1 consumable, pick **1 random consumable** (`pseudorandom_element(..., "mp_heidelberg")`), make a **Negative copy** of it (`e_negative`), and add it to your consumables. Fires **at end of every shop**. Message: `k_duplicated_ex`. |
| **Echo Deck** | `b_mp_echodeck` | `EC_echo_deck.lua`, atlas `ec_other_sandbox`, {2,0}, order 18 | `{}` | `apply`: `G.GAME.starting_params.ante_scaling = 1.2` → **X1.2 base Blind size**. `calculate`: **Retrigger all played cards once** (and held-in-hand cards that have effects) via `context.repetition` (`repetitions = 1`). At end of each **Boss** round, `ante_scaling += 0.2` → blind size **increases X0.2 per Ante**. |
| **Cocktail Deck** | `b_mp_cocktail` | `ZZ_cocktail.lua`, {4,0} | `{}` (built dynamically) | "Copies all effects of **3 other decks** at random." `apply`: forces early seed generation, pulls eligible decks (`MP.get_cocktail_decks`, respecting `deck_blacklist`/`mod_whitelist`), `pseudoshuffle` with `pseudoseed("mp_cocktail")`, picks up to **3** (honoring user-forced picks), then **merges each chosen deck's `config` into this back's config** (numbers add, tables merge, `voucher`→`vouchers`) and runs their `apply`. `calculate` proxies each chosen deck's `trigger_effect`. Special-cases Checkered (suit conversion). Per-game config string stored in mod config / lobby. Whitelisted mods: Multiplayer, Cryptid, aikoyorisshenanigans, allinjest. Blacklists itself, Challenge, `b_cry_antimatter`, `b_akyrs_hardcore_challenges`. |

### 2.2 Inactive / not in 0.4.0

| Name | Key | Status |
|---|---|---|
| **White Deck** | `b_mp_white` | **Commented out** in `09_white.lua` (entire `SMODS.Back` + `MP.WHITE` state machine block is inside `--[[ … ]]`). Intended effect (from loc + dead code): *"View Nemesis' current deck and Joker setup; updates at PvP blind"* via `G.GAME.modifiers.view_nemesis_deck`. **Not registered / not selectable in 0.4.0.** Loc string (`b_mp_white`, en-us.lua:871) still exists. |
| **Purple Deck** | `b_purple`(?) | **Does not exist as a 0.4.0 source object.** The 0.3.3 "Purple Deck" (+1 voucher slot, 50% off Ante 1) is realized in 0.4.0 as **Violet Deck** (`b_mp_violet`), expanded. |

### 2.3 BMP deck localized descriptions (en-us.lua:812–878)

- `b_mp_orange`: "Start with a {Giga Standard Pack}, and {2} copies of {The Hanged Man}"
- `b_mp_violet`: "{+1} Voucher in shop / Vouchers are {50%} off during Ante {1}, and {30%} off during Ante {2}"
- `b_mp_indigo`: "Choose {+1} additional card from all Booster Packs / Booster Packs are {unskippable}"
- `b_mp_oracle`: "Start run with {Medium} and {Clearance Sale} / Balance is capped at {$50} + {current interest cap}"
- `b_mp_gradient`: "Cards are also considered one rank {higher} or {lower} for all {Joker} effects"
- `b_mp_heidelberg`: "Creates a {Negative} copy of {1} random {consumable} card in your possession at the end of the {shop}"
- `b_mp_echodeck`: "{Retrigger} all playing cards / {X1.2} base Blind size / Increases by {X0.2} each Ante"
- `b_mp_cocktail`: "Copies all effects of {3} other decks at random"
- `b_mp_white`: "View {Nemesis'} current deck and Joker setup / {(Updates at PvP blind)}" — *inactive*

---

## 3. 0.4.0 vs 0.3.3 deltas (deck-relevant)

- **CHANGELOG.md has no deck section.** The 0.3.3→0.4.0 changelog only lists joker rebalances
  (e.g. Golden Ticket, Judgment) and hotkey/menu changes. So most deck deltas are *implicit*
  (new files vs the old changes spreadsheet).
- **"Purple Deck" (0.3.3) → "Violet Deck" (0.4.0):** same core (+1 voucher slot, Ante-1 50%
  off) **plus a new Ante-2 30%-off tier**. 0.4.0 source is authoritative.
- **New in 0.4.0 vs the 0.3.3 spreadsheet** (which listed only Orange + Purple): Indigo, Oracle,
  Gradient, Heidelberg, Echo, Cocktail are present as real source objects.
- **White Deck** is shipped but **disabled** (commented out) in 0.4.0.
- **Orange Deck** matches the 0.3.3 description (Giga Standard Pack + 2 Hanged Man); 0.4.0 ties
  it to the new `p_mp_standard_giga` pack object.

---

## 4. Engine-mapping notes (for D:/NewServer)

Starting-modifier keys our engine must support to model decks generically:

- **Round resets:** `hands` (Δ), `discards` (Δ), `joker_slot` (Δ), `consumable_slot` (Δ),
  `hand_size` (Δ), `dollars` (starting $ Δ).
- **Round economy:** `extra_hand_bonus`, `extra_discard_bonus`, `no_interest`.
- **Blind scaling:** `ante_scaling` (multiplier on blind chip requirements; Plasma=2,
  Echo=1.2 increasing +0.2/ante).
- **Shop:** `spectral_rate`, voucher-limit Δ (Violet +1), per-ante voucher cost multipliers
  (Violet), `discount_percent` interaction.
- **Starting inventory:** `voucher` / `vouchers[]` (apply at run start), `consumables[]`,
  open-a-pack-on-start (Orange → Giga Standard Pack).
- **Deck construction flags:** `remove_faces`, `randomize_rank_suit`, suit-conversion
  (Checkered).
- **Run modifiers / hooks:** booster `+N` choices + unskippable (Indigo), rank-shift evaluation
  (Gradient), end-of-shop negative-copy (Heidelberg), retrigger-all (Echo), dollar cap (Oracle),
  Boss-defeat tag grant (Anaglyph), composite-deck merge (Cocktail).
- **Banned keys:** decks can ban centers (Indigo bans `j_red_card`).

---

## Open questions

1. **`p_mp_standard_giga` exact contents** — Orange opens a "Giga Standard Pack". Its card count
   / choose count is defined in `…/objects/boosters/` (not read here). Need to confirm
   size for engine modeling. *(unverified here)*
2. **Plasma chip/mult balancing** — `ante_scaling = 2` is confirmed; the score-screen
   chip↔mult averaging is a separate base-game Plasma flag not captured in `config`. Need the
   exact balancing formula from `game.lua` evaluate/score path.
3. **Oracle dollar cap semantics** — `oracle_max = 50` + "current interest cap": confirm whether
   cap = `50 + G.GAME.interest_cap` (default 25 → $75) and how it interacts with
   interest-cap vouchers. Helper `oracle_apply_dollar_cap(mod, current, max)` confirmed but the
   `max` source value should be traced.
4. **Gradient interaction with non-vanilla jokers / Blueprint chains** — the hook hardcodes a
   blacklist/passkey and a Blueprint/Brainstorm key-resolver; behavior with modded jokers is
   explicitly "hopefully doesn't crash" per author comments. Edge cases unverified.
5. **Cocktail in ranked/standard MP** — which decks are eligible by default, and whether
   Cocktail is allowed at all, depends on lobby ruleset (`rulesets/`), not read here.
6. **White Deck** — confirm it is intentionally disabled for 0.4.0 release (vs. a build-time
   toggle). Currently dead-commented.
7. **Vanilla special-deck apply code** — Checkered/Anaglyph/Erratic/Abandoned effects are stated
   from `config` flags + known behavior; the exact `apply_to_run` body was not located in this
   dump (no `function Back:apply_to_run` match), so the precise per-flag handling is **partially
   from Balatro knowledge — flag as needing a dump trace**.

## New building blocks needed

- **`StartingParams` model** with all keys in §4 (Δ-style for slots/hands/discards; multipliers
  for `ante_scaling`; lists for starting vouchers/consumables).
- **Deck "apply hook" interface** — decks need imperative hooks beyond declarative config:
  `onRunStart`, `onShopEnd` (Heidelberg), `onBossDefeated` (Anaglyph), `onBoosterOpen` /
  `canSkipBooster` (Indigo), `onScoreCard` rank-shift (Gradient), retrigger injection (Echo),
  dollar-gain clamp (Oracle), voucher-cost override (Violet).
- **Voucher-cost override pipeline** — per-ante percentage discounts (Violet) layered on top of
  the global `discount_percent`, mirroring the `Card:set_cost` patch.
- **Booster "open pack at run start"** primitive (Orange's Giga Standard Pack).
- **Banned-center set per deck** (`banned_keys`) honored by shop/pack generation.
- **Composite/derived deck** support (Cocktail) — merge N decks' configs (numeric add, list
  concat, `voucher`→`vouchers`) and chain their apply/calculate hooks; needs seeded selection.
- **Dollar-cap state** (`oracle_max`) on the player economy with a clamp-on-gain rule + UI MAX
  signal.
- **Rank-shift evaluation context** for joker scoring (Gradient) — ability to evaluate joker
  triggers against ±1 rank-shifted virtual ranks with Ace/King wrap.
- **Per-round retrigger-all flag** (Echo) feeding the repetition context for played and
  held-in-hand cards.
