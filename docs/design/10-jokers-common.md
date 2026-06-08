# Common Jokers — Vanilla Catalogue (Balatro Multiplayer 0.4.0)

Scope: every Common-rarity (`rarity = 1`) vanilla joker in the base game. The base-game
dump (`C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua`, lines 377–525)
contains **exactly 61** entries with `rarity = 1` (verified: `grep -c "rarity = 1," game.lua` = 61).
All 61 are catalogued below.

Effect numbers come from the base-game centers table (`game.lua`). Exact runtime behavior
is cross-checked against Divvy's simulation
(`.../multiplayer-0.4.0/compatibility/Preview/Jokers/_Vanilla.lua`), which is the most
faithful structured source for how each joker actually scores.

## CRITICAL 0.4.0 grounding finding — most "reworks" are NOT active

The file `multiplayer-0.4.0/rulesets/release.lua` contains a long list of `MP.ReworkCenter`
calls (greedy s_mult=4, banner=40, odd_todd=30, runner 20/10, square 16/4, smiley=4,
ticket=3, mad/clever → Four of a Kind, etc.). **The entire body of that file (lines 2–798)
is wrapped in a `--[[ … ]]` Lua block comment** (open at line 2, close `]]` at line 798).
Those reworks therefore **do not execute** and are an archived/disabled "1.0.0 release
revert" layer. Do **not** treat release.lua numbers as the live 0.4.0 ranked values.

What actually loads in **Standard Ranked** (`rulesets/ranked.lua` → layers `{ "standard",
"ranked" }`; `layers/ranked.lua` only forces lobby options). The `standard` layer
(`layers/standard.lua`) does only this to Common jokers:

| Vanilla key (banned in standard) | Replaced by MP key | Net effect in ranked |
| --- | --- | --- |
| `j_hanging_chad` | `j_mp_hanging_chad` | Retrigger **scoring cards #1 and #2 once each** (extra=1) instead of vanilla "retrigger first card +2". |
| `j_ticket` (Golden Ticket) | `j_mp_ticket` | **Uncommon (rarity 2)**, cost **6**, **$3** per scored Gold card. |

`j_selzer`, `j_turtle_bean`, `j_bloodstone` are also banned/replaced but are not Common.
Everything else keeps **base-game default values** (the release.lua tweaks are inert).

### 0.4.0-vs-0.3.3 deltas (CHANGELOG.md) affecting Common jokers — with on-disk reality check
- **Golden Ticket** — CHANGELOG: "Reverted payout nerf, now earns $4 (up from $3)." On disk
  the loaded standard `j_mp_ticket` pays **$3** (`config.extra.dollars = 3`); the $4 variant
  is `j_mp_ticket_experimental` (experimental layer only). **Flag: CHANGELOG narrative ($4)
  does not match the loaded standard-layer object ($3).** 0.3.0 doc also moved Golden Ticket
  to Uncommon and dropped the Gold-cards-in-deck unlock gate — the loaded MP object confirms
  rarity 2 and no deck gate.
- **To Do List** — CHANGELOG: reworked to **$5**, hand chosen from all hands. On disk the
  rework (`j_mp_todo_list`, `loc_key j_mp_todo_list_release`, dollars=5) lives only in the
  **experimental** layer; **standard ranked keeps base-game $4 / High Card seed.** Flag.
- **Hanging Chad** — CHANGELOG: "Retriggers first 2 cards instead of first card twice."
  Confirmed by `objects/jokers/standard/hanging_chad.lua` (`j_mp_hanging_chad`, extra=1,
  retriggers `scoring_hand[1]` and `[2]`). This **is** active in standard.
- **Speedrun** — out of rotation (not a Common joker; no row needed).
- No other Common joker changed between 0.3.3 and 0.4.0 in the standard ruleset.

---

## Catalogue

Legend for "Expressible vs our algebra":
- Trigger / Condition / Value / Op refer to the `com.balatromp.engine.joker` enums in the task brief.
- "SCORING" = `Count(source=SCORING, …)`; "PLAYED"/"HELD" likewise.
- Where a base-game `config.extra` is an X-mult, it is stored as a plain number but applied as XMULT.

### A. Suit Mult (4 jokers) — ON_SCORED individual, +Mult per matching suit

| Name | Key | Cost | Exact effect (base game) | Trigger / sim |
| --- | --- | --- | --- | --- |
| Greedy Joker | `j_greedy_joker` | 5 | +3 Mult per scored **Diamond** (`extra.s_mult=3`) | ON_SCORED, suit match; `add_suit_mult` |
| Lusty Joker | `j_lusty_joker` | 5 | +3 Mult per scored **Heart** | ON_SCORED |
| Wrathful Joker | `j_wrathful_joker` | 5 | +3 Mult per scored **Spade** | ON_SCORED |
| Gluttonous Joker | `j_gluttenous_joker` (note misspelling) | 5 | +3 Mult per scored **Club** | ON_SCORED |

Expressible: **EXPRESSIBLE.** Trigger ON_SCORED, Condition `ScoredSuit(X)`, Value `Const(3)`, Op MULT.
(release.lua would raise s_mult to 4 but is commented out → stays 3.)

### B. Type Mult (5 jokers) — JOKER_MAIN, +Mult if played hand is type

| Name | Key | Cost | Exact effect | Trigger |
| --- | --- | --- | --- | --- |
| Jolly Joker | `j_jolly` | 3 | +8 Mult if hand contains a **Pair** (`t_mult=8`) | JOKER_MAIN, `add_type_mult` |
| Zany Joker | `j_zany` | 4 | +12 Mult if **Three of a Kind** | JOKER_MAIN |
| Mad Joker | `j_mad` | 4 | +10 Mult if **Two Pair** | JOKER_MAIN |
| Crazy Joker | `j_crazy` | 4 | +12 Mult if **Straight** | JOKER_MAIN |
| Droll Joker | `j_droll` | 4 | +10 Mult if **Flush** | JOKER_MAIN |

Expressible: **EXPRESSIBLE.** Trigger JOKER_MAIN, Condition `HandIs(type)` (or `HandContainsPair`
for Jolly — note vanilla "contains" semantics, not "is exactly"), Value `Const`, Op MULT.

### C. Type Chips (5 jokers) — JOKER_MAIN, +Chips if played hand is type

| Name | Key | Cost | Exact effect | Trigger |
| --- | --- | --- | --- | --- |
| Sly Joker | `j_sly` | 3 | +50 Chips if **Pair** (`t_chips=50`) | JOKER_MAIN, `add_type_chips` |
| Wily Joker | `j_wily` | 4 | +100 Chips if **Three of a Kind** | JOKER_MAIN |
| Clever Joker | `j_clever` | 4 | +80 Chips if **Two Pair** | JOKER_MAIN |
| Devious Joker | `j_devious` | 4 | +100 Chips if **Straight** | JOKER_MAIN |
| Crafty Joker | `j_crafty` | 4 | +80 Chips if **Flush** | JOKER_MAIN |

Expressible: **EXPRESSIBLE.** Trigger JOKER_MAIN, Condition `HandIs/HandContainsPair`, Value `Const`, Op CHIPS.

### D. Conditional flat scoring (state-free)

| Name | Key | Cost | Exact effect | Trigger / notes |
| --- | --- | --- | --- | --- |
| Joker | `j_joker` | 2 | +4 Mult (always) | JOKER_MAIN. EXPRESSIBLE: Always / Const(4) / MULT. |
| Half Joker | `j_half` | 5 | +20 Mult if played hand has **≤ 3 cards** (`extra.mult=20, size=3`) | JOKER_MAIN. EXPRESSIBLE: `PlayedCount(<=,3)` / Const(20) / MULT. |
| Mystic Summit | `j_mystic_summit` | 5 | +15 Mult while **0 discards remaining** (`extra.mult=15, d_remaining=0`) | JOKER_MAIN. EXPRESSIBLE: `DiscardsLeft`==0 / Const(15) / MULT. |
| Banner | `j_banner` | 5 | **+30 Chips per discard remaining** (`extra=30`) | JOKER_MAIN. EXPRESSIBLE: Value `RunVar(DISCARDS_LEFT)*30` / CHIPS. release.lua →40 inert. |
| Scary Face | `j_scary_face` | 4 | +30 Chips per scored **face** card (`extra=30`) | ON_SCORED. EXPRESSIBLE: `ScoredIsFace` / Const(30) / CHIPS. |
| Even Steven | `j_even_steven` | 4 | +4 Mult per scored **even** card (2,4,6,8,10) (`extra=4`) | ON_SCORED. EXPRESSIBLE: `ScoredParity(even)` / Const(4) / MULT. |
| Odd Todd | `j_odd_todd` | 4 | +31 Chips per scored **odd** card (A,3,5,7,9) (`extra=31`) | ON_SCORED. EXPRESSIBLE: `ScoredParity(odd)` / Const(31) / CHIPS. release.lua →30 inert. |
| Scholar | `j_scholar` | 4 | per scored **Ace**: +20 Chips and +4 Mult (`extra.chips=20, mult=4`) | ON_SCORED. EXPRESSIBLE: `ScoredRankBetween(A,A)` with two effect ops CHIPS+MULT. |
| Walkie Talkie | `j_walkie_talkie` | 4 | per scored **10 or 4**: +10 Chips and +4 Mult (`extra.chips=10, mult=4`) | ON_SCORED. EXPRESSIBLE: `Or(rank==10, rank==4)` / CHIPS+MULT. Needs rank-set; expressible via Or of two `ScoredRankBetween`. |
| Smiley Face | `j_smiley` | 4 | +5 Mult per scored **face** card (`extra=5`) | ON_SCORED. EXPRESSIBLE: `ScoredIsFace` / Const(5) / MULT. release.lua →4 inert. |
| Shoot the Moon | `j_shoot_the_moon` | 5 | +13 Mult per **Queen held in hand** (`extra=13`) | ON_HELD individual (rank 12). EXPRESSIBLE: ON_HELD / `ScoredRankBetween(Q,Q)` on HELD source / Const(13) / HELD_MULT (it adds Mult, not held-mult tag; treat as MULT contribution from held). Unlock: play hand of all Hearts. |

### E. Held-in-hand effects (ON_HELD / repetition)

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Raised Fist | `j_raised_fist` | 5 | Adds **2× the rank (base_chips) of the lowest-ranked card held in hand** as Mult. Ignores Stone cards; ties pick the rightmost lowest. | ON_HELD/global; `add_mult(2*cur_mult)`. **NEEDS:** read-min-over-held-cards aggregate (not a per-card Count). |

### F. X-Mult jokers

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Photograph | `j_photograph` | 5 | **X2 Mult** off the **first scored face card** (`extra=2`, applied as x_mult) | ON_SCORED individual, first-face only. **NEEDS:** "first matching scored card" selector (current ScoredIsFace fires per-face). Op XMULT exists. |
| Gros Michel | `j_gros_michel` | 5 | **+15 Mult** (`extra.mult=15`); each round-end **1-in-6** chance to be destroyed and set `gros_michel_extinct` (enables Cavendish). | JOKER_MAIN +Mult is EXPRESSIBLE (Const(15)/MULT). **NEEDS:** probabilistic self-destroy + global extinction flag. |
| Cavendish | `j_cavendish` | 4 | **X3 Mult** (`extra.Xmult=3`); 1-in-1000 self-destroy each round; only appears after Gros Michel extinct (`yes_pool_flag`). | JOKER_MAIN XMULT EXPRESSIBLE (Const(3)/XMULT). **NEEDS:** probabilistic self-destroy + pool gating flag. |

### G. Accumulating-state scoring jokers (per-joker counter)

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Ride the Bus | `j_ride_the_bus` | 6 | +1 Mult (`extra=1`) per **consecutive hand scored with NO face card**; **resets to 0** the moment a scored hand contains any face card. | BEFORE: if any scored face → mult=0 else mult+=1; JOKER_MAIN applies mult. EXPRESSIBLE: state counter with ADD on BEFORE+`Not(ScoringAnyFace)`, RESET on BEFORE+`ScoringAnyFace`; Value `State(mult)`/MULT. |
| Green Joker | `j_green_joker` | 4 | +1 Mult per hand **played**, −1 Mult per **discard** (`hand_add=1, discard_sub=1`), floored at 0. | BEFORE: mult+=1; ON_DISCARD (first discarded card): mult=max(0,mult−1); JOKER_MAIN applies. EXPRESSIBLE: state ADD on BEFORE, SUB on PRE_DISCARD/ON_DISCARD with floor. **Needs SUB-with-floor** (Mutation ADD of −1 + clamp). |
| Runner | `j_runner` | 5 | Starts +0 Chips; **+15 Chips permanently each time a Straight is played** (`chips=0, chip_mod=15`). | BEFORE: if Straight in poker_hands → chips+=15; JOKER_MAIN applies chips. EXPRESSIBLE: state ADD on BEFORE+`HandIs(Straight)` (contains), Value `State(chips)`/CHIPS. release.lua 20/10 inert. |
| Square Joker | `j_square` | 4 | **+4 Chips permanently each time a hand of exactly 4 cards is played** (`chips=0, chip_mod=4`). | BEFORE: if `#full_hand==4` → chips+=4; JOKER_MAIN applies. EXPRESSIBLE: ADD on BEFORE+`PlayedCount(==,4)`, `State(chips)`/CHIPS. release.lua 16/4 inert. |
| Ice Cream | `j_ice_cream` | 5 | **+100 Chips**, **−5 Chips after each hand played** (`chips=100, chip_mod=5`); self-destroys at 0. | JOKER_MAIN applies chips; decrement on hand played. EXPRESSIBLE for the chip value via state SUB on BEFORE; **NEEDS:** self-destroy-at-zero. |
| Popcorn | `j_popcorn` | 5 | **+20 Mult**, **−4 Mult per round** (`mult=20, extra=4`); self-destroys at 0. | JOKER_MAIN applies mult; END_OF_ROUND decrements. EXPRESSIBLE value via state SUB on END_OF_ROUND; **NEEDS:** self-destroy-at-zero. |

### H. Hand-state / count reads

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Abstract Joker | `j_abstract` | 4 | +3 Mult per **Joker owned** (`extra=3`) | JOKER_MAIN. **NEEDS:** count-own-jokers value source (not in current Value enum). |
| Supernova | `j_supernova` | 5 | +Mult equal to **number of times this poker hand has been played this run** (`extra=1`, uses `G.GAME.hands[name].played`) | JOKER_MAIN. **NEEDS:** per-hand-type play-count read. |
| Blue Joker | `j_blue_joker` | 5 | **+2 Chips per card remaining in the deck** (`extra=2`, `#G.deck.cards`) | JOKER_MAIN. **NEEDS:** deck-size read value source. |
| Misprint | `j_misprint` | 4 | **+random Mult, 0–23** each scoring (`extra.min=0, max=23`) | JOKER_MAIN. **NEEDS:** RNG/random-value source. |
| Fortune Teller | `j_fortune_teller` | 6 | +1 Mult per **Tarot card used this run** (`extra=1`, `consumeable_usage_total.tarot`) | JOKER_MAIN. **NEEDS:** lifetime tarot-usage counter (a run stat). |

### I. Economy jokers (DOLLARS / meta)

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Golden Joker | `j_golden` | 6 | **+$4 at end of round** (`extra=4`) | END_OF_ROUND. EXPRESSIBLE: END_OF_ROUND / Always / Const(4) / DOLLARS. |
| Delayed Gratification | `j_delayed_grat` | 4 | **+$2 per discard** at end of round **if no discards were used** that round (`extra=2`) | END_OF_ROUND. EXPRESSIBLE: END_OF_ROUND + Condition `DiscardsLeft == max` / Value `RunVar(DISCARDS_LEFT)*2` / DOLLARS. (Needs "discards unused this round" = discards_left==starting; expressible via DiscardsLeft compare.) |
| Business Card | `j_business` | 4 | Each scored **face** card has **1-in-2** chance to give **+$2** (`extra=2`, odds 2) | ON_SCORED. **NEEDS:** probabilistic proc. EXPRESSIBLE skeleton: ON_SCORED/`ScoredIsFace`/DOLLARS, but the 50% gate needs RNG. |
| Faceless Joker | `j_faceless` | 4 | **+$5 if 3+ face cards are discarded at once** (`extra.dollars=5, faces=3`) | ON_DISCARD / PRE_DISCARD. EXPRESSIBLE: PRE_DISCARD + `DiscardedFaceCount >= 3` / Const(5) / DOLLARS. |
| Mail-In Rebate | `j_mail` | 4 | **+$5 per discarded card of the round's rank** (`extra=5`; rank rerolls each round) | ON_DISCARD. **NEEDS:** "round-selected rank" state (a per-round random rank the joker tracks). release.lua →3 inert. |
| Reserved Parking | `j_reserved_parking` | 6 | Each **face card held in hand** has **1-in-2** chance to give **+$1** (`extra.odds=2, dollars=1`) | ON_HELD. **NEEDS:** probabilistic proc on held cards. release.lua would make it Uncommon — inert. |
| To Do List | `j_todo_list` | 4 | **+$4 if the played hand matches the listed poker hand** (`extra.dollars=4, poker_hand='High Card'`); hand reseeds each round. | BEFORE. **NEEDS:** per-round random target-hand state. EXPRESSIBLE shell: BEFORE + `HandIs(stateHand)` / Const(4) / DOLLARS, given a state-stored hand type. (Experimental layer = $5 + all-hands seed; standard stays $4.) |
| Golden Ticket | `j_ticket` → **`j_mp_ticket`** in ranked | base 5 / MP **6** | **+$3 per scored Gold-enhanced card** (MP `extra.dollars=3`; **Uncommon** in MP). Vanilla base: +$4 per Gold card, Common, cost 5, unlock-gated. | ON_SCORED + `ScoredEnhancement(Gold)` / Const(3) / DOLLARS. EXPRESSIBLE. **0.4.0:** banned vanilla, replaced by Uncommon MP version (see top). CHANGELOG claims $4 but standard object = $3. |

### J. Discard / draw / generation jokers (mostly meta)

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Credit Card | `j_credit_card` | 1 | Allows going **−$20** in debt (`extra=20`) | Meta (no scoring). **NEEDS:** debt-floor / money-rules modifier. |
| Chaos the Clown | `j_chaos` | 4 | **1 free shop reroll per shop** (`extra=1`) | REROLL_SHOP / SHOP. **NEEDS:** free-reroll grant. |
| 8 Ball | `j_8_ball` | 5 | Each scored **8** has **1-in-4** chance to create a **Tarot** (if room) (`extra=4` = odds) | ON_SCORED. **NEEDS:** create-consumable + probabilistic proc. (release.lua would make odds 1-in-2; inert.) |
| Riff-raff | `j_riff_raff` | 6 | At **blind start**, create **2 Common Jokers** (if room) (`extra=2`) | BLIND_SELECTED. **NEEDS:** create-joker. release.lua cost→4 inert. |
| Hallucination | `j_hallucination` | 4 | On **opening any Booster Pack**, **1-in-2** chance to create a **Tarot** (if room) (`extra=2` = odds) | OPEN_BOOSTER. **NEEDS:** create-consumable + probabilistic proc. |
| Egg | `j_egg` | 4 | Gains **+$3 sell value at end of each round** (`extra=3`) | END_OF_ROUND. **NEEDS:** sell-value mutation. |
| Red Card | `j_red_card` | 5 | **+3 Mult permanently each time a Booster Pack is skipped** (`extra=3`) | SKIP_BLIND? No — fires on skipping a booster pack. **NEEDS:** "booster skipped" trigger + state ADD. Mult value EXPRESSIBLE once trigger exists. |
| Juggler | `j_juggler` | 4 | **+1 hand size** (`h_size=1`) | Meta/passive. **NEEDS:** hand-size modifier. |
| Drunkard | `j_drunkard` | 4 | **+1 discard each round** (`d_size=1`) | Meta/passive. **NEEDS:** discards-per-round modifier. |
| Splash | `j_splash` | 3 | **Every played card counts in scoring** (config `{}`) | Meta. **NEEDS:** scoring-eligibility override (all cards score). |

### K. Retrigger / mp-specific

| Name | Key | Cost | Exact effect | Trigger / sim |
| --- | --- | --- | --- | --- |
| Hanging Chad | `j_hanging_chad` → **`j_mp_hanging_chad`** in ranked | 4 | **MP/0.4.0:** retrigger **scoring card #1 and #2 once each** (extra=1). **Vanilla base:** retrigger the **first scored card 2 extra times** (`extra=2`). | REPETITION_PLAYED. **NEEDS:** "first/second scored card" positional selector + REPETITIONS op (REPETITIONS exists; positional selection does not). Unlock (vanilla): win a round with High Card as last hand. |
| Swashbuckler | `j_swashbuckler` | 4 | **+Mult equal to combined sell value of all your OTHER jokers** (`mult=1` base; recomputed each frame from sum of other jokers' sell_cost) | JOKER_MAIN. **NEEDS:** read other jokers' sell values (aggregate over own jokers). Unlock: sell 20 jokers. |

---

## Quick expressibility tally

- **Fully EXPRESSIBLE today** (trigger+condition+value+op already in the algebra): Joker, all 4 Suit Mult, all 5 Type Mult, all 5 Type Chips, Half, Mystic Summit, Banner, Scary Face, Even Steven, Odd Todd, Scholar, Walkie Talkie, Smiley, Shoot the Moon, Ride the Bus, Runner, Square, Golden Joker, Delayed Gratification, Faceless, Golden Ticket (`j_mp_ticket`), Gros Michel (+Mult part), Cavendish (XMult part), To Do List (given a per-round target-hand state). ≈ **35**.
- **Need new building blocks**: Raised Fist, Photograph, Misprint, Abstract, Supernova, Blue Joker, Fortune Teller, Business Card, Mail-In Rebate, Reserved Parking, 8 Ball, Riff-raff, Hallucination, Egg, Red Card, Juggler, Drunkard, Splash, Credit Card, Chaos the Clown, Ice Cream (destroy), Popcorn (destroy), Green Joker (floored SUB), Hanging Chad (positional), Swashbuckler. ≈ **26**.

## Open questions
1. **Which ruleset is the deployment target?** This catalogue assumes Standard Ranked (`ranked.lua` → standard+ranked layers). If the server ships the *experimental* layer, To Do List ($5/all-hands) and `j_ticket_experimental` ($4) apply instead. Confirm against `D:/BalatroMultiplayerAPI-Server-main/` gamemode/protocol defaults.
2. **Golden Ticket payout discrepancy:** CHANGELOG says $4, the loaded standard `j_mp_ticket` object says $3 ($4 only in `ticket_experimental`). Which is authoritative for our engine? (Recommend matching the loaded standard object: $3, Uncommon, cost 6.)
3. **Are the release.lua reworks ever activated** by any live ruleset via a non-commented path? I confirmed the file body is block-commented and the standard/ranked layers don't pull those numbers, but a re-verify against `core.lua` load order would close this. (Unverified that nothing else re-applies them.)
4. **Hanging Chad / Golden Ticket as Common in our pool:** In ranked the Common `j_hanging_chad` and `j_ticket` are *banned* and replaced — should our Common pool list the vanilla keys, the MP keys, or both? Note `j_mp_ticket` is **Uncommon**, so it leaves the Common pool entirely.
5. **Business Card / 8 Ball / Reserved Parking / Hallucination odds:** base-game odds confirmed (2, 4, 2, 2 = "1 in N"). Need to confirm whether MP applies any global odds modifier (e.g., Oops All 6s) in the scoring pipeline before we hardcode probabilities.
6. **Raised Fist tie-break and Stone interaction** are taken from the sim (lowest rank, ignores Stone, rightmost on tie). Verify against `evaluate_play` in `game.lua` for edge cases (debuffed/face-down held cards).
7. **Misprint min/max** is 0–23 in base game; some balance docs cite 0–23 vs 1–23. The dump says `min=0, max=23`. Treat as 0–23.

## New building blocks needed
Grouped by the engine extension they require (mark on each affected joker above):

1. **Probabilistic proc (RNG-gated effect).** Business Card, 8 Ball, Hallucination, Reserved Parking (+ Gros Michel/Cavendish self-destroy). Needs a `Chance(odds, rngKey)` condition or a `RANDOM` value source seeded like `pseudoseed`/`pseudorandom`.
2. **Random value source.** Misprint (`Random(min,max)` Value) — distinct from #1 (this contributes a random magnitude, not a gate).
3. **Create card / consumable / joker.** 8 Ball (Tarot), Hallucination (Tarot), Riff-raff (2 Common Jokers). New effect op `CREATE(type, count, conditions)`.
4. **Self-destroy / consume joker.** Ice Cream and Popcorn (destroy at 0), Gros Michel/Cavendish (probabilistic). New op `DESTROY_SELF(when)`.
5. **Self sell-value mutation.** Egg (+$3 sell value/round). New op `ADD_SELL_VALUE`.
6. **Read other jokers (aggregate).** Swashbuckler (sum of other jokers' sell value), Abstract (count of jokers owned). New Value source `OwnedJokers(count|sellSum)`.
7. **Read deck / hand-type stats.** Blue Joker (`DeckSize`), Supernova (`HandTypePlayCount(currentHand)`), Fortune Teller (`TarotsUsedThisRun`). New Value sources reading run stats.
8. **Positional scored-card selector.** Photograph ("first scored face"), Hanging Chad (`j_mp`: scoring cards #1 & #2). New Condition `ScoredPositionIs(n)` / `FirstScoredMatching(cond)`.
9. **Aggregate-over-held selector.** Raised Fist (lowest-ranked held card → 2× its chips). New Value `MinHeld(rankBy)` / selector for the extremum held card.
10. **Per-round random state seeds.** Mail-In Rebate (round rank), To Do List (round poker hand). New Mutation kind: `SET_RANDOM(domain)` re-rolled on a round trigger.
11. **Floored decrement.** Green Joker (mult −1 on discard, floored at 0). Extend Mutation to support `ADD` with a `min` clamp.
12. **Booster-pack-skipped trigger.** Red Card. New Trigger `SKIP_BOOSTER` (distinct from SKIP_BLIND/OPEN_BOOSTER).
13. **Free shop reroll grant.** Chaos the Clown. New effect on REROLL_SHOP/SHOP that grants N free rerolls.
14. **Slot / sizing modifiers.** Juggler (+hand size), Drunkard (+discards/round), Credit Card (−$20 debt floor). New passive effect ops `MODIFY_HAND_SIZE`, `MODIFY_DISCARDS`, `MODIFY_DEBT_FLOOR`.
15. **Scoring-eligibility override.** Splash (all played cards score). New flag effect `ALL_CARDS_SCORE`.
16. **Global game-state flags.** Gros Michel extinction → Cavendish pool gate. New cross-joker flag mechanism (`gros_michel_extinct`).
