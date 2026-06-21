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

**Consequences for the test harness (must reconcile):**
- The **cost/rarity gate** (`JokerStatsAuditTest`) diffs against *vanilla* `game.lua`. Under a BMP target it
  must diff against BMP 0.4.2 effective values, or keep vanilla + a BMP-overlay/`INTENTIONAL_DEVIATIONS` map.
- The **scoring goldens** (pair of kings = 60) are vanilla base-hand values — likely still valid (BMP mostly
  reworks jokers/economy/pvp, not base hand chips), **except** the planet level deltas change leveled-hand
  scoring. Re-anchor any leveled-hand fixture to 0.4.2.

The lists below still separate **target-independent bugs** (wrong in any ruleset) from **BMP-delta gaps**
(now confirmed bugs to fix toward 0.4.2).

## Coverage at a glance

| Category | Implemented | Missing | Target-independent bugs | BMP-delta gaps |
|---|---|---|---|---|
| Jokers | 158 (incl. MP) | 0 vanilla | see [46] | a few |
| Tarots | 22/22 | 0 | 0 | Devil $3→$4 |
| Planets | 12/12 | 0 | 0 | 4 (Saturn/Neptune/Ceres/Eris levels) |
| Spectrals | 18/18 | 0 | Ectoplasm escalation | Ouija (handled both ways) |
| Vouchers | 32/32 | 0 | Omen Globe spectral-in-Arcana | 0 |
| Tags | 24/24 | 0 | **2 key names** | Investment $25→$15; 6 free-vs-paid |
| Bosses | 28/28 | 0 | **2 minAnte** (Tooth, Pillar) | 0 |
| Decks | 15/15 vanilla | **8 BMP decks** | 0 | 0 |
| Stakes | 8 + 3 BMP | 0 | 0 | tiers 4–5 clamp (unverified) |
| Stickers | 3/3 | 0 | 0 | 0 |
| Seals | 4/4 | 0 | 0 | 0 |
| Enhancements | 8/8 | 0 | 0 | Gold card $3→$4 |
| Editions | 4/4 | 0 | 0 | Phantom (MP) absent |
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

## BMP 0.4.0 ranked deltas (bugs ONLY if target = BMP ranked)

| Item | Vanilla (our code) | BMP 0.4.0 | Where |
|---|---|---|---|
| Straight level mult | +3 | **+2** | HandType.java (Saturn) |
| Straight Flush level mult | +4 | **+3** | HandType.java (Neptune) |
| Flush House level mult | +4 | **+3** | HandType.java (Ceres) |
| Flush Five level chips | +50 | **+40** | HandType.java (Eris) |
| Investment Tag | $25 | **$15** | Run.applyBossDefeatTags |
| Rarity/edition tags (Uncommon/Rare/Negative/Foil/Holo/Poly) | **free** joker | full-price joker | Run.applyShopTags |
| The Devil (Gold enhance) | $3 | **$4** | (cosmetic/desc) |
| Gold *card* held payout | $3 | **$4** | GameEvents end-of-round |
| Stakes scaling tiers 4–5 | clamp to tier 3 | unverified curve | Blinds.java |

> Note: vanilla scoring goldens (pair of kings = 60) and the cost/rarity gate are vanilla-based, so a switch
> to BMP-ranked target would need those overlaid too. This is exactly why the target decision must be explicit.

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
