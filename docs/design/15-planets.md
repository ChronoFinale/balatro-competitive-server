# 15 — Planet Cards & Black Hole (Balatro Multiplayer 0.4.0)

Definitive catalogue of every Planet card plus Black Hole: the poker hand each levels and the **exact** chips/mult increment applied per level-up.

## How leveling works (mechanics)

A Planet card is a `consumeable` with `set = "Planet"` and `effect = "Hand Upgrade"`. Using one increments the **level** of exactly one poker hand by 1 (`config.hand_type`). Each level-up adds a fixed increment to that hand's base **chips** and base **mult**:

- `l_chips` = chips added per level
- `l_mult`  = mult added per level

So a hand at level `N` has `baseChips + (N-1)*l_chips` chips and `baseMult + (N-1)*l_mult` mult. (Hands start at level 1.)

Source for all base values + increments: the `G.GAME.hands` / `SMODS.PokerHands` table in the decompiled base game —
`C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua:2025-2036`.
Planet center definitions (`hand_type`, cost, set): `game.lua:566-577`. Black Hole: `game.lua:597`.

## 0.4.0 DELTA — BMP reworks four hand-scaling values

BMP 0.4.0 ships a "release" ruleset that **overrides the per-level increments** for four hands via `MP.ReworkCenter(...)` against `SMODS.PokerHands`. The Planet cards themselves are unchanged in behaviour (the `c_saturn`/`c_neptune`/`c_ceres`/`c_eris` ReworkCenter calls are explicitly commented *"no behaviour change, just so it shows the sticker"*) — but because they level these hands, the **effective per-level numbers differ from vanilla**.

Source: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/rulesets/release.lua:685-724`.

| Hand | Vanilla per-level | BMP 0.4.0 release per-level | Leveled by |
|---|---|---|---|
| Straight | `l_mult = 3` | **`l_mult = 2`** | Saturn |
| Straight Flush | `l_mult = 4` | **`l_mult = 3`** | Neptune |
| Flush House | `l_mult = 4` | **`l_mult = 3`** | Ceres |
| Flush Five | `l_chips = 50` | **`l_chips = 40`** | Eris |

`l_chips` for Straight/Straight Flush and `l_mult` for Flush House/Flush Five are NOT overridden, so they keep vanilla values. The CHANGELOG.md for 0.4.0 does not mention these hand reworks (it only mentions To Do List and Ouija for hand-related items); the change is only visible in `rulesets/release.lua`. Treat the source as authoritative.

**These are "release"-ruleset values.** BMP 0.4.0 has multiple rulesets (`rulesets/`). The numbers in this doc are the *release* ruleset (default ranked/online). If a different ruleset is active, re-derive from that file.

## The standard 9 Planet cards

All cost **$3**, `consumeable = true`, `set = "Planet"`, `effect = "Hand Upgrade"`, `freq = 1`, `cost_mult = 1.0`. Increments below are the **effective BMP 0.4.0 release** values (vanilla noted where they differ).

| Name | Key | Hand leveled | +chips/level (`l_chips`) | +mult/level (`l_mult`) | Base (chips/mult @ L1) | Notes |
|---|---|---|---|---|---|---|
| Pluto | `c_pluto` | High Card | 10 | 1 | 5 / 1 | |
| Mercury | `c_mercury` | Pair | 15 | 1 | 10 / 2 | |
| Uranus | `c_uranus` | Two Pair | 20 | 1 | 20 / 2 | |
| Venus | `c_venus` | Three of a Kind | 20 | 2 | 30 / 3 | |
| Saturn | `c_saturn` | Straight | 30 | **2** (vanilla 3) | 30 / 4 | **0.4.0 delta: l_mult 3→2** |
| Jupiter | `c_jupiter` | Flush | 15 | 2 | 35 / 4 | |
| Earth | `c_earth` | Full House | 25 | 2 | 40 / 4 | |
| Mars | `c_mars` | Four of a Kind | 30 | 3 | 60 / 7 | |
| Neptune | `c_neptune` | Straight Flush | 40 | **3** (vanilla 4) | 100 / 8 | **0.4.0 delta: l_mult 4→3** |

## The 3 secret/hidden Planet cards (level the secret hands)

These are `softlock = true` and only appear once the corresponding secret hand has been played at least once. Same cost ($3) / set / effect as the standard planets. Source: `game.lua:575-577`; base hand stats `game.lua:2025-2027`.

| Name | Key | Hand leveled | +chips/level (`l_chips`) | +mult/level (`l_mult`) | Base (chips/mult @ L1) | Notes |
|---|---|---|---|---|---|---|
| Planet X | `c_planet_x` | Five of a Kind | 35 | 3 | 120 / 12 | No 0.4.0 change |
| Ceres | `c_ceres` | Flush House | 40 | **3** (vanilla 4) | 140 / 14 | **0.4.0 delta: l_mult 4→3** |
| Eris | `c_eris` | Flush Five | **40** (vanilla 50) | 3 | 160 / 16 | **0.4.0 delta: l_chips 50→40** |

## Black Hole

| Name | Key | Set | Cost | Effect | Trigger |
|---|---|---|---|---|---|
| Black Hole | `c_black_hole` | **Spectral** (not Planet) | $4 | Upgrades **every** poker hand by **+1 level** | On use (`use_consumeable`) |

Source: `game.lua:597` — `set = "Spectral", hidden = true, cost = 4`. Black Hole is technically a Spectral card, not a Planet, but it is the only card that levels all 12 hands simultaneously. Each hand gains exactly one level, i.e. it adds that hand's own `l_chips`/`l_mult` once (using the **BMP-reworked** increments above for the four affected hands). No flat number of its own — it just applies each hand's per-level increment.

The base game has no separate config numbers for Black Hole (`config = {}`); its "+1 to all hands" is hard-coded in `use_consumeable` for `c_black_hole`. **Unverified detail:** the exact in-function code block was not located by grep in the dumps (only the center def at line 597 was found); the "+1 level to all hands" behaviour is standard vanilla Balatro and is consistent with the center having no per-hand config. Flag for confirmation against `evaluate_play`/`use_consumeable` if precise hand-set iteration matters.

## Complete per-level reference table (effective BMP 0.4.0 release)

For implementing the level engine — every leveled hand, its base, and its increment. (Sorted by base scoring order.)

| Hand | `l_chips` | `l_mult` | Base chips | Base mult | Leveling planet | Also via Black Hole? |
|---|---|---|---|---|---|---|
| Flush Five | 40 *(was 50)* | 3 | 160 | 16 | Eris | yes |
| Flush House | 40 | 3 *(was 4)* | 140 | 14 | Ceres | yes |
| Five of a Kind | 35 | 3 | 120 | 12 | Planet X | yes |
| Straight Flush | 40 | 3 *(was 4)* | 100 | 8 | Neptune | yes |
| Four of a Kind | 30 | 3 | 60 | 7 | Mars | yes |
| Full House | 25 | 2 | 40 | 4 | Earth | yes |
| Flush | 15 | 2 | 35 | 4 | Jupiter | yes |
| Straight | 30 | 2 *(was 3)* | 30 | 4 | Saturn | yes |
| Three of a Kind | 20 | 2 | 30 | 3 | Venus | yes |
| Two Pair | 20 | 1 | 20 | 2 | Uranus | yes |
| Pair | 15 | 1 | 10 | 2 | Mercury | yes |
| High Card | 10 | 1 | 5 | 1 | Pluto | yes |

## Cross-check vs our current engine

`D:/NewServer/src/main/java/com/balatromp/engine/hand/HandType.java` already models `(baseChips, baseMult, lChips, lMult)` per hand and `RunState` tracks levels with `handLevelChipBonus = (level-1)*lChips` / `handLevelMultBonus = (level-1)*lMult` (`RunState.java:69-74`). This matches the vanilla formula exactly.

**BUT our HandType currently hard-codes VANILLA increments**, not the BMP 0.4.0 release values:
- `HandType.java:13` `STRAIGHT("Straight", 30, 4, 30, 3)` — lMult should be **2**
- `HandType.java:17` `STRAIGHT_FLUSH(..., 40, 4)` — lMult should be **3**
- `HandType.java:19` `FLUSH_HOUSE(..., 40, 4)` — lMult should be **3**
- `HandType.java:20` `FLUSH_FIVE(..., 50, 3)` — lChips should be **40**

These four constants must be changed to be faithful to BMP 0.4.0 release ruleset.

## Open questions

1. **Black Hole exact code path:** the `+1 to all hands` behaviour was not located in either game.lua dump by grep — only the center definition. Confirm the iteration set (does it touch hidden hands like Flush Five even if undiscovered? Vanilla: yes, it levels all 12). Currently treated as "all 12 hands +1 level" (unverified for the exact hand list).
2. **Ruleset selection:** the four reworked increments live in `rulesets/release.lua`. Do non-release rulesets (others in `rulesets/`) restore vanilla numbers? If our engine must support multiple rulesets, the increments need to be ruleset-parameterized, not enum constants.
3. **To Do List / Ouija interactions (CHANGELOG 0.4.0):** these changed but are not Planet cards; confirm they don't alter planet *generation* odds (they target hand selection, not leveling — appears unrelated).
4. **Planet generation rates** (shop/celestial packs, The Astronomer voucher, Telescope) are out of scope here but feed which planets appear — confirm covered in shop/pack design docs.

## New building blocks needed

1. **Ruleset-aware hand-scaling table.** Move the four reworked increments (Straight l_mult=2, Straight Flush l_mult=3, Flush House l_mult=3, Flush Five l_chips=40) out of `HandType` enum constants into a per-ruleset override map so the engine can switch between vanilla and BMP-release scaling without editing the enum. Minimum viable fix: update the four `HandType` constants to BMP-release values.
2. **PlanetCard → HandType mapping** registry: `c_pluto`→High Card, `c_mercury`→Pair, `c_uranus`→Two Pair, `c_venus`→Three of a Kind, `c_saturn`→Straight, `c_jupiter`→Flush, `c_earth`→Full House, `c_mars`→Four of a Kind, `c_neptune`→Straight Flush, plus secret `c_planet_x`→Five of a Kind, `c_ceres`→Flush House, `c_eris`→Flush Five.
3. **Secret-planet softlock gating:** Planet X / Ceres / Eris only spawn after their secret hand has been played (track `played > 0` per hand). Needs a "hand has been seen" flag in RunState.
4. **Black Hole consumable handler:** a Spectral-set consumable that calls `levelUp(t)` for every HandType once — reusing `RunState.levelUp` (already exists at `RunState.java:66`).
5. **Blue Seal → Planet generation** (BMP-relevant, `release.lua:656-683`): a Blue seal on a card creates the Planet for the *most-played* hand at end of round. Out of strict scope but it is a Planet *source* introduced/reworked in the release ruleset — note for the consumable-generation building block.
