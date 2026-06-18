# 11 — Uncommon Vanilla Jokers (Balatro Multiplayer 0.4.0)

Definitive catalogue of every **Uncommon-rarity** (`rarity = 2`) vanilla Balatro joker, grounded in the on-disk 0.4.0 sources.

## Sources of truth

- **Center defs / exact config**: `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` (lines ~394–530). `grep -c "rarity = 2"` returns **64** — this is the complete Uncommon set and matches the "~64" target exactly.
- **Exact effect semantics**: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/compatibility/Preview/Jokers/_Vanilla.lua` (Divvy's simulation; the most precise structured behaviour source).
- **0.4.0 deltas vs 0.3.3**: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/CHANGELOG.md`.
- **MP queue mechanics for procs/spectral**: `C:/Users/micha/AppData/Local/Temp/xlsx_out2/05_Jokers.txt` (0.3.3 baseline; flagged where 0.4 supersedes).
- **Our algebra under test**: `D:/NewServer/src/main/java/com/balatro/engine/joker` + `joker/def` (triggers/conditions/values/ops enumerated in the task brief).

> Notation: all numbers below are **base values** straight from `config` in `game.lua`. "extra" is the raw config field. Where the simulation reveals the actual operation (mult/chips/xmult/dollars/reps), that wins.

## 0.4.0 vs 0.3.3 deltas affecting Uncommons

The 0.4.0 `CHANGELOG.md` lists **no changes to any Uncommon joker** in this set. All deltas there touch other-rarity jokers or non-joker systems:
- To Do List (Common), Golden Ticket (Common), Speedrun (out of rotation), Ouija/Ectoplasm (consumables), Hanging Chad (Legacy/Common).
- Prior 0.3.0 changes that **persist into 0.4.0 and DO touch this set**: **Seltzer** lasts 8 hands (not 10); **Turtle Bean** grants +4 hand size (not +5) — but note `game.lua` config still literally reads `h_size = 5` / `extra = 10` (see those rows; the nerf is applied in mod override code, not the center config — flagged per-row).

---

## Catalogue (all 64)

Legend for **Expressibility** column: `EXPRESSIBLE` = current JokerDef algebra covers it; `NEEDS:` = lists missing building blocks.

| # | Name | Key | Cost | Exact effect (real numbers) | Trigger(s) / context | Expressibility |
|---|------|-----|------|------------------------------|----------------------|----------------|
| 1 | Joker Stencil | `j_stencil` | 8 | X1 Mult per **empty** joker slot (`card_limit - #jokers`); each Joker Stencil counts as empty (+1 per copy). | JOKER_MAIN (global xmult) | NEEDS: slot-count read (empty joker slots), self-count |
| 2 | Four Fingers | `j_four_fingers` | 7 | Flushes & Straights need only **4** cards. Meta (hand-detection). `blueprint_compat=false`. | BEFORE (hand-eval modifier) | NEEDS: hand-evaluation modifier (flush/straight size) |
| 3 | Mime | `j_mime` | 5 | Retriggers **all held-in-hand cards** abilities (`extra=1` repetition). | REPETITION_HELD, ON_HELD | EXPRESSIBLE: REPETITION_HELD + Always + REPETITIONS=Const(1) |
| 4 | Ceremonial Dagger | `j_ceremonial` | 6 | On BLIND_SELECTED: destroys joker to the right, gains **2× its sell value** as +Mult (stored `mult`, starts 0). Then adds stored Mult each hand. | BLIND_SELECTED (destroy+accumulate); JOKER_MAIN (add mult) | NEEDS: destroy joker, read sell-value, persistent mult from external value |
| 5 | Marble Joker | `j_marble` | 6 | On BLIND_SELECTED: adds **1** Stone card to deck (`extra=1`). | BLIND_SELECTED | NEEDS: modify the deck (create Stone card) |
| 6 | Loyalty Card | `j_loyalty_card` | 5 | **X4 Mult** (`Xmult=4`) every **6th** hand played (`every=5`, fires when 5 hands since last; counter "remaining"). | JOKER_MAIN, counter on hand-played | NEEDS: per-N-hands counter cycle (have ADD/RESET counter, but need cycle compare); partially EXPRESSIBLE with State counter + StateAtLeast + RESET |
| 7 | Dusk | `j_dusk` | 5 | Retriggers each **scored** card **1×** (`extra=1`) on the **final hand** of the round (`hands_left == 1`). | REPETITION_PLAYED + HandsLeft(==1) | EXPRESSIBLE: REPETITION_PLAYED + HandsLeft(cmp,1) + REPETITIONS=Const(1) |
| 8 | Fibonacci | `j_fibonacci` | 8 | +**8** Mult (`extra=8`) for each scored **Ace, 2, 3, 5, or 8** (ranks {2,3,5,8,14}). | ON_SCORED, individual | EXPRESSIBLE: ON_SCORED + ScoredRankBetween/rank-set + MULT=Const(8). NEEDS: rank-set condition (set membership, not just between) |
| 9 | Steel Joker | `j_steel_joker` | 7 | **X(1 + 0.2 × #Steel cards in full deck)** (`extra=0.2`, `steel_tally`). | JOKER_MAIN (global xmult) | NEEDS: deck-wide enhancement count (Steel) as a Value source |
| 10 | Hack | `j_hack` | 6 | Retriggers each scored **2, 3, 4, or 5** **1×** (`extra=1`). | REPETITION_PLAYED + rank-set {2,3,4,5} | EXPRESSIBLE: REPETITION_PLAYED + ScoredRankBetween(2,5) + REPETITIONS=Const(1) |
| 11 | Pareidolia | `j_pareidolia` | 5 | **All cards are considered face cards.** Meta. `blueprint_compat=false`. | BEFORE (global card-property modifier) | NEEDS: global card-property override (treat all as face) |
| 12 | Space Joker | `j_space` | 5 | **1 in 4** chance (`extra=4`) to **level up** the played poker hand. | BEFORE, probabilistic | NEEDS: level-up a hand, probabilistic/queue-driven proc |
| 13 | Burglar | `j_burglar` | 6 | On BLIND_SELECTED: **+3 Hands** (`extra=3`), **lose all discards** this round. Meta. | BLIND_SELECTED | NEEDS: modify hands/discards count (run-state mutation of hands_left/discards_left) |
| 14 | Blackboard | `j_blackboard` | 6 | **X3 Mult** (`extra=3`) if **all cards held in hand** are Spades or Clubs (empty hand qualifies). | JOKER_MAIN + held-cards all-black condition | NEEDS: held-cards-all-of-suit-set condition (HELD source + all-match) |
| 15 | Sixth Sense | `j_sixth_sense` | 6 | If you play a **single 6** as your only card and it's first hand-of-round-area, **destroy it** and create a **Spectral** card. `blueprint_compat=false`. (MP: takes from Up Top Spectral Queue.) | ON_SCORED/AFTER, single-card-6 condition | NEEDS: destroy card, create consumable (Spectral), queue-driven generation |
| 16 | Constellation | `j_constellation` | 6 | **X Mult grows +0.1** (`extra=0.1`) each time a **Planet card** is used (`Xmult` starts 1). | USE_CONSUMABLE(Planet) accumulate; JOKER_MAIN xmult | EXPRESSIBLE: USE_CONSUMABLE + ConsumableType(Planet) → ADD state(0.1); JOKER_MAIN XMULT=State(base 1, scale 0.1) |
| 17 | Hiker | `j_hiker` | 5 | Every scored card permanently gains **+5 chips** (`extra=5`, `perma_bonus`). | ON_SCORED, mutates card | NEEDS: permanent per-card chip bonus (mutate scored card's base chips) |
| 18 | Card Sharp | `j_card_sharp` | 6 | **X3 Mult** (`Xmult=3`) if this poker hand was **already played this round** (`played_this_round > 1`). | JOKER_MAIN + per-hand-type-played-this-round read | NEEDS: per-hand-type "played this round" counter read |
| 19 | Madness | `j_madness` | 7 | On BLIND_SELECTED (small/big only): **X Mult +0.5** (`extra=0.5`) and **destroys a random joker** (`Xmult` accumulates). `perishable_compat=false`. | BLIND_SELECTED accumulate; JOKER_MAIN xmult | NEEDS: destroy random joker; accumulate xmult (EXPRESSIBLE part: BLIND_SELECTED ADD state 0.5, JOKER_MAIN XMULT=State) |
| 20 | Seance | `j_seance` | 6 | If played hand is a **Straight Flush** (`poker_hand='Straight Flush'`), create a random **Spectral** card (if room). (MP: from Up Top Spectral Queue.) | AFTER + HandIs(Straight Flush) | NEEDS: create consumable (Spectral), queue-driven generation |
| 21 | Vampire | `j_vampire` | 7 | **X Mult +0.1** per scored **enhanced** card (`extra=0.1`); **removes that enhancement** (sets to base). `Xmult` starts 1, persists. `perishable_compat=false`. | ON_SCORED accumulate + remove enhancement; JOKER_MAIN xmult | NEEDS: read+remove card enhancement, persistent xmult driven by per-hand count |
| 22 | Shortcut | `j_shortcut` | 7 | Allows **Straights with gaps of 1** (e.g. 10 8 6 5 3). Meta. `blueprint_compat=false`. | BEFORE (hand-eval modifier) | NEEDS: hand-evaluation modifier (gapped straights) |
| 23 | Hologram | `j_hologram` | 7 | **X Mult +0.25** (`extra=0.25`) each time a **playing card is added** to the deck (`Xmult` starts 1). `perishable_compat=false`. | CARD_ADDED accumulate; JOKER_MAIN xmult | EXPRESSIBLE: CARD_ADDED → ADD state(0.25); JOKER_MAIN XMULT=State(base 1, scale 0.25) |
| 24 | Cloud 9 | `j_cloud_9` | 7 | End of round: **$1 per 9** (`extra=1`) in your **full deck**. `blueprint_compat=false`. | END_OF_ROUND + deck-rank-count | NEEDS: deck-wide rank count as Value at END_OF_ROUND |
| 25 | Rocket | `j_rocket` | 6 | End of round: earn **$1** (`dollars=1`); payout **increases by $2** (`increase=2`) when a **Boss Blind is defeated**. `blueprint/perishable_compat=false`. | END_OF_ROUND (dollars) + boss-defeat accumulate | NEEDS: boss-blind-defeat trigger, accumulating dollars value |
| 26 | Midas Mask | `j_midas_mask` | 7 | All **scored face cards** become **Gold** (`m_gold`) cards. `blueprint_compat=false`. | BEFORE/ON_SCORED, mutate face cards | NEEDS: mutate scored card enhancement (set Gold) |
| 27 | Luchador | `j_luchador` | 5 | When **sold**, **disables the current Boss Blind**. Meta. `eternal_compat=false`. | SELL_SELF | NEEDS: disable boss blind on sell |
| 28 | Gift Card | `j_gift` | 6 | End of round: **+$1 sell value** (`extra=1`) to **every Joker and Consumable**. `blueprint_compat=false`. | END_OF_ROUND | NEEDS: modify sell-value of all jokers/consumables |
| 29 | Turtle Bean | `j_turtle_bean` | 6 | **+5 hand size** initially (`h_size=5`), **−1 each round** (`h_mod=1`) until 0. `blueprint_compat=false`, `eternal_compat=false`. **0.3.0+ NERF (active in 0.4.0): grants +4, not +5** — applied via mod override; `game.lua` config still says 5. FLAG delta. | BLIND_SELECTED/END_OF_ROUND, hand-size mod | NEEDS: hand-size/slot change, self-decrementing buff |
| 30 | Erosion | `j_erosion` | 6 | **+4 Mult** (`extra=4`) for **each card below starting deck size** (`starting_deck_size - #playing_cards`). | JOKER_MAIN + deck-size-delta value | NEEDS: deck-size-vs-starting delta as Value source |
| 31 | To the Moon | `j_to_the_moon` | 5 | End of round: earn **extra $1 interest** (`extra=1`) per $5 held (doubles interest cap effectively, +1 per $5). `blueprint_compat=false`. | END_OF_ROUND | NEEDS: interest-rate modifier (money/5 → extra dollars) |
| 32 | Stone Joker | `j_stone` | 6 | **+25 chips** (`extra=25`) per **Stone card** in full deck (`stone_tally`). | JOKER_MAIN (global chips) | NEEDS: deck-wide enhancement count (Stone) as Value source |
| 33 | Lucky Cat | `j_lucky_cat` | 6 | **X Mult +0.25** (`extra=0.25`) each time a **Lucky card** **successfully triggers** (`lucky_trigger`). `Xmult` starts 1; `perishable_compat=false`. | ON_SCORED + lucky-trigger accumulate; JOKER_MAIN xmult | NEEDS: read Lucky-card trigger result (probabilistic proc result), accumulating xmult |
| 34 | Bull | `j_bull` | 6 | **+2 chips** (`extra=2`) per **$1** you currently have (`2 × max(0, dollars)`). | JOKER_MAIN (global chips) | EXPRESSIBLE: JOKER_MAIN + CHIPS = Const(2) × RunVar(MONEY) — needs multiply-by-runvar value form (have base+scale*n; n=MONEY). Likely EXPRESSIBLE via Value scale*RunVar |
| 35 | Diet Cola | `j_diet_cola` | 6 | When **sold**, creates a **free Double Tag**. Meta. `eternal_compat=false`. | SELL_SELF | NEEDS: create tag on sell |
| 36 | Trading Card | `j_trading` | 6 | If **first discard of round** discards a **single card**, destroy it and earn **$3** (`extra=3`). `blueprint_compat=false`. | ON_DISCARD + first-discard + single-card | NEEDS: destroy card, first-discard-of-round single-card condition + dollars |
| 37 | Flash Card | `j_flash` | 5 | **+2 Mult** (`extra=2`) per **shop reroll** (`mult` accumulates, starts 0). `perishable_compat=false`. | REROLL_SHOP accumulate; JOKER_MAIN add mult | EXPRESSIBLE: REROLL_SHOP → ADD state(2); JOKER_MAIN MULT=State(base 0, scale 2) |
| 38 | Spare Trousers | `j_trousers` | 6 | **+2 Mult** (`extra=2`) each time played hand contains a **Two Pair or Full House** (`mult` accumulates). `perishable_compat=false`. | BEFORE + HandContains(Two Pair/Full House) accumulate; JOKER_MAIN add mult | EXPRESSIBLE: BEFORE + Or(HandContains TwoPair, FullHouse) → ADD state(2); JOKER_MAIN MULT=State |
| 39 | Ramen | `j_ramen` | 6 | **X2 Mult** (`Xmult=2`) initially, **−0.01** (`extra=0.01`) per card discarded; min X1. `eternal_compat=false`. | ON_DISCARD (per card) decrement; JOKER_MAIN xmult | NEEDS: per-discarded-card xmult decrement (accumulator that subtracts per card, floor 1) |
| 40 | Seltzer | `j_selzer` | 6 | Retriggers **all scored cards 1×** for the next **10 hands** (`extra=10`); then self-destructs. **0.3.0+ NERF (active in 0.4.0): 8 hands, not 10** — `game.lua` config still says 10. FLAG delta. `eternal_compat=false`. | REPETITION_PLAYED + countdown; SELL_SELF/destroy | NEEDS: self-destruct after N hands, countdown counter (partial: REPETITION_PLAYED + REPETITIONS=Const(1) + State countdown + CARD_DESTROYED self) |
| 41 | Castle | `j_castle` | 6 | **+3 chips** (`chip_mod=3`) per **discarded card of a chosen suit** (`castle_card.suit`, re-rolled each round); `chips` accumulates from 0. `perishable_compat=false`. | ON_DISCARD + suit-match accumulate; JOKER_MAIN chips | NEEDS: round-randomized target suit, accumulating chips (rest EXPRESSIBLE) |
| 42 | Mr. Bones | `j_mr_bones` | 5 | Prevents death if **chips ≥ 25%** of required; then self-destructs. Meta. Locked (unlock: lose 5 runs). `blueprint/eternal_compat=false`. | BEFORE/END_OF_ROUND (death prevention) | NEEDS: death-prevention hook, self-destruct |
| 43 | Acrobat | `j_acrobat` | 6 | **X3 Mult** (`extra=3`) on the **final hand** of the round (`hands_left == 1`). Locked (200 hands played). | JOKER_MAIN + HandsLeft(==1) | EXPRESSIBLE: JOKER_MAIN + HandsLeft(cmp,1) + XMULT=Const(3) |
| 44 | Sock and Buskin | `j_sock_and_buskin` | 6 | Retriggers all scored **face cards 1×** (`extra=1`). Locked (300 face cards played). | REPETITION_PLAYED + ScoringAnyFace/ScoredIsFace | EXPRESSIBLE: REPETITION_PLAYED + ScoredIsFace + REPETITIONS=Const(1) |
| 45 | Troubadour | `j_troubadour` | 6 | **+2 hand size** (`h_size=2`), **−1 hand per round** (`h_plays=-1`). Meta. Locked (win 5 rounds). `blueprint_compat=false`. | BLIND_SELECTED (static modifiers) | NEEDS: hand-size change + hands-per-round change (static run modifiers) |
| 46 | Certificate | `j_certificate` | 6 | On BLIND_SELECTED: add a **random playing card with a random Seal** to hand. Locked (gold-seal/double-gold). | BLIND_SELECTED | NEEDS: create card with seal, add to hand |
| 47 | Smeared Joker | `j_smeared` | 7 | **Hearts↔Diamonds and Spades↔Clubs** count as the same suit. Meta. `blueprint_compat=false`. Locked. | BEFORE (suit-equivalence modifier) | NEEDS: suit-equivalence override for hand-eval & suit conditions |
| 48 | Throwback | `j_throwback` | 6 | **X Mult +0.25** (`extra=0.25`) per **Blind skipped** this run (`Xmult` starts 1). Locked (continue a saved run). | SKIP_BLIND accumulate; JOKER_MAIN xmult | EXPRESSIBLE: SKIP_BLIND → ADD state(0.25); JOKER_MAIN XMULT=State(base 1, scale 0.25) |
| 49 | Rough Gem | `j_rough_gem` | 7 | Each scored **Diamond** earns **$1** (`extra=1`). Locked (30 Diamonds in deck). | ON_SCORED + ScoredSuit(Diamonds) | EXPRESSIBLE: ON_SCORED + ScoredSuit(Diamonds) + DOLLARS=Const(1) |
| 50 | Bloodstone | `j_bloodstone` | 7 | **1 in 2** (`odds=2`) chance per scored **Heart** for **X1.5 Mult** (`Xmult=1.5`). Locked (30 Hearts). MP: dual-queue (game-long + per-ante PvP queue that resets each hand). | ON_SCORED + ScoredSuit(Hearts) + probabilistic | NEEDS: probabilistic/queue-driven proc (per-suit, per-ante PvP queue) |
| 51 | Arrowhead | `j_arrowhead` | 7 | Each scored **Spade** gives **+50 chips** (`extra=50`). Locked (30 Spades). | ON_SCORED + ScoredSuit(Spades) | EXPRESSIBLE: ON_SCORED + ScoredSuit(Spades) + CHIPS=Const(50) |
| 52 | Onyx Agate | `j_onyx_agate` | 7 | Each scored **Club** gives **+7 Mult** (`extra=7`). Locked (30 Clubs). | ON_SCORED + ScoredSuit(Clubs) | EXPRESSIBLE: ON_SCORED + ScoredSuit(Clubs) + MULT=Const(7) |
| 53 | Glass Joker | `j_glass` | 6 | **X Mult +0.75** (`extra=0.75`) per **Glass card destroyed** (`Xmult` starts 1). Locked (5 Glass in deck). `perishable_compat=false`. | CARD_DESTROYED(Glass) accumulate; JOKER_MAIN xmult | EXPRESSIBLE-ish: CARD_DESTROYED + ScoredEnhancement(Glass)?→ need "destroyed-card-was-Glass" condition; ADD state(0.75); JOKER_MAIN XMULT=State. NEEDS: destroyed-card enhancement condition |
| 54 | Showman | `j_ring_master` | 5 | Allows **duplicate** Jokers/Tarot/Planet/Spectral in shop & packs. Meta. (internal key is `j_ring_master`.) `blueprint_compat=false`. Locked (ante 4). | SHOP_EXIT/shop-gen modifier | NEEDS: shop-generation modifier (allow duplicates) |
| 55 | Flower Pot | `j_flower_pot` | 6 | **X3 Mult** (`extra=3`) if scoring hand contains a card of **all 4 suits** (Wild cards fill gaps). Locked (ante 8). | JOKER_MAIN + 4-suit-presence condition | NEEDS: "scoring hand contains all 4 suits" condition (with Wild handling) |
| 56 | Merry Andy | `j_merry_andy` | 7 | **+3 discards** (`d_size=3`), **−1 hand size** (`h_size=-1`). Meta. `blueprint_compat=false`. Locked (win in ≤12 rounds). | BLIND_SELECTED (static modifiers) | NEEDS: discards-count + hand-size static modifiers |
| 57 | Oops! All 6s | `j_oops` | 4 | **Doubles all listed probabilities** (e.g. 1-in-4 → 1-in-2). Meta. `blueprint_compat=false`. Locked (10000 chip score). MP: halves the roll threshold. | BEFORE (probability modifier) | NEEDS: global probability-odds modifier |
| 58 | The Idol | `j_idol` | 6 | **X2 Mult** (`extra=2`) per scored card matching a **specific rank+suit** (`idol_card`, re-rolled each round). Locked (1,000,000 chips). MP: deterministic sort-based selection (see 05_Jokers.txt). | ON_SCORED + rank&suit match | NEEDS: round-randomized target rank+suit (deterministic MP queue), then EXPRESSIBLE op (XMULT=Const(2)) |
| 59 | Seeing Double | `j_seeing_double` | 6 | **X2 Mult** (`extra=2`) if scoring hand has a **Club AND a card of any other suit** (Wilds fill, Clubs prioritized). Locked (four 7♣ in one hand). | JOKER_MAIN + suit-composition condition | NEEDS: "has Club + other suit" composition condition (Wild handling) |
| 60 | Matador | `j_matador` | 7 | Earn **$8** (`extra=8`) if played hand **triggers the Boss Blind's ability**. Locked (defeat a boss with it). | JOKER_MAIN + debuffed_hand/blind-triggered context | NEEDS: read "boss blind ability triggered this hand" + dollars |
| 61 | Satellite | `j_satellite` | 6 | End of round: earn **$1** (`extra=1`) per **unique Planet card used** this run. `blueprint_compat=false`. Locked ($400 held). | END_OF_ROUND + unique-planet-count | NEEDS: count of unique Planet types used (run-wide set tracker) |
| 62 | Cartomancer | `j_cartomancer` | 6 | On BLIND_SELECTED: create a random **Tarot** card (if room). Locked (discover 22 Tarots). | BLIND_SELECTED | NEEDS: create consumable (Tarot), queue-driven generation |
| 63 | Astronomer | `j_astronomer` | 8 | All **Planet cards & Celestial Packs in shop are free**. Meta. `blueprint_compat=false`. Locked (discover 12 Planets). | SHOP/shop-pricing modifier | NEEDS: shop-pricing modifier (free Planets/Celestial) |
| 64 | Bootstraps | `j_bootstraps` | 7 | **+2 Mult** (`mult=2`) for **every $5** you have (`floor(dollars/5) × 2`, `dollars=5`). Locked (2 Polychrome jokers owned). | JOKER_MAIN + money-stepped value | NEEDS: stepped value `floor(MONEY/5)` as Value source (have RunVar(MONEY) but not floor-division step) |

---

## Cross-cutting expressibility notes against our algebra

**Fully EXPRESSIBLE now (12):** Mime (3), Dusk (7), Hack (10), Hologram (23), Acrobat (43), Sock and Buskin (44), Rough Gem (49), Arrowhead (51), Onyx Agate (52), Throwback (48), Flash Card (37), Spare Trousers (38). Constellation (16) is expressible if `USE_CONSUMABLE + ConsumableType(Planet)` drives an ADD-state + State-scaled XMULT. Bull (34) and Bootstraps (64) are expressible only if `Value` supports `scale × RunVar(MONEY)` (Bull) and floored steps (Bootstraps).

**Accumulator-style (ADD state on a trigger, then State value):** Constellation, Hologram, Flash Card, Spare Trousers, Throwback, Castle, Glass, Vampire, Madness, Loyalty(cycle). Our `State`/`ADD/SET/RESET` algebra covers the accumulate→read pattern; the gaps are the *condition that detects the increment* (e.g. "Glass card was destroyed", "Planet used") and a few need decrement/floor.

**Probabilistic / queue-driven procs (MP-deterministic queues):** Space (12), Sixth Sense (15), Bloodstone (50), Lucky Cat (33), Idol (58). These are the biggest engine gap — MP uses shared deterministic queues (Up Top Tarot/Spectral queues; Bloodstone's per-ante PvP queue; Idol's sort-based deterministic pick) per `05_Jokers.txt`.

**Card/deck/consumable creation & mutation:** Marble (deck add), Sixth Sense/Seance/Cartomancer (create consumable), Certificate (create sealed card), Hiker/Midas Mask/Vampire (mutate scored card), Trading/Sixth Sense (destroy card), DNA-class copying (not in this set).

**Run-state / slot mutations:** Burglar (hands/discards), Turtle Bean/Troubadour/Merry Andy (hand size & hands/discards), Gift Card (sell-value), Joker Stencil (empty-slot count).

**Boss/blind interactions (PvP-relevant):** Luchador (disable boss on sell), Matador (boss-trigger read), Rocket (boss-defeat payout), Mr. Bones (death prevention).

**Hand-evaluation modifiers (meta):** Four Fingers, Shortcut, Smeared, Pareidolia, Oops! All 6s, Showman, Astronomer — these alter global rules, not per-hand scoring lines.

---

## Open questions

1. **Turtle Bean / Seltzer numeric source of truth in 0.4.0.** `game.lua` config literally reads `h_size = 5` and Seltzer `extra = 10`, but the 0.3.0 changelog (carried into 0.4.0) says +4 and 8 hands. Need to confirm the mod override file in `multiplayer-0.4.0/objects/` or `overrides/` applies the nerf for ranked (not yet read). Which value does the server enforce?
2. **Bloodstone PvP queue** is documented in the 0.3.3 spreadsheet (per-ante queue, resets per hand). Does 0.4.0 keep identical mechanics, or did the queue model change with the new `rulesets/`/`gamemodes/` structure? Not verified against 0.4.0 source.
3. **Idol determinism** (deck-sort-based pick) — confirm 0.4.0 still uses the 0.3.3 sort algorithm vs. vanilla per-round random `idol_card`.
4. **Madness / Ceremonial Dagger joker destruction in PvP** — does destroying a joker sync to the nemesis view, and do they read our `ON_OTHER_JOKER`/destroy hooks?
5. **Loyalty Card cycle** — exact "every 6th hand" boundary (`every=5`, fires at 6th). Confirm our counter-cycle compare matches `(every-1 - diff) % (every+1) == every`.
6. **Joker Stencil self-counting** — does each *additional* Joker Stencil add +1 to the empty-slot tally (per `simulate_stencil`)? Confirm with multiple copies.

## New building blocks needed

Grouped, in rough priority for covering the Uncommon set:

1. **Probabilistic / queue-driven proc** (shared deterministic MP queue): odds `1 in N`, with Oops! All-6s odds-halving. Needed by Space, Sixth Sense, Bloodstone, Lucky Cat, Idol.
2. **Create consumable** (Tarot/Spectral/Planet) from a queue: Cartomancer, Seance, Sixth Sense.
3. **Create / add card to deck or hand** (Stone via Marble; sealed random card via Certificate).
4. **Destroy card** (Trading Card, Sixth Sense) and **destroy joker** (Ceremonial Dagger, Madness).
5. **Mutate scored card** — set enhancement (Midas Mask→Gold), remove enhancement (Vampire), permanent chip bonus (Hiker `perma_bonus`).
6. **Deck-wide aggregate Value sources**: enhancement count (Steel→Steel Joker, Stone→Stone Joker), rank count (9s→Cloud 9), starting-deck-size delta (Erosion).
7. **Per-hand-type-level / per-hand-type-played reads**: Card Sharp (`played_this_round`), Space/level-up-a-hand.
8. **Level up a poker hand** (Space Joker).
9. **Run-state mutations**: hand size (Turtle Bean, Troubadour, Merry Andy), hands-per-round (Troubadour, Burglar), discards (Burglar, Merry Andy), empty-joker-slot read (Joker Stencil).
10. **Sell-value reads/writes**: Gift Card (+sell value to all), Ceremonial Dagger (2× right joker's sell value).
11. **Sell-self side effects**: Diet Cola (create Double Tag), Luchador (disable boss blind).
12. **Hand-evaluation rule modifiers** (meta/global): Four Fingers (4-card flush/straight), Shortcut (gapped straights), Smeared (suit equivalence), Pareidolia (all cards face), Showman (allow duplicates), Oops! All 6s (odds), Astronomer (free Planet pricing).
13. **Composite suit-presence conditions**: all-4-suits (Flower Pot), Club+other-suit (Seeing Double), all-held-cards-black (Blackboard), with Wild-card resolution.
14. **Round-randomized targets**: target suit (Castle), target rank+suit (Idol) — per-round re-roll synced across players.
15. **Boss-blind hooks (PvP)**: boss-defeated trigger (Rocket payout), boss-ability-triggered read (Matador), disable-boss (Luchador), death-prevention (Mr. Bones).
16. **Accumulator refinements**: decrement-per-card with floor (Ramen X−0.01/card min 1; Castle/Glass/Vampire need a per-event ADD where the *event-detection condition* is the new piece), self-destruct-after-N-hands countdown (Seltzer), per-blind-skipped (Throwback — covered if SKIP_BLIND fires).
17. **Stepped/derived money Values**: `floor(MONEY/5)` (Bootstraps), `scale × MONEY` (Bull), interest-rate modifier (To the Moon), unique-Planet-count (Satellite).
18. **CARD_ADDED / CARD_DESTROYED condition payloads**: need the added/destroyed card's properties (enhancement = Glass for Glass Joker; any playing card for Hologram).
