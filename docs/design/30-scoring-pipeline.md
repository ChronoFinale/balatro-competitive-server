# 30 — The `evaluate_play` Scoring Pipeline (server-authoritative, ruleset-driven)

**Target parity:** Balatro Multiplayer (BMP) **0.4.0** running on Balatro **1.0.1o-FULL** with
SMODS as the calculation backbone. This is the definitive scoring spec: the exact ordered
phases of `evaluate_play`, how chips/mult/xmult/retriggers/editions/enhancements/seals/held
cards combine, and where joker hooks fire.

> Convention used throughout: **xmult** means *multiply current mult by amount*;
> **xchips** means *multiply current chips by amount*; **+mult/+chips** are additive.
> "Card order matters" everywhere — both played cards and the joker row are iterated
> left→right, and that interleaving of `+mult` vs `×mult` is score-determining.

---

## 1. How the REAL game / BMP 0.4.0 does it (grounded)

There are **two** scoring code paths that must agree:

1. **The authoritative play resolver** — `G.FUNCS.evaluate_play`. On disk this is the
   SMODS-overridden version at
   `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/game-dump/functions/state_events.lua:619`.
   This is what actually mutates `G.GAME.chips` when you hit Play Hand.
2. **BMP's score-preview simulator** — `FN.SIM` in
   `…/multiplayer-0.4.0/compatibility/Preview/EngineSimulate.lua` (+ `UtilsSimulate.lua`,
   `Jokers/_Vanilla.lua`). BMP re-implements the whole pipeline a second time to show the
   live score/timer preview without committing. It is the **single cleanest, most
   linear transcription of the ordering** and is the best model to mirror in Java because it
   already strips out animation/event-queue noise. When the two disagree, the SMODS
   `evaluate_play` wins (the preview is allowed to approximate, e.g. it uses
   probabilistic min/exact/max ranges).

### 1a. The macro phase order (authoritative)

From `evaluate_play` (`state_events.lua:619`) cross-checked against `FN.SIM.run`
(`EngineSimulate.lua:4-29`). The simulator's top-level order is the canonical summary:

```
FN.SIM.run() (EngineSimulate.lua:13-24):
  if not simulate_blind_debuffs():          -- boss blind may zero the hand (debuff_hand)
      simulate_joker_before_effects()        -- context.before
      add_base_chips_and_mult()              -- base from hand level
      simulate_blind_effects()               -- boss blind modify_hand
      simulate_scoring_cards()               -- each played scoring card, L→R
      simulate_held_cards()                  -- each held-in-hand card, L→R
      simulate_joker_global_effects()        -- jokers main + joker-on-joker + joker editions
      simulate_consumable_effects()          -- e.g. Observatory planet xmult
      simulate_deck_effects()                -- selected_back final_scoring_step
  else:
      simulate_all_jokers({debuffed_hand=true}) -- Matador only
  return chips * mult
```

The authoritative `evaluate_play` does the same in this concrete order:

1. **Identify hand** — `get_poker_hand_info(G.play.cards)` →
   `(text, disp_text, poker_hands, scoring_hand, non_loc_disp_text)`. Increment
   `hands[text].played / played_this_round / played_this_ante`; set
   `last_hand_played`. (`state_events.lua:620-627`)
2. **Assemble the final scoring set** (`state_events.lua:629-646`). For each played card:
   - `splashed = SMODS.always_scores(card) or Splash joker present`, else true if the card
     is in the detected `scoring_hand`.
   - Fire `context.modify_scoring_hand` (lets jokers/seals add/remove cards;
     `flags.add_to_hand` / `flags.remove_from_hand`).
   - Keep card iff `splashed and not unsplashed`. **Stone cards always score**
     (`always_scores`). The kept list **preserves played (left→right) order.**
3. **Boss-blind veto** — `G.GAME.blind:debuff_hand(...)`. If it returns true the hand is
   *Not Allowed*: `mult=0, chips=0`, fire `context.debuffed_hand`, skip everything else
   (`state_events.lua:837-860`). (BMP preview: `simulate_blind_debuffs`,
   `EngineSimulate.lua:385-421`, with special-cased The Tooth / The Arm / The Ox / Matador.)
4. **Base chips/mult** = the *played hand's* current level values:
   `mult = mod_mult(G.GAME.hands[text].mult)`, `hand_chips = mod_chips(G.GAME.hands[text].chips)`
   (`state_events.lua:667-668`, re-read at `695-696`). Preview equivalent
   `add_base_chips_and_mult` (`EngineSimulate.lua:221-224`).
5. **`context.before`** — `SMODS.calculate_context({…, before=true})`
   (`state_events.lua:689`). Jokers that act before scoring (e.g. setup, conditional
   priming) fire here. **This is before per-card scoring** and is a distinct hook from the
   main joker pass.
6. **Boss-blind `modify_hand`** — `mult, hand_chips, modded = G.GAME.blind:modify_hand(...)`
   (`state_events.lua:700`). Re-wrapped through `mod_mult/mod_chips`.
7. **`context.initial_scoring_step`** then **`SMODS.calculate_main_scoring` per card-area**
   (`state_events.lua:704-708`). The playing-card areas are `G.play` (scored) then `G.hand`
   (held) — see `SMODS.get_card_areas('playing_cards')` at
   `…/smods/src/utils.lua:2267-2276` which returns `{G.play, G.hand}` (and optionally
   `G.deck`/`G.discard` if a mod opts in — BMP does not).
8. **Joker main pass + joker-on-joker + joker editions** (`state_events.lua:713-794`).
9. **`context.final_scoring_step`** + deck `final_scoring_step` trigger
   (`state_events.lua:797-802`).
10. **Destruction pass** — `context.destroying_card` collects cards to destroy (Glass shatter,
    etc.), then `context.remove_playing_cards` (`state_events.lua:804-836`).
11. **Round score** = `SMODS.calculate_round_score()` = `current_scoring_calculation:func(chips, mult)`
    which for the default ruleset is **`floor(chips * mult)`**
    (`game-dump/SMODS/_/src/utils.lua:2815-2818`). Added to `G.GAME.chips`.
12. **`context.after`** (`state_events.lua:916`) — post-hand cleanup hooks.

### 1b. Per-card scoring (the micro level — exact field order)

Authoritative path: `SMODS.calculate_main_scoring` → `SMODS.score_card`
(`smods/src/utils.lua:2091-2118` and `2048-2089`). For each card in the area:

- Skip if `card.debuff` (debuffed cards score nothing but still trigger the blind's juice;
  `utils.lua:2102-2110`).
- **Repetitions are collected first**, then the card's effect is applied once per rep
  (`score_card` loops `reps`, calling `SMODS.calculate_repetitions` to expand the count).
  Red Seal and retrigger jokers (Hanging Chad, Sock and Buskin, Dusk, Hack, Seltzer, Mime)
  add reps; each rep re-runs the card's full base+enhancement+edition+seal plus the
  per-card joker `individual` pass.

The cleanest exact ordering is the preview's `simulate_card`
(`EngineSimulate.lua:441-500`) — for a **played** card (`cardarea == G.play`):

```
1. Chips:  Stone → ability.bonus + perma_bonus
           else  → base_chips + ability.bonus + perma_bonus    (Bonus card = +30 via bonus)
2. Mult:   Lucky → probabilistic +20 (1-in-5)   [pseudorandom('lucky_mult')]
           else  → ability.mult                  (Mult card = +4 via ability.mult)
3. XMult:  if ability.x_mult > 1 → x_mult(ability.x_mult)   (Glass = x2)
4. Dollars: Gold Seal → +$3 ; Lucky p_dollars → probabilistic +$20 (1-in-15)
5. Edition: Foil → +50 chips ; Holo → +10 mult ; Polychrome → x1.5 mult
```

For a **held** card (`cardarea == G.hand`, `EngineSimulate.lua:495-499`):

```
   ability.h_mult > 0   → +mult   (Steel is modelled as h_x_mult below)
   ability.h_x_mult > 0 → x_mult  (Steel Card = x1.5 mult while held)
```

> **Per-card retrigger structure** (`EngineSimulate.lua:427-438`): reps are computed
> *once* (Red seal +1, Echo deck +1, joker `repetition` context), then the card effect and
> the per-card joker `individual` pass run inside the rep loop. So a retrigger re-applies
> *both* the card's intrinsic chips/mult/edition/seal *and* any joker that reacts to that
> card (e.g. Hack retrigger also re-fires a "+mult per scored 2" type joker on that card).

The **field-application order inside a single effect table** (what order chips vs mult vs
xmult resolve when one source returns several) is fixed by `SMODS.calculation_keys`
(`game-dump/SMODS/_/src/utils.lua:1409-1431`):

```
pre_func →
chips, h_chips, chip_mod →
mult, h_mult, mult_mod →
x_chips/xchips/Xchip_mod →
x_mult/Xmult/xmult/x_mult_mod/Xmult_mod →
[then non-scoring keys: p_dollars, dollars, swap, balance, level_up, func, message, …]
```

i.e. **additive chips, then additive mult, then ×chips, then ×mult**, per source. Within an
`effect.extra` chain, `extra` recurses (`calculate_individual_effect` key `'extra'` →
`SMODS.calculate_effect(amount)`, `utils.lua:1305-1307`), so a joker can emit
`{mult=…, extra={x_mult=…}}` and get +mult applied before its own ×mult.

### 1c. The exact math primitives

`SMODS.calculate_individual_effect` (`game-dump/SMODS/_/src/utils.lua:1192-1332`) dispatches
scoring keys to `SMODS.Scoring_Parameters[…]:calc_effect`
(`smods/src/game_object.lua:3809-3910`):

- **chips** (`chip`/`h_chips`/`chip_mod`): `hand_chips = mod_chips(hand_chips + amount)`.
- **xchips** (`x_chips`/`xchips`/`Xchip_mod`), guarded by `amount ~= 1`:
  `hand_chips = mod_chips(hand_chips * (amount - 1) + hand_chips)` — i.e. multiply chips.
- **mult** (`mult`/`h_mult`/`mult_mod`): `mult = mod_mult(mult + amount)`.
- **xmult** (`x_mult`/`xmult`/`Xmult`/…), guarded by `amount ~= 1`:
  `mult = mod_mult(mult * (amount - 1) + mult)` — i.e. multiply mult.

`mod_mult` / `mod_chips` wrap **every** assignment (base-game clamps/normalises; relevant for
overflow at extreme antes). The final reduce is `floor(chips * mult)`
(`SMODS.calculate_round_score`, `utils.lua:2815-2818`). Dollars use `ease_dollars` with **no**
mod wrapper (`utils.lua:1202-1221`).

`swap` (Vampire-ish) swaps `mult`↔`hand_chips`; `balance` (Acrobat-ish) sets both to
`(mult+hand_chips)/2` (`utils.lua:1236-1296`). These are real keys we will eventually need.

### 1d. Joker hook points (the `context.*` surface, in fire order)

Within `evaluate_play`, jokers are evaluated via `eval_card(joker, context)` →
`Card:calculate_joker` (override at `game-dump/SMODS/_/src/overrides.lua:2710-2729`). The
ordered hooks a joker can answer (matching the macro order in §1a):

| Order | `context` flags | When | Typical effect |
|---|---|---|---|
| 1 | `modify_scoring_hand` | per played card, pre-score | add/remove card from scoring (`add_to_hand`/`remove_from_hand`) |
| 2 | `before = true` | once, before per-card scoring | priming, conditional setup (`state_events.lua:689`) |
| 3 | `repetition = true`, `cardarea = G.play/G.hand`, `other_card` | per card, before its score | return `{repetitions=n}` (retrigger jokers) |
| 4 | `individual = true`, `cardarea = G.play`, `other_card` | per **played** scoring card | +chips/+mult/×mult *because of that card* (suit/rank jokers) |
| 4h | `individual = true`, `cardarea = G.hand`, `other_card` | per **held** card | held-card reactions (e.g. Raised Fist, Baron, Shoot the Moon) |
| 5 | `joker_main = true`, `cardarea = G.jokers` | main joker pass, L→R | the joker's headline chips/mult/xmult |
| 5e | `edition = true, pre_joker / post_joker` | wraps each joker's main eval | Foil/Holo `+`, Polychrome `×` on the joker itself |
| 5o | `other_joker = <card>` | for every joker, against every joker | Blueprint/Brainstorm copy; "+mult per other joker"; Baseball etc. |
| 6 | `final_scoring_step = true` | once, after all jokers | last-chance adjustments + deck `final_scoring_step` |
| 7 | `destroying_card` / `remove_playing_cards` | destruction pass | Glass shatter, Hiker permabonus targets, etc. |
| 8 | `after = true` | once, post-hand | counters, "after each hand" jokers |

`Card:calculate_joker` order inside the main pass (`state_events.lua:713-794`):
**(a)** joker edition `pre_joker` → **(b)** `joker_main` (+ its retriggers) → **(c)** for
each other joker `other_joker`/`other_consumeable`/`other_voucher` (+ retriggers) →
**(d)** `eval_individual` for individual scoring targets (deck/blind/stake/challenge/mods) →
**(e)** joker edition `post_joker`. All effects are accumulated into `effects` and applied by
`SMODS.trigger_effects` at the end, which resolves keys in the
`{playing_card, enhancement, edition, seals, stickers, end_of_round, jokers, retriggers, individual}`
order (`game-dump/SMODS/_/src/utils.lua:1335-1376`).

### 1e. Blueprint / Brainstorm (re-entrant copy)

`SMODS.blueprint_effect` (`smods/src/utils.lua:2217-2243`): a copier re-runs the copied
card's `calculate_joker` with `context.blueprint` depth-tracking (guard: depth ≤ #jokers, no
self-copy, `blueprint_compat`). The returned effect is **attributed to the copier card**
(`other_joker_ret.card = eff_card`). Brainstorm copies the *leftmost* joker; Blueprint copies
the joker to its *right*. Position-dependent.

### 1f. RNG: probabilistic effects

Lucky (+20 mult 1-in-5, +$20 1-in-15), Glass break (1-in-4), and similar use
`pseudorandom(pseudoseed(key))` (base `game.lua` RNG; preview uses `pseudorandom("nope")` /
`"notthistime"` placeholders and computes min/exact/max — `EngineSimulate.lua:454-487`). The
numerator is mutable (`Oops! All 6s` doubles every probability). **Determinism requirement:
both clients must see identical procs for identical inputs** — this is why our engine models
these as game-long pop queues (see §4).

### 1g. 0.3.3 → 0.4.0 deltas (`CHANGELOG.md`) relevant to scoring

The 0.4.0 CHANGELOG contains **no changes to the scoring pipeline order or to
chips/mult/xmult combination math.** The scoring-adjacent deltas are all *parameter / effect*
tweaks, not pipeline structure:

- **Gold Card enhancement**: payout $3 → **$4** (the per-card Gold *enhancement* end-of-round
  money, distinct from Gold *seal*'s +$3 in-scoring). (`CHANGELOG.md:20`)
- **Hanging Chad** (Legacy ruleset): now retriggers the **first 2 cards** instead of the
  first card twice — changes *which* cards get `repetitions`, not the rep mechanic.
  (`CHANGELOG.md:34`)
- **Golden Ticket** $3 → $4; **To Do List** $4 → $5 and targets all hands; **Seltzer** 8
  hands; **Turtle Bean** +4 hand size — economy/retrigger-budget tuning, not pipeline.
- **Comeback Gold** now on any life loss. **Judgment** draws from its own queue on
  Orange Stake+ (queue-routing, see queue-model doc).

> **Flag (vs the 0.3.3 ranked CHANGES spreadsheet in `xlsx_out2/`):** that sheet is a 0.3.3
> baseline. Where it lists Gold/Golden Ticket/To Do List/Hanging Chad values, **0.4.0 source
> above wins.** No spreadsheet entry alters the *order* of phases.

### 1h. Critical multiplayer parity note (the reference server is NOT authoritative)

In the reference `BalatroMultiplayerAPI-Server-main`, scoring is done **client-side**: the
client computes its score and submits it via `playHand{score}`
(`src/actionHandlers.ts:204-229`), and the server merely **compares two submitted scores**
using `InsaneInt` (`src/InsaneInt.ts`, a 3-limb big-number to survive f64 overflow at high
antes — `actionHandlers.ts:185, 220, 238-249`). The server trusts the client's number.
**Our project deliberately diverges: scoring becomes server-authoritative.** That means we
must re-derive §1a–§1f exactly, because we will *recompute* the score from intents rather
than accept it.

---

## 2. How OUR engine does it today (cite our Java)

`com.balatromp.engine.scoring.ScoringEngine.score(played, held, run, rng)`
(`ScoringEngine.java:46-135`). Structure:

1. `HandEvaluator.evaluate(played)` → `HandResult` (`:47`).
2. **Scoring set** = detected scoring cards + Stone cards, re-sorted by played index
   (`:50-54`). ✅ matches §1.2 intent (preserves play order, Stone always scores).
3. **Base chips/mult** from hand level (`:57-60`). ✅ matches §1a.4.
4. **BEFORE pass** over jokers (`:72-78`, `Trigger.BEFORE`). ✅ matches §1d.2.
5. **Per played card, L→R** (`:81-94`): compute `retriggers(...)`, then per rep apply
   `applyCardScored` then the `ON_SCORED` joker pass. ✅ matches §1b structure.
6. **Held cards** (`:97-111`): `REPETITION_HELD` then `applyCardHeld` + `ON_HELD` joker pass.
   ✅ matches §1a.7 (`G.hand` area).
7. **Main joker pass + joker-on-joker** (`:114-130`): `JOKER_MAIN` then nested
   `ON_OTHER_JOKER`. ✅ matches §1d.5/5o.
8. **Final score** = `chips * mult` (`:133`, a `double`).

Per-card math (`applyCardScored`, `:174-212`): base rank chips, Stone +50, Bonus +30,
Mult +4, Lucky (two game-long queues: `lucky_mult` 1-in-5 honouring
`probabilityNumerator/5`, `lucky_money` 1-in-15), Glass ×2 + 1-in-4 break queue, then
`applyCardEdition` (Foil +50 / Holo +10 / Poly ×1.5), then Gold seal +$3. Held
(`:224-229`): Steel ×1.5.

Effect application order (`apply`, `:232-254`):
**chips → mult → dollars → hMult → xMult**.

RNG queues: `QueueSet` keyed by string (`lucky`/`glass`/etc.), popped via `GameQueue`
(`:202, 257-260`) — the game-long pop-queue model so both players proc identically.

Result: `ScoreResult(handType, chips, mult, score, replayLog, destroyed)`
(`ScoreResult.java`), score is a `double`. Joker surface is the single `Trigger` enum
(`Trigger.java`) + `JokerEffect` return (`JokerEffect.java`) + `EvaluationContext`
(`EvaluationContext.java`) with `forCopy()` for Blueprint depth.

---

## 3. The GAP

Ordered by parity-risk:

**G1 — Missing `modify_scoring_hand` phase.** Our scoring set is built purely from
`HandEvaluator` + Stone (`:50-54`). The real game runs a per-card
`modify_scoring_hand` joker pass that can `add_to_hand` / `remove_from_hand` (Splash adds all;
some jokers/seals remove). We have no hook → Splash, and any "this card also scores" joker is
unscoreable. (§1.2)

**G2 — Missing boss-blind `debuff_hand` / `modify_hand` phases.** No veto (Not-Allowed →
0×0), no boss modify_hand step (`The Arm` delevels, `The Ox`/`The Tooth` money, Matador
debuffed-hand joker pass). Our `score()` has no `blind` parameter at all. (§1a.3/3-veto, §1a.6)

**G3 — `final_scoring_step`, `after`, destruction phases absent.** No
`final_scoring_step` joker/deck hook, no `context.after`, and destruction is only the inline
Glass-break inside `applyCardScored` (`:199-206`) — there is no general
`destroying_card`/`remove_playing_cards` pass. Glass break also currently happens *inside the
rep loop*, so a retriggered Glass card could be marked destroyed twice. (§1a.9-10)

**G4 — Field-application order is incomplete & xchips missing.** Our `apply` order is
`chips → mult → dollars → hMult → xMult` (`:235-253`). The real order per source is
`chips → mult → xchips → xmult` (`calculation_keys`, §1b). We have **no `xChips`** (Hologram,
Glass-on-chips builds, some editions) and no `extra`-chain recursion, no `swap`/`balance`.
`hMult` is also applied *after* dollars, whereas real order puts h_mult with mult.

**G5 — Edition on jokers not modelled.** §1d.5e: each joker's own Foil/Holo (+chips/+mult)
fires *before* its main effect and Polychrome (×mult) *after*. Our joker pass
(`:114-130`) only calls `JOKER_MAIN`/`ON_OTHER_JOKER`; there is no joker-edition pre/post
wrap. (BMP `simulate_joker_global_effects` does Foil/Holo before, Poly after —
`EngineSimulate.lua:191-203`.)

**G6 — Retrigger semantics narrow.** `retriggers()` (`:152-171`) sums `e.repetitions` but
(a) only adds reps to the **card's intrinsic** apply + the `ON_SCORED` joker pass — it does
*not* re-run held-card retriggers through the joker `repetition` context with `cardarea`
distinction shown in §1b, and (b) Red Seal is hard-coded (`:155-158`) rather than a
ruleset/enhancement-driven value. Echo Deck (+1 rep) and per-card retrigger *with post
effects* (SMODS `insert_repetitions` post-trigger chain, `utils.lua:1443-1469`) are absent.

**G7 — Consumable / deck "individual" scoring targets absent.** §1a.8 fires Observatory
(planet xmult), and `SMODS.get_card_areas('individual')` scores deck/blind/stake/challenge.
We have none of `consumables`, `vouchers`, `selected_back`, `blind` as scoring contributors.

**G8 — `floor` + big-number.** Real round score is `floor(chips * mult)`
(`utils.lua:2817`); we return a raw `double` (`:133`). No `floor`, and `double` overflows at
high antes where the reference server uses `InsaneInt` (3-limb). At parity-critical antes our
score will diverge from the opponent's client-computed score.

**G9 — Not ruleset-driven.** All numbers (Red Seal +1, Lucky 1-in-5/1-in-15, Glass ×2/1-in-4,
Steel ×1.5, Bonus +30, edition values, Gold seal $3) are hard-coded constants in
`ScoringEngine`/`applyCardScored`. 0.4.0 has Standard vs Legacy rulesets
(`multiplayer-0.4.0/rulesets/`) and stake-dependent behaviour (Judgment queue, Comeback Gold
$4/$2). The pipeline must read these from a ruleset object, not literals.

**G10 — No `before`-vs-`main` distinction enforced for jokers, and no
`initial_scoring_step`.** We have `BEFORE` and `JOKER_MAIN`, which is the important split, but
there is no `initial_scoring_step`/`modify_hand` seam for blind effects to land between base
and per-card scoring.

---

## 4. Proposed target design (authoritative + queue-shaped + ruleset-driven)

### 4.1 Pipeline skeleton (mirror `FN.SIM.run`, phase-for-phase)

Refactor `ScoringEngine.score(...)` to accept the full context and execute named phases. Each
phase is a method so the order is auditable against §1a:

```
ScoreResult score(PlayContext pc):
  hr   = HandEvaluator.evaluate(pc.played)
  acc  = new Acc(ruleset)                      // carries chips, mult, dollars, log, destroyed
  ctx  = baseContext(hr, pc)

  scoring = assembleScoringSet(hr, pc, ctx)    // PHASE 0  (modify_scoring_hand) — fixes G1
  if blind.debuffHand(scoring, ctx):           // PHASE 1  (debuff veto)         — fixes G2
       acc.chips = 0; acc.mult = 0
       jokerPass(ctx, DEBUFFED_HAND)           // Matador
       return finalize(acc)                    // floor + bignum
  jokerPass(ctx, BEFORE)                       // PHASE 2  context.before
  applyBase(acc, hr, run)                       // PHASE 3  base chips/mult (hand level)
  blind.modifyHand(acc, ctx)                   // PHASE 4  modify_hand            — fixes G2/G10
  jokerPass(ctx, INITIAL_SCORING_STEP)         // PHASE 5
  for area in {PLAY, HAND}:                     // PHASE 6  per-card scoring
       for card in area.cards (L→R):
           if card.debuffed: { blind.juice(); continue }
           reps = collectReps(card, area, ctx) // red seal + echo + joker repetition ctx
           for r in 0..reps:
               applyCardIntrinsic(acc, card, area, ctx) // chips→mult→xchips→xmult→edition→seal
               jokerPass(ctx.individual(card, area))     // per-card joker reactions + their reps
  jokerMainPass(acc, ctx)                      // PHASE 7  edition-pre → main(+reps) → other_joker → edition-post  — fixes G5
  consumablePass(acc, ctx)                     // PHASE 8  Observatory etc.        — fixes G7
  individualTargetsPass(acc, ctx)              // PHASE 8b deck/blind/stake
  jokerPass(ctx, FINAL_SCORING_STEP); deck.finalScoringStep(acc)  // PHASE 9        — fixes G3
  runDestruction(acc, ctx)                     // PHASE 10 destroying_card + remove_playing_cards — fixes G3
  jokerPass(ctx, AFTER)                         // PHASE 11
  return finalize(acc)                          // floor(chips*mult), bignum         — fixes G8
```

### 4.2 Effect application order (fix G4)

Replace `apply(...)` field order with the SMODS `calculation_keys` order, applied **per
source effect**:

```
applyEffect(acc, e):
  pre_func
  acc.chips += e.chips (+ h_chips)             // additive chips
  acc.mult  += e.mult  (+ h_mult)              // additive mult
  if e.xChips != null && != 1: acc.chips *= e.xChips   // ×chips   — NEW field
  if e.xMult  != null && != 1: acc.mult  *= e.xMult    // ×mult
  applyEffect(acc, e.extra)                    // recurse extra-chain — NEW
  e.dollars / swap / balance / level_up / message (non-scoring, after)
```

Add `xChips` (boxed Double) and `extra` (nested `JokerEffect`) to `JokerEffect.java`. Add
`swap`/`balance` as flags. Keep dollars *out* of the chips/mult ordering (it has no math
effect on the product).

### 4.3 Per-card intrinsic order (fix G4 within card)

`applyCardIntrinsic` must follow §1b exactly: **base chips (+bonus/+perma_bonus) → mult
(Mult enh / Lucky) → xmult (Glass) → dollars (Gold seal / Lucky money) → edition
(Foil chips, Holo mult, Poly xmult)** for `PLAY`; **h_mult → h_xmult (Steel)** for `HAND`.
Move Glass *break* out of the per-rep loop into the PHASE 10 destruction pass (a card is
destroyed at most once regardless of retriggers).

### 4.4 Retriggers (fix G6)

`collectReps(card, area, ctx)` returns `1 + sum(reps)` where reps come from:
`enhancement/seal` (Red seal → `ruleset.redSealReps`, default 1), deck (Echo → +1), and a
joker `REPETITION` pass that is **area-aware** (`cardarea == PLAY` vs `HAND`). Each rep re-runs
both the intrinsic apply and the per-card joker `individual` pass (already structured in
`:81-94`; extend to held). Support per-rep *post-trigger* effects later (SMODS
`insert_repetitions`), gated behind a ruleset/optional-feature flag.

### 4.5 Joker edition wrap (fix G5)

In `jokerMainPass`, for each joker L→R: apply its **Foil/Holo (+chips/+mult) before**
`JOKER_MAIN`, run main (+ its retriggers), run the `ON_OTHER_JOKER` inner loop, then apply its
**Polychrome (×mult) after**. This is `state_events.lua:713-794` order
(`edition pre_joker` … `joker_main` … `other_joker` … `edition post_joker`).

### 4.6 Ruleset-driven values (fix G9)

Introduce a `Ruleset` (Standard / Legacy, stake-aware) holding all magic numbers:
`redSealReps`, `luckyMultNumerator/Denominator (1/5)`, `luckyMoneyOdds (1/15)`,
`glassXmult (2)`, `glassBreakOdds (1/4)`, `steelXmult (1.5)`, `bonusChips (30)`,
`stoneChips (50)`, `editionFoilChips (50)`, `editionHoloMult (10)`, `editionPolyXmult (1.5)`,
`goldSealDollars (3)`, plus per-joker overrides (To Do List $5, Golden Ticket $4, Hanging Chad
firstNCards=2 on Legacy, Gold enhancement $4). The pipeline reads `ruleset.*` instead of
literals. `ScoringEngine` becomes stateless w.r.t. tuning; the ruleset is injected per match.

### 4.7 Determinism & queues (preserve current model)

Keep the game-long pop-queue model (`QueueSet`/`GameQueue`) — it already matches the BMP
requirement that both players proc identically for identical played cards. Route the
queues through `PlayContext`/`RunState` (already done via `run.queues`, `:69`). Stake-dependent
queue routing (Judgment own-queue on Orange+) lives in the queue model doc, not here, but the
scoring pipeline must pull Lucky/Glass rolls from the ruleset-named queues.

### 4.8 Final reduce & big-number (fix G8)

`finalize(acc)` returns `floor(chips * mult)` as a big-number score. Introduce a `Score`
type (port of `InsaneInt`'s 3-limb design, `BalatroMultiplayerAPI-Server-main/src/InsaneInt.ts`)
or use `BigDecimal`→floor→`BigInteger`. `ScoreResult.score` becomes that type;
comparison against the blind requirement and against the opponent uses big-number compare.
This is mandatory for high-ante parity with the opponent's client-computed score.

### 4.9 Phase enum additions

Extend `Trigger` with: `MODIFY_SCORING_HAND`, `INITIAL_SCORING_STEP`, `FINAL_SCORING_STEP`,
`DEBUFFED_HAND`, `DESTROYING_CARD`, `REMOVE_PLAYING_CARDS`. (`AFTER`, `BEFORE`, `ON_SCORED`,
`ON_HELD`, `ON_OTHER_JOKER`, `JOKER_MAIN`, repetition triggers already exist.)

---

## Open questions

1. **f64 vs big-number boundary.** At what ante/score does the reference client's
   `InsaneInt` diverge from our `double`? We need the exact threshold to decide whether to
   port `InsaneInt` (3-limb) or whether `BigDecimal` floor suffices for the score range our
   gamemodes actually reach. (Source: `InsaneInt.ts`; unverified which limb layout the
   client uses for display vs compare.)
2. **`mod_mult`/`mod_chips` semantics.** The base game wraps every chips/mult assignment in
   these. Do they merely `math.max(0, …)` / round, or do they implement the
   naninf/overflow normalisation? Need to read `game.lua` `mod_mult`/`mod_chips` bodies — not
   yet located in the dump — to know if intermediate clamping affects parity (likely matters
   only at overflow).
3. **Order of `before` vs blind `modify_hand`.** Authoritative code fires `context.before`
   (§1a.5) *before* `blind:modify_hand` (§1a.6); the preview's `simulate_joker_before_effects`
   also precedes `simulate_blind_effects`. Confirm no joker observes post-modify base in its
   `before` branch (would couple the two).
4. **Held-card joker `individual` pass.** Does the real game run the per-card `individual`
   joker pass for **held** cards (cardarea=G.hand), or only for played cards? `score_card` is
   shared by both areas (`utils.lua:2091-2118`), implying yes — but we should confirm which
   vanilla jokers actually branch on `cardarea==G.hand, individual` (Baron/Raised Fist are
   `joker_main`, not individual). Affects whether we run a held `individual` pass at all.
5. **Hanging Chad Legacy rework exact targeting.** 0.4.0 retriggers "first 2 cards" — is that
   the first 2 *played* cards or first 2 *scoring* cards, and does each get +1 rep or does
   card-1 still get its historical double? (`CHANGELOG.md:34`; effect body in
   `compatibility/Preview/Jokers/Multiplayer.lua` — not yet read.)
6. **Quantum enhancements** (`SMODS.calculate_quantum_enhancements`, `score_card:2064`) —
   are any in BMP 0.4.0's pool (e.g. a card counting as multiple enhancements)? If not we can
   defer; if yes the per-card apply must loop enhancement variants.
7. **`extra` chain depth & message ordering.** How deep do vanilla jokers nest `extra`
   (mult then xmult then dollars)? Needed to size the recursion and to keep replay-log
   ordering identical to the client's animation order.

## New building blocks needed

- **`Ruleset`** (`engine/ruleset/Ruleset.java`) — all scoring magic numbers + per-joker
  overrides, Standard vs Legacy, stake-aware; injected per match. (G9)
- **`Score`** big-number type (port of `InsaneInt` or `BigInteger` wrapper) with
  `floor(chips*mult)`, compare, toString. Replaces `double score`. (G8)
- **`Blind`** scoring participant interface: `debuffHand(scoring, ctx) → boolean`,
  `modifyHand(acc, ctx)`, `finalScoringStep`, plus boss-specific impls
  (The Arm/Ox/Tooth/Matador/Flint/etc.). (G2)
- **`PlayContext`** input record: `played, held, deck?, run, ruleset, blind, queues` — replaces
  the loose `score(played, held, run, rng)` signature. (G2/G7)
- **`JokerEffect.xChips`** (boxed Double) + **`JokerEffect.extra`** (nested effect) +
  `swap`/`balance` flags; and a `applyEffect` that recurses `extra` in
  `chips→mult→xchips→xmult` order. (G4)
- **New `Trigger` values**: `MODIFY_SCORING_HAND`, `INITIAL_SCORING_STEP`,
  `FINAL_SCORING_STEP`, `DEBUFFED_HAND`, `DESTROYING_CARD`, `REMOVE_PLAYING_CARDS`. (G1/G3/G10)
- **`ScoringSetBuilder`** — runs the `modify_scoring_hand` pass (Splash add-all,
  always_scores/never_scores, joker add/remove). (G1)
- **Joker-edition wrap** helper in the main pass (pre Foil/Holo, post Poly). (G5)
- **`IndividualTarget`** abstraction for deck/blind/stake/challenge/consumable scoring
  contributors (Observatory xmult, deck `final_scoring_step`). (G7)
- **`DestructionPass`** — single post-scoring pass collecting Glass shatter / removed cards,
  decoupled from the per-rep loop. (G3)
- **Area-aware `collectReps`** that distinguishes PLAY vs HAND retrigger contexts and reads
  Red-seal/Echo rep counts from the ruleset/enhancement, with optional per-rep post-trigger
  support. (G6)
