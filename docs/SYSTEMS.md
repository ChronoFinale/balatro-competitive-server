# SYSTEMS — target behavior specs (scoring, RNG, economy, lifecycle, PvP, networking, rulesets). Parity target: BMP 0.4.2 ranked, behavioral not byte-compatible. Validate against the real game (D:\BalatroMod). Current implementation state lives in docs/HANDOFF.md, not here.

> These are the **target** behavior specs the engine is built to. They consolidate the
> former `docs/design/30..36` system specs + `docs/rulesets/bmp-0.4.2-ranked.md`. The
> grounding parity reference is **Balatro Multiplayer (BMP) 0.4.0** running on Balatro
> **1.0.1o-FULL** with SMODS as the calculation backbone (the on-disk source); the ranked
> overlay target is **BMP 0.4.2 ranked**. Parity is **behavioral**, not byte-compatible:
> our server re-simulates authoritatively; we do not need to match vanilla seeds.
>
> Core invariant throughout: the **server re-derives every outcome**; the client sends
> intents and animates an authoritative replay. Never move RNG, scoring, or outcome
> decisions to the client.

## Table of contents

1. [Scoring pipeline](#1-scoring-pipeline) — `evaluate_play` phase order + per-source field order
2. [RNG & queue topology](#2-rng--queue-topology) — game-long-queue model, queue-key registry, ante-zeroing
3. [Economy](#3-economy) — every dollar in/out
4. [Run lifecycle](#4-run-lifecycle) — run/blind state machine, gamemode overlays
5. [PvP / Attrition / Nemesis](#5-pvp--attrition--nemesis) — gamemode rules, MP-joker formulas, OpponentView shape
6. [Networking protocol](#6-networking-protocol) — wire envelope, auth, reconnect/resync
7. [Content / ruleset pipeline](#7-content--ruleset-pipeline) — layered composition, bans/reworks, + BMP-0.4.2-ranked overlay

---

# 1. Scoring pipeline

> Convention: **xmult** = multiply current mult; **xchips** = multiply current chips;
> **+mult / +chips** = additive. Card order matters everywhere — both played cards and the
> joker row are iterated left→right, and that interleaving of `+mult` vs `×mult` is
> score-determining.

**Status:** pipeline skeleton exists in `ScoringEngine`; missing `modify_scoring_hand`,
boss `debuff_hand`/`modify_hand`, `final_scoring_step`/`after`/destruction phases, xchips,
joker-edition wrap, `floor`+big-number, ruleset-driven constants. (Details in HANDOFF.)

There are **two** scoring code paths that must agree: the authoritative play resolver
`G.FUNCS.evaluate_play` (`state_events.lua:619`, mutates `G.GAME.chips` on Play Hand) and
BMP's score-preview simulator `FN.SIM` (`EngineSimulate.lua`), the cleanest linear
transcription of the ordering and the best model to mirror. When the two disagree, the
SMODS `evaluate_play` wins (the preview may approximate with min/exact/max ranges).

## 1a. Macro phase order (authoritative)

The simulator's top-level order (`FN.SIM.run`, `EngineSimulate.lua:13-24`) is the canonical
summary:

```
FN.SIM.run():
  if not simulate_blind_debuffs():       -- boss blind may zero the hand (debuff_hand)
      simulate_joker_before_effects()     -- context.before
      add_base_chips_and_mult()           -- base from hand level
      simulate_blind_effects()            -- boss blind modify_hand
      simulate_scoring_cards()            -- each played scoring card, L→R
      simulate_held_cards()               -- each held-in-hand card, L→R
      simulate_joker_global_effects()     -- jokers main + joker-on-joker + joker editions
      simulate_consumable_effects()       -- e.g. Observatory planet xmult
      simulate_deck_effects()             -- selected_back final_scoring_step
  else:
      simulate_all_jokers({debuffed_hand=true})  -- Matador only
  return chips * mult
```

The authoritative `evaluate_play` does the same in this concrete order:

1. **Identify hand** — `get_poker_hand_info(G.play.cards)` → `(text, disp_text, poker_hands,
   scoring_hand, non_loc_disp_text)`. Increment `hands[text].played/played_this_round/
   played_this_ante`; set `last_hand_played`. (`state_events.lua:620-627`)
2. **Assemble the final scoring set** (`:629-646`). Per played card:
   `splashed = SMODS.always_scores(card) or Splash present`, else true if in detected
   `scoring_hand`. Fire `context.modify_scoring_hand` (jokers/seals add/remove via
   `flags.add_to_hand`/`flags.remove_from_hand`). Keep card iff `splashed and not
   unsplashed`. **Stone cards always score.** Kept list **preserves played (L→R) order.**
3. **Boss-blind veto** — `G.GAME.blind:debuff_hand(...)`. If true the hand is *Not Allowed*:
   `mult=0, chips=0`, fire `context.debuffed_hand`, skip everything else (`:837-860`).
   (Preview `simulate_blind_debuffs`, special-cases The Tooth / The Arm / The Ox / Matador.)
4. **Base chips/mult** = the *played hand's* current level values: `mult =
   mod_mult(G.GAME.hands[text].mult)`, `hand_chips = mod_chips(G.GAME.hands[text].chips)`
   (`:667-668`, re-read `:695-696`).
5. **`context.before`** — `SMODS.calculate_context({…, before=true})` (`:689`). Jokers
   acting before scoring (setup, conditional priming). Distinct from the main joker pass.
6. **Boss-blind `modify_hand`** — `mult, hand_chips, modded = blind:modify_hand(...)`
   (`:700`), re-wrapped through `mod_mult/mod_chips`.
7. **`context.initial_scoring_step`** then **`SMODS.calculate_main_scoring` per card-area**
   (`:704-708`). Areas are `G.play` (scored) then `G.hand` (held) — `SMODS.get_card_areas(
   'playing_cards')` returns `{G.play, G.hand}` (BMP does not opt in `G.deck`/`G.discard`).
8. **Joker main pass + joker-on-joker + joker editions** (`:713-794`).
9. **`context.final_scoring_step`** + deck `final_scoring_step` trigger (`:797-802`).
10. **Destruction pass** — `context.destroying_card` collects cards to destroy (Glass
    shatter, etc.), then `context.remove_playing_cards` (`:804-836`).
11. **Round score** = `SMODS.calculate_round_score()` = `current_scoring_calculation:func(
    chips, mult)` = **`floor(chips * mult)`** for the default ruleset (`utils.lua:2815-2818`).
    Added to `G.GAME.chips`.
12. **`context.after`** (`:916`) — post-hand cleanup hooks.

## 1b. Per-card scoring (micro level — exact field order)

Authoritative path `SMODS.calculate_main_scoring` → `SMODS.score_card`. Per card in the area:
- Skip if `card.debuff` (debuffed cards score nothing but still trigger the blind's juice).
- **Repetitions are collected first**, then the card's effect applies once per rep
  (`score_card` loops `reps` via `SMODS.calculate_repetitions`). Red Seal and retrigger
  jokers (Hanging Chad, Sock and Buskin, Dusk, Hack, Seltzer, Mime) add reps; each rep
  re-runs the card's full base+enhancement+edition+seal plus the per-card joker
  `individual` pass.

Cleanest exact ordering is the preview's `simulate_card`. For a **played** card
(`cardarea == G.play`):

```
1. Chips:  Stone → ability.bonus + perma_bonus
           else  → base_chips + ability.bonus + perma_bonus    (Bonus card = +30 via bonus)
2. Mult:   Lucky → probabilistic +20 (1-in-5)   [pseudorandom('lucky_mult')]
           else  → ability.mult                  (Mult card = +4 via ability.mult)
3. XMult:  if ability.x_mult > 1 → x_mult(ability.x_mult)   (Glass = x2)
4. Dollars: Gold Seal → +$3 ; Lucky p_dollars → probabilistic +$20 (1-in-15)
5. Edition: Foil → +50 chips ; Holo → +10 mult ; Polychrome → x1.5 mult
```

For a **held** card (`cardarea == G.hand`):

```
   ability.h_mult > 0   → +mult
   ability.h_x_mult > 0 → x_mult   (Steel Card = x1.5 mult while held)
```

**Per-card retrigger structure:** reps are computed *once* (Red seal +1, Echo deck +1, joker
`repetition` context), then the card effect and the per-card joker `individual` pass run
inside the rep loop. A retrigger re-applies *both* the card's intrinsic chips/mult/edition/
seal *and* any joker that reacts to that card.

**Field-application order inside a single effect table** is fixed by
`SMODS.calculation_keys` (`utils.lua:1409-1431`):

```
pre_func →
chips, h_chips, chip_mod →
mult, h_mult, mult_mod →
x_chips/xchips/Xchip_mod →
x_mult/Xmult/xmult/x_mult_mod/Xmult_mod →
[then non-scoring keys: p_dollars, dollars, swap, balance, level_up, func, message, …]
```

i.e. **additive chips → additive mult → ×chips → ×mult**, per source. Within an
`effect.extra` chain, `extra` recurses, so a joker can emit `{mult=…, extra={x_mult=…}}` and
get +mult applied before its own ×mult.

## 1c. Exact math primitives

`SMODS.calculate_individual_effect` dispatches scoring keys to
`SMODS.Scoring_Parameters[…]:calc_effect`:
- **chips** (`chip`/`h_chips`/`chip_mod`): `hand_chips = mod_chips(hand_chips + amount)`.
- **xchips** (`x_chips`/`xchips`/`Xchip_mod`), guarded by `amount ~= 1`:
  `hand_chips = mod_chips(hand_chips * (amount - 1) + hand_chips)`.
- **mult** (`mult`/`h_mult`/`mult_mod`): `mult = mod_mult(mult + amount)`.
- **xmult** (`x_mult`/`xmult`/`Xmult`/…), guarded by `amount ~= 1`:
  `mult = mod_mult(mult * (amount - 1) + mult)`.

`mod_mult`/`mod_chips` wrap **every** assignment (base-game clamp/normalise; matters at
overflow). Final reduce is `floor(chips * mult)`. Dollars use `ease_dollars` with **no** mod
wrapper. `swap` (Vampire-ish) swaps `mult`↔`hand_chips`; `balance` (Acrobat-ish) sets both to
`(mult+hand_chips)/2`.

## 1d. Joker hook points (`context.*` surface, in fire order)

| Order | `context` flags | When | Typical effect |
|---|---|---|---|
| 1 | `modify_scoring_hand` | per played card, pre-score | add/remove card from scoring (`add_to_hand`/`remove_from_hand`) |
| 2 | `before = true` | once, before per-card scoring | priming, conditional setup |
| 3 | `repetition = true`, `cardarea = G.play/G.hand`, `other_card` | per card, before its score | return `{repetitions=n}` (retrigger jokers) |
| 4 | `individual = true`, `cardarea = G.play`, `other_card` | per **played** scoring card | +chips/+mult/×mult because of that card (suit/rank jokers) |
| 4h | `individual = true`, `cardarea = G.hand`, `other_card` | per **held** card | held-card reactions (Raised Fist, Baron, Shoot the Moon) |
| 5 | `joker_main = true`, `cardarea = G.jokers` | main joker pass, L→R | joker's headline chips/mult/xmult |
| 5e | `edition = true, pre_joker / post_joker` | wraps each joker's main eval | Foil/Holo `+`, Polychrome `×` on the joker itself |
| 5o | `other_joker = <card>` | for every joker, against every joker | Blueprint/Brainstorm copy; "+mult per other joker"; Baseball |
| 6 | `final_scoring_step = true` | once, after all jokers | last-chance adjustments + deck `final_scoring_step` |
| 7 | `destroying_card` / `remove_playing_cards` | destruction pass | Glass shatter, Hiker permabonus targets |
| 8 | `after = true` | once, post-hand | counters, "after each hand" jokers |

`Card:calculate_joker` order inside the main pass (`:713-794`): **(a)** joker edition
`pre_joker` → **(b)** `joker_main` (+ retriggers) → **(c)** for each other joker
`other_joker`/`other_consumeable`/`other_voucher` (+ retriggers) → **(d)** `eval_individual`
for deck/blind/stake/challenge/mods → **(e)** joker edition `post_joker`. Effects accumulate
and apply via `SMODS.trigger_effects` resolving keys in order `{playing_card, enhancement,
edition, seals, stickers, end_of_round, jokers, retriggers, individual}`.

## 1e. Blueprint / Brainstorm (re-entrant copy)

`SMODS.blueprint_effect`: a copier re-runs the copied card's `calculate_joker` with
`context.blueprint` depth-tracking (guard: depth ≤ #jokers, no self-copy, `blueprint_compat`).
Returned effect is **attributed to the copier card**. Brainstorm copies the *leftmost* joker;
Blueprint copies the joker to its *right*. Position-dependent.

## 1f. RNG: probabilistic effects

Lucky (+20 mult 1-in-5, +$20 1-in-15), Glass break (1-in-4), etc. use
`pseudorandom(pseudoseed(key))`. Numerator is mutable (`Oops! All 6s` doubles every
probability). **Determinism requirement: both clients must see identical procs for identical
inputs** — modeled as game-long pop queues (see §2). Lucky uses keys `lucky_mult`/`lucky_money`.

## 1g. Big-number requirement

Real round score is `floor(chips * mult)`. The reference server uses `InsaneInt` (3-limb
big-number, `BalatroMultiplayerAPI-Server-main/src/InsaneInt.ts`) to survive f64 overflow at
high antes. **Our score must be a big-number** (port `InsaneInt` or `BigDecimal`→floor→
`BigInteger`); blind-requirement and opponent comparisons use big-number compare. At
parity-critical antes a raw `double` diverges from the opponent's client-computed score.

## 1h. 0.3.3 → 0.4.0 scoring-adjacent deltas

No changes to pipeline order or chips/mult/xmult math. Parameter tweaks only:
- **Gold Card enhancement** payout $3 → **$4** (end-of-round money; distinct from Gold
  *seal*'s +$3 in-scoring).
- **Hanging Chad** (Legacy): retriggers the **first 2 cards** instead of the first card twice
  (changes *which* cards get reps, not the rep mechanic).
- **Golden Ticket** $3 → $4; **To Do List** $4 → **$5** and targets all hands; **Seltzer** 8
  hands; **Turtle Bean** +4 hand size.
- **Comeback Gold** on any life loss. **Judgment** draws from its own queue on Orange Stake+.

## 1i. Multiplayer parity note

In the reference `BalatroMultiplayerAPI-Server-main`, scoring is **client-side**: the client
submits its score via `playHand{score}` and the server merely **compares two submitted
scores** with `InsaneInt`. The server trusts the client's number. **Our project deliberately
diverges: scoring is server-authoritative** — we recompute the score from intents, so we must
re-derive §1a–§1f exactly.

---

# 2. RNG & queue topology

Server-authoritative spec for the complete game-long queue system. Target: **BMP 0.4.0**
("The Order"). The reframing finding:

> **BMP 0.4.0 does NOT maintain hand-rolled per-category queue data structures.** It reuses
> vanilla's `pseudoseed(key)` keyed-stream RNG and turns each key into a game-long stream by
> two tricks: (1) **forcing `G.GAME.round_resets.ante = 0`** around card creation so ante
> drops out of every key, and (2) **dropping the `_resample` suffix** so a blocked/duplicate
> roll re-draws the *same* stream (advancing it) instead of branching to a side stream.
> (`TheOrder.lua:1-27,44-50`; vanilla `_resample` ternary `common_events.lua:2449`.)

So a "queue" in BMP = "a `pseudoseed` key whose state is never reset across the run and is
never perturbed by ante or resample-branching." The **key namespacing and the ante-zeroing
are the load-bearing parity details**, not the queue container itself.

**Status:** `GameQueue`/`QueueSet` (per-key cursored streams, `nextWhere` block/skip) is the
correct abstraction and already correct on resample-branching; missing most concrete keys +
the ante-zeroing discipline + shop/pack/voucher/soul generation. (Details in HANDOFF.)

## 2.1 pseudohash / pseudoseed / pseudorandom (vanilla)

```lua
function pseudohash(str)                                  -- misc_functions.lua:309
  local num = 1
  for i=#str, 1, -1 do
    num = ((1.1239285023/num)*string.byte(str, i)*math.pi + math.pi*i)%1
  end
  return num
end
function pseudoseed(key, predict_seed)                    -- :328
  if key == 'seed' then return math.random() end
  -- if G.SETTINGS.paused and key ~= 'to_do' then return math.random() end  -- DISABLED by MP
  if not G.GAME.pseudorandom[key] then
    G.GAME.pseudorandom[key] = pseudohash(key..(G.GAME.pseudorandom.seed or ''))
  end
  G.GAME.pseudorandom[key] =
    math.abs(tonumber(string.format("%.13f",
      (2.134453429141 + G.GAME.pseudorandom[key]*1.72431234) % 1)))
  return (G.GAME.pseudorandom[key] + (G.GAME.pseudorandom.hashed_seed or 0))/2
end
```

Properties that matter:
- **Per-key state** `G.GAME.pseudorandom[key]` is a float advanced by a fixed affine-mod
  recurrence each call. This **is a queue**: call N for a key always yields the same value on
  a given seed; consuming key A never perturbs key B.
- **`hashed_seed`** = `pseudohash(seed)`, set once at run start, blended into every output.
- BMP **disables the `paused` early-return** (the line is commented out in the dump). Vanilla
  returns a live `math.random()` while paused; BMP must not (voucher RNG can run paused →
  desync). **DELTA: never short-circuit a queue draw on "paused".**

## 2.2 Pool + block/skip ("UNAVAILABLE") model (vanilla)

`get_current_pool(_type,_rarity,_legendary,_append)` builds an *ordered* list the same way
every time, mapping each entry to its key or the literal `'UNAVAILABLE'`:
- Jokers split by rarity into `G.P_JOKER_RARITY_POOLS[1..4]`; pool key `'Joker'..rarity..
  append` → `"Joker1"`,`"Joker2"`,`"Joker3"`,`"Joker4"` (Common/Uncommon/Rare/Legendary) —
  the rarity-split queues.
- Planets `UNAVAILABLE` if soft-locked and never played. Vouchers `UNAVAILABLE` if owned /
  prereq unmet / already in shop. Soul / Black Hole / hidden centers always `UNAVAILABLE` in
  normal pools (enter only via the soul roll, §2.5). `banned_keys` and `mp_include`/ruleset
  gating remove items.

Selection (`create_card`):
```lua
local _pool, _pool_key = get_current_pool(_type, _rarity, legendary, key_append)
center = pseudorandom_element(_pool, pseudoseed(_pool_key))
local it = 1
while center == 'UNAVAILABLE' do
  it = it + 1
  center = pseudorandom_element(_pool,
    pseudoseed(_pool_key..(MP.should_use_the_order() and '' or ('_resample'..it))))
end
```
This is **`GameQueue.nextWhere(notUnavailable)`**: keep drawing the same key's stream until a
usable item appears; never branch. The Order forces the suffix to `''` so resample advances
the SAME stream.

## 2.3 Main category stream — `cdt`

`create_card_for_shop` decides each shop slot's *category* before the item:
```lua
local total_rate = G.GAME.joker_rate + G.GAME.playing_card_rate + Σ consumable rates
local polled_rate = pseudorandom(pseudoseed('cdt'..MP.ante_based())) * total_rate
-- walk rates in FIXED order {Joker, Tarot, Planet, Base/Enhanced, Spectral, ...}
```
`MP.ante_based()` returns `0` under The Order → key is just `"cdt"` → **one game-long
category stream**. Default rates (unverified exact values): Joker ~20, Tarot ~4, Planet ~4,
PlayingCard 0 (needs Magic Trick voucher), Spectral 0 (needs Ghost deck). A category gated to
rate 0 can never be selected (the "skip" behavior).

For a Joker slot, rarity is rolled by `SMODS.poll_rarity("Joker",'rarity'..ante..append)` →
under The Order key `"rarity"` → thresholds `>0.95 → Rare, >0.7 → Uncommon, else Common`.
The player-facing "main queue of tags" = interleaving of `cdt` + `rarity` streams.

## 2.4 Up-Top vs Pack consumable queues

Realized by `key_append` (`TheOrder.lua:9-14`):
```lua
if _type=="Tarot"/"Planet"/"Spectral" then
  if area == G.pack_cards then key_append = _type.."_pack"   -- Pack queue
  else                          key_append = _type           -- Up-Top queue
end
```
Pool key becomes `Tarot` (up-top) vs `Tarot_pack` (pack) — **two distinct game-long streams
per consumable type** (6 streams total). Up-Top = The Fool/Superposition/Séance etc. (any
non-pack creation); Pack = booster contents.

## 2.5 Soul / Black Hole insertion

`create_card`:
```lua
if (_type=='Tarot' or 'Spectral' or 'Tarot_Planet') and not used(c_soul) then
  if pseudorandom('soul_'..(MP.should_use_the_order() and 'c_soul' or _type)..ante) > 0.997
    then forced_key = 'c_soul' end
end
if (_type=='Planet' or 'Spectral') and not used(c_black_hole) then
  if pseudorandom('soul_'..(MP.should_use_the_order() and 'c_black_hole' or _type)..ante) > 0.997
    then if not (the_order and forced_key) then forced_key = 'c_black_hole' end end
end
```
- The Order collapses to **two game-long streams: `"soul_c_soul"` and `"soul_c_black_hole"`**
  (ante zeroed, type-independent).
- Threshold `> 0.997` → ~0.3% per consumable created.
- **Insertion semantics:** every consumable created in a Tarot/Spectral pack rolls the soul
  stream; on a hit, the would-be consumable is *pushed back one* and The Soul/Black Hole takes
  the slot. The soul stream is consumed once per created card, independent of the pool stream
  — a hit *inserts*, does not consume a pool item. Both players at equal pack positions see
  the soul at the same slot.
- Mutual exclusion: under The Order, if `c_soul` won the slot, `c_black_hole` cannot overwrite.

## 2.6 Packs queue (booster types, with skip-offset)

`get_pack(_key,_type)` weight-rolls a booster from `G.P_CENTER_POOLS['Booster']` using
`pseudoseed((_key or 'pack_generic')..ante)`. Under The Order ante=0 → game-long packs
stream. First-ever shop is hard-pinned to a random normal Buffoon pack (`math.random(1,2)` —
**a raw `math.random`, parity hazard**). **Skip-offset:** the packs stream advances only **when
a shop is actually seen** — skipping a blind doesn't draw, so your packs shift one shop later
vs a nemesis who didn't skip. It is a queue keyed to *shop-views*, not ante.

## 2.7 Voucher queue 1–16

`get_culled` + `SMODS.get_next_vouchers`/`get_next_voucher_key` (`TheOrder.lua:222-290`):
- Pool is **paired Tier-1/Tier-2** (`get_culled` walks `i=1,#pool,2`). The "1–16" framing =
  16 base voucher families; each resolves to its Tier-1, or Tier-2 if T1 owned, or skipped if
  both owned (`UNAVAILABLE` culling).
- **Single key `"Voucher0"`** for every draw → one game-long stream, ante-independent.
- Resample on `UNAVAILABLE`/already-spawned re-draws the SAME `Voucher0` stream; only after
  1000 failures appends `it` as fallback.
- Voucher **tags** advance the same stream; **back-to-back duplicate skipped** (maps to the
  `vouchers.spawn[center]` dedupe guard).

## 2.8 Side joker queues (Rare-tag/Wraith, Uncommon-tag, Riff-Raff/Top-Up)

Tag/joker effects that create jokers **outside** the shop. Each forces a rarity and calls
`create_card('Joker', area, false, rarity, ...)`. Under The Order they ride the shared
`Joker1/2/3` per-rarity streams (advancing them at positions the opponent may not), partly
achieving side-independence. **Judgement** is special-cased: `TheOrder.lua:16-19` gives it its
own rarity sub-roll (`pseudorandom("order_jud_rarity")`) only when eternals are enabled.
**Ambiguity:** whether Riff-Raff/Wraith use a truly separate key is not explicit; we may be
stricter (separate keys) if we want true side-independence.

## 2.9 Bloodstone / Lucky / Glass (in-scoring probability)

SMODS `pseudorandom_probability` calls inside `calculate`. Bloodstone:
`SMODS.pseudorandom_probability(card, "j_mp_bloodstone_sandbox", 1, odds)` with `odds=3,
Xmult=2`. Lucky/Glass use vanilla keys `lucky_money`/`lucky_mult` and the glass-break key.
Parity property: **equal triggers → equal hits regardless of hands-left or order** (per-key
stream model). The spreadsheet adds a **Bloodstone PvP nuance**: a per-ante PvP queue that
*resets to its start after each hand*, so two players with equal triggers get identical hits
even if one had more hands left. **Not visible in the on-disk 0.4.0 sandbox joker** (plain
probability call) — flagged as a 0.3.3-doc behavior to verify.

## 2.10 Shuffle / pseudorandom_element determinism (The Order)

`TheOrder.lua:358-461` replaces `pseudoshuffle` and `pseudorandom_element` for playing-card/
joker collections with a **value-keyed sort** (`give_shufflevals`): each card gets
`mp_shuffleval = pseudorandom(suit+rank+seed)` and the list is sorted by it (after grouping
identical cards by enhancement/seal/edition "stdval"). This makes "pick a random card from
deck/jokers" **insensitive to table iteration order** (Lua `pairs()` is unordered), which
would otherwise desync players. Idol and Mail are reworked to sort-then-weighted-pick.

## 2.11 Canonical queue keys (run's queue topology)

All keys are **ante-independent** (ante-zeroing baked in by *not* putting ante in the key).
One `GameQueue` per key (`QueueSet` namespaces as `"queue:"+key`):

```
joker.common        joker.uncommon      joker.rare       joker.legendary
joker.rarity        // rate stream: maps roll → rarity bucket (>.95 Rare, >.7 Uncommon, else Common)
shop.category       // "cdt": maps roll → {JOKER,TAROT,PLANET,PLAYING,SPECTRAL,...} (fixed rate-walk order)
tarot.uptop         planet.uptop        spectral.uptop
tarot.pack          planet.pack         spectral.pack
soul.c_soul         soul.c_black_hole   // hit/miss, threshold at read (>0.997)
packs               // booster-type queue, advanced on shop reveal (skip-offset)
voucher             // single stream over culled T1/T2 pairs ("Voucher0")
playing_card        // Magic Trick / standard pack faces
prob.lucky_mult  prob.lucky_money  prob.glass  prob.<joker_key>
bloodstone.global   bloodstone.pvp     // pvp resets each hand
select.idol  select.todo  select.mail  select.invisible   // sort-then-pick
boss                // per-ante get_new_boss
tag                 // Small/Big skip tags
```

Block/skip = `nextWhere`; the **ruleset supplies the ordered, culled pool** (analog of
`get_current_pool`) with `UNAVAILABLE` markers, recomputed at each draw from current
ownership while the stream itself never resets. Pool order must be deterministic and identical
for both players (sort by stable id). Each draw consults the `BanSet` (reject-and-redraw
deterministically).

## 2.12 0.3.3 → 0.4.0 queue deltas

- **To Do List** reworked: target hand from **all** hands, not just discovered (changes the
  selection stream's candidate set).
- **Speedrun out of rotation** (drop its nemesis-spend stream); **Justice back in rotation**
  (re-add to Up-Top/Pack tarot pools).
- **Ouija/Ectoplasm cost $4** (economy only, no queue effect).
- **Judgement own-queue** rule: "draws from its own queue on Orange Stake and above; uses
  shop queue on lower stakes" — **stake-gated queue selection**.

---

# 3. Economy

Authoritative reference for every dollar entering/leaving a run. All money mutations are
**server-authoritative**: the client sends intents (buy/sell/reroll/skip), the server
recomputes balances. The only economy value BMP transmits is the *opponent's* shop spend
(`spentLastShop`), input to nemesis-aware jokers.

**Status:** starting $4, interest `min(5, money/5)`, blind rewards $3/$4/$5, gold-card +$3,
generic joker `dollars` path, flat reroll $5 exist. Missing: selling, leftover-hand/discard
cash, comeback, interest-cap vouchers, edition premiums, reroll scaling/vouchers, discounts,
inflation, economy tags, nemesis-spend jokers, Credit-Card debt floor, Oracle cap. (HANDOFF.)

## 3.1 Starting money (per deck)

Base default: most decks start the run with **$4** (`previous_round.dollars = 4`).

| Deck | Economy effect |
|------|----------------|
| Red / most | start $4 |
| Yellow Deck | start **$14** ($4 + `config.dollars = 10`) |
| Green Deck | **no interest**; instead $2/leftover hand + $1/leftover discard at round end (`extra_hand_bonus=2, extra_discard_bonus=1, no_interest=true`) |
| Plasma Deck | `ante_scaling = 2` (doubles blind requirements; indirect pressure) |
| Magic / Nebula / Zodiac | start with free voucher(s) — shop economy, not starting cash |
| Oracle (BMP) | starts with `v_clearance_sale`; **money hard-capped at `oracle_max=50` + `interest_cap=25` = $75** for positive money; negative mods (spending) bypass the cap |

## 3.2 Interest

Vanilla: at round end, **+$1 per $5 held, capped at $5**:
```lua
if G.GAME.dollars >= 5 and not G.GAME.modifiers.no_interest then
  dollars = dollars + interest_amount * math.min(math.floor(dollars/5), interest_cap/5)
end
```
Defaults `interest_cap = 25`, `interest_amount = 1` → max interest `1 * 5` = **$5**.

| Source | Effect |
|--------|--------|
| **To the Moon** (joker, U, $5) | `interest_amount += 1` ($2 per $5) |
| **Seed Money** (voucher, $10) | `interest_cap → 50` (max interest $10) |
| **Money Tree** (voucher, $10, req Seed Money) | `interest_cap → 100` (max interest $20) |
| **Green Deck** | `no_interest = true` |
| BMP `no_interest` mutator | disables interest entirely |
| BMP `mp_modified_interest_rate` | pays per `rate` dollars instead of per $5, `cap = interest_cap/(5/rate)` |

Interest is computed off held cash at round-eval time.

## 3.3 Blind rewards

| Blind | Reward | Score mult |
|-------|--------|-----------|
| Small Blind | **$3** | ×1 |
| Big Blind | **$4** | ×1.5 |
| Boss (regular) | **$5** | ×2 (varies; Wall ×4, Needle ×1, Violet Vessel ×6) |
| Finisher bosses (Acorn/Leaf/Vessel/Bell/Heart — ante 8) | **$8** | ×2 (Vessel ×6) |

Reward only if the blind is **beaten** (`chips - blind.chips >= 0`); saved/unbeaten = $0.
Skipping a Small/Big blind awards its **skip tag** instead of money.

Round-eval bonus money on top of the blind reward:
- **Leftover hands**: `+$ (hands_left × money_per_hand)`, default `money_per_hand = 1` → **$1
  per unused hand** (suppressed by `no_extra_hand_money`; never paid during a PvP/Nemesis blind).
- **Leftover discards**: `+$ (discards_left × money_per_discard)`, but `money_per_discard`
  defaults to **0** unless a deck/voucher/mutator enables it (Green Deck $1, Recyclomancy, BMP
  `frugal`).

## 3.4 Sell values

```lua
function Card:set_sell_value()
  self.sell_cost = math.max(1, math.floor(self.cost/2)) + (self.ability.extra_value or 0)
end
```
- Jokers/consumables: `max(1, floor(cost/2)) + extra_value`.
- **Egg** (joker, $4): +$3 sell value each round. **Gift Card** (joker, $6): +$1 sell value to
  every joker & consumable each round.
- Phantom-edition (BMP) cards have `sell_cost = 0`.
- Selling calls `ease_dollars(sell_cost)`; BMP broadcasts `sold_joker` for Taxes.

## 3.5 Shop pricing

```lua
self.cost = math.max(1, math.floor((self.base_cost + self.extra_cost + 0.5) * (100 - G.GAME.discount_percent)/100))
```
`extra_cost = G.GAME.inflation + edition premium`.

**Joker cost by rarity** (per-joker in data, not a flat constant): Common ≈ **$4–6**, Uncommon
≈ **$5–8**, Rare ≈ **$8**, Legendary ≈ **$20**. (e.g. Credit Card $1, Golden Joker $6, Rocket
$6, Bull $6, Bootstraps $7, To the Moon $5, Egg/Mail-In/Business/Faceless/To-Do/Delayed-Grat/
Chaos $4.)

| Item | Base cost |
|------|-----------|
| Tarot / Planet / Spectral (shop slot) | **$3** (Immolate spectral $4) |
| Voucher | **$10** (all `v_*`) |
| Booster packs | Normal $4 / Jumbo $6 / Mega $8 |
| BMP Ouija / Ectoplasm | **$4** (0.4.0 fix) |

**Edition price premiums** (`extra_cost`):

| Edition | extra_cost | gameplay |
|---------|-----------|----------|
| Foil | **+$2** | +50 chips |
| Holographic | **+$3** | +10 mult |
| Polychrome | **+$5** | ×1.5 mult |
| Negative | **+$5** | +1 joker/consumable slot |

Special rules: Coupon tag → $0 in shop; Rental → $1 in shop + `rental_rate = 3`/round upkeep;
Astronomer → all Planets & Celestial packs free; `booster_ante_scaling` → pack cost
`+ (ante-1)` (BMP `pricey_packs`); inflation → +$1 per purchase, compounding all future
`extra_cost`.

## 3.6 Reroll cost scaling

```lua
function calculate_reroll_cost(skip_increment)
  if free_rerolls > 0 then reroll_cost = 0; return end
  if not skip_increment then reroll_cost_increase = reroll_cost_increase + 1 end
  reroll_cost = (temp_reroll_cost or round_resets.reroll_cost) + reroll_cost_increase
end
```
- **Base reroll cost = $5.** Each paid reroll increments `reroll_cost_increase` by **+1**
  (compounding within the round). `reroll_cost_increase` and `free_rerolls` reset each round.

| Source | Effect |
|--------|--------|
| **Reroll Surplus** (voucher, $10) | base reroll **−$2** |
| **Reroll Glut** (voucher, $10, req Surplus) | another **−$2** (total −$4) |
| **Chaos the Clown** (joker, $4) | **1 free reroll per shop** |
| Coupon-style tags | temporary discounts via `temp_reroll_cost` |

## 3.7 Cash-out screen (round evaluation, ordered)

`G.FUNCS.evaluate_round` builds rows in order:
1. **Blind reward** (`blind.dollars`, or $0 if saved).
2. **Remaining hands** × `money_per_hand` (default $1).
3. **Remaining discards** × `money_per_discard` (default $0).
4. **Per-joker `calculate_dollar_bonus`** (Golden Joker, Rocket, Cloud 9, …).
5. **Per-object `calc_dollar_bonus`** (custom/individual).
6. **Tag eval bonuses** (Investment Tag etc.).
7. **Interest.**
8. **(BMP) Comeback bonus** — injected here.
9. **(BMP) `mp_modified_interest`** band if active.
10. Rows beyond 7 collapse into a hidden "+N more" row.

Sum applied to `G.GAME.dollars`. **Credit Card** sets the bankrupt floor so balance can go
negative.

## 3.8 Economy-modifying jokers

| Joker | Rarity / cost | Economy effect |
|-------|---------------|----------------|
| **Golden Joker** | C / $6 | **+$4 at end of round** |
| **Rocket** | U / $6 | **+$1 at end of round, +$2 each boss defeated** |
| **Bull** | U / $6 | **+2 chips per $1 held** |
| **Bootstraps** | U / $7 | **+2 mult per $5 held** |
| **Cloud 9** | U / $7 | **+$1 per 9 in deck at end of round** |
| **To the Moon** | U / $5 | **interest_amount +1** |
| **Gift Card** | U / $6 | **+$1 sell value to all jokers/consumables each round** |
| **Egg** | C / $4 | **+$3 sell value each round** |
| **Credit Card** | C / $1 | **debt floor: go to −$20** |
| **Mail-In Rebate** | C / $4 | **+$5 per discarded card of the round's rank** |
| **Delayed Gratification** | C / $4 | **+$2 per discard if no discards used this round** |
| **Business Card** | C / $4 | **face cards chance to give $2** |
| **Faceless Joker** | C / $4 | **+$5 if ≥3 face cards discarded** |
| **To Do List** | C / $4 | **+$5** (0.4.0; was $4) if scored hand matches target |
| **Reserved Parking** | C / $6 | face cards chance to give $1 (odds=2) |
| **Matador** | U / $7 | **+$8 if played hand triggers boss ability** |
| **Chaos the Clown** | C / $4 | 1 free reroll/shop |

## 3.9 Economy vouchers & tags

**Vouchers** (all base $10): Clearance Sale (−25% discount), Liquidation (−50%, req
Clearance), Seed Money (interest cap → $10), Money Tree (→ $20), Reroll Surplus/Glut (−$2/−$4),
Overstock / Overstock Plus (+1/+2 shop slots), Tarot/Planet Merchant + Tycoon (raise
appearance rate), Director's Cut / Retcon (reroll boss blind, $10 each reroll).

**Tags:**

| Tag | Economy effect |
|-----|----------------|
| **Investment Tag** | **+$25 after defeating next boss** |
| **Economy Tag** | **doubles your money**, capped at +$40 |
| **Handy Tag** | **+$1 per hand played this run** |
| **Garbage Tag** | **+$1 per unused discard this run** |
| **Skip Tag** | **+$5** immediate |
| **Top-up Tag** | spawns 2 free Common jokers (value, not cash) |
| Foil/Holo/Poly/Negative Tags | next shop joker gets that edition free (saves the premium) |

## 3.10 BMP-specific economy

**Comeback bonus / "Comeback Gold"** — awarded once per round at cash-out, *given on any life
loss* (0.4.0 change; previously only PvP-boss losses):
```
sandbox ruleset:        comeback = 3 * (ante - 1)
major-league ruleset:   comeback = 4 * MP.GAME.comeback_bonus   (increments per life lost)
default:                comeback = 2 if stake >= 6 else 4
```
`comeback_bonus` increments each life lost; `comeback_bonus_given` resets per round (pays at
most once per round).

**Nemesis (PvP) blind**: `dollars = 5, mult = 1` (mult kept 1 to avoid a crash). Beating/
ending the PvP blind pays $5. During a Nemesis blind, **leftover-hand money is suppressed**
(`is_pvp_boss()`), and the blind reward is always paid even if you "lose" on chips. Cash-out
runs **after the PvP result is decided** (engine: `endPvp()` awards reward+interest, then
`endOfRound`); both players resolve independently; the only cross-player signal is
`spentLastShop`.

**Opponent shop-spend tracking**: every money decrease adds to `MP.GAME.spent_total`; on
leaving the shop the client sends `spentLastShop(spent_total - spent_before_shop)`; the server
**relays** it (no validation); opponent stores it in `enemy.spent_in_shop[]`.

**Penny Pincher** ($4 C): at cash-out, **$1 per $3 nemesis spent in their last shop**
(`floor(spent/3)`). **Taxes** ($5 C): when the Nemesis blind is set, **+4 Mult per joker/
consumable the nemesis sold this ante** (accumulates earlier antes pre-PvP). **Pizza** ($4 C):
at `mp_end_of_pvp`, **+2 discards to you, −1 to nemesis**, then self-destructs.

**BMP economy mutator layers** (`G.GAME.modifiers`, deterministic across both clients):

| Layer | Effect |
|-------|--------|
| `inflation` | shop prices +$1 per purchase, compounding |
| `no_interest` | no interest |
| `discard_tax` | each discard costs $1 |
| `frugal` | leftover discards pay $1 each |
| `spartan` | Small/Big blinds pay $0 (Boss/PvP intact) |
| `pricey_packs` | packs cost +$1 per ante into run |

## 3.11 0.3.x → 0.4.0 economy deltas

To Do List $4 → $5 (target from all hands); Golden Ticket reverted $3 → $4; Gold Card
enhancement $3 → $4; Ouija & Ectoplasm fixed to $4; Comeback Gold on **any** life loss
(payouts $4 / $2-high-stake unchanged); Speedrun out of rotation; Justice back in rotation.

---

# 4. Run lifecycle

The run/ante/blind state machine — Small/Big/Boss selection, blind skipping + tags, shop
entry/exit, ante progression, win/loss, endless — plus the multiplayer overlays
(Nemesis/PvP blinds, lives, gamemodes).

**Status:** `Run` has phases (no explicit blind-select), faithful blind-requirement curve,
boss pick, ante progression, PvP via `pvpFromAnte`. Missing blind-select phase, skip/tags,
gamemode abstraction, gamemode-driven lives, all-PvP showdown slots, bans, PvP timer,
endless-continue. (HANDOFF.)

## 4.1 Base-game round-state container (`round_resets`)

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
- **Three blinds per ante**: Small, Big, Boss, each with an independent state in
  `{Select, Upcoming, Selected, Skipped, Defeated, Hidden, Current}`.
- **Boss chosen at run start, re-chosen each ante** (`get_new_boss()`).
- **Win condition**: `win_ante = 8`; game-over/win gated on `round_resets.ante <= win_ante`.
  Beating the Boss of `win_ante` wins; exceeding it without an endless continue = win screen.
- **Tags chosen per ante for Small and Big** (the skip reward, `get_next_tag_key()`). Only
  Small and Big can be skipped; Boss cannot.
- **Seed**: set in `start_run`, pseudohashed per-key, `hashed_seed = pseudohash(seed)`; BMP
  forces an `"*"` prefix when `the_order` is on.

## 4.2 Vanilla blind selection / skip / ante-up flow

`BLIND_SELECT` → pick one of three offered blinds OR skip Small/Big (claim its tag) →
`SELECTING_HAND` (play/discard) → on clear `ROUND_EVAL` (economy) → `SHOP` → `select_blind`
advances the pointer; after Boss, `ante_up` increments `round_resets.ante` and `get_new_boss()`
re-rolls. Skipping calls `add_tag(_tag)` and flips that blind's state to `Skipped`.

## 4.3 BMP 0.4.0 multiplayer overlay

BMP layers a **gamemode** + **ruleset** on the vanilla loop (distinct SMODS object types):

- **Rulesets** declare banned/reworked content (`banned_jokers`, `banned_consumables`,
  `banned_vouchers`, `banned_enhancements`, `banned_tags`, `banned_blinds`, `reworked_*`),
  composed from **layers**. E.g. Standard Ranked: `layers = {"standard","ranked"},
  forced_gamemode = "gamemode_mp_attrition"`. Bans applied at run start via `MP.ApplyBans()`
  → `G.GAME.banned_keys[v] = true`, merging ruleset + gamemode + deck bans.

- **Gamemodes**: required hook **`get_blinds_by_ante(self, ante) -> small, big, boss`**
  (blind keys, or `nil` = keep vanilla). This is the run/blind state-machine seam.
  - **Attrition**: `if ante >= pvp_start_round (default 2) then return nil, nil,
    "bl_mp_nemesis"` (unless `normal_bosses`, which forces a PvP *choice* via
    `round_resets.pvp_blind_choices.Boss = true`). From `pvp_start_round` the **Boss slot
    becomes a Nemesis (PvP) blind**. Bans: `j_mr_bones, j_luchador, j_matador, j_chicot`;
    vouchers `v_hieroglyph, v_petroglyph, v_directors_cut, v_retcon`; tag `tag_boss`; blinds
    `bl_wall, bl_final_vessel`.
  - **Showdown**: `if ante >= showdown_starting_antes (default 3) then return
    "bl_mp_nemesis","bl_mp_nemesis","bl_mp_nemesis"` — **all three blinds PvP**. Same bans.
  - **Survival**: always `nil,nil,nil` (pure vanilla blinds), **1 life**, endless — last
    standing / furthest. Bans a large MP-joker list + `c_mp_asteroid`.

- **Nemesis blind**: `SMODS.Blind{ key="nemesis", dollars=5, mult=1 ("Jen's Almanac crashes
  if mult is 0"), boss={min=1,max=10}, in_pool=()->false }`. **Never in the natural boss
  pool** — only injected by the gamemode hook. `MP.is_pvp_boss()` true when
  `blind.config.blind.key == "bl_mp_nemesis"` or `G.GAME.blind.pvp`.

## 4.4 Lives & lobby config defaults (BMP 0.4.0)

`MP.reset_lobby_config()`:
- `starting_lives = 4`, `pvp_start_round = 2`, `showdown_starting_antes = 3`
- `gold_on_life_loss = true`, `no_gold_on_round_loss = false`, `death_on_round_loss = true`
- `different_seeds = false`, `the_order = true`, `custom_seed = "random"`
- timers: `timer_base_seconds = 150`, `timer_increment_seconds = 60`, `pvp_countdown_seconds
  = 3`, `timer_forgiveness = 0`
- default `ruleset = "ruleset_mp_blitz"`, default `gamemode = "gamemode_mp_attrition"`
- decks/loadout: `different_decks=false`, `random_loadout=false`, `back="Red Deck"`,
  `stake = 1`, `multiplayer_jokers = true`, `legacy_smallworld = false`

`MP.reset_game_states()` tracks the live overlay: `lives`, `enemy = {score, hands=4, skips,
lives=starting_lives, ...}`, `next_blind_context`, `pvp_reached`, `pvp_countdown`,
`furthest_blind`, `comeback_bonus`, `end_pvp`, `stats`.

## 4.5 Blind requirement curve

Ante 1..8 base chips are fixed; >8 use `a*(b+(k*c)^d)^c`, truncated to the 2nd significant
digit. Small ×1, Big ×1.5, Boss ×2; rewards $3/$4/$5. Nemesis blind `dollars=5, mult=1`.

## 4.6 Target state machine

Explicit blind-select step; PvP is a property of the *current blind slot*, not Boss-only:

```
enum Phase {
    BLIND_SELECT,   // choosing/skipping the upcoming blind (NEW)
    BLIND_ACTIVE,   // play/discard until requirement met or hands out
    PVP_PENDING,    // out of hands in a PvP blind, awaiting comparison
    ROUND_EVAL,     // economy applied (NEW, explicit)
    SHOP,
    BLIND_FAILED,   // failed a blind in a lives gamemode (life lost, continue)
    RUN_WON, RUN_LOST
}
enum BlindState { UPCOMING, SELECT, CURRENT, SELECTED, DEFEATED, SKIPPED, HIDDEN }
record BlindSlot(BlindType type, String blindKey, BlindState state, boolean pvp,
                 String skipTagKey)   // skipTagKey only for SMALL/BIG
```

`Run` holds `BlindSlot[3]` per ante (small, big, boss), recomputed at each ante via the
gamemode hook. Transitions:
```
ante start → gamemode.resolveBlinds(ante, lobbyOpts) fills 3 slots → first non-done slot → BLIND_SELECT
BLIND_SELECT:
   selectBlind() → slot.state=CURRENT, deal deck, Phase=BLIND_ACTIVE
   skipBlind()   → (SMALL/BIG only, not banned, not pvp) claim slot.skipTagKey,
                   slot.state=SKIPPED, advance to next slot's BLIND_SELECT
BLIND_ACTIVE:
   playHand → if pvp && handsLeft==0 → PVP_PENDING
            → else if score>=req → slot.state=DEFEATED, ROUND_EVAL→SHOP
            → else if handsLeft==0 → RUN_LOST (solo) | BLIND_FAILED (lives mode)
SHOP.proceed() → next slot; after BOSS: ante++ or win/endless
```

## 4.7 Skip + tags

`skipBlind()` legal only when `slot.type ∈ {SMALL,BIG}`, `!slot.pvp`, key not banned, tag not
banned. A run-long **tag inventory**; on skip, append `slot.skipTagKey`. Small/Big tag keys
drawn from a deterministic **tag queue** (mirrors `get_next_tag_key()`), gated by `bannedTags`.
Tag *effects* resolve at their trigger points (shop entry, next blind, immediate). A `skips`
counter is surfaced for the PvP timer-increment + enemy HUD.

## 4.8 Gamemode hook

```java
public record Gamemode(
    String key, int startingLives, boolean endless,
    BlindResolver blinds, BanSet bans) { }
record BlindOverride(String small, String big, String boss) {} // null = keep vanilla
```
- **attrition**: `ante >= pvpStartAnte (def 2)` → `(null,null,"bl_pvp")` (unless `normalBosses`).
- **showdown**: `ante >= showdownStartingAntes (def 3)` → `("bl_pvp","bl_pvp","bl_pvp")`.
- **survival**: always all-null; `startingLives=1`, `endless=true`.

Any non-null slot is marked `pvp=true, blindKey="bl_pvp", requirement=0`; non-PvP slots fall
through to normal selection (boss honoring bans).

## 4.9 Ante progression, win, endless

```
proceed() after BOSS:
   if gamemode.endless || ruleset.winAnte()==0:  ante++; continue   // survival/attrition: only lives end it
   else if ante >= ruleset.winAnte():
       if endlessRequested: ante++; markEndless(); continue
       else Phase=RUN_WON
   else ante++
```
`winAnte` stays in `Ruleset`; **endless-vs-bounded is a gamemode property** so survival/
attrition are endless regardless of ruleset.

## 4.10 Bans (ruleset + gamemode + deck)

Resolved `BanSet` = union of `ruleset.banned*` ∪ `gamemode.banned*` ∪ `deck.banned*` (mirror
`MP.ApplyBans`). Gates: shop joker/consumable/voucher pools, the boss pool (`bl_wall`,
`bl_final_vessel` banned in attrition/showdown), the tag queue (`tag_boss` banned).

## 4.11 Determinism / queues

One run-long `QueueSet` per player. Add queues: `boss` (per-ante `get_new_boss`), `tag`
(Small/Big skip tags), `voucher` (per-ante), each ban-gated (reject-and-redraw
deterministically). Same-seed both players ⇒ identical blind/boss/tag/voucher/shop sequences;
only build/play varies. Keep `the_order` `"*"`-seed prefix as a ruleset/lobby flag.

## 4.12 Authoritative intents

New intents through `IntentHandler`/`Run`: `SelectBlind`, `SkipBlind`, `RerollBoss` (if a
reroll voucher is owned), `EnterShop` (implicit on round-eval), `LeaveShop` (→ `proceed`), plus
existing play/discard/buy/use. Each validated against phase + bans + economy server-side;
`ServerUpdate` returns the new `ClientView` (extended with `blindStates`, `blindChoices`,
`tags`, `lives`, `skips`, the 3 blind choices, `pvp` flags).

## 4.13 Server-vs-mod delta to flag

`SERVER/src/GameMode.ts` attrition returns `{ boss: "bl_pvp" }` for **every** ante (no
`pvp_start_round` gate), whereas the 0.4.0 mod gates on `ante >= pvp_start_round` (default 2).
The mod is newer/authoritative; **our design follows the 0.4.0 mod**. Server showdown
`startingLives: 2` vs the 0.4.0 UI showing **4**: lives are a *lobby option* (`starting_lives
= 4` default); the server's per-mode literal is a fallback. Treat `startingLives` as gamemode
default, overridable by lobby config.

---

# 5. PvP / Attrition / Nemesis

The gamemode/PvP layer: per-gamemode rules, lives, the Nemesis blind, win/loss resolution,
and the exact opponent ("nemesis") state our jokers must read.

**Status:** `Match` is a 2-player same-seed race with hardcoded `STARTING_LIVES=4`,
`PVP_FROM_ANTE=2`, `resolveNemesisIfDecided` racing a `long roundScore`, and a thin
`opponentSummary`. Missing gamemode abstraction, big-number score, PvP timer, OpponentView,
`mp_pvp_loss` context, per-gamemode bans, comeback-on-any-life-loss. (HANDOFF.)

## 5.1 Gamemode object model

Each gamemode supplies a **blind-selection override** (`get_blinds_by_ante(self, ante) ->
small, big, boss`; `nil` = vanilla blind) plus banned/reworked pools and a UI info menu. This
hook is the **only** rules difference between the three modes; everything else is bans/UI.

## 5.2 Attrition

- **Lives: 4** (lobby default; server `startingLives: 4`).
- **Blind schedule**: ante < `pvp_start_round` (default **2**) → normal Small/Big/Boss; ante
  >= `pvp_start_round` → if `normal_bosses` false (default) boss = `bl_mp_nemesis` (Small/Big
  stay normal); if `normal_bosses` true keep a real boss but flag
  `pvp_blind_choices.Boss = true`. So Attrition = solo through antes, **PvP only on boss from
  ante 2**.
- **Banned jokers**: `j_mr_bones, j_luchador, j_matador, j_chicot`. **Banned vouchers**:
  `v_hieroglyph, v_petroglyph, v_directors_cut, v_retcon`. **Banned tags**: `tag_boss`.
  **Banned blinds**: `bl_wall, bl_final_vessel`.

## 5.3 Showdown

- **Lives: 4** (mod info menu) **but server says 2** — conflict; lobby `starting_lives` (4)
  overrides at runtime; treat lives as a lobby option (default 4), server's 2 is a fallback.
- **Blind schedule**: once ante >= `showdown_starting_antes` (default **3**), **ALL three
  blinds** become `bl_mp_nemesis`; before that, normal blinds. So Showdown = solo antes 1–2,
  then **every blind is PvP**. Server mirror uses legacy key `bl_pvp`; 0.4.0 client key
  `bl_mp_nemesis` wins. Same bans as Attrition.

## 5.4 Survival

- **Lives: 1.** **No PvP blind ever** (`get_blinds_by_ante` always `nil,nil,nil`) — pure
  same-seed solo race, "go as far as you can on 1 life."
- **Win/loss by furthest blind reached, not score-vs-score.** With `death_on_round_loss`
  (default true) failing a round costs the life; at `lives === 0`: both at 0 → compare
  `furthestBlind` (equal = **both win** / draw, else lower loses); one at 0 → if dying
  player's `furthestBlind < enemy.furthestBlind` they lose, else nothing yet (enemy continues).
- **Banned jokers**: the MP nemesis-interaction jokers (`conjoined_joker, defensive_joker,
  lets_go_gambling, magnet_sandbox, pacifist, penny_pincher, pizza, skip_off, speedrun,
  taxes`) — everything reading opponent state. **Banned consumable**: `c_mp_asteroid`.

## 5.5 The Nemesis blind

```lua
SMODS.Blind({ key = "nemesis",   -- full key "bl_mp_nemesis"
  dollars = 5, mult = 1,         -- NOT a chip target; 1 because 0 crashes Jen's Almanac
  boss = { min = 1, max = 10 }, in_pool = function(self) return false end })
function MP.is_pvp_boss()
  return G.GAME.blind.config.blind.key == "bl_mp_nemesis" or G.GAME.blind.pvp
end
```
- No fixed chip target — the real target is **the opponent's score** (a race), resolved by the
  server. Reward on winning the duel is **$5**.

## 5.6 PvP duel lifecycle (ready timing, score race, lives)

Client → server: `readyBlind` (on Ready, stores `next_blind_context`); `playHand(score,
handsLeft)` every hand on a PvP boss (also sends `spentLastShop`).

Server (`actionHandlers.ts`):
- `readyBlindAction`: when **both** ready → reset both scores to 0, both `handsLeft = 4`,
  compute `firstPlayer` from `firstReady`, broadcast `{action:"startBlind", firstPlayer}`.
  **First-ready grants Speedrun**; the second player also gets it if they ready **within 30s**
  (`firstReadyAt`, 30000 ms).
- Client `action_start_blind`: resets PvP timers, sets `pvp_reached_first`, starts the
  countdown (`pvp_countdown_seconds`, default **3**), then `begin_pvp_blind → select_blind`.
- `playHandAction`: records score + handsLeft, relays as `enemyInfo`. **Round decided when** a
  player is out of hands (`handsLeft < 1`) **and behind**, or both are out of hands. Then:
  tie (`host.score == guest.score`) → **no life lost**, both `endPvP`; else
  `roundLoser.loseLife()`. If anyone hits `lives <= 0` → `winGame`/`loseGame`; else both
  `endPvP` (loser gets `lost: true`).
- **PvP timer loss** (`failPvPTimerAction`): the timed-out player `loseLife()`; same
  resolution; `endPvP` carries `pvpTimerLost: true`.

## 5.7 The exact opponent ("enemy") state — what jokers read

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
Plus `MP.GAME` PvP scalars: `lives` (mine), `pincher_index` (starts **-3**, ++ each
end-of-round so it indexes the correct historical shop-spend entry), `pincher_unlock` (false
until first PvP reached), `score`, `highest_score`, `pvp_reached`, `pvp_reached_first`,
`nemesis_timer_started`, `comeback_bonus(_given)`.

Updated by `action_enemy_info` (sets `enemy.hands/skips/lives`, eases displayed score,
plays a sound when lives drop / skips increase); `action_spent_last_shop` appends to
`enemy.spent_in_shop`; `enemy.sells_per_ante` from the sell relay; `enemy.location` by
`enemyLocation`.

## 5.8 MP jokers and the exact nemesis state each reads

| Joker | Reads | Exact effect |
|---|---|---|
| **Defensive Joker** | `enemy.lives`, `my.lives` | `t_chips = max((enemy.lives - my.lives) * chips, 0)`, `chips = 75 if stake>=6 else 125`. Adds `chip_mod` on `joker_main`. |
| **Conjoined Joker** | `enemy.hands` | `x_mult = clamp(1 + enemy.hands * 0.5, 1, 3)`. Applies `x_mult` **only when `is_pvp_boss()`** and not a phantom. |
| **Penny Pincher** | `enemy.spent_in_shop[pincher_index]` | `calc_dollar_bonus = floor(spent / 3)`. In-pool only when `pincher_unlock`. |
| **Taxes** | `enemy.sells_per_ante` | On `setting_blind` of `bl_mp_nemesis`: `mult += sells_this_ante * 4` (accumulates earlier antes' sells if PvP not yet reached). Gives `mult` on `joker_main`. |
| **Skip Off** | `G.GAME.skips`, `enemy.skips` | `skip_diff = max(my.skips - enemy.skips, 0)`; grants `skip_diff` extra **hands and discards** on `setting_blind` ("unlocked hands"). |
| **Let's Go Gambling** | (nemesis relay) | gives opponent `ease_dollars(nemesis_dollars or 5)`. |

Jokers reacting to losing a PvP round read the calculate context, not enemy state directly:
`context.mp_pvp_loss` + `context.mp_hands_left` is fired by `action_end_pvp` **only when**
`lost and pvpTimerLost` and `hands_left > 0`. Ice Cream / Seltzer consume `mp_hands_left` as
the number of hands to decrement — only `if MP.is_layer_active("pvp_timer")`.

## 5.9 Lobby/config knobs driving PvP

`starting_lives` (4), `pvp_start_round` (2), `showdown_starting_antes` (3),
`pvp_countdown_seconds` (3), `timer_base_seconds` (150), `timer_increment_seconds` (60),
`normal_bosses`, `different_seeds` (false), `death_on_round_loss` (true), `gold_on_life_loss`
(true), `no_gold_on_round_loss` (false), `multiplayer_jokers` (true). Life-loss → Comeback
Gold bumps `comeback_bonus` when `gold_on_life_loss` and a life was actually lost.

## 5.10 OpponentView shape (joker-visible contract)

```java
record OpponentView(
  Score score, Score highestScore,
  int handsLeft,                 // enemy.hands
  int skips,                     // enemy.skips
  int lives,                     // enemy.lives
  int sells,                     // enemy.sells (total)
  Map<Integer,Integer> sellsPerAnte,    // enemy.sells_per_ante[ante]
  List<Integer> spentInShop,            // enemy.spent_in_shop[] (per visit, appended)
  String location
) {}
```
Plus self-side PvP scalars on `RunState`: `pincherIndex` (init **-3**, ++ each end-of-round),
`pincherUnlock`, `lives`, `handsLeft`, `skips`, `spentInShopHistory`, `sellsPerAnte`,
`pvpReached`, `pvpReachedFirst`, `furthestBlind`, `comebackBonus`. Carried in
`EvaluationContext`; refreshed/pushed **after every action**.

## 5.11 Joker formulas to encode (target)

- **Defensive Joker**: `chipMod = max((opp.lives - self.lives) * (stake>=6?75:125), 0)`.
- **Conjoined**: `xMult = clamp(1 + opp.handsLeft*0.5, 1, 3)`, only when `isPvpBoss()`.
- **Penny Pincher**: `dollarBonus = floor(opp.spentInShop[pincherIndex] / 3)`; in-pool only
  when `pincherUnlock`.
- **Taxes**: on entering `bl_mp_nemesis`, `mult += sellsThisAnte*4` (sum earlier antes if PvP
  not yet reached); apply on main.
- **Skip Off**: `skipDiff = max(self.skips - opp.skips, 0)`; grant `skipDiff` extra hands and
  discards for the upcoming blind (feeds `handsLeft`).
- **mp_pvp_loss context**: on a PvP **timer** loss with `handsLeft>0`, fire `{mpPvpLoss=true,
  mpHandsLeft=handsLeft}` (Ice Cream/Seltzer decay).

## 5.12 Server key compatibility & deltas

Internally use one PvP-blind concept; map `bl_mp_nemesis <-> bl_pvp` for the legacy server.
0.3.3→0.4.0: **Comeback Gold on any life loss** ($4, or $2 high-stake) — must award on every
life loss, not only Nemesis. **Speedrun** out of rotation in Standard Ranked (the 30s
second-player grant is unchanged server-side). The reference server is still 0.3.x-shaped
(`bl_pvp`, hard-codes Showdown lives 2, no `bl_mp_nemesis` mapping) — the 0.4.0 client wins.

---

# 6. Networking protocol

The transport & coordination plane: wire protocol, auth/identity, lobby/matchmaking,
reconnect/resync, anti-cheat boundary.

**Status:** ours = WebSocket + JSON `{type,seq}` envelope with accept/reject + `view` + `replay`;
intents only (no client score), HS256 JWT auth, server-authoritative ruleset, server-computed
PvP. Missing: keepalive, reconnect/resync, multi-mode, ready-flow, cross-player MP-joker
coupling, matchmaking queue. (HANDOFF.)

## 6.1 BMP transport & wire protocol

BMP is **raw TCP, newline-delimited JSON, bidirectional, fire-and-forget**. Client side is a
LÖVE thread (`tcp-nodelay=true`, `settimeout(10)` connect then `settimeout(0)` loop, messages
`msg .. "\n"`). Server is `node:net` `createServer` on **port 8788**, `setNoDelay()`, OS
`setKeepAlive(true, 10000)`. **Framing**: server buffers partial reads, splits on `\n`, keeps
the trailing partial; each line `JSON.parse`d into `{action, ...args}` dispatched through a
giant `switch`. **Message shape**: a flat JSON object with a string `action` discriminator and
ad-hoc sibling fields — **no envelope, no sequence number, no request/response correlation, no
version on the frame.** A second `serializeAction` (`key:value,...` comma form) is used only
for the admin channel.

**Action vocabulary** (from `actions.ts` + `HANDLERS`):

*Server→Client*: `connected`, `version` (request), `error`, `joinedLobby`, `rejoinedLobby`,
`enemyDisconnected`, `enemyReconnected`, `lobbyInfo`, `stopGame`, `startGame` (`{deck, stake?,
seed?}`), `startBlind` (`{firstPlayer}`), `winGame`, `loseGame`, `gameInfo` (deprecated),
`playerInfo` (`{lives}`), `enemyInfo` (`{score, handsLeft, skips, lives}`), `endPvP` (`{lost,
pvpTimerLost?}`), `lobbyOptions`, `enemyLocation`, `sendPhantom`, `removePhantom`, `speedrun`,
`asteroid`, `letsGoGamblingNemesis`, `eatPizza`, `soldJoker`, `spentLastShop`, `magnet`,
`magnetResponse`, `getEndGameJokers`, `receiveEndGameJokers`, `getNemesisDeck`,
`receiveNemesisDeck`, `endGameStatsRequested`, `nemesisEndGameStats`, `startAnteTimer`,
`pauseAnteTimer`, `jimboAppear/Talk/Move/Remove` (admin mascot), `moddedAction`, `keepAlive`,
`keepAliveAck`, `tcg_compatible`/`tcgStartGame`/`tcgStartTurn`/`tcgPlayerStatus`,
`handyMPExtensionLobbyEnabled`.

*Client→Server*: `username` (`{username, modHash}`), `createLobby` (`{gameMode}`), `joinLobby`
(`{code}`), `rejoinLobby` (`{code, reconnectToken}`), `leaveLobby`, `readyLobby`/`unreadyLobby`,
`lobbyInfo`, `startGame`, `readyBlind`/`unreadyBlind`, `playHand` (`{score, handsLeft,
hasSpeedrun}`), `stopGame`, `lobbyOptions` (flat k/v), `failRound`, `setAnte` (`{ante}`),
`setFurthestBlind`, `newRound`, `skip` (`{skips}`), `version` (`{version}`), `setLocation`, the
MP-joker echoes (`sendPhantom`, `removePhantom`, `asteroid`, `letsGoGamblingNemesis`,
`eatPizza`, `soldJoker`, `spentLastShop`, `magnet`, `magnetResponse`), end-game transfers
(`getEndGameJokers`/`receiveEndGameJokers`, `getNemesisDeck`/`receiveNemesisDeck`,
`endGameStatsRequested`/`nemesisEndGameStats`), timer ops (`startAnteTimer`, `pauseAnteTimer`,
`failTimer`, `failPvPTimer`), `syncClient` (`{isCached}`), `moddedAction` (`{modId, modAction,
target?, ...}`), TCG, Handy extension toggles.

**Score encoding on the wire**: a string parsed by `InsaneInt` — leading `e`-prefixes for
tetration depth (`startingECount`), then `coefficient[e exponent]`, optional `#count` for very
deep stacks. The client formats `to_big(score)` and strips commas/decimals. Exists purely
because Balatro scores overflow f64.

## 6.2 Keepalive / liveness (BMP)

Symmetric application-level heartbeat (independent of TCP keepalive):
- **Server**: `KEEP_ALIVE_INITIAL_TIMEOUT=15000ms`. After 15s idle sends `keepAlive`, then a
  `retryTimer` every `KEEP_ALIVE_RETRY_TIMEOUT=5000ms`; after `KEEP_ALIVE_RETRY_COUNT=4`
  unanswered it `socket.end()`s. Any inbound data refreshes.
- **Client**: `keepAliveInitialTimeout=20s`, `keepAliveRetryTimeout=5s`, `keepAliveRetryCount=4`.
  The net thread answers a server `keepAlive` **directly on the socket** (no UI round-trip):
  on inbound containing `"keepAlive"` and not `Ack`, it immediately sends `{"action":
  "keepAliveAck"}`. Client→server keepAlive is acked server-side.

## 6.3 Lobby / matchmaking (BMP)

- **Lobby codes**: 5 uppercase letters A–Z, collision-checked, in a global `Lobbies` map.
- A lobby is strictly **2-player host/guest**; `createLobby` seats creator as host, `joinLobby`
  seats guest (rejecting if full). **No real matchmaking / no ranked queue** — code-based only.
  "Ranked" branding = rulesets/balance, not MMR.
- **Ready model**: lobby-ready (`readyLobby`/`unreadyLobby` → `isReadyLobby`) and per-blind
  ready (`readyBlind`/`unreadyBlind` → `isReady`). `startGame` is **host-only** and does *not*
  currently require guest ready (commented out).
- **Game modes**: `attrition | showdown | survival` via tiny `GameMode.ts` (`startingLives`
  4/2/1 + `getBlindFromAnte`). Survival/attrition resolution in `failRound`/`setFurthestBlind`.
- **Lobby options**: free-form string→(string|bool) bag; only the guest is echoed
  `lobbyOptions`. **The ruleset/gamemode/modifier_layers live entirely client-side** — the
  server stores strings and relays them but does not enforce them.
- **Seed**: host's `startGame` generates an 8-char A–Z/1–9 seed (unless `different_seeds`); the
  same string goes to both clients; **each client runs its own local Balatro with that seed**.

## 6.4 Auth & identity (BMP)

- **There is none, cryptographically.** Identity is a `username` string the client asserts
  (suffixed with a `~<blind_col>` cosmetic), plus `modHash` — a 4-digit decimal hash
  (`h = (h*31+char) % 10000`) of a sorted mod list + `encryptID, unlocked, preview,
  serversideConnectionID`. Purely a **mod-parity / version-mismatch warning**, not security.
- `Client.id` is a server-issued `uuidv4`; `reconnectToken` is `randomBytes(16).hex`.
- **Version check**: client sends `version`; server compares to a hardcoded
  `serverVersion = "0.3.2-MULTIPLAYER"` (**stale — server lags the 0.4.0 mod**) and emits only
  a soft `error` warning if the client is older. Non-blocking.
- **Admin channel**: a *separate* TCP server on `127.0.0.1:8789`, Ed25519 signature-verified
  (`crypto.verify` against `admin_public.pem`) — the one place BMP uses real crypto. Commands:
  `message`, `jimbo*` (mascot), `listLobbies`.

## 6.5 Reconnect / resync (BMP) — the headline 0.4.0 addition

- On TCP `end`/`error` the server calls `disconnect(client)`. `Lobby.disconnect`: if no game in
  progress or no opponent → plain `leave`; otherwise **reserves the slot for
  `RECONNECT_GRACE_PERIOD = 60000ms`**, snapshotting a `SavedGameState` (lives, score,
  handsLeft, ante, skips, furthestBlind, ready flags, livesBlocker, location, username,
  modHash), clears the live slot, notifies the opponent `enemyDisconnected {timeout: 60}`.
- `rejoinLobby {code, reconnectToken}` → `Lobby.rejoin`: validates token vs the reserved slot,
  restores the snapshot onto the *new* `Client`, reseats it, sends `rejoinedLobby` with a
  **fresh** reconnectToken, notifies the opponent `enemyReconnected`.
- **Client mirror**: persists `reconnectToken` + `lastLobbyCode`; on `connected` auto-sends
  `rejoinLobby`. The net thread retries TCP connect 3× with backoff `{2,4,8}s`. UI shows a 60s
  countdown.
- **There is NO mid-game state resync of the actual run** — the snapshot is only coordination
  scalars; the Balatro run lives on the client. The "deck/jokers transfer" actions exist only
  for the **end-game results screen** (nemesis's final board), not resync.

## 6.6 Anti-cheat boundary (BMP)

**There is effectively none, by architecture.** `playHandAction` stores the client's
self-reported score verbatim and relays it; PvP win/loss is decided by comparing two
self-reported numbers. Every progression value is client-asserted (`setAnte`,
`setFurthestBlind`, `skip`, `spentLastShop`, `failRound`, `failTimer`, `soldJoker`). The server
is a **dumb relay + tiny win/lose arbiter**. `moddedAction` relays arbitrary unvalidated
payloads. Since the client runs Steamodded, a self-reported score is unfixable — BMP is
explicitly a **trust-the-peer** design. **Our project closes this by re-simulating
authoritatively.**

## 6.7 Target wire protocol (native)

Keep the `{type, seq}` request/response envelope:
- **Client→Server frame**: `{ "type": <intentType>, "seq": <u64>, ...args }`. `seq` is a
  monotonic per-connection counter for correlation + idempotency.
- **Server→Client frames**:
  - **Reply** (correlated): `WsResponse {type:"update"|"error", seq, accepted, rejection, view,
    replay}` — `view` is a versioned `ClientView`, `replay` the scoring-event log.
  - **Push** (uncorrelated, `seq: 0`/absent): `opponentUpdate`, `matchStart`, `pvpResult`,
    `matchResult`, `lifeLost`, `enemyDisconnected`, `enemyReconnected`, `phantom`, `ping`.
- **Versioning on the frame**: add `protocolVersion` to the **auth** message and a server
  `serverInfo {protocolVersion, engineVersion, contentHash}` on connect. Hard-reject
  incompatible `protocolVersion`; for ranked, also require matching `contentHash` (hash of the
  active ruleset + joker pool — our *enforced* analog of BMP's `modHash`).
- Native transport stays **WebSocket**; an optional **BMP-compat TCP+`\n` adapter** can serve
  the stock 0.4.0 Lua client but must **drop** client-supplied scores/progression and recompute
  — and since the stock client never sends the cards played, the adapter is **"relay-faithful"
  (insecure, casual) only**; ranked requires our thin client.

## 6.8 Target liveness, lobby, ready-flow

- **Liveness (WS native)**: Jetty ping/pong; server pings after **15s** idle, retries up to
  **4×** at **5s**, then close — mirroring BMP `15s/5s/×4`. On close, **do not** purge the run
  (hand off to reconnect manager).
- **Lobby codes**: keep our unambiguous 5-char alphabet (`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`,
  avoids 0/O/1/I).
- **Ready model**: `readyLobby/unreadyLobby` + `readyBlind/unreadyBlind`. `startGame` host-only
  but **require guest lobby-ready** (BMP has this commented out — we should enforce it).
  Per-blind: both ready → `startBlind {firstPlayer}`; grant a "speedrun" flag to first-ready
  and to the second if within **30s** (`firstReadyAt`/30000ms).
- **Game modes** authoritative: `startingLives`, `blindScheduleForAnte(ante, ruleset)`.
  Attrition (lives 4, boss PvP from `pvp_start_round`), Showdown (lives 2, all slots PvP after
  `showdown_starting_antes`), Survival (lives 1, race to furthest blind).
- **Matchmaking (our differentiator)**: a `RankedQueue` — `enqueue(playerId, preference)` →
  MMR-bucketed pairing → synthesize a server-side `Lobby` (no code) with a server-chosen
  ruleset; persist results to an MMR store. (Future `36-matchmaking.md`.)

## 6.9 Target reconnect / resync (full-state, our advantage)

Because the run is server-authoritative we resync the *actual game*, not just scalars:
- **On disconnect**: not in a live match / no opponent → plain leave; else **do not destroy the
  `Run`** — move the `Seat` to `Disconnected`, keep its `Run`/`Match` alive, start a **grace
  timer** (default **60s**, from `Ruleset`), push opponent `enemyDisconnected {timeout}`.
  Issue/retain a `reconnectToken` bound to the seat.
- **On reconnect**: client auths (JWT — TTL ≥ grace; our 12h JWT is fine), then
  `rejoinLobby {code, reconnectToken}`. Server validates token ⇄ reserved seat, rebinds the new
  `sessionId`/`WsContext` to the existing `Seat`+`Run`, replays a **full resync**:
  `rejoinedLobby` + current authoritative `ClientView` + latest `opponentUpdate`; pushes
  opponent `enemyReconnected`. No client-side run state needed.
- **Grace expiry**: opponent wins (attrition/showdown) or survival rule applies; emit
  `matchResult`.
- **Match event log** (also powers 0.4.0 ghost/practice): every authoritative transition
  appends to a per-match log (with checksum); resync can fast-forward from it; log export = the
  `.json` ghost-replay format.

## 6.10 Target auth & cross-player coupling

- Keep **JWT** session tokens; production `/login` validates a **Steam encrypted app ticket**
  → `sub = steamId`. **Identity ≠ reconnect token**: JWT proves *who you are*; the per-seat
  `reconnectToken` proves *you owned this seat*; both required to rejoin a ranked match.
- **Content/version handshake** on auth: exchange `protocolVersion` + `contentHash`. Ranked:
  hard-reject mismatch; casual: soft-warn. `contentHash` = hash over (engine protocol version,
  active ruleset id+params, joker-pool keys+configs).
- **Cross-player (MP-joker) coupling — as validated intents**: reproduce BMP's MP-joker
  *effects* server-side and validated. Each BMP relay becomes an intent validated against the
  sender's authoritative state, then a **derived push** to the opponent:

| BMP relay | Our intent → authority | Push to nemesis |
|---|---|---|
| `sendPhantom {key}` / `removePhantom` | server confirms the joker is in sender's board | `phantom {add/remove, key}` |
| `magnet`/`magnetResponse` | server selects highest-sell joker from sender's *real* board | `phantom`-style add |
| `eatPizza {whole}` | derived from sender's discard action | `eatPizza {discards}` |
| `asteroid` / `letsGoGamblingNemesis` / `soldJoker` / `spentLastShop {amount}` | derived from authoritative shop/sell/score events | typed push |
| `skip {skips}` | `skipBlind` intent → apply tag, advance packs queue (skip-offset) | `enemyInfo` summary |

- **No `moddedAction`-style arbitrary trusted payloads.** Validate **every** cross-player
  intent against the sender's authoritative state; rate-limit intents; bound line length on the
  compat adapter.

## 6.11 Anti-cheat boundary (target, restated)

- **Server-only, never on any wire**: seed, `hashed_seed`, every `GameQueue` cursor/contents
  below what's revealed, unrevealed deck order, future shop/pack contents, boss before reveal.
- **Client may send only intents** referencing currently-visible handles (card indices in *its*
  hand, shop slot indices, joker indices). No scores, money, ante, or "I won".
- **Server emits**: authoritative `ClientView` (only what's legitimately visible) + scoring
  `replay` log + agreed-public opponent summary.
- **Ranked gate**: native thin client + enforced `contentHash` + JWT(steamId) + reconnect
  token. **Casual/compat**: BMP TCP adapter, explicitly flagged unverified.

---

# 7. Content / ruleset pipeline

How content gets authored, validated, governed for ranked, versioned, and made cheat-proof —
the pipeline, not the per-joker effects.

**Status:** ours has a data-driven authoring pipeline (`JokerDef`/`Rule`/`EffectTemplate`/
`Value`, `DataJoker` server-side interpretation, `BuilderSchema` derived from engine enums,
`CustomJokerStore`/`RulesetStore` with safety validation, two-tier curated/custom rulesets).
Missing: layer composition, full ban categories, rework overlay, forced gamemode/lobby locks,
provenance/credits, content hashing/versioning, per-tier numeric bounds. (HANDOFF.)

## 7.1 The 0.4.0 restructure: rulesets are layered compositions

0.3.x ruleset = a flat table of `banned_*`/`reworked_*` arrays. **0.4.0 splits into two object
types — `MP.Layer` (reusable fragment) and `MP.Ruleset` (named composition of layers + own
fields).**

**Ruleset base object** (`SMODS.GameObject:extend`, `class_prefix = "ruleset"`). Required params:
```
key, multiplayer_content,
banned_jokers, banned_consumables, banned_vouchers,
banned_enhancements, banned_tags, banned_blinds,
reworked_jokers, reworked_consumables, reworked_vouchers,
reworked_enhancements, reworked_tags, reworked_blinds
```

**Layers** (`MP.Layer(name, definition)`): same field vocabulary plus hooks. On-disk:
`standard, classic, ranked, smallworld, glass_cannon, glass_variants, speedlatro_timer,
pvp_timer, pressure_timer, no_anim_timer, experimental, sandbox`, plus mutator stubs
(`ban_mutators, economy_mutators, esoteric_mutators, shop_mutators, mutator_stubs`).

**Composition**: a ruleset names its layers; `resolve_layers` merges them *before* SMODS
validates `required_params`. E.g. Standard Ranked `layers = {"standard","ranked"},
forced_gamemode = "gamemode_mp_attrition"`; Legacy Ranked `layers = {"classic","ranked"}`;
`badlatro` is a *flat* ruleset (no layers, explicit lists). Both styles coexist.

**Merge semantics**: array fields (the 14 `_LAYER_ARRAY_FIELDS`) are **concatenated** across
layers + the ruleset's own entries; scalar fields are **last-layer-wins, but the ruleset's own
value always beats any layer** (`ruleset_owned[k]`). Resolved layer names stashed as `_layers`
(set) + `_layer_order` (ordered).

**Modifiers** are layers chosen at *runtime* (host overlay / practice), held in `MP.MODIFIERS`,
**not** materialized onto the ruleset — queried at read sites alongside the ruleset's layers,
reset on lobby-leave. `default_modifiers` pre-checks some in the overlay.

**Active-context resolution**: `MP.current_ruleset()` returns a metatable proxy whose
`__index` merges (ruleset + active modifiers) per field — arrays concatenated, scalars
modifier-last then ruleset. `active_layer_chain` produces a **deduped ordered** list (ruleset's
`_layer_order`, its self-name, then modifiers) — dedup matters because not all hooks are
idempotent (smallworld's 75% cull would re-cull survivors).

## 7.2 Bans and reworks — two distinct mechanisms

**Bans** (`MP.ApplyBans`): iterate the six banned tables off the resolved ruleset proxy + the
active gamemode + the deck's `BANNED_*`, writing `G.GAME.banned_keys[v] = true`. Plus a
`banned_silent` table (keys hidden from the player). Dynamic bans run as layer hooks:
`smallworld.lua:on_apply_bans` seed-shuffles each pool and bans `floor(0.5 + 0.75*n)` of every
category via `pseudoshuffle(v, pseudoseed(k.."_mp_smallworld"))` — a **deterministic,
seed-driven content cull** so both clients agree.

**Reworks** (`MP.ReworkCenter`/`MP.LoadReworks`): a rework does not replace a center; it
**stamps layer-prefixed shadow keys** onto the vanilla center: for layer `L`, every property
`k` is written to `center["mp_"..L.."_"..k]`, and the untouched original to
`center["mp_vanilla_"..k]` (fallback sentinel `"NULL"`). `MP.LoadReworks(ruleset)` then, per
the `active_layer_chain`, resets to vanilla and re-applies layer shadows in order. This is how
the *same* `j_to_do_list` is "vanilla" in one ruleset and "reworked" in another **without a
second center** — a deterministic overlay keyed by active layer. Rework injection auto-sets
`config.mp_balanced = true`, driving the **Balanced sticker** tooltip.

## 7.3 New-content gating: default-deny on `*_mp_*` keys

`MP.should_exclude_from_pool`: any center whose key matches `^%a+_mp_` is **excluded from every
pool by default** unless it has an `mp_include` function returning true. Registration grafts
auto-attach an `mp_include` for any object listed in some layer's `reworked_*` array (via
reverse indices `MP._JOKER_LAYERS` etc.), and warn if an `_mp_` object has neither `mp_include`
nor reworked-list membership. So MP content is **opt-in per layer/ruleset, fail-closed**.
Per-object `mp_include` pattern: `return MP.LOBBY.code and MP.LOBBY.config.multiplayer_jokers`.

## 7.4 JokerDef authoring

A joker = `SMODS.Joker({...})` + a sibling `SMODS.Atlas({...})`. Fields:
- Identity/shop: `key, atlas, rarity` (1=Common…4=Legendary), `cost, unlocked, discovered`.
- Compat flags: `blueprint_compat, eternal_compat, perishable_compat`.
- State: `config = { extra = {...}, t_chips = 0 }`.
- Effect: `calculate(self, card, context)` returning `{chip_mod, mult_mod, Xmult, dollars,
  message, repetitions}` keyed off `context` flags (`context.cardarea == G.jokers`,
  `context.joker_main`, …) — the SMODS calculate-context grammar.
- Display: `loc_vars(self, info_queue, card)` → `{vars}`; `add_nemesis_info` pushes tooltips.
- Lifecycle: `update(self, card, dt)` for per-frame recompute (Defensive Joker recomputes
  `t_chips` from `enemy.lives - my.lives` each frame).
- Pool gating: `in_pool(self)`, `mp_include(self)`.
- **Governance metadata**: `mp_credits = { idea=, art=, code= }` — every MP joker has one.

Display defs are *separate* (JokerDisplay `display_definitions.lua`).

## 7.5 Sprites, load order, versioning, cheat-proofing (BMP)

- **Sprites**: `SMODS.Atlas({key, path = "j_<name>.png", px = 71, py = 95})` — one PNG atlas
  per joker, **71×95 cell**, referenced by the joker's `atlas`. Art ships inside the mod.
  Stickers are also atlases.
- **Load order** (strict, deterministic): `layers → rulesets → objects/editions,enhancements,
  seals,stickers,blinds,decks → objects/jokers (+sandbox, standard, experimental) → stakes →
  tags → consumables (+sandbox) → boosters → challenges`. Layers/rulesets load **first** so the
  `MP._*_LAYERS` reverse indices are populated and auto-gating fires when jokers `:register()`.
- **Versioning**: `Multiplayer.json` `version: "0.4.0"`, hard `dependencies` (`Steamodded
  >=1.0.0~BETA-1221a, Lovely >=0.8, Balatro >=1.0.1o`) and `conflicts` (`Cryptid <<0.5.4,
  Talisman <=2.0.2, JokerDisplay <<1.9.6, …`). The `ranked` layer self-disables if SMODS/Lovely
  are too old. **No per-ruleset semantic version** — ruleset identity *is* its content; balance
  changes ship as a whole mod version bump in `CHANGELOG.md`.
- **Cheat-proofing in BMP**: thin relay server + each client runs its own authoritative Balatro;
  **determinism, not server re-simulation, is the integrity mechanism** (hence the obsessive
  `pseudoseed` use, the To-Do-List queue-parity fix, `active_layer_chain` dedup). **Our server
  closes this gap: we re-simulate authoritatively.**

## 7.6 0.3.3 → 0.4.0 content deltas

To Do List reworked: pays **$5** (was $4), target from *all* hands; Golden Ticket payout
reverted to **$4**; Speedrun out of rotation; Ouija/Ectoplasm now **$4** (fix); Justice back in
rotation (note: the `standard` layer still bans `c_justice` while the ruleset re-enables — a
tension to confirm); Gold Card enhancement **$3 → $4**; Comeback Gold on *any* life loss ($4 /
$2 high-stake unchanged); Balanced sticker now shows a tooltip of what changed; Legacy Ranked
Hanging Chad retriggers first **2** cards. New 0.4.0 systems: Match Replays / ghost practice
(`replays/`), the `gamemodes/` split (`_gamemodes, attrition, showdown, survival`) that
`forced_gamemode` references.

## 7.7 Target composition model (as data)

```
record ContentLayer(
    String name, boolean multiplayerContent,
    BanSet bans,              // jokers, consumables, vouchers, enhancements, tags, blinds, silent
    List<Rework> reworks,
    String forcedGamemode,    // nullable
    LobbyLock lobbyLock,      // forced lobby options (timer, the_order, preview_disabled)
    List<String> dynamicBanHooks)  // named, deterministic — e.g. "smallworld_cull"

record Ruleset(
    String name, int formatVersion,
    List<String> layers,          // ordered; resolved like _layers.lua
    List<String> defaultModifiers,
    StartParams start, Scaling scaling,   // startingMoney/hands/discards/handSize; anteScaling/winAnte/blindBaseAmounts
    BanSet bans, List<Rework> reworks,
    List<String> jokerPool,       // additive allowlist for custom content
    String forcedGamemode, LobbyLock lobbyLock,
    RankedTier rankedTier)        // CURATED_RANKED | CURATED_CASUAL | EXPERIMENTAL | CUSTOM
```
`RulesetResolver` merges layers in order then the ruleset's own fields — **arrays concatenate,
scalars last-layer-wins but ruleset-own beats layer** — plus `active_layer_chain` dedup/order.
Keep the `BUILTIN_KEYS` snapshot so custom content is opt-in. Modifiers are a runtime list
merged at read time, never baked into the persisted ruleset.

## 7.8 Target bans, reworks, locks, curation, hashing

- **`BanSet`**: six explicit key sets + `silent`; `applyBans` writes a per-run `bannedKeys`
  consulted by **every pool draw** (shop, packs, tags, vouchers). `dynamicBanHooks` are named,
  registered Java functions (default-deny: only registry hooks run), seeded from
  `RandomStreams` so a smallworld 75% cull is reproducible. No author-supplied code runs.
- **`Rework`** = `(targetKey, Map<String,Object> overrides, List<RuleDelta> ruleDeltas, boolean
  balanced)`; a `ReworkResolver` applies the deduped, ordered active layer chain onto a base
  joker (data parity with the `mp_<layer>_*` shadow-prop system). Built-in jokers need a small
  override-able `config`-like param map so reworks can retune (e.g. To Do List → $5) without
  forking Java. `balanced=true` → client shows a "changed in this ruleset" tooltip.
- **Forced gamemode + lobby locks**: `forcedGamemode` + `LobbyLock` (pinned `timerBaseSeconds,
  timerForgiveness, theOrder, previewDisabled`), applied at lobby-config time and re-validated
  server-side so a client can't unlock them.
- **Curation**: `RankedTier` on every ruleset — only `CURATED_RANKED` rulesets selectable in
  the ranked queue; `CUSTOM`/`EXPERIMENTAL` casual-only. Content provenance: `author, credits
  (idea/art/code), createdAt`, content hash. A custom joker enters a ranked pool only by
  **promotion** (admin moves its def into a version-pinned `ranked-content/` set, freezing its
  hash). A `CurationService` exposes list/propose/promote/diff.
- **Versioning & reproducibility**: `formatVersion` (int, bump on any balance change); **content
  hash** = SHA-256 over the *resolved* ruleset (layers merged, reworks applied, pool sorted,
  defs canonicalized), stored on the match record and echoed to both clients — two players share
  the same content surface iff hashes match. A match persists `{rulesetName, formatVersion,
  contentHash, seed, activeModifiers}` (full reproduce/replay).
- **Validation hardening**: per-tier numeric bounds on `EffectTemplate`/`Value` (ranked caps
  `XMULT`/`CHIPS`/`DOLLARS` so a promoted joker can't carry 1e9); cross-reference validation for
  *all* ban categories (unknown key → reject, BMP only warns); dead-rule lint; determinism lint
  for `dynamicBanHook`. Keep current safety checks (key regex `^j_[a-z0-9_]{3,48}$`,
  `MAX_RULES=24`, `MAX_MUTATIONS=16`, PNG magic-byte, `MAX_SPRITE_BYTES = 2 MiB`,
  traversal-safe asset resolution; ruleset name regex `^[A-Za-z0-9][A-Za-z0-9 _-]{1,31}$`,
  every `jokerPool` key must be a known joker). Sprites stay **metadata-only**, art served to
  client, never in scoring; add 71×95-multiple dimension validation and include the sprite hash
  in the content hash.

## 7.9 BMP-0.4.2-ranked overlay (the target ranked ruleset)

The active ranked overlay (`bmp-0.4.2-ranked`) diffed against base `vanilla`. **Removed (4),
Changed (3), Added (9):**

**Removed (4)** — banned jokers:
- **Matador** (`j_matador`) — boss-triggered economy snowballs uncontested in PvP.
- **Luchador** (`j_luchador`) — on-sell boss disable trivializes boss antes.
- **Chicot** (`j_chicot`) — disables boss blinds, removes the core PvP variable.
- **Mr. Bones** (`j_mr_bones`) — prevents loss, distorts the race.

**Changed (3)** — reworked:
- **Hanging Chad** (`j_hanging_chad`) — changed description + rules: retriggers first AND
  second played card.
- **Golden Ticket** (`j_golden_ticket`) — changed description + rarity + rules: Uncommon, $3
  per Gold card.
- **Seltzer** (`j_seltzer`) — changed description + rules: 8-hand acquire window.

**Added (9)** — nemesis MP-jokers (formulas in §5.8/§5.11):
- **Pizza** (`j_pizza`) — spends at PvP end for temporary discards on both sides.
- **Speedrun** (`j_speedrun`) — rewards reaching the PvP blind first.
- **Penny Pincher** (`j_penny_pincher`) — taxes the opponent's shop spend.
- **Skip-Off** (`j_skip_off`) — rewards skipping blinds.
- **Let's Go Gambling** (`j_lets_go_gambling`) — high-variance gamble payout.
- **Pacifist** (`j_pacifist`) — pays off outside PvP blinds.
- **Defensive Joker** (`j_defensive_joker`) — scales with the opponent's life deficit.
- **Conjoined Joker** (`j_conjoined`) — scales with the opponent's remaining hands.
- **Taxes** (`j_taxes`) — scales with the opponent's cards sold.

> Note: the per-doc grounding above cites Golden Ticket at **$4** (§1h/§3.11, the 0.4.0
> CHANGELOG reverting the 0.3.0 $3 nerf) while this 0.4.2 overlay specifies the rework as
> **Uncommon, $3 per Gold card**. The overlay is the engine's concrete ranked target;
> validate the exact payout against the live `bmp-0.4.2-ranked` resolved pool.
