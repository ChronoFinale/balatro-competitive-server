# 46 — Joker as-built ledger (real effect → our model → is it faithful)

> The single place that ties every joker to **what it really is** and **how it actually fits our
> mechanism** — and, crucially, whether the two match. Docs 10–13 catalogue the *real* effects (grounded
> in BMP 0.4.0 + game.lua) and judge *expressibility*; this ledger records the **as-built** model and its
> **faithfulness status**, so a discrepancy is written down instead of discovered by accident.
>
> This exists because content errors kept slipping through: values were hand-transcribed and the tests
> only ever checked the code against itself (see `JokerStatsAuditTest` for the cost/rarity gate that now
> diffs against ground truth). This ledger is the manual counterpart for *effect* fidelity, filled in
> slowly, joker by joker.

## Target & sources

- **Target ruleset: BMP 0.4.0 ranked.** Most BMP reworks are disabled (commented out in `release.lua`),
  so **vanilla `game.lua` values are live** *except* the enumerated BMP deltas (e.g. Seltzer 8 hands,
  Turtle Bean +4 hand size, Golden Ticket $3) — those are captured in our `variants("multiplayer")`.
- **Real effect:** docs 10–13 (per rarity) + `D:\BalatroMod\Balatro\game.lua` (config) + `card.lua` (calc).
- **Our model:** `BuiltinJokerDefs.java` defs (the fluent rules).

## Status legend

- ✅ **faithful** — modeled effect, trigger, condition, and magnitude match the real joker.
- ⚠️ **approximation** — close but knowingly off (timing, an edge case, or a stand-in for a missing primitive).
- ❌ **gap/bug** — wrong or missing behavior; needs fixing.
- 🔧 **fixed** — was wrong, now corrected (kept here as a scar so we don't regress).

## Coverage so far

Cost & rarity for every vanilla joker are now gated automatically (`JokerStatsAuditTest` vs game.lua), so
this ledger tracks **effect fidelity**. A 4-way audit (2026-06) cross-checked all ~158 jokers against
game.lua + card.lua; the commons batch (51 jokers) came back fully faithful. Entries below capture every
joker that is *not* a plain ✅, plus the fixes already made. The long tail of faithful jokers will be
filled in rarity by rarity.

## Ledger

### Fixed this session (🔧 — were wrong, now correct)

| Joker | Was | Now (real) | Source |
|---|---|---|---|
| Gros Michel | +15 Mult, no destroy | +15 Mult; 1-in-6 destroy at end of round | card.lua:3019 |
| Cavendish | x3 Mult, no destroy; cost 5 | x3 Mult; 1-in-1000 destroy; cost 4 | card.lua:3019, game.lua |
| Ice Cream | +100/−5 chips, clamp-to-0 (never consumed) | consumed when chips hit 0 | card.lua |
| Popcorn | +20/−4 mult, clamp-to-0 | consumed when mult hits 0 | card.lua:2945 |
| Ramen | x2/−0.01, clamp-to-1 | consumed when it hits x1 | card.lua:2757 |
| Mail-In Rebate | $3/card | $5/card | doc 10 / game.lua |
| Merry Andy | +1 discard | +3 discards | game.lua (`d_size=3`) |
| Acrobat | cost 7 | cost 6 | game.lua |
| Bootstraps | cost 6 | cost 7 | game.lua |
| Superposition | cost 5 | cost 4 | game.lua |
| Reserved Parking | cost 5 | cost 6 | game.lua |
| Obelisk | Legendary $20 (wrong pool!) | Rare $8 | game.lua |
| Showman/Astronomer/To-the-Moon | key-string detection | folded policy vars | — |
| Pizza/Speedrun | behaviorInCode (no data) | PvP-trigger data | — |

### Open — ⚠️ approximations / ❌ gaps

| Joker | Status | Real effect | Our model | The gap / what it needs |
|---|---|---|---|---|
| Turtle Bean | ❌ | +5 hand size, −1/round, **destroyed at 0** | `decayingHandSize(5)`, floors at 0 forever | self-destruct at 0; needs a rounds-since-acquired condition + `DestroySelf` |
| Certificate | ⚠️ | adds a sealed card on **first hand drawn** | fires on `BLIND_SELECTED` (proxy) | `FIRST_HAND_DRAWN` trigger is not raised in the engine — wire it, then retarget |
| Canio | ⚠️ | gains x1 on a face card **destroyed OR removed** | only `CARD_DESTROYED` | `REMOVE_PLAYING_CARDS` trigger not raised — wire it, add a second rule |
| Loyalty Card | ⚠️ | x4 Mult every 6 hands **since acquired** | global `runVarModulo(HANDS_PLAYED_TOTAL, 6, 5)` | needs a since-acquire modulo (+ correct offset) |
| Joker Stencil | ⚠️ | xMult = empty slots **+ # of Stencils**, dynamic card limit | `1 + EMPTY_JOKER_SLOTS` (hardcodes 5 slots) | correct for 1 stencil & 5 slots; off for multi-stencil / modified slot count |
| Astronomer | ⚠️ | Planets **and Celestial Packs** free | only `PLANETS_FREE` | add a free-celestial-packs policy |
| Let's Go Gambling | ⚠️ | also 1-in-4 to **drain Nemesis $10** (PvP) | x4 Mult + $10 only | opponent-money grant (like Pizza's, but money) |
| Ice Cream | ⚠️ | destroyed **per-hand** when chips hit 0 | destroyed at **end of round** when depleted | timing approximation (actually-consumed now vs never before) |

### Verified faithful (✅)

- **All 51 commons** in the L117–300 audit batch (suit-mult & type families, Joker, Sly, Half, Even Steven,
  Hack, Banner, Scary Face, Odd Todd, Square, Scholar, Walkie Talkie, Fibonacci, Baron, Runner, the Duo/Trio/
  Family/Order/Tribe, Arrowhead/Onyx, Sock & Buskin, Misprint, Bloodstone, Blue Joker, …).
- Satellite (per **unique** planet — confirmed correct; an audit false-positive), Hit the Road (per-jack total
  is equivalent — false-positive), and the rest of the L300–826 batches not listed above.

> _Next to fill: the remaining uncommons/rares as plain-✅ rows, rarity by rarity, so the whole set is
> written down here and any future drift shows up as a status change._
