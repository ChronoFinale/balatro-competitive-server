# 12 — Rare-Rarity Vanilla Jokers (Balatro Multiplayer 0.4.0)

Definitive catalogue of **every Rare-rarity (`rarity = 3`) vanilla Joker** in Balatro,
as shipped in Balatro Multiplayer (BMP) **0.4.0**. There are exactly **20** Rare vanilla Jokers.

## Grounding / sources

- Center definitions, rarity, cost, base config:
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` — all 20 confirmed via
  `grep "rarity = 3"` + `set = "Joker"` (exactly 20 matches).
- Exact effect semantics (Divvy's simulation, the most structured machine-readable source):
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/compatibility/Preview/Jokers/_Vanilla.lua`
- Multiplayer-specific behavior (queues, seeding, Phantoms, Blueprint-compat):
  `C:/Users/micha/AppData/Local/Temp/xlsx_out2/05_Jokers.txt`
- 0.4.0 deltas: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/CHANGELOG.md`
- Display/value derivation: `C:/Users/micha/AppData/Roaming/Balatro/Mods/JokerDisplay/definitions/display_definitions.lua`

### 0.4.0 vs 0.3.3 delta for Rare vanilla Jokers

**None.** The `CHANGELOG.md` "Ranked Update 0.4.0 → Jokers" section only touches BMP/non-vanilla or
non-Rare jokers (To Do List, Golden Ticket, Speedrun, Ouija/Ectoplasm cost fix, Hanging Chad on
Legacy). No Rare-rarity vanilla Joker (DNA, Vagabond, Baron, Obelisk, Baseball Card, Ancient,
Campfire, Blueprint, Wee, Hit the Road, the five "The X" pair/poker-hand jokers, Stuntman, Invisible,
Brainstorm, Driver's License, Burnt) was changed in 0.4.0. All numbers below are vanilla-current and
match the 0.3.3 baseline. Where the *ranked spreadsheet* adds MP-only behavior (Invisible RNG fix,
Tarot/queue sourcing for Vagabond) it is flagged inline.

## Catalogue (all 20)

All are `set = "Joker"`, `rarity = 3`. "Cost" is base shop cost in `$`. "Numbers" are pulled from the
center `config` block (game.lua) and corroborated by the Divvy sim function (`_Vanilla.lua`).

| # | Name | Internal key | Cost | Exact effect (real numbers) | Trigger(s) | sim/source line |
|---|------|-------------|------|------------------------------|-----------|-----------------|
| 1 | DNA | `j_dna` | 8 | If **first hand of round** is played as a **single card**, add a permanent copy of that card to hand (and to deck). `config = {}`. | BEFORE (on play, first hand only) | `_Vanilla.lua:270` |
| 2 | Vagabond | `j_vagabond` | 8 | Create a **Tarot card** if hand is played while holding **≤ $4** (`config.extra = 4`). | JOKER_MAIN / on play (money check) | center `game.lua:451`; sim `:379` (effect noted, consumable) |
| 3 | Baron | `j_baron` | 8 | Each **King held in hand** gives **X1.5 Mult** (`config.extra = 1.5`). | ON_HELD (per King in hand) | `_Vanilla.lua:382` |
| 4 | Obelisk | `j_obelisk` | 8 | **+X0.2 Mult** per consecutive hand played **without** playing your most-played poker hand; resets when you play your (currently) most-played hand. `config.extra = 0.2`, base `Xmult = 1`. | JOKER_MAIN (X mult), state reset on play | `_Vanilla.lua:395`; display `:1176` |
| 5 | Baseball Card | `j_baseball` | 8 | Each **Uncommon Joker** gives **X1.5 Mult** (`config.extra = 1.5`; matches `other_joker.rarity == 2`). | ON_OTHER_JOKER (per Uncommon joker owned) | `_Vanilla.lua:524` |
| 6 | Ancient Joker | `j_ancient` | 8 | Each played card of a **specific suit** gives **X1.5 Mult** (`config.extra = 1.5`); suit changes each round (`G.GAME.current_round.ancient_card.suit`). | ON_SCORED (per scored card of chosen suit) | `_Vanilla.lua:562` |
| 7 | Campfire | `j_campfire` | 9 | **+X0.25 Mult** (`config.extra = 0.25`) for each card **sold** this run; resets when Boss Blind is defeated. | JOKER_MAIN (X mult), state on SELL_CARD, reset END_OF_ROUND(boss) | `_Vanilla.lua:606`; display `:1658` |
| 8 | Blueprint | `j_blueprint` | 10 | **Copies** the ability of the Joker to its **right** (`effect = "Copycat"`, `config = {}`). | (copies whatever the target's trigger is) | `_Vanilla.lua:760` |
| 9 | Wee Joker | `j_wee` | 8 | Gains **+8 Chips** permanently each time a **2** is scored (`config.extra = {chips = 0, chip_mod = 8}`); gives accumulated Chips. | ON_SCORED(rank 2) → state add; JOKER_MAIN → +Chips | `_Vanilla.lua:771` |
| 10 | Hit the Road | `j_hit_the_road` | 8 | **+X0.5 Mult** (`config.extra = 0.5`) per **Jack discarded** this round; resets each round. | ON_DISCARD(rank Jack) → state add; JOKER_MAIN → X mult | `_Vanilla.lua:848`; display `:2140` |
| 11 | The Duo | `j_duo` | 8 | **X2 Mult** (`config.Xmult = 2`) if played hand contains a **Pair** (`config.type = 'Pair'`). | JOKER_MAIN (X mult, hand-type gated) | `_Vanilla.lua:856` |
| 12 | The Trio | `j_trio` | 8 | **X3 Mult** (`config.Xmult = 3`) if played hand contains a **Three of a Kind**. | JOKER_MAIN | `_Vanilla.lua:859` |
| 13 | The Family | `j_family` | 8 | **X4 Mult** (`config.Xmult = 4`) if played hand contains a **Four of a Kind**. | JOKER_MAIN | `_Vanilla.lua:862` |
| 14 | The Order | `j_order` | 8 | **X3 Mult** (`config.Xmult = 3`) if played hand contains a **Straight**. | JOKER_MAIN | `_Vanilla.lua:865` |
| 15 | The Tribe | `j_tribe` | 8 | **X2 Mult** (`config.Xmult = 2`) if played hand contains a **Flush**. | JOKER_MAIN | `_Vanilla.lua:868` |
| 16 | Stuntman | `j_stuntman` | 7 | **+250 Chips** (`config.extra.chip_mod = 250`), **−2 hand size** (`config.extra.h_size = 2`). | JOKER_MAIN (+Chips); passive hand-size −2 | `_Vanilla.lua:871` |
| 17 | Invisible Joker | `j_invisible` | 8 | After **2 rounds** (`config.extra = 2`), can be **sold** to create a free **copy** of a random owned Joker. `blueprint_compat = false`, `eternal_compat = false`. | END_OF_ROUND (counter), SELL_SELF (create copy) | `_Vanilla.lua:874` (meta); MP detail `05_Jokers.txt:48-70` |
| 18 | Brainstorm | `j_brainstorm` | 10 | **Copies** the ability of the **leftmost** Joker (`effect = "Copycat"`, `config = {}`). | (copies leftmost joker's trigger) | `_Vanilla.lua:877` |
| 19 | Driver's License | `j_drivers_license` | 7 | **X3 Mult** (`config.extra = 3`) if you own **≥ 16 enhanced** cards (`driver_tally >= 16`). | JOKER_MAIN (X mult, deck-state gated) | `_Vanilla.lua:893`; display `:2350` |
| 20 | Burnt Joker | `j_burnt` | 8 | Upgrades the **level of the first discarded poker hand** each round (`config.extra = 4` magnitude; `h_size = 0`). | PRE_DISCARD / ON_DISCARD (first discard of round → level up hand) | center `game.lua:529`; sim `:904` (effect = Discard, not scored) |

### Notes on specific entries

- **DNA** (`j_dna`): sim only fires when `G.GAME.current_round.hands_played == 0 and #full_hand == 1`
  — i.e. literally the first hand of the round AND a single card. It physically inserts a copy into
  the held cards (and the real game adds it to the deck too). `config = {}` — no numeric tuning.
- **Vagabond** (`j_vagabond`): `config.extra = 4` is the money threshold. Creates a Tarot when a hand
  is played at **≤ $4**. MP note (`05_Jokers.txt:30-32`): "work as expected, with the Tarot cards they
  give taken from the **Up Top Tarot Queue**" (deterministic shared queue, alongside Superposition &
  Cartomancer). The Divvy sim leaves it as a no-op (`-- Effect might be relevant? (Consumable)`,
  `:379`) because creating a consumable does not affect score simulation.
- **Obelisk** (`j_obelisk`): `perishable_compat = false`. The X mult grows by `extra = 0.2` each hand
  you do **not** play your most-played hand type; the moment you play (one of) your most-played
  type(s) it resets to X1. Display calc (`:1185`) recomputes `play_more_than` over all visible
  `G.GAME.hands`.
- **Baseball Card** (`j_baseball`): matches `other_joker.rarity == 2` (Uncommon) and excludes itself.
  X1.5 per Uncommon joker, multiplicative.
- **Ancient Joker** (`j_ancient`): suit is `G.GAME.current_round.ancient_card.suit`, re-rolled per
  round. X1.5 per scored card of that suit.
- **Wee Joker** (`j_wee`): `perishable_compat = false`. Permanent growth: `chips += chip_mod(8)` for
  every scored **2**; the accumulated `chips` is added on JOKER_MAIN. Starts at 0 chips.
- **Hit the Road** (`j_hit_the_road`): per-round X mult. `x_mult += extra(0.5)` for each Jack
  discarded that round; the running `x_mult` is applied via `x_mult_if_global`. Resets each round.
- **The Duo/Trio/Family/Order/Tribe**: each is a flat **conditional X mult** gated on the played hand
  *containing* the named hand type (`config.type`). Values: Duo X2, Trio X3, Family X4, Order X3,
  Tribe X2.
- **Stuntman** (`j_stuntman`): flat **+250 chips** plus a passive **−2 hand size**. Sim only models
  the chip add (`:871`); the hand-size penalty is a passive slot/hand modifier.
- **Invisible Joker** (`j_invisible`): NOT blueprint/eternal compatible. Sell after surviving 2 rounds
  to duplicate a random owned Joker for free. **MP-specific behavior (0.4.0, from `05_Jokers.txt`):**
  the random selection was reworked to reduce cross-player variance — each Joker is assigned a
  deterministic position by type/recency, and on sell Invisible picks a random point mapping to a
  Joker, so two players with overlapping joker sets get correlated (fair) results
  (`05_Jokers.txt:50-70`). This is a PvP determinism concern, not a number change.
- **Blueprint / Brainstorm**: pure copycat. Blueprint copies the Joker to its **right**; Brainstorm
  copies the **leftmost** Joker. Sim implements recursion with a `context.blueprint` depth guard
  capped at `#jokers + 1` to prevent infinite copy loops.
- **Driver's License** (`j_drivers_license`): X3 once `driver_tally >= 16` enhanced cards owned. The
  `unlock_condition` (`count = 16, tally = 'total'`) is also the in-run condition the sim checks via
  `ability.driver_tally`.
- **Burnt Joker** (`j_burnt`): `h_size = 0`. Levels up the poker hand of the **first discard each
  round**. Sim marks it `-- Effect not relevant (Discard)` (`:904`) because hand-level changes are
  applied to `G.GAME.hands` rather than to immediate score; the level-up persists.

## Expressibility against our JokerDef algebra

Assessed against `com.balatro.engine.joker` triggers/conditions/values/effect-ops and state
mutations listed in the engine spec.

| Joker | Verdict | Mapping / missing building blocks |
|-------|---------|-----------------------------------|
| DNA | **NEEDS** | create card (copy of scored card), modify the deck. Trigger FIRST_HAND_DRAWN/BEFORE + `PlayedCount(==,1)` is expressible, but the *copy-card-into-hand-and-deck* effect is not. |
| Vagabond | **NEEDS** | create card/consumable (Tarot), probabilistic/queue-driven procs (Up Top Tarot Queue). Condition `MoneyAtLeast`/`Not` + JOKER_MAIN expressible; the create-Tarot op is not. |
| Baron | **EXPRESSIBLE** | ON_HELD, `ScoredRankBetween(King)` (held-card rank == K), op `XMULT` Const 1.5, `Count(source=HELD, match King)`. |
| Obelisk | **NEEDS** | per-hand-type-level reads (must read most-played hand type across all hands). State counter (ADD per non-match hand, RESET on match) + `XMULT` scale works once we can read "is this hand my most-played type". |
| Baseball Card | **NEEDS** | reading other jokers' rarity. ON_OTHER_JOKER trigger exists; need a condition "other joker rarity == Uncommon". Op `XMULT` Const 1.5 expressible. |
| Ancient Joker | **NEEDS** | per-round random suit selection (RunVar/State seeded per round). Once the round-suit is a readable State, ON_SCORED + `ScoredSuit(state)` + `XMULT` Count is expressible. |
| Campfire | **NEEDS** | sell-value/run-event counter (cards sold this run) + reset on boss defeat. SELL_CARD trigger + state ADD + END_OF_ROUND(boss) RESET + `XMULT` State scale — *mostly* expressible if we add a "cards sold this run" state source and a boss-defeat condition. |
| Blueprint | **NEEDS** | copy/retrigger another joker (positional: right neighbor). |
| Wee Joker | **EXPRESSIBLE** | ON_SCORED `ScoredRankBetween(2..2)` → Mutation ADD 8 to counter; JOKER_MAIN op `CHIPS` Value State(counter). |
| Hit the Road | **EXPRESSIBLE** | ON_DISCARD `ScoredRankBetween(Jack)` → Mutation ADD 0.5 to counter (reset END_OF_ROUND); JOKER_MAIN op `XMULT` base 1 + scale·counter. (DiscardedFaceCount/state supports this.) |
| The Duo | **EXPRESSIBLE** | JOKER_MAIN, `HandContainsPair` (or `HandIs`), op `XMULT` Const 2. |
| The Trio | **EXPRESSIBLE** | JOKER_MAIN, `HandIs(Three of a Kind)` (contains-variant), op `XMULT` Const 3. |
| The Family | **EXPRESSIBLE** | JOKER_MAIN, `HandIs(Four of a Kind)`, op `XMULT` Const 4. |
| The Order | **EXPRESSIBLE** | JOKER_MAIN, `HandIs(Straight)`, op `XMULT` Const 3. |
| The Tribe | **EXPRESSIBLE** | JOKER_MAIN, `HandIs(Flush)`, op `XMULT` Const 2. |
| Stuntman | **NEEDS** | hand-size/slot change (−2 hand size). The **+250 chips** part is EXPRESSIBLE (JOKER_MAIN, op `CHIPS` Const 250); the passive hand-size penalty is not. |
| Invisible Joker | **NEEDS** | sell-self create joker copy, copy another joker, probabilistic/queue-driven (deterministic MP positioning), reading other jokers. END_OF_ROUND counter is expressible; the rest is not. |
| Brainstorm | **NEEDS** | copy/retrigger another joker (positional: leftmost). |
| Driver's License | **NEEDS** | reading deck enhancement count (≥16 enhanced cards). Once "count of enhanced cards in deck" is a Value source, JOKER_MAIN + condition `>=16` + op `XMULT` Const 3 is expressible. |
| Burnt Joker | **NEEDS** | level-up a hand, first-discard-of-round gating. ON_DISCARD trigger + a "first discard this round" condition + a "level up the discarded hand type" op are all missing. |

**Summary:** 8 fully EXPRESSIBLE today (Baron, Wee, Hit the Road, The Duo/Trio/Family/Order/Tribe).
12 NEED new building blocks (DNA, Vagabond, Obelisk, Baseball Card, Ancient, Campfire, Blueprint,
Stuntman, Invisible, Brainstorm, Driver's License, Burnt).

## Open questions

- **Vagabond Tarot sourcing in PvP:** confirm the exact "Up Top Tarot Queue" seeding/advancement
  rule shared with Superposition/Cartomancer (`05_Jokers.txt:30-32` describes it qualitatively; the
  precise queue index function lives in the BMP `objects/`/queue code, not yet read).
- **Invisible Joker MP determinism:** the reworked position-assignment algorithm
  (`05_Jokers.txt:50-70`) is described by example, not formula. Need the actual sort key (type →
  recency → unique position) and the random-point→joker mapping to reproduce it deterministically on
  our server.
- **Obelisk "most played hand" tie-break:** when multiple hand types tie for most-played, does playing
  any of them reset Obelisk? Display code (`:1191`) iterates `>=` so ties count as "most played";
  confirm the real `calculate` matches.
- **Ancient Joker round-suit seeding:** which pseudoseed key picks `ancient_card.suit` each round, and
  is it synchronized/independent per player in BMP?
- **Burnt Joker exact magnitude:** `config.extra = 4` — confirm whether this is levels-per-upgrade or a
  legacy field; vanilla Burnt upgrades the first discarded hand by exactly **1 level**. Flag as
  **unverified** until the `calculate` body is located (not present in the decompiled `game.lua` slice
  read; logic is name-dispatched elsewhere).
- **DNA deck mutation:** confirm whether the copied card also enters the **draw deck** (vanilla: yes)
  vs. only current hand (sim only inserts into `held_cards`).

## New building blocks needed

Distinct capabilities required to express the 12 NEEDS jokers above:

1. **Create card** (copy of an existing scored/held card) and insert into hand + deck — *DNA*.
2. **Create consumable** (Tarot) — *Vagabond*; plus **queue-driven/deterministic proc source** (Up Top
   Tarot Queue) for PvP fairness — *Vagabond, Invisible*.
3. **Per-hand-type-level / play-count reads** — read "most-played poker hand type" and per-hand level —
   *Obelisk*; **level-up a poker hand** as an effect op — *Burnt Joker*.
4. **Read other jokers' attributes** (rarity, identity, position) — *Baseball Card, Invisible*.
5. **Copy / retrigger another joker** by position (right-neighbor, leftmost) — *Blueprint, Brainstorm*;
   and on-sell **create a copy of a joker** — *Invisible*.
6. **Per-run event counters** beyond per-round: "cards sold this run" with **boss-defeat reset
   condition** — *Campfire*; **SELL_SELF** create-effect — *Invisible*.
7. **Round-seeded random state** (e.g., per-round chosen suit) readable as a Value/Condition — *Ancient
   Joker*.
8. **Deck-composition reads** — count of enhanced cards in deck — *Driver's License*.
9. **Hand-size / slot modifiers** as a passive joker effect — *Stuntman* (−2 hand size; also Burnt's
   `h_size=0` is a no-op but uses the same field).
