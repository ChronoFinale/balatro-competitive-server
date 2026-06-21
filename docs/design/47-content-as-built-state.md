# 47 — Content as-built state (the whole picture)

> A single snapshot of **what content exists, how faithfully it's modeled, and what's missing or wrong** —
> across every category, not just jokers (joker detail lives in [46](46-joker-as-built-ledger.md)). Produced
> by a six-way audit (2026-06-21) of the implementation vs `game.lua`/`card.lua` + the design catalogues
> (14–21). This is a living state record: as items are fixed/verified, update the status here.

## ✅ DECIDED: target is BMP 0.4.2 ranked

The target ruleset is **Balatro Multiplayer (BMP) 0.4.2** (decided 2026-06-21). That makes the "BMP-delta
gaps" below **real bugs to fix** — but toward **0.4.2** values, which may differ from the 0.4.0 numbers the
audit agents pulled from changelogs. **Ground truth: `D:\BalatroMod\multiplayer-0.4.2`** — effective ranked
values come from its `rulesets/` + `layers/` (active reworks; some are commented out, so read what actually
loads), with `compatibility/Preview/Jokers/_Vanilla.lua` as the precise behavior reference. Verify each
BMP-delta value against 0.4.2 before fixing — do **not** trust the 0.4.0 changelog numbers below.

**Consequences for the test harness — RESOLVED:** the re-audit (below) confirmed 0.4.2 ranked = vanilla +
*narrow* reworks (it does NOT touch hand-levels/economy/tag prices). So the **vanilla scoring goldens and the
vanilla cost/rarity gate are correctly anchored** — no BMP overlay needed. 0.4.2 only swaps a few jokers for
`mp_*` reworks, bans 2 consumables, reworks Glass, and adds the Nemesis jokers (mostly already modeled).

The lists below separate **target-independent bugs** (wrong in any ruleset) from the **real 0.4.2 deltas**.

## Coverage at a glance

| Category | Implemented | Missing | Target-independent bugs | Real 0.4.2 deltas |
|---|---|---|---|---|
| Jokers | 158 (incl. MP) | 0 vanilla | see [46] | **Turtle Bean** (+4 + destroy); verify Nemesis jokers vs 0.4.2 |
| Tarots | 22/22 | 0 | 0 | Justice banned in ranked |
| Planets | 12/12 | 0 | 0 | 0 (vanilla levels correct) |
| Spectrals | 18/18 | 0 | Ectoplasm escalation | Ouija ✅ (capability) |
| Vouchers | 32/32 | 0 | Omen Globe spectral-in-Arcana | 0 |
| Tags | 24/24 | 0 | **2 key names** | 0 (Investment $25 / free tags are correct) |
| Bosses | 28/28 | 0 | **2 minAnte** (Tooth, Pillar) | 0 |
| Decks | 15/15 vanilla | **8 BMP decks** | 0 | 0 |
| Stakes | 8 + 3 BMP | 0 | 0 | tiers 4–5 clamp (unverified) |
| Stickers | 3/3 | 0 | 0 | 0 |
| Seals | 4/4 | 0 | 0 | 0 |
| Enhancements | 8/8 | 0 | 0 | Glass ✅ (x1.5 capability) |
| Editions | 4/4 | 0 | 0 | 0 |
| Packs | 15/15 vanilla | Giga pack | 0 | 0 |

## Target-independent bugs (wrong regardless of vanilla/BMP)

| # | Item | Current | Correct | Where |
|---|---|---|---|---|
| 1 | **Tag key `tag_d6`** | `tag_d6` | `tag_d_six` | TagCatalog.java — real game key; breaks any real-key matching |
| 2 | **Tag key `tag_speed`** | `tag_speed` | `tag_skip` | TagCatalog.java |
| 3 | **The Tooth minAnte** | 2 | 3 | BossCatalog.java:35 (appears an ante early) |
| 4 | **The Pillar minAnte** | 2 | 1 | BossCatalog.java:57 (appears an ante late) |
| 5 | **Omen Globe** | +1 consumable slot only | also: Spectrals appear in Arcana packs | VoucherCatalog — missing the SPECTRAL_RATE-in-Arcana effect |
| 6 | **Ectoplasm escalation** | −1 hand size flat | −1, −2, −3… (escalates per use) | Run.applyJokerEdition — needs a per-run use counter |
| 7 | **Loyalty Card** | global hand count | hands *since acquired* | (joker, in [46]) |
| 8 | **Turtle Bean / Certificate / Canio** | see [46] | self-destruct / trigger gaps | [46] |

## Missing content (not implemented at all)

| Item | Notes |
|---|---|
| **8 BMP decks** | Orange, Violet, Indigo, Oracle, Gradient, Heidelberg, Echo, Cocktail — need run/shop/boss hooks, voucher-cost override, unskippable-pack flag, rank-shift, dollar cap, end-of-shop negative copy, per-round retrigger, composite merge (doc 18 §4) |
| **Giga booster pack** | 10 cards / pick 4 / $16 / unskippable / Orange-deck-only — no Giga, no unskippable flag |
| **Phantom edition** | MP-mod edition; not in the Java engine |
| **Pack-kind queues / unskippable** | per-pack-kind content queues + Buffoon→shop passthrough not modeled |

## The 0.4.0 "deltas" are NOT in 0.4.2 ranked — our vanilla values are correct

Re-audited against the **real 0.4.2 source** (`multiplayer-0.4.2/layers/{standard,ranked}.lua` + `objects/`).
The ranked ruleset `standard_ranked` loads only `{standard, ranked}`; `release.lua` (the 1.0.0-style rework
layer) is commented out. The `standard` layer does **not** rework joker/planet/tag/economy numbers. So every
0.4.0-changelog "delta" the first audit flagged is **inactive** — **leave our vanilla values as-is:**

| Claimed delta | Verdict in 0.4.2 ranked |
|---|---|
| Saturn/Neptune/Ceres/Eris hand-levels | ✅ NOT changed — vanilla correct |
| Investment Tag $25 | ✅ NOT changed (vanilla $25 is right; $15 was 0.4.0 noise) |
| Rarity/edition tags free vs paid | ✅ NOT changed — vanilla "free" is right |
| Devil / Gold-card $3 vs $4 | ✅ NOT changed — $3 correct |

This means the **vanilla scoring goldens and the cost/rarity gate are correctly anchored** — no BMP overlay
needed. (The earlier worry about re-anchoring was based on the false 0.4.0 deltas.)

## What 0.4.2 ranked ACTUALLY changes (the real delta set)

`standard` layer: bans 5 jokers + 2 consumables and swaps in `mp_*` reworks; reworks the Glass enhancement;
adds the multiplayer/Nemesis jokers; `ranked` forces "The Order".

| 0.4.2 change | Real effect | Our state |
|---|---|---|
| **mp_turtle_bean** (replaces vanilla) | **+4** hand size (not +5), −1/round, **destroyed at 0** | ❌ we have +5 and never destroyed — fix to +4 + self-destruct |
| **mp_seltzer** | retrigger window **8 hands** (not 10) | ✅ `variants("multiplayer")` = 8 |
| **mp_ticket** (Golden Ticket) | $3 per Gold card (= vanilla; experimental variant is $4) | ✅ verify base/variant = $3 |
| **mp_hanging_chad** | mechanically identical to vanilla (just an mp flag) | ✅ no change needed |
| **mp_bloodstone** | 1-in-2 → x1.5 Mult on Hearts + PvP-deterministic RNG | ✅ verify our bloodstone = 1-in-2 x1.5 |
| **m_glass** rework | x1.5 Mult, 1-in-4 break (vanilla x2) | ✅ `glassMult` capability = x1.5 in MP |
| **c_mp_ouija_standard** | destroy 3 random, rest → 1 rank, **no −1 hand size** | ✅ `ouijaRework` capability |
| **Justice** | banned in ranked | ⚠️ ensure ranked pool excludes it |
| **bans + mp swap** | hanging_chad/ticket/seltzer/turtle_bean/bloodstone banned, replaced by mp_* | ⚠️ verify our ban+variant swap covers all 5 (esp. turtle_bean, bloodstone) |
| **Nemesis jokers** (8: pizza, speedrun, penny_pincher, defensive, conjoined, pacifist, lets_go_gambling, skip_off, taxes) | PvP effects + costs/rarities | ⚠️ cross-check each cost/rarity/effect vs `objects/jokers/*.lua` |

> So the *real* 0.4.2 work is small: **Turtle Bean** (+4 + self-destruct) is the one clear gap; the rest is
> verification that our existing variants/capabilities/Nemesis jokers match the 0.4.2 object files. The big
> "missing content" (8 BMP decks, Giga pack) and the target-independent bugs above still stand.

## What's solid (audited faithful)

- **Jokers**: commons 51/51 clean; the rest faithful except the handful in [46].
- **Vouchers**: 31/32 carry correct `Modify` data (Blank is an intentional no-op); tier-gating + MP bans correct.
- **Bosses**: all 28 effects correct (debuff/halve/per-hand/legality/face-down/draw-override/joker-disable/forced-card); only the 2 minAnte timings are off.
- **Seals** (4/4), **Enhancements** (8/8 numbers verified), **Editions** (4/4), **vanilla Packs** (15/15 sizes/costs/picks) — all faithful.
- **Decks** (15 vanilla), **Stakes** (cumulative model), **Stickers** (eternal/perishable/rental) — all working and tested.
- **Consumables**: all 52 vanilla present; effect verbs all map onto the unified `Effect`/`Selector` vocabulary.

## Verification confidence

Each row is grounded in `game.lua`/`card.lua` + the design catalogues; the two scariest (tag keys, Anaglyph
wiring) were hand-verified (tag keys confirmed wrong; Anaglyph confirmed *correctly wired* — an audit
false-positive). Other agent claims (planet deltas, tag prices) are BMP-changelog-sourced and marked as
target-dependent rather than asserted as bugs.
