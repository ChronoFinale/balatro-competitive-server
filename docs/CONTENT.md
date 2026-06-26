# CONTENT — Balatro/BMP content reference

Ground-truth keys/numbers for authoring + validation. Validate against the real game
(D:\BalatroMod) before relying on any number. NOTE: any Trigger/Condition NAMES used
pedagogically here may predate the current `com.balatro.grammar` vocabulary — the DSL
dictionary is `docs/DSL.md`.

Target version throughout: **Balatro Multiplayer (BMP) 0.4.0**, base-game decompiled dump.
Numbers come from `lovely/dump/game.lua` (centers/config), Divvy's sim
(`compatibility/Preview/Jokers/_Vanilla.lua`, `EngineSimulate.lua`), the BMP `objects/`/`rulesets/`/`layers/`/`gamemodes/`,
`CHANGELOG.md`, and the 0.3.3 ranked-changes spreadsheet (`xlsx_out2/*.txt`, flagged where 0.4.0 supersedes).

## Table of contents

1. [Jokers](#1-jokers) — [Common](#11-common-jokers-rarity-1-61) / [Uncommon](#12-uncommon-jokers-rarity-2-64) / [Rare](#13-rare-jokers-rarity-3-20) / [Legendary](#14-legendary-jokers-rarity-4-5) / [BMP-exclusive](#15-bmp-04-exclusive-jokers-multiplayer-jokers)
2. [Consumables](#2-consumables) — [Tarots](#21-tarot-cards-22) / [Planets + Black Hole](#22-planet-cards--black-hole) / [Spectrals](#23-spectral-cards-18)
3. [Vouchers](#3-vouchers-32)
4. [Decks](#4-decks-backs-b_)
5. [Packs, Editions, Enhancements, Seals](#5-packs-editions-enhancements--seals)
6. [Skip Tags](#6-skip-tags-24)
7. [Boss Blinds](#7-boss-blinds)

### Critical cross-cutting BMP 0.4.0 grounding note

`rulesets/release.lua` contains a long list of `MP.ReworkCenter` calls (greedy s_mult=4, banner=40,
odd_todd=30, runner 20/10, square 16/4, smiley=4, mad/clever → Four of a Kind, etc.). **The entire body
of that file (lines 2–798) is wrapped in a `--[[ … ]]` Lua block comment** — those reworks **do not
execute** in Standard Ranked. Do not treat release.lua numbers as live ranked values unless re-verified.
Standard Ranked = `ranked.lua` → layers `{ "standard", "ranked" }`; the `standard` layer applies only a
small set of bans/swaps (noted per item). Where a number below cites "release.lua → X inert," that rework
is commented out and the base value stands.

---

# 1. Jokers

`rarity`: 1=Common, 2=Uncommon, 3=Rare, 4=Legendary. "Cost" is base shop cost in `$`. Numbers are from
the center `config` block, corroborated by the Divvy sim. Where a `config.extra` is an X-mult it is applied
as XMULT.

## 1.1 Common Jokers (`rarity = 1`, 61)

**Standard-ranked Common swaps:** `j_hanging_chad` → `j_mp_hanging_chad` (retrigger scoring cards #1 and #2
once each, extra=1, instead of vanilla "retrigger first card +2"). `j_ticket` (Golden Ticket) →
`j_mp_ticket` (**Uncommon (rarity 2)**, cost **6**, **$3** per scored Gold card; CHANGELOG narrative claims
$4 but the loaded standard object is $3; $4 only in `j_mp_ticket_experimental`). Everything else keeps base
defaults.

### A. Suit Mult — ON_SCORED individual, +Mult per matching suit
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Greedy Joker | `j_greedy_joker` | 5 | +3 Mult per scored **Diamond** (`extra.s_mult=3`) |
| Lusty Joker | `j_lusty_joker` | 5 | +3 Mult per scored **Heart** |
| Wrathful Joker | `j_wrathful_joker` | 5 | +3 Mult per scored **Spade** |
| Gluttonous Joker | `j_gluttenous_joker` (misspelled key) | 5 | +3 Mult per scored **Club** |

### B. Type Mult — JOKER_MAIN, +Mult if played hand contains type
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Jolly Joker | `j_jolly` | 3 | +8 Mult if hand contains a **Pair** (`t_mult=8`) |
| Zany Joker | `j_zany` | 4 | +12 Mult if **Three of a Kind** |
| Mad Joker | `j_mad` | 4 | +10 Mult if **Two Pair** |
| Crazy Joker | `j_crazy` | 4 | +12 Mult if **Straight** |
| Droll Joker | `j_droll` | 4 | +10 Mult if **Flush** |

### C. Type Chips — JOKER_MAIN, +Chips if played hand contains type
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Sly Joker | `j_sly` | 3 | +50 Chips if **Pair** (`t_chips=50`) |
| Wily Joker | `j_wily` | 4 | +100 Chips if **Three of a Kind** |
| Clever Joker | `j_clever` | 4 | +80 Chips if **Two Pair** |
| Devious Joker | `j_devious` | 4 | +100 Chips if **Straight** |
| Crafty Joker | `j_crafty` | 4 | +80 Chips if **Flush** |

### D. Conditional flat scoring (state-free)
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Joker | `j_joker` | 2 | +4 Mult (always) |
| Half Joker | `j_half` | 5 | +20 Mult if played hand has **≤ 3 cards** (`extra.mult=20, size=3`) |
| Mystic Summit | `j_mystic_summit` | 5 | +15 Mult while **0 discards remaining** (`extra.mult=15, d_remaining=0`) |
| Banner | `j_banner` | 5 | **+30 Chips per discard remaining** (`extra=30`); release.lua →40 inert |
| Scary Face | `j_scary_face` | 4 | +30 Chips per scored **face** card (`extra=30`) |
| Even Steven | `j_even_steven` | 4 | +4 Mult per scored **even** card (2,4,6,8,10) (`extra=4`) |
| Odd Todd | `j_odd_todd` | 4 | +31 Chips per scored **odd** card (A,3,5,7,9) (`extra=31`); release.lua →30 inert |
| Scholar | `j_scholar` | 4 | per scored **Ace**: +20 Chips and +4 Mult (`extra.chips=20, mult=4`) |
| Walkie Talkie | `j_walkie_talkie` | 4 | per scored **10 or 4**: +10 Chips and +4 Mult (`extra.chips=10, mult=4`) |
| Smiley Face | `j_smiley` | 4 | +5 Mult per scored **face** card (`extra=5`); release.lua →4 inert |
| Shoot the Moon | `j_shoot_the_moon` | 5 | +13 Mult per **Queen held in hand** (`extra=13`). Unlock: play hand of all Hearts |

### E. Held-in-hand effects
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Raised Fist | `j_raised_fist` | 5 | Adds **2× the rank (base_chips) of the lowest-ranked card held in hand** as Mult. Ignores Stone cards; ties pick the rightmost lowest |

### F. X-Mult jokers
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Photograph | `j_photograph` | 5 | **X2 Mult** off the **first scored face card** (`extra=2`) |
| Gros Michel | `j_gros_michel` | 5 | **+15 Mult** (`extra.mult=15`); each round-end **1-in-6** chance to be destroyed and set `gros_michel_extinct` (enables Cavendish) |
| Cavendish | `j_cavendish` | 4 | **X3 Mult** (`extra.Xmult=3`); 1-in-1000 self-destroy each round; only appears after Gros Michel extinct (`yes_pool_flag`) |

### G. Accumulating-state scoring jokers
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Ride the Bus | `j_ride_the_bus` | 6 | +1 Mult (`extra=1`) per **consecutive hand scored with NO face card**; **resets to 0** when a scored hand contains any face card |
| Green Joker | `j_green_joker` | 4 | +1 Mult per hand **played**, −1 Mult per **discard** (`hand_add=1, discard_sub=1`), floored at 0 |
| Runner | `j_runner` | 5 | Starts +0 Chips; **+15 Chips permanently each Straight played** (`chips=0, chip_mod=15`); release.lua 20/10 inert |
| Square Joker | `j_square` | 4 | **+4 Chips permanently each time a hand of exactly 4 cards is played** (`chips=0, chip_mod=4`); release.lua 16/4 inert |
| Ice Cream | `j_ice_cream` | 5 | **+100 Chips**, **−5 Chips after each hand played** (`chips=100, chip_mod=5`); self-destroys at 0 |
| Popcorn | `j_popcorn` | 5 | **+20 Mult**, **−4 Mult per round** (`mult=20, extra=4`); self-destroys at 0 |

### H. Hand-state / count reads
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Abstract Joker | `j_abstract` | 4 | +3 Mult per **Joker owned** (`extra=3`) |
| Supernova | `j_supernova` | 5 | +Mult equal to **number of times this poker hand has been played this run** (`extra=1`, `G.GAME.hands[name].played`) |
| Blue Joker | `j_blue_joker` | 5 | **+2 Chips per card remaining in the deck** (`extra=2`, `#G.deck.cards`) |
| Misprint | `j_misprint` | 4 | **+random Mult, 0–23** each scoring (`extra.min=0, max=23`) |
| Fortune Teller | `j_fortune_teller` | 6 | +1 Mult per **Tarot card used this run** (`extra=1`, `consumeable_usage_total.tarot`) |

### I. Economy jokers
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Golden Joker | `j_golden` | 6 | **+$4 at end of round** (`extra=4`) |
| Delayed Gratification | `j_delayed_grat` | 4 | **+$2 per discard** at end of round **if no discards were used** that round (`extra=2`) |
| Business Card | `j_business` | 4 | Each scored **face** card has **1-in-2** chance to give **+$2** (`extra=2`, odds 2) |
| Faceless Joker | `j_faceless` | 4 | **+$5 if 3+ face cards are discarded at once** (`extra.dollars=5, faces=3`) |
| Mail-In Rebate | `j_mail` | 4 | **+$5 per discarded card of the round's rank** (`extra=5`; rank rerolls each round); release.lua →3 inert |
| Reserved Parking | `j_reserved_parking` | 6 | Each **face card held in hand** has **1-in-2** chance to give **+$1** (`extra.odds=2, dollars=1`); release.lua → Uncommon inert |
| To Do List | `j_todo_list` | 4 | **+$4 if the played hand matches the listed poker hand** (`extra.dollars=4, poker_hand='High Card'`); hand reseeds each round. Experimental layer = $5 + all-hands seed; standard stays $4 |
| Golden Ticket | `j_ticket` → **`j_mp_ticket`** (ranked) | base 5 / MP **6** | **+$3 per scored Gold-enhanced card** (MP `extra.dollars=3`; **Uncommon** in MP). Vanilla base: +$4 per Gold card, Common, cost 5, unlock-gated |

### J. Discard / draw / generation jokers
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Credit Card | `j_credit_card` | 1 | Allows going **−$20** in debt (`extra=20`) |
| Chaos the Clown | `j_chaos` | 4 | **1 free shop reroll per shop** (`extra=1`) |
| 8 Ball | `j_8_ball` | 5 | Each scored **8** has **1-in-4** chance to create a **Tarot** (if room) (`extra=4`); release.lua → 1-in-2 inert |
| Riff-raff | `j_riff_raff` | 6 | At **blind start**, create **2 Common Jokers** (if room) (`extra=2`); release.lua cost→4 inert |
| Hallucination | `j_hallucination` | 4 | On **opening any Booster Pack**, **1-in-2** chance to create a **Tarot** (if room) (`extra=2`) |
| Egg | `j_egg` | 4 | Gains **+$3 sell value at end of each round** (`extra=3`) |
| Red Card | `j_red_card` | 5 | **+3 Mult permanently each time a Booster Pack is skipped** (`extra=3`) |
| Juggler | `j_juggler` | 4 | **+1 hand size** (`h_size=1`) |
| Drunkard | `j_drunkard` | 4 | **+1 discard each round** (`d_size=1`) |
| Splash | `j_splash` | 3 | **Every played card counts in scoring** (`config {}`) |

### K. Retrigger / mp-specific
| Name | Key | Cost | Effect |
| --- | --- | --- | --- |
| Hanging Chad | `j_hanging_chad` → **`j_mp_hanging_chad`** (ranked) | 4 | **MP/0.4.0:** retrigger **scoring card #1 and #2 once each** (extra=1). **Vanilla base:** retrigger the **first scored card 2 extra times** (`extra=2`). Unlock (vanilla): win a round with High Card as last hand |
| Swashbuckler | `j_swashbuckler` | 4 | **+Mult equal to combined sell value of all your OTHER jokers** (`mult=1` base; recomputed from sum of other jokers' sell_cost). Unlock: sell 20 jokers |

**Common-joker odds reference:** Business Card 2, 8 Ball 4, Reserved Parking 2, Hallucination 2 (all "1 in N").

## 1.2 Uncommon Jokers (`rarity = 2`, 64)

**0.3.0+ nerfs active in 0.4.0 (config in game.lua still reads the old value):** Seltzer lasts **8** hands (config still 10); Turtle Bean grants **+4** hand size (config still 5). All are base shop cost in `$`. `*_compat=false` flags noted where present.

| # | Name | Key | Cost | Exact effect |
|---|------|-----|------|--------------|
| 1 | Joker Stencil | `j_stencil` | 8 | X1 Mult per **empty** joker slot (`card_limit - #jokers`); each Joker Stencil counts as empty (+1 per copy) |
| 2 | Four Fingers | `j_four_fingers` | 7 | Flushes & Straights need only **4** cards. `blueprint_compat=false` |
| 3 | Mime | `j_mime` | 5 | Retriggers **all held-in-hand cards** abilities (`extra=1`) |
| 4 | Ceremonial Dagger | `j_ceremonial` | 6 | On BLIND_SELECTED: destroys joker to the right, gains **2× its sell value** as +Mult (stored, starts 0); adds stored Mult each hand |
| 5 | Marble Joker | `j_marble` | 6 | On BLIND_SELECTED: adds **1** Stone card to deck (`extra=1`) |
| 6 | Loyalty Card | `j_loyalty_card` | 5 | **X4 Mult** (`Xmult=4`) every **6th** hand played (`every=5`) |
| 7 | Dusk | `j_dusk` | 5 | Retriggers each **scored** card **1×** (`extra=1`) on the **final hand** of round (`hands_left==1`) |
| 8 | Fibonacci | `j_fibonacci` | 8 | +**8** Mult (`extra=8`) for each scored **Ace, 2, 3, 5, or 8** (ranks {2,3,5,8,14}) |
| 9 | Steel Joker | `j_steel_joker` | 7 | **X(1 + 0.2 × #Steel cards in full deck)** (`extra=0.2`, `steel_tally`) |
| 10 | Hack | `j_hack` | 6 | Retriggers each scored **2, 3, 4, or 5** **1×** (`extra=1`) |
| 11 | Pareidolia | `j_pareidolia` | 5 | **All cards are considered face cards.** `blueprint_compat=false` |
| 12 | Space Joker | `j_space` | 5 | **1 in 4** chance (`extra=4`) to **level up** the played poker hand |
| 13 | Burglar | `j_burglar` | 6 | On BLIND_SELECTED: **+3 Hands** (`extra=3`), **lose all discards** this round |
| 14 | Blackboard | `j_blackboard` | 6 | **X3 Mult** (`extra=3`) if **all cards held in hand** are Spades or Clubs (empty hand qualifies) |
| 15 | Sixth Sense | `j_sixth_sense` | 6 | Play a **single 6** as only card (first hand-of-round) → **destroy it**, create a **Spectral**. `blueprint_compat=false`. MP: Up Top Spectral Queue |
| 16 | Constellation | `j_constellation` | 6 | **X Mult grows +0.1** (`extra=0.1`) each time a **Planet card** is used (Xmult starts 1) |
| 17 | Hiker | `j_hiker` | 5 | Every scored card permanently gains **+5 chips** (`extra=5`, `perma_bonus`) |
| 18 | Card Sharp | `j_card_sharp` | 6 | **X3 Mult** (`Xmult=3`) if this poker hand was **already played this round** (`played_this_round > 1`) |
| 19 | Madness | `j_madness` | 7 | On BLIND_SELECTED (small/big only): **X Mult +0.5** (`extra=0.5`) and **destroys a random joker**. `perishable_compat=false` |
| 20 | Seance | `j_seance` | 6 | If played hand is a **Straight Flush**, create a random **Spectral** (if room). MP: Up Top Spectral Queue |
| 21 | Vampire | `j_vampire` | 7 | **X Mult +0.1** per scored **enhanced** card (`extra=0.1`); **removes that enhancement**. Xmult starts 1, persists. `perishable_compat=false` |
| 22 | Shortcut | `j_shortcut` | 7 | Allows **Straights with gaps of 1** (e.g. 10 8 6 5 3). `blueprint_compat=false` |
| 23 | Hologram | `j_hologram` | 7 | **X Mult +0.25** (`extra=0.25`) each time a **playing card is added** to the deck (Xmult starts 1). `perishable_compat=false` |
| 24 | Cloud 9 | `j_cloud_9` | 7 | End of round: **$1 per 9** (`extra=1`) in your **full deck**. `blueprint_compat=false` |
| 25 | Rocket | `j_rocket` | 6 | End of round: earn **$1** (`dollars=1`); payout **+$2** (`increase=2`) when a **Boss Blind is defeated**. `blueprint/perishable_compat=false` |
| 26 | Midas Mask | `j_midas_mask` | 7 | All **scored face cards** become **Gold** (`m_gold`). `blueprint_compat=false` |
| 27 | Luchador | `j_luchador` | 5 | When **sold**, **disables the current Boss Blind**. `eternal_compat=false` |
| 28 | Gift Card | `j_gift` | 6 | End of round: **+$1 sell value** (`extra=1`) to **every Joker and Consumable**. `blueprint_compat=false` |
| 29 | Turtle Bean | `j_turtle_bean` | 6 | **+5 hand size** (`h_size=5`), **−1 each round** (`h_mod=1`) until 0. **0.4.0 nerf: +4 not +5.** `blueprint/eternal_compat=false` |
| 30 | Erosion | `j_erosion` | 6 | **+4 Mult** (`extra=4`) for **each card below starting deck size** (`starting_deck_size - #playing_cards`) |
| 31 | To the Moon | `j_to_the_moon` | 5 | End of round: earn **extra $1 interest** (`extra=1`) per $5 held. `blueprint_compat=false` |
| 32 | Stone Joker | `j_stone` | 6 | **+25 chips** (`extra=25`) per **Stone card** in full deck (`stone_tally`) |
| 33 | Lucky Cat | `j_lucky_cat` | 6 | **X Mult +0.25** (`extra=0.25`) each time a **Lucky card** **successfully triggers**. Xmult starts 1; `perishable_compat=false` |
| 34 | Bull | `j_bull` | 6 | **+2 chips** (`extra=2`) per **$1** you currently have (`2 × max(0, dollars)`) |
| 35 | Diet Cola | `j_diet_cola` | 6 | When **sold**, creates a **free Double Tag**. `eternal_compat=false` |
| 36 | Trading Card | `j_trading` | 6 | If **first discard of round** discards a **single card**, destroy it and earn **$3** (`extra=3`). `blueprint_compat=false` |
| 37 | Flash Card | `j_flash` | 5 | **+2 Mult** (`extra=2`) per **shop reroll** (mult accumulates, starts 0). `perishable_compat=false` |
| 38 | Spare Trousers | `j_trousers` | 6 | **+2 Mult** (`extra=2`) each time played hand contains a **Two Pair or Full House**. `perishable_compat=false` |
| 39 | Ramen | `j_ramen` | 6 | **X2 Mult** (`Xmult=2`) initially, **−0.01** (`extra=0.01`) per card discarded; min X1. `eternal_compat=false` |
| 40 | Seltzer | `j_selzer` | 6 | Retriggers **all scored cards 1×** for the next **10 hands** (`extra=10`), then self-destructs. **0.4.0 nerf: 8 hands.** `eternal_compat=false` |
| 41 | Castle | `j_castle` | 6 | **+3 chips** (`chip_mod=3`) per **discarded card of a chosen suit** (`castle_card.suit`, re-rolled each round); chips accumulate from 0. `perishable_compat=false` |
| 42 | Mr. Bones | `j_mr_bones` | 5 | Prevents death if **chips ≥ 25%** of required; then self-destructs. Locked (lose 5 runs). `blueprint/eternal_compat=false` |
| 43 | Acrobat | `j_acrobat` | 6 | **X3 Mult** (`extra=3`) on the **final hand** of the round (`hands_left==1`). Locked (200 hands) |
| 44 | Sock and Buskin | `j_sock_and_buskin` | 6 | Retriggers all scored **face cards 1×** (`extra=1`). Locked (300 face cards) |
| 45 | Troubadour | `j_troubadour` | 6 | **+2 hand size** (`h_size=2`), **−1 hand per round** (`h_plays=-1`). Locked (win 5 rounds). `blueprint_compat=false` |
| 46 | Certificate | `j_certificate` | 6 | On BLIND_SELECTED: add a **random playing card with a random Seal** to hand. Locked |
| 47 | Smeared Joker | `j_smeared` | 7 | **Hearts↔Diamonds and Spades↔Clubs** count as the same suit. `blueprint_compat=false`. Locked |
| 48 | Throwback | `j_throwback` | 6 | **X Mult +0.25** (`extra=0.25`) per **Blind skipped** this run (Xmult starts 1). Locked |
| 49 | Rough Gem | `j_rough_gem` | 7 | Each scored **Diamond** earns **$1** (`extra=1`). Locked (30 Diamonds) |
| 50 | Bloodstone | `j_bloodstone` | 7 | **1 in 2** (`odds=2`) chance per scored **Heart** for **X1.5 Mult** (`Xmult=1.5`). Locked (30 Hearts). MP: dual-queue (game-long + per-ante PvP queue, resets each hand) |
| 51 | Arrowhead | `j_arrowhead` | 7 | Each scored **Spade** gives **+50 chips** (`extra=50`). Locked (30 Spades) |
| 52 | Onyx Agate | `j_onyx_agate` | 7 | Each scored **Club** gives **+7 Mult** (`extra=7`). Locked (30 Clubs) |
| 53 | Glass Joker | `j_glass` | 6 | **X Mult +0.75** (`extra=0.75`) per **Glass card destroyed** (Xmult starts 1). Locked (5 Glass). `perishable_compat=false` |
| 54 | Showman | `j_ring_master` | 5 | Allows **duplicate** Jokers/Tarot/Planet/Spectral in shop & packs. (key is `j_ring_master`.) `blueprint_compat=false`. Locked (ante 4) |
| 55 | Flower Pot | `j_flower_pot` | 6 | **X3 Mult** (`extra=3`) if scoring hand contains a card of **all 4 suits** (Wild fills gaps). Locked (ante 8) |
| 56 | Merry Andy | `j_merry_andy` | 7 | **+3 discards** (`d_size=3`), **−1 hand size** (`h_size=-1`). `blueprint_compat=false`. Locked (win ≤12 rounds) |
| 57 | Oops! All 6s | `j_oops` | 4 | **Doubles all listed probabilities** (1-in-4 → 1-in-2). `blueprint_compat=false`. Locked (10000 chip score). MP: halves the roll threshold |
| 58 | The Idol | `j_idol` | 6 | **X2 Mult** (`extra=2`) per scored card matching a **specific rank+suit** (`idol_card`, re-rolled each round). Locked (1M chips). MP: deterministic sort-based selection |
| 59 | Seeing Double | `j_seeing_double` | 6 | **X2 Mult** (`extra=2`) if scoring hand has a **Club AND a card of any other suit** (Wilds fill, Clubs prioritized). Locked (four 7♣ in one hand) |
| 60 | Matador | `j_matador` | 7 | Earn **$8** (`extra=8`) if played hand **triggers the Boss Blind's ability**. Locked (defeat a boss with it) |
| 61 | Satellite | `j_satellite` | 6 | End of round: earn **$1** (`extra=1`) per **unique Planet card used** this run. `blueprint_compat=false`. Locked ($400 held) |
| 62 | Cartomancer | `j_cartomancer` | 6 | On BLIND_SELECTED: create a random **Tarot** (if room). Locked (discover 22 Tarots) |
| 63 | Astronomer | `j_astronomer` | 8 | All **Planet cards & Celestial Packs in shop are free**. `blueprint_compat=false`. Locked (discover 12 Planets) |
| 64 | Bootstraps | `j_bootstraps` | 7 | **+2 Mult** (`mult=2`) for **every $5** you have (`floor(dollars/5) × 2`). Locked (2 Polychrome jokers owned) |

## 1.3 Rare Jokers (`rarity = 3`, 20)

All `set = "Joker"`, `rarity = 3`. No 0.4.0 numeric changes to any Rare vanilla joker.

| # | Name | Key | Cost | Exact effect |
|---|------|-----|------|--------------|
| 1 | DNA | `j_dna` | 8 | If **first hand of round** is played as a **single card**, add a permanent copy of that card to hand (and deck). `config={}` |
| 2 | Vagabond | `j_vagabond` | 8 | Create a **Tarot card** if hand is played while holding **≤ $4** (`extra=4`). MP: Tarot from Up Top Tarot Queue |
| 3 | Baron | `j_baron` | 8 | Each **King held in hand** gives **X1.5 Mult** (`extra=1.5`) |
| 4 | Obelisk | `j_obelisk` | 8 | **+X0.2 Mult** per consecutive hand played **without** playing your most-played poker hand; resets when you play your most-played hand. `extra=0.2`, base Xmult=1 |
| 5 | Baseball Card | `j_baseball` | 8 | Each **Uncommon Joker** gives **X1.5 Mult** (`extra=1.5`; matches `other_joker.rarity==2`, excludes itself) |
| 6 | Ancient Joker | `j_ancient` | 8 | Each played card of a **specific suit** gives **X1.5 Mult** (`extra=1.5`); suit changes each round (`ancient_card.suit`) |
| 7 | Campfire | `j_campfire` | 9 | **+X0.25 Mult** (`extra=0.25`) per card **sold** this run; resets when Boss Blind defeated |
| 8 | Blueprint | `j_blueprint` | 10 | **Copies** the ability of the Joker to its **right** (`effect="Copycat"`, `config={}`) |
| 9 | Wee Joker | `j_wee` | 8 | Gains **+8 Chips** permanently each time a **2** is scored (`chips=0, chip_mod=8`). `perishable_compat=false` |
| 10 | Hit the Road | `j_hit_the_road` | 8 | **+X0.5 Mult** (`extra=0.5`) per **Jack discarded** this round; resets each round |
| 11 | The Duo | `j_duo` | 8 | **X2 Mult** if played hand contains a **Pair** (`type='Pair'`) |
| 12 | The Trio | `j_trio` | 8 | **X3 Mult** if played hand contains a **Three of a Kind** |
| 13 | The Family | `j_family` | 8 | **X4 Mult** if played hand contains a **Four of a Kind** |
| 14 | The Order | `j_order` | 8 | **X3 Mult** if played hand contains a **Straight** |
| 15 | The Tribe | `j_tribe` | 8 | **X2 Mult** if played hand contains a **Flush** |
| 16 | Stuntman | `j_stuntman` | 7 | **+250 Chips** (`chip_mod=250`), **−2 hand size** (`h_size=2`) |
| 17 | Invisible Joker | `j_invisible` | 8 | After **2 rounds** (`extra=2`), can be **sold** to create a free **copy** of a random owned Joker. `blueprint/eternal_compat=false`. MP: deterministic position-assignment to reduce cross-player variance |
| 18 | Brainstorm | `j_brainstorm` | 10 | **Copies** the ability of the **leftmost** Joker (`effect="Copycat"`, `config={}`) |
| 19 | Driver's License | `j_drivers_license` | 7 | **X3 Mult** (`extra=3`) if you own **≥ 16 enhanced** cards (`driver_tally >= 16`) |
| 20 | Burnt Joker | `j_burnt` | 8 | Upgrades the **level of the first discarded poker hand** each round (`extra=4`; `h_size=0`). Vanilla upgrades by 1 level (extra=4 unverified as legacy field) |

Notes: DNA fires only when `hands_played==0 and #full_hand==1`. Blueprint/Brainstorm recursion has a depth guard capped at `#jokers + 1`. The Duo/Trio/Family/Order/Tribe are conditional flat X-mults gated on the played hand *containing* the named type.

## 1.4 Legendary Jokers (`rarity = 4`, 5)

Pure vanilla in BMP 0.4.0 (not redefined/rebalanced/banned). All `rarity = 4, cost = 20`,
`unlocked=false, discovered=false` (Soul/Legendary pool only). Source `game.lua:531-535`.

| Name | Key | Cost | Exact effect | Config |
|------|-----|------|--------------|--------|
| Canio | `j_caino` (misspelled **caino**) | 20 | Gains **X1 Mult** when a **face card is destroyed** (no upper limit) | `{extra=1}`; live `caino_xmult` |
| Triboulet | `j_triboulet` | 20 | Played **Kings and Queens** each give **X2 Mult** when scored | `{extra=2}` |
| Yorick | `j_yorick` | 20 | Gains **X1 Mult** per **23 cards discarded**; counter resets to 23 each trigger | `{extra={xmult=1, discards=23}}`; live `yorick_discards` |
| Chicot | `j_chicot` | 20 | **Disables the effect of every Boss Blind.** `blueprint_compat=false` | `{}` |
| Perkeo | `j_perkeo` | 20 | At end of shop, creates a **Negative** copy of **1 random consumable** in your possession | `{}` |

(Chicot vs the BMP `bl_mp_nemesis` PvP boss is unverified — `bl_mp_nemesis` is a custom blind, not a vanilla `boss` blind. Chicot/Luchador/Matador/Mr. Bones are **banned** in Showdown + Attrition — see Boss Blinds §7.)

## 1.5 BMP 0.4.0 Exclusive Jokers ("Multiplayer Jokers")

Internal keys prefixed **`j_mp_`**. Only enter the pool in an online lobby with the **Multiplayer Jokers**
option on (`config.lua` default `multiplayer_jokers = true`). "Nemesis" = the opponent. PvP-blind detection =
`MP.is_pvp_boss()` (true iff `G.GAME.blind` is `bl_mp_nemesis` or has `blind.pvp`). Several ship a **phantom**
mirror to the opponent; phantom copies are skipped in their own `calculate` via `card.edition.type ~= "mp_phantom"`.

| Name | Key | Rarity / Cost | Exact effect (real numbers) | PvP / nemesis mechanic |
|------|-----|---------------|------------------------------|------------------------|
| Defensive Joker | `j_mp_defensive_joker` | Common / $4 | **+125 Chips** per life you have *fewer* than your Nemesis (**+75** on Stake ≥ 6 / Gold+). `t_chips = max((enemy.lives - your.lives)*chips, 0)`. `config.extra = {extra=125, highstake=75}` | Reads `enemy.lives` vs `lives` |
| Conjoined Joker | `j_mp_conjoined_joker` | Uncommon / $6 | In a PvP Blind: **X0.5 Mult per Hand the Nemesis has left**, `x_mult = clamp(1 + enemy.hands*0.5, 1, 3)` → **max X3**. `extra={x_mult_gain=0.5, max_x_mult=3, x_mult=1}` | Reads `enemy.hands`; sends phantom; excluded when sandbox active |
| Pizza | `j_mp_pizza` | Common / $4 | At **end of next PvP Blind**: consume self, **+2 discards to you**, **+1 discard to Nemesis** for the ante. `extra={discards=2, discards_nemesis=1}`. `blueprint/eternal_compat=false` | Self-destructs; sends `eatPizza` action |
| Let's Go Gambling | `j_mp_lets_go_gambling` | Uncommon / $6 | **1 in 4**: **X4 Mult and +$10**. In a PvP Blind, **1 in 4** to give Nemesis $10 (misfire). `extra={odds=4, xmult=4, dollars=10, nemesis_odds=4, nemesis_dollars=10}` | Misfire gives nemesis $10; sends phantom |
| Penny Pincher | `j_mp_penny_pincher` | Common / $4 | End of round: **$1 for every $3** your Nemesis spent in the corresponding shop last ante. `calc = floor(enemy_spent/3)`. `extra={dollars=1, nemesis_dollars=3}`. `blueprint_compat=false` | Reads `enemy.spent_in_shop`; gated on `pincher_unlock` |
| SPEEDRUN | `j_mp_speedrun` | Uncommon / $6 | Reach a PvP Blind **within 30s of Nemesis** → create a **random Spectral** (needs room). No `config.extra` | **0.4.0 DELTA: "Out of rotation"** in Standard Ranked (in code, not pooled). Sends phantom |
| Pacifist | `j_mp_pacifist` | Common / $4 | **X10 Mult while NOT in a PvP Blind.** `extra={x_mult=10}` | Inverse of Conjoined; no phantom |
| Taxes | `j_mp_taxes` | Common / $5 | **+4 Mult per card the Nemesis sold** since last PvP Blind; committed when the nemesis Blind is selected. `extra={mult_gain=4, mult=0}`. `perishable_compat=false` | Reads `enemy.sells_per_ante`; 0.4.0 sold-action fires on any card sold |
| Skip-Off | `j_mp_skip_off` | Uncommon / $5 | **+1 Hand and +1 Discard per additional Blind skipped vs Nemesis.** `hands = max(your_skips - enemy_skips, 0)`, same for discards. `extra={hands=0, discards=0, extra_hands=1, extra_discards=1}`. `blueprint_compat=false` | Reads `G.GAME.skips` vs `enemy.skips` |

---

# 2. Consumables

## 2.1 Tarot Cards (22)

All `set = "Tarot"`, `cost = 3`, `cost_mult = 1.0`, `consumeable = true`, `discovered = false`. The "enhance/convert" Tarots route through a shared deterministic branch (player-selected targets, no RNG); `max_highlighted` caps selection.

**Enhancement keys (`mod_conv`):** `m_lucky`=LUCKY (1-in-5 +20 mult, 1-in-15 +$20), `m_mult`=MULT (+4 Mult scored), `m_bonus`=BONUS (+30 Chips), `m_wild`=WILD (every suit), `m_steel`=STEEL (x1.5 Mult held), `m_glass`=GLASS (x2 Mult scored / 1-in-4 shatter; BMP standard reworked to x1.5), `m_gold`=GOLD (+$3 held end of round; CHANGELOG says $4, see §5), `m_stone`=STONE (+50 Chips, always scores, no rank/suit).

| # | Name | Key | `config` | Exact effect & targets | RNG / queue |
|---|---|---|---|---|---|
| 1 | The Fool | `c_fool` | `{}` | Creates a copy of **the last Tarot or Planet used this run** (`G.GAME.last_tarot_planet`). Cannot copy itself; cannot be first consumable used | no RNG (fixed key) |
| 2 | The Magician | `c_magician` | `{mod_conv='m_lucky', max_highlighted=2}` | Enhance up to **2** cards to **Lucky** | none |
| 3 | The High Priestess | `c_high_priestess` | `{planets=2}` | Creates up to **2 random Planet cards** (capped by free slots) | RNG → Up-Top **Planet** queue |
| 4 | The Empress | `c_empress` | `{mod_conv='m_mult', max_highlighted=2}` | Enhance up to **2** cards to **Mult** | none |
| 5 | The Emperor | `c_emperor` | `{tarots=2}` | Creates up to **2 random Tarot cards** (capped by free slots) | RNG → Up-Top **Tarot** queue |
| 6 | The Hierophant | `c_heirophant` (misspelled key) | `{mod_conv='m_bonus', max_highlighted=2}` | Enhance up to **2** cards to **Bonus** | none |
| 7 | The Lovers | `c_lovers` | `{mod_conv='m_wild', max_highlighted=1}` | Enhance **1** card to **Wild** | none |
| 8 | The Chariot | `c_chariot` | `{mod_conv='m_steel', max_highlighted=1}` | Enhance **1** card to **Steel** | none |
| 9 | Justice | `c_justice` | `{mod_conv='m_glass', max_highlighted=1}` | Enhance **1** card to **Glass**. **Ranked-ban contradictory (see below)** | none |
| 10 | The Hermit | `c_hermit` | `{extra=20}` | **Doubles money, capped at +$20**: `ease_dollars(max(0, min(dollars, 20)))` | none |
| 11 | The Wheel of Fortune | `c_wheel_of_fortune` | `{extra=4}` | **1-in-4** chance to add a random edition (Foil/Holo/Poly) to a random **eligible** Joker (no existing edition). On fail: "Nope!" | RNG: `wheel_of_fortune` prob queue (1/4) + target pick + edition poll |
| 12 | Strength | `c_strength` | `{mod_conv='up_rank', max_highlighted=2}` | **Increase rank by 1** of up to **2** cards. A→2 (wraps), K→A, else `min(id+1,14)` | none |
| 13 | The Hanged Man | `c_hanged_man` | `{remove_card=true, max_highlighted=2}` | **Destroys** up to **2** selected hand cards | none |
| 14 | Death | `c_death` | `{mod_conv='card', max_highlighted=2, min_highlighted=2}` | Requires **exactly 2**. **Converts left card into right** (rightmost is template) | none |
| 15 | Temperance | `c_temperance` | `{extra=50}` | Money = **total sell value of all Jokers**, capped **$50** | none |
| 16 | The Devil | `c_devil` | `{mod_conv='m_gold', max_highlighted=1}` | Enhance **1** card to **Gold** | none |
| 17 | The Tower | `c_tower` | `{mod_conv='m_stone', max_highlighted=1}` | Enhance **1** card to **Stone** | none |
| 18 | The Star | `c_star` | `{suit_conv='Diamonds', max_highlighted=3}` | Up to **3** cards' suit → **Diamonds** | none |
| 19 | The Moon | `c_moon` | `{suit_conv='Clubs', max_highlighted=3}` | Up to **3** cards' suit → **Clubs** | none |
| 20 | The Sun | `c_sun` | `{suit_conv='Hearts', max_highlighted=3}` | Up to **3** cards' suit → **Hearts** | none |
| 21 | Judgement | `c_judgement` | `{}` | Creates a **random Joker** (any rarity, respects slots). **Orange Stake+: own Judgement queue; lower stakes: shop queue** | RNG → Joker queue |
| 22 | The World | `c_world` | `{suit_conv='Spades', max_highlighted=3}` | Up to **3** cards' suit → **Spades** | none |

**Tarot deltas / flags:** Justice "Back in rotation" per CHANGELOG, but `layers/standard.lua:12-14` still lists `c_justice` in `banned_consumables` (discrepancy — code currently bans it). Gold enhancement payout raised $3→$4 in 0.4.0 narrative (affects The Devil's output, not the Tarot). No Tarot `config` numeric change 0.3.3→0.4.0.

## 2.2 Planet Cards & Black Hole

A Planet is `consumeable`, `set = "Planet"`, `effect = "Hand Upgrade"`. Using one adds +1 level to one poker hand. A hand at level `N` = `baseChips + (N-1)*l_chips` chips and `baseMult + (N-1)*l_mult` mult (hands start at level 1). All Planets cost **$3**, `freq=1`, `cost_mult=1.0`.

**0.4.0 DELTA — BMP `release` ruleset reworks four hand per-level increments** (`release.lua:685-724`; Planet behaviour unchanged, only the leveled hand's increment differs):

| Hand | Vanilla per-level | BMP 0.4.0 release per-level | Leveled by |
|---|---|---|---|
| Straight | `l_mult = 3` | **`l_mult = 2`** | Saturn |
| Straight Flush | `l_mult = 4` | **`l_mult = 3`** | Neptune |
| Flush House | `l_mult = 4` | **`l_mult = 3`** | Ceres |
| Flush Five | `l_chips = 50` | **`l_chips = 40`** | Eris |

### Standard 9 Planets
| Name | Key | Hand | +chips/lvl | +mult/lvl | Base (chips/mult @ L1) |
|---|---|---|---|---|---|
| Pluto | `c_pluto` | High Card | 10 | 1 | 5 / 1 |
| Mercury | `c_mercury` | Pair | 15 | 1 | 10 / 2 |
| Uranus | `c_uranus` | Two Pair | 20 | 1 | 20 / 2 |
| Venus | `c_venus` | Three of a Kind | 20 | 2 | 30 / 3 |
| Saturn | `c_saturn` | Straight | 30 | **2** (vanilla 3) | 30 / 4 |
| Jupiter | `c_jupiter` | Flush | 15 | 2 | 35 / 4 |
| Earth | `c_earth` | Full House | 25 | 2 | 40 / 4 |
| Mars | `c_mars` | Four of a Kind | 30 | 3 | 60 / 7 |
| Neptune | `c_neptune` | Straight Flush | 40 | **3** (vanilla 4) | 100 / 8 |

### 3 secret/hidden Planets (`softlock = true`, appear after the secret hand has been played once)
| Name | Key | Hand | +chips/lvl | +mult/lvl | Base (chips/mult @ L1) |
|---|---|---|---|---|---|
| Planet X | `c_planet_x` | Five of a Kind | 35 | 3 | 120 / 12 |
| Ceres | `c_ceres` | Flush House | 40 | **3** (vanilla 4) | 140 / 14 |
| Eris | `c_eris` | Flush Five | **40** (vanilla 50) | 3 | 160 / 16 |

### Black Hole
| Name | Key | Set | Cost | Effect |
|---|---|---|---|---|
| Black Hole | `c_black_hole` | **Spectral** (not Planet) | $4 | Upgrades **every** poker hand by **+1 level** (`config={}`; applies each hand's per-level increment once, using BMP-reworked values) |

## 2.3 Spectral Cards (18)

All `set = "Spectral"`, `cost = 4`. Two hidden "soul" cards (The Soul, Black Hole) never appear in the normal pool. Seals map to `Seal` enum (RED/BLUE/GOLD/PURPLE); editions to `Edition` (FOIL/HOLO/POLY/NEGATIVE). 0.4.0 changelog: only Spectral line is Ouija + Ectoplasm → $4 (bug fix).

### Full key/cost/order reference
| # (order) | Name | Key | Cost | `hidden` | One-line effect |
|---|---|---|---|---|---|
| 1 | Familiar | `c_familiar` | 4 | – | Destroy 1 random card, create **3** enhanced **face** cards (`extra=3`) |
| 2 | Grim | `c_grim` | 4 | – | Destroy 1 random card, create **2** enhanced **Aces** (`extra=2`) |
| 3 | Incantation | `c_incantation` | 4 | – | Destroy 1 random card, create **4** enhanced **numbered** cards (`extra=4`) |
| 4 | Talisman | `c_talisman` | 4 | – | Add **Gold seal** to 1 selected card (`extra='Gold', max_highlighted=1`) |
| 5 | Aura | `c_aura` | 4 | – | Add random edition to 1 selected card: **Foil 50% / Holo 35% / Poly 15%**, no Negative. `can_use`: card has no edition |
| 6 | Wraith | `c_wraith` | 4 | – | Create free random **Rare** joker, set money to $0 |
| 7 | Sigil | `c_sigil` | 4 | – | Convert all hand cards to a single random **suit** |
| 8 | Ouija | `c_ouija` → `c_mp_ouija_standard` | 4 | – | **Vanilla:** all hand→1 rank, **−1 hand size**. **BMP standard:** destroy **3** random, rest→1 rank, no size loss (`extra={destroy=3}`) |
| 9 | Ectoplasm | `c_ectoplasm` | 4 | – | Add **Negative** to a random editionless joker, **escalating −hand size** (`ecto_minus` 1,2,3,…). 0.4.0: cost $4 |
| 10 | Immolate | `c_immolate` | 4 | – | Destroy **5** random cards, gain **$20** (`extra={destroy=5, dollars=20}`) |
| 11 | Ankh | `c_ankh` | 4 | – | Copy **1** joker, destroy all other non-eternal jokers (copy loses Negative; can't copy `mp_phantom`). `extra=2` (display) |
| 12 | Deja Vu | `c_deja_vu` | 4 | – | Add **Red seal** to 1 selected card |
| 13 | Hex | `c_hex` | 4 | – | Add **Polychrome** to a random editionless joker, destroy all other non-eternal jokers (`extra=2`) |
| 14 | Trance | `c_trance` | 4 | – | Add **Blue seal** to 1 selected card |
| 15 | Medium | `c_medium` | 4 | – | Add **Purple seal** to 1 selected card |
| 16 | Cryptid | `c_cryptid` | 4 | – | Create **2** exact copies of 1 selected card (`extra=2, max_highlighted=1`); grows deck `card_limit` |
| 17 | The Soul | `c_soul` | 4 | **yes** | Create a random **Legendary** joker (0.3% pack roll, `effect="Unlocker"`) |
| 18 | Black Hole | `c_black_hole` | 4 | **yes** | Level up **every** poker hand by +1 (0.3% pack roll) |

Notes: Familiar/Grim/Incantation enhancement pool excludes Stone. Ghost Deck (`b_ghost`) starts with one `c_hex`. Soul/Black Hole forced spawn: roll `pseudorandom('soul_'..type..ante) > 0.997` (0.3%) on Tarot/Spectral/Planet pack generation; Soul takes precedence over Black Hole.

### RNG seeds (`pseudoseed`/`pseudorandom` keys)
| Card | Keys |
|---|---|
| Familiar | `random_destroy`, `familiar_create` (rank+suit), `spe_card` (enhancement) |
| Grim | `random_destroy`, `grim_create` (suit), `spe_card` |
| Incantation | `random_destroy`, `incantation_create` (rank+suit), `spe_card` |
| Aura | `aura` (edition poll) |
| Wraith | joker queue via `create_card(..., 'wra')` |
| Sigil | `sigil` (suit) |
| Ouija (vanilla) | `ouija` (rank) |
| Ouija (BMP) | `ouija_destroy` (which 3), `ouija` (new rank via `SMODS.Ranks`) |
| Ectoplasm | `ectoplasm` (joker pick) |
| Immolate | `immolate` (shuffle to pick 5) |
| Ankh | `ankh_choice` (joker to copy) |
| Hex | `hex` (joker pick) |
| Cryptid | (no roll) |
| The Soul | `soul_<type>ante` / joker `'sou'` |
| Black Hole | `soul_<type>ante` |

"The Order" determinism patches: `pseudoshuffle` (Immolate, BMP Ouija) → deterministic sort by `mp_stdval`/`mp_shuffleval`; `pseudorandom_element` (Familiar/Grim/Incantation card pick, Ankh/Hex/Ectoplasm/Wraith joker pick) → top of deterministic value sort; `create_card` → ante-independent single pool (Wraith, Soul); soul/Black-Hole seeds switch from `_type` to the card key. Wraith shares a game-long Rare queue with Rare Skip Tags.

---

# 3. Vouchers (32)

16 Tier-1 + 16 Tier-2 upgrades. All `set = "Voucher"`, `cost = 10`. A Tier-2 carries `requires = {tier1_key}` and only appears/redeems after Tier-1 is redeemed. Base run params the vouchers mutate: `tarot_rate=4`, `planet_rate=4`, `edition_rate=1`, `interest_cap=25`, `base_reroll_cost=5`. BMP 0.4.0 does **not** redefine voucher centers — only bans + The Order queue.

| Col | Tier-1 (key) | order | config | Tier-2 (key) | order | config |
|----|--------------|------|--------|--------------|------|--------|
| 1 | Overstock (`v_overstock_norm`) | 1 | `{}` | Overstock Plus (`v_overstock_plus`) | 2 | `{}` |
| 2 | Tarot Merchant (`v_tarot_merchant`) | 17 | `extra=9.6/4, extra_disp=2` | Tarot Tycoon (`v_tarot_tycoon`) | 18 | `extra=32/4, extra_disp=4` |
| 3 | Planet Merchant (`v_planet_merchant`) | 19 | `extra=9.6/4, extra_disp=2` | Planet Tycoon (`v_planet_tycoon`) | 20 | `extra=32/4, extra_disp=4` |
| 4 | Clearance Sale (`v_clearance_sale`) | 3 | `extra=25` | Liquidation (`v_liquidation`) | 4 | `extra=50` |
| 5 | Hone (`v_hone`) | 5 | `extra=2` | Glow Up (`v_glow_up`) | 6 | `extra=4` |
| 6 | Reroll Surplus (`v_reroll_surplus`) | 7 | `extra=2` | Reroll Glut (`v_reroll_glut`) | 8 | `extra=2` |
| 7 | Crystal Ball (`v_crystal_ball`) | 9 | `extra=3` | Omen Globe (`v_omen_globe`) | 10 | `extra=4` |
| 8 | Telescope (`v_telescope`) | 11 | `extra=3` | Observatory (`v_observatory`) | 12 | `extra=1.5` |
| 9 | Grabber (`v_grabber`) | 13 | `extra=1` | Nacho Tong (`v_nacho_tong`) | 14 | `extra=1` |
| 10 | Wasteful (`v_wasteful`) | 15 | `extra=1` | Recyclomancy (`v_recyclomancy`) | 16 | `extra=1` |
| 11 | Seed Money (`v_seed_money`) | 21 | `extra=50` | Money Tree (`v_money_tree`) | 22 | `extra=100` |
| 12 | Blank (`v_blank`) | 23 | `extra=5` | Antimatter (`v_antimatter`) | 24 | `extra=15` |
| 13 | Magic Trick (`v_magic_trick`) | 25 | `extra=4` | Illusion (`v_illusion`) | 26 | `extra=4` |
| 14 | Hieroglyph (`v_hieroglyph`) ⛔ | 27 | `extra=1` | Petroglyph (`v_petroglyph`) ⛔ | 28 | `extra=1` |
| 15 | Director's Cut (`v_directors_cut`) ⛔ | 29 | `extra=10` | Retcon (`v_retcon`) ⛔ | 30 | `extra=10` |
| 16 | Paint Brush (`v_paint_brush`) | 31 | `extra=1` | Palette (`v_palette`) | 32 | `extra=1` |

⛔ = banned in ranked (attrition) + showdown.

### Exact effects per column
- **1 Overstock / Overstock Plus:** Shop **+1 card slot** (3→4); Plus **+1 more** (4→5), additive.
- **2 Tarot Merchant / Tycoon:** Tarots **2×** more frequent; Tycoon **4×** (replaces, absolute).
- **3 Planet Merchant / Tycoon:** Planets **2×**; Tycoon **4×** (replaces).
- **4 Clearance Sale / Liquidation:** Shop **25% off** (`discount_percent=25`); Liquidation **50% off** (`=50`).
- **5 Hone / Glow Up:** Foil/Holo/Poly **2×** more often; Glow Up **4×** total.
- **6 Reroll Surplus / Glut:** Rerolls **$2 less**; Glut **−$2 more** (−4 total).
- **7 Crystal Ball / Omen Globe:** **+1 consumable slot** (2→3); Omen Globe enables **Spectral in Arcana Packs**.
- **8 Telescope / Observatory:** First Celestial Pack each round contains the Planet for your **most-played hand**; Observatory: Planets in consumable slots give **×1.5 Mult** for their hand type when scored.
- **9 Grabber / Nacho Tong:** **+1 hand** per round; Nacho Tong **+1 more** (+2 total).
- **10 Wasteful / Recyclomancy:** **+1 discard** per round; Recyclomancy **+1 more** (+2 total).
- **11 Seed Money / Money Tree:** interest cap to **$10** (`interest_cap` 25→50); Money Tree to **$20** (→100).
- **12 Blank / Antimatter:** Blank does **nothing** (advances queue / unlocks Antimatter); Antimatter **+1 Joker slot** (5→6).
- **13 Magic Trick / Illusion:** **Playing cards purchasable** from shop; Illusion: shop playing cards may have Enhancements/Editions/Seals.
- **14 Hieroglyph / Petroglyph ⛔:** **−1 Ante** but **−1 hand**/round; Petroglyph: −1 Ante but **−1 discard**/round.
- **15 Director's Cut / Retcon ⛔:** Reroll Boss Blind **once per ante** for **$10**; Retcon: **unlimited** rerolls $10 each.
- **16 Paint Brush / Palette:** **+1 hand size** (8→9); Palette **+1 more** (8→10).

### BMP 0.4.0 ranked bans (authoritative)
`banned_vouchers = { "v_hieroglyph", "v_petroglyph", "v_directors_cut", "v_retcon" }` in both `gamemodes/attrition.lua` and `gamemodes/showdown.lua`. `survival` bans none. Same four as the 0.3.3 design. The Order = deterministic 1–16 column queue (spawn Tier-1 → Tier-2 if owned → skip if both owned; banned keys substituted via `get_next_voucher_key()`; Voucher Tags advance it, collapsing duplicate back-to-back).

---

# 4. Decks (Backs `b_*`)

A Deck is a Back center (`b_*`, `set = "Back"`). Starting modifiers via declarative `config` (read in `Back:apply_to_run`) and/or imperative `apply`/`calculate` hooks.

## 4.1 Vanilla decks (15), all `set = "Back"`, `stake = 1`
| # | Name | Key | `pos` | `config` | Effect |
|---|---|---|---|---|---|
| 1 | Red Deck | `b_red` | {0,0} | `{discards = 1}` | **+1 discard** every round (default starting deck) |
| 2 | Blue Deck | `b_blue` | {0,2} | `{hands = 1}` | **+1 hand** every round |
| 3 | Yellow Deck | `b_yellow` | {1,2} | `{dollars = 10}` | **Start with extra $10** |
| 4 | Green Deck | `b_green` | {2,2} | `{extra_hand_bonus = 2, extra_discard_bonus = 1, no_interest = true}` | End of round: **+$2 per remaining Hand, +$1 per remaining Discard**; **no interest** |
| 5 | Black Deck | `b_black` | {3,2} | `{hands = -1, joker_slot = 1}` | **+1 Joker slot**, **−1 hand** every round |
| 6 | Magic Deck | `b_magic` | {0,3} | `{voucher = 'v_crystal_ball', consumables = {'c_fool','c_fool'}}` | Start with **Crystal Ball** voucher applied + **2 copies of The Fool** |
| 7 | Nebula Deck | `b_nebula` | {3,0} | `{voucher = 'v_telescope', consumable_slot = -1}` | Start with **Telescope** voucher; **−1 consumable slot** |
| 8 | Ghost Deck | `b_ghost` | {6,2} | `{spectral_rate = 2, consumables = {'c_hex'}}` | **Spectrals may appear in shop**; start with **Hex** |
| 9 | Abandoned Deck | `b_abandoned` | {3,3} | `{remove_faces = true}` | Start with **no Face cards** → 40 cards |
| 10 | Checkered Deck | `b_checkered` | {1,3} | `{}` | Start with **26 Spades + 26 Hearts** (Clubs→Spades, Diamonds→Hearts) |
| 11 | Zodiac Deck | `b_zodiac` | {3,4} | `{vouchers = {'v_tarot_merchant','v_planet_merchant','v_overstock_norm'}}` | Start with **Tarot Merchant + Planet Merchant + Overstock** |
| 12 | Painted Deck | `b_painted` | {4,3} | `{hand_size = 2, joker_slot = -1}` | **+2 hand size**, **−1 Joker slot** |
| 13 | Anaglyph Deck | `b_anaglyph` | {2,4} | `{}` | After each **Boss Blind** defeated, gain **1 Double Tag** (`tag_double`) |
| 14 | Plasma Deck | `b_plasma` | {4,2} | `{ante_scaling = 2}` | **Balance Chips & Mult**; **X2 base Blind size** |
| 15 | Erratic Deck | `b_erratic` | {2,3} | `{randomize_rank_suit = true}` | All **52 card ranks & suits randomized** at start |

(`b_challenge` {0,4} order 16, `omit = true` — hidden Challenge back, not selectable.)

## 4.2 BMP 0.4.0 decks (keys `b_mp_*`)
**Naming:** the 0.3.3 "Purple Deck" is now **Violet Deck** (`b_mp_violet`), expanded. **White Deck** (`b_mp_white`) is shipped but **fully commented out / inactive** in 0.4.0.

| Name | Key | Starting `config` | `apply` / `calculate` effect |
|---|---|---|---|
| Orange Deck | `b_mp_orange` | `{consumables = {'c_hanged_man','c_hanged_man'}}` | Start with **2 × The Hanged Man** + opens a **Giga Standard Pack** (`p_mp_standard_giga`) at run start |
| Violet Deck | `b_mp_violet` | `{}` | **+1 Voucher slot** in shop; Voucher cost: **Ante 1 → 50% off**, **Ante 2 → 30% off** (min $1, stacks with `discount_percent`) |
| Indigo Deck | `b_mp_indigo` | `{}` | **+1 additional card chosen from every Booster Pack** (`booster_choice_mod += 1`); **bans `j_red_card`**; packs **unskippable** while any card is usable |
| Oracle Deck | `b_mp_oracle` | `{vouchers = {'v_clearance_sale'}, consumables = {'c_medium'}}` | Start with **Clearance Sale** + **1 × Medium**; **balance capped at $50 + current interest cap** (`oracle_max = 50`) |
| Gradient Deck | `b_mp_gradient` | `{}` | Cards count as **one rank higher OR lower** for all Joker effects (shifts `base.id` by −1/0/+1, wraps Ace↔King); also patches `is_face` and Cloud 9's nine-tally. Blacklist Photograph/Faceless/Ramen; passkey Superposition/Sixth Sense |
| Heidelberg Deck | `b_mp_heidelberg` | *(none)* | At **end of every shop**, if you hold ≥1 consumable, make a **Negative copy** of 1 random consumable (`pseudoseed("mp_heidelberg")`) |
| Echo Deck | `b_mp_echodeck` | `{}` | `ante_scaling = 1.2` (**X1.2 base Blind size**); **Retrigger all played cards once** (and held cards with effects); blind size **+X0.2 per Ante** at each Boss round |
| Cocktail Deck | `b_mp_cocktail` | `{}` (dynamic) | "Copies all effects of **3 other decks** at random." Merges each chosen deck's config (numbers add, tables merge, `voucher`→`vouchers`) + runs their apply/calculate. Whitelist: Multiplayer, Cryptid, aikoyorisshenanigans, allinjest. Blacklists itself, Challenge, `b_cry_antimatter`, `b_akyrs_hardcore_challenges` |

(Inactive: `b_mp_white` — "View Nemesis' current deck and Joker setup, updates at PvP blind" — commented out. `b_purple` — does not exist as a 0.4.0 object; realized as Violet.)

**Starting-modifier keys engine must support:** round resets (`hands`, `discards`, `joker_slot`, `consumable_slot`, `hand_size`, `dollars` Δ); round economy (`extra_hand_bonus`, `extra_discard_bonus`, `no_interest`); blind scaling (`ante_scaling`: Plasma=2, Echo=1.2 increasing +0.2/ante); shop (`spectral_rate`, voucher-limit Δ, per-ante voucher cost multipliers, `discount_percent`); starting inventory (`voucher`/`vouchers[]`, `consumables[]`, open-pack-on-start); construction flags (`remove_faces`, `randomize_rank_suit`, suit conversion); banned keys (Indigo bans `j_red_card`).

---

# 5. Packs, Editions, Enhancements & Seals

## 5.1 Booster Packs
All vanilla packs: `set='Booster'`, `atlas='Booster'`. `config.extra` = cards generated; `config.choose` = picks.

| Internal key | Name | Kind | Cost | `extra` | `choose` | Shop weight |
|---|---|---|---|---|---|---|
| `p_arcana_normal_1..4` | Arcana Pack | Arcana | 4 | 3 | 1 | 1 |
| `p_arcana_jumbo_1..2` | Jumbo Arcana Pack | Arcana | 6 | 5 | 1 | 1 |
| `p_arcana_mega_1..2` | Mega Arcana Pack | Arcana | 8 | 5 | 2 | 0.25 |
| `p_celestial_normal_1..4` | Celestial Pack | Celestial | 4 | 3 | 1 | 1 |
| `p_celestial_jumbo_1..2` | Jumbo Celestial Pack | Celestial | 6 | 5 | 1 | 1 |
| `p_celestial_mega_1..2` | Mega Celestial Pack | Celestial | 8 | 5 | 2 | 0.25 |
| `p_spectral_normal_1..2` | Spectral Pack | Spectral | 4 | 2 | 1 | 0.3 |
| `p_spectral_jumbo_1` | Jumbo Spectral Pack | Spectral | 6 | 4 | 1 | 0.3 |
| `p_spectral_mega_1` | Mega Spectral Pack | Spectral | 8 | 4 | 2 | 0.07 |
| `p_standard_normal_1..4` | Standard Pack | Standard | 4 | 3 | 1 | 1 |
| `p_standard_jumbo_1..2` | Jumbo Standard Pack | Standard | 6 | 5 | 1 | 1 |
| `p_standard_mega_1..2` | Mega Standard Pack | Standard | 8 | 5 | 2 | 0.25 |
| `p_buffoon_normal_1..2` | Buffoon Pack | Buffoon | 4 | 2 | 1 | 0.6 |
| `p_buffoon_jumbo_1` | Jumbo Buffoon Pack | Buffoon | 6 | 4 | 1 | 0.6 |
| `p_buffoon_mega_1` | Mega Buffoon Pack | Buffoon | 8 | 4 | 2 | 0.15 |

**MP-exclusive Giga Standard Pack** (`p_mp_standard_giga`, Orange Deck only): kind Standard, `extra` **10**, `choose` **4**, cost **16**, weight **0** (never random), `unskippable=true`. Per card: edition via `poll_edition("standard_edition"..append, 2, true)`, seal via `SMODS.poll_seal({mod=10, key="stdseal"..append})`, `set = (pseudorandom(...) > 0.6) and "Enhanced" or "Base"` (~40% Enhanced). `append = MP.ante_based() .. s_append` (deterministic per ante).

**MP pack-queue model:** game-long shared pack queue (advances on *view*). Arcana/Celestial/Spectral each draw their own consumable pack-queue. Buffoon packs draw the **Shop Queue** (Normal=next 2 Jokers; Jumbo & Mega=next 4). Standard packs draw a game-long **Playing-Card queue** (separate from Magic Trick's).

## 5.2 Editions
| Key | Name | `config.extra` | Effect |
|---|---|---|---|
| `e_foil` | Foil | 50 | **+50 Chips** |
| `e_holo` | Holographic | 10 | **+10 Mult** |
| `e_polychrome` | Polychrome | 1.5 | **×1.5 Mult** |
| `e_negative` | Negative | 1 | On a **Joker:** +1 joker slot. On a **consumable:** +1 consumable slot. No scoring effect |

**Edition tags** (grant a free edition on a shop joker): `tag_negative`→negative, `min_ante=2`, `odds=5`; `tag_foil`→foil, `odds=2`; `tag_holo`→holo, `odds=3`; `tag_polychrome`→polychrome, `odds=4`.

**MP-exclusive Phantom** (`e_mp_phantom`): `in_shop=false`, voucher shader. `on_apply` sets `eternal=true` + `mp_sticker_nemesis=true`. `extra_cost=0`, global min sell value set to −1 (shows no sell value). No chips/mult — structural ownership marker for Nemesis/shared-joker mechanics.

## 5.3 Enhancements
All `set="Enhanced"`, `max=500`.
| Key | Name | Config | Effect |
|---|---|---|---|
| `m_bonus` | Bonus Card | `bonus = 30` | **+30 Chips** |
| `m_mult` | Mult Card | `mult = 4` | **+4 Mult** |
| `m_wild` | Wild Card | `{}` | Card **counts as every suit** |
| `m_glass` | Glass Card | vanilla `Xmult=2, extra=4` | **×Mult on score; 1/`extra` shatter.** Vanilla ×2, 1-in-4. **BMP rework: see below** |
| `m_steel` | Steel Card | `h_x_mult = 1.5` | **×1.5 Mult while held in hand** |
| `m_stone` | Stone Card | `bonus = 50` | **+50 Chips**, no rank/suit, always scores |
| `m_gold` | Gold Card | `h_dollars = 3` | **+$3 at end of round if held in hand.** See §5.5 |
| `m_lucky` | Lucky Card | `mult = 20, p_dollars = 20` | **1 in 5: +20 Mult. 1 in 15: +$20** |

**Glass rework (BMP 0.4.0):** `MP.ReworkCenter("m_glass", { layers = {"standard","classic"}, config = {Xmult=1.5, extra=4} })` → Standard/Classic Glass = **×1.5 Mult, 1-in-4 break**. Sandbox: `{Xmult=1.5, extra=3}` → ×1.5, 1-in-3. Glass breaks operate on a game-long shared break queue, left-to-right.

MP enhancement bans: `m_gold` and `m_lucky` are bannable mutators (`layers/ban_mutators.lua`), not a blanket ban; standard layer reworks only `m_glass`.

## 5.4 Seals
Bare markers (no config; effects hard-coded). `P_SEALS` order: Red=1, Blue=2, Gold=3, Purple=4.
| Key | Name | Effect |
|---|---|---|
| `Red` | Red Seal | **Retrigger the card once** (+1 repetition) when it scores |
| `Blue` | Blue Seal | Creates the **Planet card** for the final played poker hand when held at end of round (if free consumable slot) |
| `Gold` | Gold Seal | **+$3 when the card scores** (stacks with Red-seal retriggers) |
| `Purple` | Purple Seal | Creates a **Tarot card** when the card is **discarded** (if free slot; from shared "Up Top Tarot" queue) |

## 5.5 Gold $4 discrepancy (FLAGGED)
- CHANGELOG: Gold **Card (Enhancement)** payout $3→$4. **No `ReworkCenter("m_gold")` exists on disk**; base value is `h_dollars = 3`. Treat as **$3 in code / $4 per CHANGELOG — unresolved; verify at runtime.**
- Gold **Seal** $3→$4 rework is **commented out** in `objects/seals/mp_gold_seal.lua` → Gold Seal still pays **$3** in 0.4.0.
- `j_ticket` (Golden Ticket joker) is a distinct per-Gold-card payout; CHANGELOG reverts it to $4.

---

# 6. Skip Tags (24)

`config.type` drives the trigger phase. "Separate queue" = the tag uses its own `pseudoseed` key, advancing a sequence the shop never touches. `min_ante` = earliest ante. Effects reflect **BMP 0.4.0**.

| # | Name | Key | `config.type` | Effect (BMP 0.4.0) | min_ante | Queue |
|---|---|---|---|---|---|---|
| 1 | Uncommon Tag | `tag_uncommon` | `store_joker_create` | Adds an **Uncommon** joker to shop. **0.4.0: full price** (vanilla free) | — | Separate `uta` queue |
| 2 | Rare Tag | `tag_rare` | `store_joker_create` (`odds=3`) | Adds a **Rare** joker; if you own every distinct Rare → `nope()`. **0.4.0: full price.** Requires `j_blueprint` discovered | — | Separate `rta` queue, **shared with Wraith** |
| 3 | Negative Tag | `tag_negative` | `store_joker_modify` (`edition='negative'`, `odds=5`) | Next base-edition shop **Joker** → **Negative**. Requires `e_negative` | 2 | In-place (shop joker queue) |
| 4 | Foil Tag | `tag_foil` | `store_joker_modify` (`edition='foil'`, `odds=2`) | Next base-edition shop Joker → **Foil** (+50 chips) | — | In-place |
| 5 | Holographic Tag | `tag_holo` | `store_joker_modify` (`edition='holo'`, `odds=3`) | Next base-edition shop Joker → **Holographic** (+10 Mult) | — | In-place |
| 6 | Polychrome Tag | `tag_polychrome` | `store_joker_modify` (`edition='polychrome'`, `odds=4`) | Next base-edition shop Joker → **Polychrome** (×1.5 Mult) | — | In-place |
| 7 | Investment Tag | `tag_investment` | `eval` | Gain **$15** after defeating the Boss Blind. **0.4.0 DELTA: $15** (vanilla $25) | — | none |
| 8 | Voucher Tag | `tag_voucher` | `voucher_add` | Adds **one** Voucher to shop (`get_next_voucher_key(true)`; `card_limit += 1`) | — | Advances Voucher queue (1–16) |
| 9 | Boss Tag | `tag_boss` | `new_blind_choice` | **Rerolls the Boss Blind** (`reroll_boss()`). **BANNED in ranked** | — | none |
| 10 | Standard Tag | `tag_standard` | `new_blind_choice` | Opens free **Mega Standard Pack** (`p_standard_mega_1`): 5 cards, pick 2 | 2 | Pack queue (Standard) |
| 11 | Charm Tag | `tag_charm` | `new_blind_choice` | Opens free **Mega Arcana Pack** (`p_arcana_mega_1`/`_2`): 5 Tarots, pick 2 | — | Pack queue (Arcana) |
| 12 | Meteor Tag | `tag_meteor` | `new_blind_choice` | Opens free **Mega Celestial Pack** (`p_celestial_mega_1`/`_2`): 5 Planets, pick 2 | 2 | Pack queue (Celestial) |
| 13 | Buffoon Tag | `tag_buffoon` | `new_blind_choice` | Opens free **Mega Buffoon Pack** (`p_buffoon_mega_1`): 4 Jokers, pick 2 | 2 | Pack queue (Buffoon) |
| 14 | Handy Tag | `tag_handy` | `immediate` (`dollars_per_hand=1`) | Gain **$1 per hand played** this run (`hands_played × 1`) | 2 | none |
| 15 | Garbage Tag | `tag_garbage` | `immediate` (`dollars_per_discard=1`) | Gain **$1 per unused discard** this run (`unused_discards × 1`) | 2 | none |
| 16 | Ethereal Tag | `tag_ethereal` | `new_blind_choice` | Opens free **Spectral Pack** (`p_spectral_normal_1`): 2 Spectrals, pick 1 | 2 | Pack queue (Spectral) |
| 17 | Coupon Tag | `tag_coupon` | `shop_final_pass` | All **shop Jokers and Booster Packs** cost **$0** this shop (`shop_free`). Playing cards/vouchers unaffected | — | none |
| 18 | Double Tag | `tag_double` | `tag_add` | Copies the next selected tag (won't copy another Double; carries `orbital_hand`) | — | none (copy runs its own) |
| 19 | Juggle Tag | `tag_juggle` | `round_start_bonus` | **+3 hand size** for next round only (`h_size=3`) | — | none |
| 20 | D6 Tag | `tag_d_six` | `shop_start` | Shop **rerolls start at $0** this shop (`temp_reroll_cost=0`) | — | none |
| 21 | Top-up Tag | `tag_top_up` | `immediate` (`spawn_jokers=2`) | Creates up to **2 Common Jokers** into joker area | 2 | Separate Common-joker queue, **shared with Riff-Raff** |
| 22 | Skip Tag | `tag_skip` | `immediate` (`skip_bonus=5`) | Gain **$5 × blinds skipped this run** (`skips × 5`) | — | none |
| 23 | Orbital Tag | `tag_orbital` | `immediate` (`levels=3`) | **+3 levels** to a poker hand (fixed `orbital_hand`, else `pseudorandom_element(visible, pseudoseed('orbital'))`) | 2 | own `pseudoseed('orbital')` stream |
| 24 | Economy Tag | `tag_economy` | `immediate` (`max=40`) | **Doubles money, capped +$40** (`ease_dollars(min(40, max(0, dollars)))`) | — | none |

**Queue sharing:** Uncommon→`uta`; Rare→`rta` (shared with Wraith); Top-up→common queue (shared with Riff-Raff); pack tags share the Pack sub-queue of their type (incl. Giga on Orange); Voucher Tag → Voucher queue; Orbital → `pseudoseed('orbital')`. Sandbox layer bans `tag_rare`, `tag_juggle`, `tag_investment`.

---

# 7. Boss Blinds

Chip requirement = `get_blind_amount(ante) * self.mult * G.GAME.starting_params.ante_scaling`. Small Blind `mult=1`, Big Blind `mult=1.5`. **All standard bosses `mult=2`** except: The Wall=4, The Needle=1, The Flint=2 (halves score instead), Violet Vessel=6. `dollars` = $5 standard / $8 finishers (Small $3, Big $4). `boss = {min, max}`; `max=10` everywhere (no real cap); `min` = earliest ante. No 0.4.0 boss changes vs vanilla/0.3.3.

## 7.1 Regular Boss Blinds
| Name | Key | `mult` | $ | min ante | Effect |
|------|-----|:--:|:--:|:--:|--------|
| The Hook | `bl_hook` | 2 | 5 | 1 | After each hand, **discards 2 random cards from hand** (`pseudoseed('hook')`) |
| The Ox | `bl_ox` | 2 | 5 | 6 | Playing your **most-played poker hand sets money to $0** |
| The House | `bl_house` | 2 | 5 | 2 | **First hand drawn face down** |
| The Wall | `bl_wall` | **4** | 5 | 2 | **Extra-large blind** (×2 chips). **BANNED in all PvP gamemodes** |
| The Wheel | `bl_wheel` | 2 | 5 | 2 | **1 in 7 cards drawn face down** |
| The Arm | `bl_arm` | 2 | 5 | 2 | **Decreases level of played poker hand by 1** (min level 1) |
| The Club | `bl_club` | 2 | 5 | 1 | **All Club cards debuffed** |
| The Fish | `bl_fish` | 2 | 5 | 2 | **Cards drawn face down after each played hand** |
| The Psychic | `bl_psychic` | 2 | 5 | 1 | **Must play exactly 5 cards** |
| The Goad | `bl_goad` | 2 | 5 | 1 | **All Spade cards debuffed** |
| The Water | `bl_water` | 2 | 5 | 2 | **Start with 0 discards** |
| The Window | `bl_window` | 2 | 5 | 1 | **All Diamond cards debuffed** |
| The Manacle | `bl_manacle` | 2 | 5 | 1 | **−1 hand size** |
| The Eye | `bl_eye` | 2 | 5 | 3 | **No repeat hand types this round** (each type scores once) |
| The Mouth | `bl_mouth` | 2 | 5 | 2 | **Only one hand type allowed this round** |
| The Plant | `bl_plant` | 2 | 5 | 4 | **All face cards debuffed** |
| The Serpent | `bl_serpent` | 2 | 5 | 5 | **Always draw 3 cards after play/discard** (count unverified from disk) |
| The Pillar | `bl_pillar` | 2 | 5 | 1 | **Cards played this ante (since last boss) are debuffed** |
| The Needle | `bl_needle` | **1** | 5 | 2 | **Play only 1 hand this round** |
| The Head | `bl_head` | 2 | 5 | 1 | **All Heart cards debuffed** |
| The Tooth | `bl_tooth` | 2 | 5 | 3 | **Lose $1 per card played** |
| The Flint | `bl_flint` | 2 | 5 | 2 | **Base Chips and Mult of played hand halved** (`floor(x*0.5+0.5)`, mins 1/0) |
| The Mark | `bl_mark` | 2 | 5 | 2 | **All face cards drawn face down** |

## 7.2 Finisher Boss Blinds (`boss = {showdown = true}`, ante 8 and multiples)
All `dollars = 8`.
| Name | Key | `mult` | $ | Effect |
|------|-----|:--:|:--:|--------|
| Amber Acorn | `bl_final_acorn` | 2 | 8 | **Flips and shuffles all Jokers face down** at start (RNG key `'aajk'`); un-flip on defeat |
| Verdant Leaf | `bl_final_leaf` | 2 | 8 | **All played cards debuffed until 1 Joker is sold** |
| Violet Vessel | `bl_final_vessel` | **6** | 8 | **Very large blind** (×3 chips). **BANNED in all PvP gamemodes** |
| Crimson Heart | `bl_final_heart` | 2 | 8 | **One random Joker disabled each hand** (`pseudoseed('crimson_heart')`); rotates, avoids re-picking previous |
| Cerulean Bell | `bl_final_bell` | 2 | 8 | **Forces 1 card to always be selected** (`pseudoseed('cerulean_bell')`, auto-highlights) |

## 7.3 MP PvP Boss — `bl_mp_nemesis` ("Your Nemesis")
`SMODS.Blind`: `key="nemesis"` (full `bl_mp_nemesis`), `dollars=5`, `mult=1` (0 crashes Jen's Almanac), `boss_colour=G.C.MULTIPLAYER`, `boss={min=1, max=10}`, `discovered=true`, `in_pool = function(self) return false end` (never in random pool — force-assigned). Effect: *"Face another player, most chips wins."* `MP.is_pvp_boss()` true iff `G.GAME.blind.config.blind.key=="bl_mp_nemesis"` or `G.GAME.blind.pvp`.

**Per-gamemode assignment** (via `get_blinds_by_ante(ante)`):
| Gamemode | PvP boss rule |
|----------|---------------|
| Showdown | When `ante >= showdown_starting_antes` (default **3**): all three blinds become `bl_mp_nemesis` |
| Attrition | When `ante >= pvp_start_round` (default **2**): if not `normal_bosses` → Boss = `bl_mp_nemesis`; if `normal_bosses` → keep vanilla boss but mark `pvp_blind_choices.Boss=true` |
| Survival | `return nil,nil,nil` — **no PvP boss**, pure vanilla |

## 7.4 Bans & boss-interacting content (PvP modes)
- **Banned bosses (Showdown + Attrition):** The Wall (`bl_wall`, mult 4), Violet Vessel (`bl_final_vessel`, mult 6). Survival: not banned. No ruleset adds a boss ban.
- **Banned tag:** `tag_boss` (Boss Reroll Tag) in Showdown + Attrition.
- **Banned jokers (Showdown + Attrition):** `j_mr_bones`, `j_luchador`, `j_matador`, `j_chicot` (anti-boss / boss-disabling).
- **Banned vouchers (Showdown + Attrition):** `v_hieroglyph`, `v_petroglyph`, `v_directors_cut`, `v_retcon`.

**Per-blind RNG keys:** `'hook'`, `'wheel'`, `'cerulean_bell'`, `'crimson_heart'`, `'aajk'` (Amber Acorn shuffle).
