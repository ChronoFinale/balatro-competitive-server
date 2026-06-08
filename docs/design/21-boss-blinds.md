# 21 — Boss Blinds (Balatro Multiplayer 0.4.0)

Definitive catalogue of every Boss Blind (regular + finisher) plus the Multiplayer-only
`bl_mp_nemesis` PvP boss. Covers: exact effect, base chip multiplier, payout, ante
appearance range, and Balatro Multiplayer (BMP) 0.4.0 ranked/gamemode interactions and bans.

## Sources (all claims grounded)

- **`P_BLINDS` table** (every blind's `mult`, `dollars`, `order`, `boss = {min,max}`,
  `debuff`, `boss_colour`): `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua`
  lines **272–305** (identical in `.../game-dump/game.lua`).
- **Exact effect logic** (`Blind:press_play`, `:modify_hand`, `:debuff_hand`,
  `:drawn_to_hand`, `:stay_flipped`, `:debuff_card`, `:disable`, `:defeat`, `:set_blind`):
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/blind.lua`.
- **BMP PvP boss `bl_mp_nemesis`**: `.../multiplayer-0.4.0/objects/blinds/nemesis.lua`;
  localization `Your Nemesis` / `Nemesis` in `.../multiplayer-0.4.0/localization/en-us.lua`
  lines **793–973**.
- **Gamemode boss assignment & bans**: `.../multiplayer-0.4.0/gamemodes/{showdown,attrition,survival}.lua`
  and `_gamemodes.lua` (`get_blinds_by_ante`, `banned_blinds`, `banned_tags`).
- **Ban-layer plumbing**: `.../multiplayer-0.4.0/layers/_layers.lua` lines 122–138
  (`banned_blinds`, `reworked_blinds` are merged layer-array fields); applied to choices in
  `.../multiplayer-0.4.0/ui/game/round.lua` lines 47–57.
- **Config defaults**: `.../multiplayer-0.4.0/core.lua` (`pvp_start_round = 2` L178,
  `showdown_starting_antes = 3` L182).
- **0.4.0 vs 0.3.3 delta**: `.../multiplayer-0.4.0/CHANGELOG.md` — **contains no boss-blind
  changes**; boss roster/effects are unchanged from vanilla and from 0.3.3 (see Delta note).

> **Numeric note:** every `mult` and `boss.min/max` below is copied verbatim from `P_BLINDS`.
> Where the exact probability or magnitude lives in base-game localization that is not present
> on disk in these dumps (e.g. The Wheel "1 in 7"), I take it from `blind.lua` code constants
> where possible and otherwise FLAG as *unverified (Balatro knowledge)*.

---

## How chip requirement is computed

`Blind:set_blind` (blind.lua L130):

```
self.chips = get_blind_amount(ante) * self.mult * G.GAME.starting_params.ante_scaling
```

So a boss's score requirement = base ante amount × the blind's `mult`. Small Blind `mult = 1`,
Big Blind `mult = 1.5`; **all standard bosses are `mult = 2`** except:

- **The Wall** `mult = 4` (double-size boss)
- **The Needle** `mult = 1` (single-size — but only one hand allowed)
- **The Flint** `mult = 2` (halves your score/chips instead of raising the bar)
- **Violet Vessel** (finisher) `mult = 6`
- All other finishers `mult = 2` (except Vessel)

`dollars` = reward on clear: **$5** for standard bosses, **$8** for finishers (Small $3, Big $4).

---

## Regular Boss Blinds

`boss = {min = A, max = B}` means the blind can only be selected when `min ≤ ante ≤ max`.
`max = 10` everywhere — i.e. there is no upper cap in practice (vanilla pool selection wraps via
`get_new_boss`). The meaningful field is **`min` = earliest ante it can appear**. Finishers use
`showdown = true` and only appear on ante-8 multiples.

| Name | Key | `mult` | $ | Earliest ante (`min`) | Effect (exact, from blind.lua) |
|------|-----|:--:|:--:|:--:|--------|
| The Hook | `bl_hook` | 2 | 5 | **1** | After each played hand, **discards 2 random cards from hand** (`press_play`: picks 2 via `pseudoseed('hook')`, force-discards them). blind.lua L502–522 |
| The Ox | `bl_ox` | 2 | 5 | **6** | Playing your **most-played poker hand this run sets your money to $0** (`debuff_hand`: if `handname == most_played_poker_hand` → `ease_dollars(-G.GAME.dollars, true)`). blind.lua L598–607. Display var = `most_played_poker_hand`. |
| The House | `bl_house` | 2 | 5 | **2** | **First hand is drawn face down** (`stay_flipped`: true while `hands_played == 0 and discards_used == 0`). blind.lua L668–669 |
| The Wall | `bl_wall` | **4** | 5 | **2** | **Extra-large blind** — required chips ×2 vs a normal boss (mult 4 not 2). `disable` halves `self.chips`. blind.lua L416–418. **BANNED in all PvP gamemodes** (see below). |
| The Wheel | `bl_wheel` | 2 | 5 | **2** | **1 in 7 cards drawn face down** (`stay_flipped`: `pseudorandom_probability(self, pseudoseed('wheel'), 1, 7, 'wheel')`). blind.lua L667 |
| The Arm | `bl_arm` | 2 | 5 | **2** | **Decreases level of played poker hand by 1** (`debuff_hand`: if `hands[handname].level > 1` → `level_up_hand(..., -1)`; min level 1). blind.lua L589–597 |
| The Club | `bl_club` | 2 | 5 | **1** | **All Club cards are debuffed** (`debuff = {suit = 'Clubs'}`; `debuff_card` sets debuff on `is_suit('Clubs')`). blind.lua L701 |
| The Fish | `bl_fish` | 2 | 5 | **2** | **Cards drawn face down after each played hand** (`press_play` sets `prepped`; `stay_flipped` true while `prepped`). blind.lua L528–529, L673 |
| The Psychic | `bl_psychic` | 2 | 5 | **1** | **Must play exactly 5 cards** (`debuff = {h_size_ge = 5}`; `debuff_hand` triggers when `#cards < 5`, blocking the hand). blind.lua L567 |
| The Goad | `bl_goad` | 2 | 5 | **1** | **All Spade cards are debuffed** (`debuff = {suit = 'Spades'}`). blind.lua L701 |
| The Water | `bl_water` | 2 | 5 | **2** | **Start with 0 discards** (`set_blind`: stores `discards_sub` then `ease_discard(-discards_sub)`; `disable` restores). blind.lua L206–208, L403 |
| The Window | `bl_window` | 2 | 5 | **1** | **All Diamond cards are debuffed** (`debuff = {suit = 'Diamonds'}`). blind.lua L701 |
| The Manacle | `bl_manacle` | 2 | 5 | **1** | **−1 hand size** (`set_blind`: `G.hand:change_size(-1)`; `disable`/`defeat` add it back). blind.lua L212–213, L376–378, L423 |
| The Eye | `bl_eye` | 2 | 5 | **3** | **No repeat hand types this round** — each poker-hand type scores only once (`hands` table tracks; `debuff_hand` blocks an already-used `handname`). blind.lua L187–201, L575–580 |
| The Mouth | `bl_mouth` | 2 | 5 | **2** | **Only one hand type allowed this round** (`only_hand` locks to first played type; `debuff_hand` blocks any other). blind.lua L202–203, L581–586 |
| The Plant | `bl_plant` | 2 | 5 | **4** | **All face cards are debuffed** (`debuff = {is_face='face'}`; `debuff_card` on `is_face(true)`). blind.lua L706 |
| The Serpent | `bl_serpent` | 2 | 5 | **5** | **Always draw 3 cards after play/discard** (regardless of cards played). Effect handled in base draw logic, not blind.lua directly; `disable` is a no-op branch. blind.lua L425. Magnitude "3" *unverified from disk* (Balatro knowledge). |
| The Pillar | `bl_pillar` | 2 | 5 | **1** | **Cards played this ante (since last boss) are debuffed** (`debuff_card`: if `card.ability.played_this_ante` → debuff). blind.lua L711–714 |
| The Needle | `bl_needle` | **1** | 5 | **2** | **Play only 1 hand this round** (`set_blind`: `hands_sub = round_resets.hands - 1`, `ease_hands_played(-hands_sub)` → 1 hand; `disable` restores). Note `mult = 1`. blind.lua L209–210, L414 |
| The Head | `bl_head` | 2 | 5 | **1** | **All Heart cards are debuffed** (`debuff = {suit='Hearts'}`). blind.lua L701 |
| The Tooth | `bl_tooth` | 2 | 5 | **3** | **Lose $1 per card played** (`press_play`: per card in `G.play.cards`, `ease_dollars(-1)`). blind.lua L530–539 |
| The Flint | `bl_flint` | 2 | 5 | **2** | **Base Chips and Mult of played hand are halved** (`modify_hand`: returns `floor(mult*0.5+0.5)` and `floor(hand_chips*0.5+0.5)`, mins 1/0). blind.lua L548–550 |
| The Mark | `bl_mark` | 2 | 5 | **2** | **All face cards drawn face down** (`stay_flipped`: true when `card:is_face(true)`). blind.lua L671 |

### Notes on regular bosses

- **The Ox display variable** is the run's `most_played_poker_hand`, resolved in
  `Blind:set_text` (blind.lua L56–58) — its text and trigger change per-run.
- **The Wheel face-down flip** is sticky: flipped cards get `ability.wheel_flipped`, cleared
  in `disable` for Wheel/House/Mark/Fish (blind.lua L405–413).
- **Suit-debuff bosses** (Club/Goad/Window/Head) and **face-debuff** (Plant) set
  `card.debuffed_by_blind = true`, which interacts with anti-debuff jokers/consumables.
- **The Eye / The Mouth** keep per-round state (`self.hands`, `self.only_hand`) that is saved
  in `Blind:save`/`load` (blind.lua L783–784) — relevant for our server-side save state.

---

## Finisher Boss Blinds (Showdown bosses, `boss = {showdown = true}`)

Finishers appear only on **ante 8 and every multiple of 8** (the `showdown` flag; the exact
`ante % 8 == 0` selection lives in `get_new_boss`/`common_events.lua`, not present in these
dumps — *selection rule is Balatro knowledge, unverified from disk*; the `showdown = true` flag
on each is verified). All have `dollars = 8`, `min = max = 10` in the `boss` table.

| Name | Key | `mult` | $ | Effect (exact) |
|------|-----|:--:|:--:|--------|
| Amber Acorn | `bl_final_acorn` | 2 | 8 | **Flips and shuffles all Jokers face down** at start (`set_blind`: flips every joker, then `G.jokers:shuffle('aajk')` thrice if >1). Jokers un-flip on defeat (`defeat` L366–367). blind.lua L214–228 |
| Verdant Leaf | `bl_final_leaf` | 2 | 8 | **All played cards debuffed until 1 Joker is sold** (`debuff_card`: every `playing_card` debuffed while active). blind.lua L733. "until a joker is sold" is the vanilla mechanic — *the sell-to-disable hook is not in blind.lua; unverified from disk*. |
| Violet Vessel | `bl_final_vessel` | **6** | 8 | **Very large blind** — required chips ×3 of a normal finisher (mult 6 vs 2). `disable` divides `self.chips` by 3. blind.lua L426–428. **BANNED in all PvP gamemodes.** |
| Crimson Heart | `bl_final_heart` | 2 | 8 | **One random Joker disabled each hand** (`drawn_to_hand`: picks a non-debuffed joker via `pseudoseed('crimson_heart')`, sets `crimson_heart_chosen`, `recalc_debuff`). Rotates each draw; cleared on `disable`/`defeat`. blind.lua L523–527, L628–654, L372–378 |
| Cerulean Bell | `bl_final_bell` | 2 | 8 | **Forces 1 card to always be selected** (`drawn_to_hand`: picks a card via `pseudoseed('cerulean_bell')`, sets `forced_selection`, auto-highlights it; cleared on disable). blind.lua L419–421, L615–627 |

### Notes on finishers

- **Crimson Heart** uses a fairness pool: it avoids re-picking the previously chosen joker
  unless all jokers are debuffed (`prev_chosen_set`/`fallback_jokers`, blind.lua L629–647).
- **Amber Acorn** shuffle uses RNG key `'aajk'`; flip-down only happens `if #G.jokers.cards > 0`.

---

## Multiplayer PvP Boss — `bl_mp_nemesis` ("Your Nemesis")

Defined as an `SMODS.Blind` in `.../multiplayer-0.4.0/objects/blinds/nemesis.lua`:

```lua
SMODS.Blind({
  key = "nemesis",          -- full key: bl_mp_nemesis
  dollars = 5,
  mult = 1,                 -- "Jen's Almanac crashes the game if the mult is 0"
  boss_colour = G.C.MULTIPLAYER,
  boss = { min = 1, max = 10 },
  atlas = "player_blind_chip",
  discovered = true,
  in_pool = function(self) return false end,   -- never in the random boss pool
})
```

- **Effect (localization, en-us.lua L793–798):** *"Your Nemesis — Face another player, most
  chips wins."* It is the head-to-head PvP round: both players score against each other; the
  higher score wins the round (loser takes a life / round result per gamemode).
- **`mult = 1`**, **`dollars = 5`**, color = `G.C.MULTIPLAYER`. Never appears via the normal
  boss pool (`in_pool` returns `false`); it is **force-assigned** by the gamemode's
  `get_blinds_by_ante` (see below).
- **`MP.is_pvp_boss()`** (nemesis.lua L32–35) returns true when
  `G.GAME.blind.config.blind.key == "bl_mp_nemesis"` **or** `G.GAME.blind.pvp` — the canonical
  "are we in a PvP boss?" check the rest of BMP uses.
- A companion display, `current_nemesis` ("Nemesis", en-us.lua L967–972), labels the opponent.

### How `bl_mp_nemesis` is assigned per gamemode

Set via each gamemode's `get_blinds_by_ante(ante)` (returns Small, Big, Boss choice; `nil`
means "use vanilla"). Applied in `ui/game/round.lua` L52–57.

| Gamemode | File | PvP boss rule |
|----------|------|---------------|
| **Showdown** | `gamemodes/showdown.lua` L3–8 | When `ante >= showdown_starting_antes` (default **3**), **all three blinds become `bl_mp_nemesis`** (`return "bl_mp_nemesis","bl_mp_nemesis","bl_mp_nemesis"`). Before that, normal blinds. |
| **Attrition** | `gamemodes/attrition.lua` L3–12 | When `ante >= pvp_start_round` (default **2**): if **not** `normal_bosses` → Boss = `bl_mp_nemesis` (Small/Big stay normal). If `normal_bosses` is on → keep the vanilla boss but mark `pvp_blind_choices.Boss = true` (vanilla boss effect + PvP). |
| **Survival** | `gamemodes/survival.lua` L3–5 | `return nil,nil,nil` — **no PvP boss**; pure vanilla blinds throughout. |

Config defaults (`core.lua`): `pvp_start_round = 2` (L178), `showdown_starting_antes = 3` (L182).
The `normal_bosses` toggle (lobby option `b_opts_normal_bosses` = "Enable Boss Blind effects",
en-us.lua L1200) only matters for **Attrition** — it decides whether the PvP ante also runs a
real boss effect or just the nemesis face-off.

---

## BMP Ranked / Gamemode bans & interactions

### Banned Boss Blinds (per gamemode)

`banned_blinds` is a merged layer-array field (`layers/_layers.lua` L122–138). The **PvP
gamemodes ban two oversized bosses** so a normal boss ante can't be unwinnably large:

| Blind | Banned in |
|-------|-----------|
| **The Wall** (`bl_wall`, mult 4) | Showdown, Attrition (`banned_blinds`). Survival: not banned. |
| **Violet Vessel** (`bl_final_vessel`, mult 6) | Showdown, Attrition (`banned_blinds`). Survival: not banned. |

Verified: `showdown.lua` L26–29, `attrition.lua` L30–33. **No ruleset** (vanilla, minorleague,
majorleague, badlatro) adds any boss ban — all `banned_blinds = {}` in `rulesets/*.lua`. So
boss bans are purely a gamemode concern.

### Banned boss tag

`tag_boss` (the Skip Tag that rerolls the boss) is in **`banned_tags`** for **Showdown** and
**Attrition** (showdown.lua L23–25, attrition.lua L27–29) — players cannot reroll the boss in
PvP modes. Not banned in Survival.

### Joker / voucher bans that interact with bosses (PvP modes)

PvP modes ban jokers/vouchers that trivialize boss antes (showdown.lua L9–21, attrition.lua
L13–25):

- **Banned jokers (Showdown & Attrition):** `j_mr_bones`, `j_luchador`, `j_matador`,
  `j_chicot` — all are anti-boss / boss-disabling jokers (Luchador & Chicot disable boss
  effects; Matador profits off boss triggers; Mr. Bones is a death-save). Removing them keeps
  boss effects and the PvP face-off meaningful.
- **Banned vouchers (Showdown & Attrition):** `v_hieroglyph`, `v_petroglyph` (ante skips),
  `v_directors_cut`, `v_retcon` (boss reroll vouchers) — prevents skipping or re-rolling the
  PvP boss ante.

### Ranked changes spreadsheet (0.3.3 baseline)

`C:/Users/micha/AppData/Local/Temp/xlsx_out2/*.txt` — the parsed BMP ranked spreadsheet has
**no dedicated Boss Blind sheet**; `06_Misc.txt` only mentions "Hand Smoothing" (ante→round
draw cadence), which has no boss-blind effect. No boss-blind rebalances exist in the ranked doc.

---

## 0.4.0 vs 0.3.3 Delta

- **No boss-blind changes in 0.4.0.** `CHANGELOG.md` "Ranked Update 0.4.0" lists only joker /
  consumable / economy tweaks (To Do List, Golden Ticket, Speedrun, Ouija/Ectoplasm cost,
  Justice, Gold Card, Comeback Gold, Hanging Chad). The boss roster, every `mult`/`dollars`,
  every effect, the `bl_mp_nemesis` definition, and the Wall/Vessel + `tag_boss` bans are
  **unchanged** from the prior version.
- Structural 0.4.0 difference: bosses now flow through the **gamemode `get_blinds_by_ante` +
  layer-merged `banned_blinds`/`banned_tags`** system (gamemodes/ + layers/_layers.lua), rather
  than ad-hoc 0.3.3 logic. The *resulting* boss behavior is identical, but our engine should
  model boss selection as "gamemode override → else vanilla pool, minus banned set."

---

## Open questions

1. **Showdown finisher selection rule.** The `showdown = true` flag is verified, but the exact
   "ante % 8 == 0 → pick a finisher" rule lives in `get_new_boss`/`common_events.lua`, which is
   not present in the supplied dumps. Confirm the selection logic (and how finisher among the 5
   is RNG-picked) before implementing finisher antes server-side.
2. **The Serpent magnitude.** "Always draw exactly 3 cards" is not in `blind.lua` (the draw
   override is elsewhere). Confirm the count and whether it applies to both play and discard.
3. **Verdant Leaf disable trigger.** blind.lua debuffs all playing cards but does not contain
   the "until a Joker is sold" un-debuff hook. Locate where selling a joker calls
   `disable`/recalc for `bl_final_leaf`.
4. **PvP boss scoring & life model.** `bl_mp_nemesis` localization says "most chips wins" but
   the actual score comparison, tie-breaking, and life/round-loss accounting live in the
   networking layer / server protocol (`action_handlers.lua`, `D:/BalatroMultiplayerAPI-Server-main/`).
   Needs its own spec section.
5. **`normal_bosses` interaction.** When Attrition has `normal_bosses` on, both a vanilla boss
   effect AND a PvP face-off run on the same ante (`pvp_blind_choices.Boss = true`). Confirm how
   the two scoring requirements stack (vanilla chip goal vs opponent-score comparison).
6. **The Wheel probability.** "1 in 7" is encoded as `pseudorandom_probability(...,1,7,...)` —
   verified in code. Other vanilla descriptive numbers (e.g. exact Eye/Mouth wording) come from
   base-game localization not on disk; cross-check if exact UI text is needed.

## New building blocks needed

- **`BossBlind` engine model:** key, `mult`, `dollars`, `boss.min` (earliest ante),
  `showdown` flag, and a typed effect enum mapping to the blind.lua hook it uses:
  `PRESS_PLAY` (Hook, Tooth, Fish), `MODIFY_HAND` (Flint), `DEBUFF_HAND` (Ox, Arm, Eye, Mouth,
  Psychic), `DRAWN_TO_HAND` (Crimson Heart, Cerulean Bell), `STAY_FLIPPED` (House, Wheel, Mark,
  Fish), `DEBUFF_CARD` (Club, Goad, Window, Head, Plant, Pillar, Verdant Leaf, Crimson Heart),
  `SET_BLIND` startup (Water, Needle, Manacle, Amber Acorn), `DISABLE`/`DEFEAT` cleanup.
- **Per-blind RNG keys** for deterministic multiplayer replay: `'hook'`, `'wheel'`,
  `'cerulean_bell'`, `'crimson_heart'`, `'aajk'` (Amber Acorn shuffle) — must seed identically
  to the client (`pseudoseed`/`pseudorandom_probability`).
- **Boss-effect chip-requirement calculator:** `requirement = get_blind_amount(ante) * mult *
  ante_scaling`, with The Flint instead halving the *played* hand's chips+mult (`MODIFY_HAND`).
- **Per-round boss state container** (saved/loaded): `self.hands` map (The Eye),
  `self.only_hand` (The Mouth), `discards_sub` (Water), `hands_sub` (Needle),
  `forced_selection`/`crimson_heart_chosen`/`wheel_flipped` card flags.
- **Gamemode boss-override hook:** implement `get_blinds_by_ante(ante) -> (small, big, boss)`
  per gamemode, with `bl_mp_nemesis` as a force-assigned non-pool boss, plus a
  layer-merged `banned_blinds` / `banned_tags` filter applied to the vanilla boss pool.
- **`bl_mp_nemesis` / PvP-boss type:** `mult = 1`, `dollars = 5`, `in_pool = false`,
  `is_pvp_boss()` predicate, and "highest score wins the round" comparison wired to the
  networking layer.
- **`normal_bosses` lobby flag** handling for Attrition (vanilla boss effect + PvP face-off on
  the same ante).
