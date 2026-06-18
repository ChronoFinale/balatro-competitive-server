# 00 — Master Design: Server-Authoritative Competitive Balatro

This is the cohesive program design that ties together the foundation specs
(`balatro-engine-spec.md`, `queue-model.md`, `README.md`) and the detailed
content/system sections (`10-*` … `40-*`). It states the vision, the target
architecture, the **complete content inventory**, the determinism model, the gap
vs. Balatro Multiplayer (BMP) 0.4.0 behavioral parity, and a **phased build
plan**. The companion `01-WORK-BREAKDOWN.md` decomposes the phases into
independently-assignable work packages.

> Target parity baseline throughout: **Balatro Multiplayer 0.4.0** ("The Order"
> / Ranked Update) on Balatro 1.0.1o + SMODS. We match BMP's *behavior*, not its
> bytes or its trust model. We are deliberately **not** byte-compatible with
> vanilla/BMP seeds (engine spec §8).

---

## 1. Vision & non-goals

### Vision
A **server-authoritative** game engine for *competitive* Balatro. The server
simulates the entire game — state, RNG, scoring — and the client sends only
**intents** and renders authoritative results (a scoring replay log). Fair play
above all: every cheat class is closed structurally, not by detection
(`README.md`, `balatro-engine-spec.md` §5–§7).

| Threat | Structural defense |
|---|---|
| Fabricated score / money / cards | Authoritative server; client sends intents, never outcomes |
| Reading hidden info (deck order, next shop, seed) | `ClientView` structurally carries no deck/seed/queue state |
| Game-speed / tempo abuse in timed modes | Server owns the clock |
| Logic mods on a client | No effect — the client is not the authority |

### Non-goals
- **Open-ended modding / a programmable client.** We deliberately trade
  flexibility for provably-fair competition (`README.md`). Content is
  data-driven and **server-interpreted**, never client code.
- **Byte-for-byte vanilla seed compatibility.** Our PRNG is a clean xoshiro256**;
  we copy BMP's *queue structure*, not LuaJIT arithmetic (spec §8, `31-rng-and-queues.md`).
- **Trusting the peer.** Unlike BMP (a TCP relay that stores
  `client.score = whatever the client claimed`, spec §7), we re-simulate.
- **>2-seat lobbies, full ranked MMR, and a stock-BMP-client bridge** are later /
  optional (`35-networking-protocol.md` §4.9, open Qs).

---

## 2. Target architecture

Eight engine packages under `com.balatro.engine`, all server-side; the client
sees only `ClientView` + a scoring replay log.

### 2.1 Authoritative scoring core
A faithful, **ruleset-driven** transcription of `evaluate_play` (`30-scoring-pipeline.md`).
The pipeline runs named phases in BMP's exact order — `modify_scoring_hand` →
blind `debuff_hand` veto → `before` → base chips/mult → blind `modify_hand` →
per-card (played then held, L→R, with retriggers) → joker main (edition-pre →
main → joker-on-joker → edition-post) → consumable/deck individual targets →
`final_scoring_step` → destruction → `after` → `floor(chips × mult)` as a
big-number. The per-source field order is `chips → mult → xchips → xmult`.

### 2.2 Data-driven content (`JokerDef` algebra)
Every joker/consumable effect is **pure data** — `trigger → condition → effect`
rules + state mutations — interpreted server-side by `DataJoker`, so a built
joker is exactly as cheat-proof as a hand-coded one. `40-jokerdef-algebra-v2.md`
is the definitive schema: v1 (24 triggers / 21 conditions / 4 value shapes / 6
effect ops / 3 mutation ops) plus a v2 delta of **+5 triggers, +13 conditions,
+5 value sources, +11 effect-types, +2 mutation ops, +2 context fields, +~12
RunState fields**. A small set of effects stay **NATIVE** (hand-evaluator rule
changes, the Order RNG shims, soul forced-spawn, Blueprint recursion engine,
boss-disable/death-prevent, phantom networking) — referenced by key, not defined
as data.

### 2.3 Game-long queue determinism (the fairness substrate)
Every source of in-run randomness is a **game-long queue**: one deterministic
sequence per *purpose*, shared by both players on a seed, each advancing its own
cursor by its own actions. The Nth item of a category is always the same item, so
identical choices give identical results and extra rerolls just move you further
along the *same* list (`queue-model.md`, `31-rng-and-queues.md`). This is BMP's
behavioral model rebuilt on our own PRNG. See §4.

### 2.4 Ruleset bundles (layered, data-only)
A **ruleset + the jokers it names** fully dictates a match. Target model
(`36-builder-content-pipeline.md`) mirrors BMP 0.4.0's structural change: rulesets
become **layered compositions** (`Layer` fragments + `Ruleset` composition, merge
= arrays concat / scalars last-layer-wins / ruleset-own beats layer), carrying six
ban categories + six rework categories, `forcedGamemode`, lobby locks, a ranked
tier, and a content hash for reproducibility. Reworks are a **deterministic data
overlay** (same center, layer-keyed shadow values), not a forked key.

### 2.5 Builder (content authoring pipeline)
A `/jokers/schema`-driven joker builder (already shipped) and a ruleset builder,
both data-only: saved defs persist under `web-assets/` and register through the
same authoritative path as built-ins. Validation is about **safety, not balance**
(key regex, size caps, pool-membership cross-refs); ranked adds per-tier numeric
bounds + curation/promotion + content hashing.

### 2.6 PvP / Attrition (the competitive focus)
Two authenticated players, same seed, each driving a full authoritative `Run`.
Lives + the **Nemesis (PvP) blind** (`bl_mp_nemesis`: no chip target, the target
is the opponent's score) resolved **server-side** from authoritative scores
(`34-pvp-attrition-nemesis.md`, `33-run-lifecycle.md`). Gamemodes
(Attrition / Showdown / Survival) are first-class data objects supplying
`get_blinds_by_ante`, lives, and bans. PvP-aware jokers read a server-owned
**nemesis snapshot** (lives, hands-left, skips, shop-spend, sold-count), never the
opponent's hidden state.

### 2.7 Networking (transport & coordination)
WebSocket + JSON with a correlated `{type, seq}` envelope + accept/reject + view +
replay; JWT auth (Steam-ticket-ready). Target additions: liveness (ping/pong),
**reconnect with full authoritative resync** (our advantage over BMP — we own the
run), ready-flow, a match event log (powers resync + ghost replays), and validated
cross-player MP-joker intents (`35-networking-protocol.md`).

### 2.8 BMP 0.4.0 parity intent
Parity is **behavioral**: same modes, blinds, content effects, queue topology, and
ban sets as BMP 0.4.0 Standard Ranked — reproduced on top of our authoritative
core, never BMP's trust model. Where the 0.3.3 ranked spreadsheet disagrees with
the 0.4.0 on-disk source, **0.4.0 source wins** (every content doc flags these).

---

## 3. Complete content inventory

Totals across categories, with pointers to the detail section that catalogues each
(with exact keys, numbers, RNG seeds, and BMP-vs-vanilla deltas).

| Category | Count | Detail | Notes |
|---|---:|---|---|
| **Common jokers** | 61 | `10-jokers-common.md` | `rarity=1`; ~35 expressible today, ~26 need new blocks |
| **Uncommon jokers** | 64 | `11-jokers-uncommon.md` | `rarity=2`; ~12 expressible, rest need blocks |
| **Rare jokers** | 20 | `12-jokers-rare.md` | `rarity=3`; 8 expressible, 12 need blocks |
| **Legendary jokers** | 5 | `13-jokers-legendary-mp.md` Part A | Canio/Triboulet/Yorick/Chicot/Perkeo (Soul pool) |
| **BMP-exclusive jokers** | 9 | `13-jokers-legendary-mp.md` Part B | All nemesis-coupled (Defensive, Conjoined, Pizza, Let's Go Gambling, Penny Pincher, Speedrun, Pacifist, Taxes, Skip-Off) |
| *Jokers subtotal* | **159** | | |
| **Tarots** | 22 | `14-tarots.md` | All `cost=3`; 11 operation classes; 3 RNG touch-points |
| **Planets** | 12 | `15-planets.md` | 9 standard + 3 secret (Planet X/Ceres/Eris); 4 BMP per-level reworks |
| **Spectrals** | 18 | `16-spectrals.md` | All `cost=4`; incl. hidden The Soul + Black Hole; BMP Ouija rework |
| **Vouchers** | 32 | `17-vouchers.md` | 16 Tier-1 / 16 Tier-2 pairs; 4 banned in ranked |
| **Decks (backs)** | 23 | `18-decks.md` | 15 vanilla + 8 active BMP (Orange/Violet/Indigo/Oracle/Gradient/Heidelberg/Echo/Cocktail); White disabled |
| **Booster packs** | 16 | `19-packs-editions-seals.md` | 15 vanilla families + Giga Standard (Orange-only) |
| **Editions** | 5 | `19-packs-editions-seals.md` | Foil/Holo/Poly/Negative + BMP Phantom |
| **Enhancements** | 8 | `19-packs-editions-seals.md` | Bonus/Mult/Wild/Glass/Steel/Stone/Gold/Lucky (Glass ×1.5 BMP rework) |
| **Seals** | 4 | `19-packs-editions-seals.md` | Red/Blue/Gold/Purple |
| **Skip tags** | 24 | `20-skip-tags.md` | Queue-mapped; Boss tag banned in PvP modes |
| **Boss blinds** | 28 | `21-boss-blinds.md` | 22 regular + 5 finisher + 1 `bl_mp_nemesis`; Wall/Vessel banned in PvP |

**Total content items across categories: 351.**

### Expressibility summary (jokers, vs. the v1 algebra)
- **Fully expressible today (~63):** see `40-jokerdef-algebra-v2.md` "v1 coverage
  baseline" — all suit/type mult & chips jokers, simple conditionals, simple
  accumulators (Hologram/Flash Card/Throwback/Constellation), Baron/Wee/Hit the
  Road/The Duo–Tribe, Triboulet/Yorick.
- **Need new building blocks (the v2 delta):** the remaining ~96 jokers + all
  consumables, unlocked by the 20 numbered v2 blocks. Highest leverage:
  `CREATE` (~38 items) > `MUTATE_CARD` (~25) > `Stat`-values (~17) > `DESTROY` (~15).

### New building-block count
The v2 algebra adds **20 numbered building blocks** comprising **38 discrete schema
additions** (+5 triggers, +13 conditions, +5 value sources/combinators, +11
effect-types, +2 mutation ops, +2 context fields). These are the unit the work
breakdown assigns. (RunState/Native infra — ~12 fields + 7 native carve-outs — are
the supporting substrate, not counted as algebra building blocks.)

---

## 4. Determinism / the queue model

The single most important parity finding (`31-rng-and-queues.md`): **BMP 0.4.0 does
not maintain hand-rolled queue structures.** A "queue" is a `pseudoseed(key)`
keyed stream whose state is never reset across the run and never perturbed by ante
or resample-branching. The Order achieves this by (1) **zeroing the ante** in every
key and (2) **forcing the `_resample` suffix to `''`** so a blocked draw re-draws
the *same* stream. Our `GameQueue.nextWhere(pred)` is already the correct
abstraction (advance same stream, never re-roll). The load-bearing parity detail is
**key namespacing + ante-independence**, not the container.

### Queue topology (target, §4.1 of `31-rng-and-queues.md`)
Per-purpose, ante-independent keys on `RunState.queues`, each a `GameQueue`:
- Jokers: `joker.common/uncommon/rare/legendary` + `joker.rarity` (rarity stream).
- Shop: `shop.category` (the `cdt` rate-walk picking each slot's category).
- Consumables: `tarot/planet/spectral` × `.uptop` and `.pack` (6 streams).
- `soul.c_soul`, `soul.c_black_hole` (hit/miss, **insert on hit**, push-back-one).
- `packs` (booster types, advanced **on shop view** → skip-offset).
- `voucher` (single stream over culled Tier-1/Tier-2 pairs; tag-advance; dup-skip).
- `playing_card` (Magic Trick / Standard-pack faces).
- `prob.lucky_mult` (1/5), `prob.lucky_money` (1/15), `prob.glass` (1/4),
  `prob.<joker>` — raw `[0,1)` stored, threshold applied at read (so Oops! All 6s
  changes the threshold, not the sequence).
- `bloodstone.global` + `bloodstone.pvp` (per-ante, **resets to start each hand**).
- `select.idol/todo/mail/invisible` — sort-then-pick (order-insensitive).

**Migrated so far** (`queue-model.md`): shop jokers + planets (uniform), Lucky
(1/5 mult, 1/15 money), Glass break (1/4). The rest is the build-out above.

### Hidden-information boundary
Server-only, never on any wire: the seed, `hashed_seed`, every queue cursor/contents
below what's revealed, unrealized deck order, upcoming shop/pack contents, the boss
blind before reveal, tags not yet shown (spec §8). Revealed only at the legitimate
moment (cards drawn, shop entered, pack opened, the scoring replay log). Opponent
coupling exposes only the agreed-public summary.

---

## 5. Gap vs. BMP 0.4.0 behavioral parity

Each detail doc carries a "How OURS does it today / The GAP" section grounded in
real Java. Consolidated, the gap falls into eight fronts:

1. **Scoring pipeline (`30`).** Missing `modify_scoring_hand`, boss
   `debuff_hand`/`modify_hand`, `final_scoring_step`/`after`/destruction phases;
   incomplete field order (no `xchips`, no `extra`-chain); no joker-edition wrap; no
   `floor`/big-number; not ruleset-driven. Ten gaps G1–G10.
2. **RNG/queues (`31`).** Right shape (`GameQueue`), wrong coverage: need rarity-split
   joker queues, the `cdt` category stream, Up-Top vs Pack split, Soul/Black Hole
   insertion, packs queue (shop-view-advanced), voucher 1–16 queue, side joker
   queues, Bloodstone PvP reset, deterministic sort-then-pick.
3. **Run lifecycle (`33`).** No blind-select phase, no skip+tags, no per-ante gamemode
   blind override, gamemodes/lives are hardcoded constants, no bans, no endless.
4. **PvP/nemesis (`34`).** No gamemode abstraction, lives are a constant, score is a
   `long` (needs big-number), no nemesis snapshot for jokers, no Nemesis blind type,
   no `mp_pvp_loss` context, no Comeback-Gold-on-any-life-loss.
5. **Networking (`35`).** We are *ahead* on trust/auth/structure; *behind* on
   liveness, reconnect/resync, multi-mode, ready-flow, cross-player MP-joker
   coupling, matchmaking.
6. **Builder/content pipeline (`36`).** Flat `Ruleset` (no layers/modifiers/reworks),
   only a joker allowlist (no other-category bans), no forced gamemode / lobby lock,
   no curation tier / content hash / versioning, validation lacks per-tier numeric
   bounds.
7. **Content breadth (`10–21`).** ~96 of 159 jokers + all consumables/vouchers/
   decks/tags/bosses are not yet authored — gated on the algebra v2 blocks and the
   consumable/voucher/tag/boss subsystems.
8. **Algebra v2 (`40`).** The 20 building blocks above are the throughput
   constraint: each unblocks a counted slice of content.

**What's already strictly better than BMP:** authoritative re-simulation (vs. score
relay), server-side rulesets (vs. client strings), JWT auth (vs. asserted username),
structured envelope + replay log. Parity work never regresses these.

---

## 6. Phased build plan

Sequenced by dependency and competitive value. The ordering reflects the engine
spec's "security is won at the authoritative core; then one mode end-to-end; then
breadth" build order (spec §5) and the README focus: *Attrition done well first.*
Each milestone lists rationale + hard dependencies. Work packages are in
`01-WORK-BREAKDOWN.md`, grouped to these milestones.

### M0 — Scoring-pipeline correctness & ruleset-driven core *(foundation; no deps)*
**Rationale.** The pipeline is the whole ballgame (spec §0). Parity-critical gaps
(field order, `xchips`, joker editions, destruction phase, `floor`+big-number,
boss `debuff_hand`/`modify_hand`, ruleset-driven values) must close before content
breadth, or every joker built on top inherits the divergence. Closes `30` G1–G10.
**Depends on:** nothing. Everything else builds on it.

### M1 — Queue topology & shop/pack/voucher generation *(determinism substrate)*
**Rationale.** The fairness guarantee. Build the full queue topology (§4): rarity-split
joker queues, `cdt` category stream, Up-Top/Pack split, Soul insertion, packs queue,
voucher queue, deterministic sort-then-pick — plus the `ShopGenerator`/`PackOpener`/
`VoucherQueue` that consume them. Closes `31` gaps.
**Depends on:** M0 (scoring consumes `prob.*` queues; pools feed the shop).

### M2 — Run lifecycle, gamemodes & Attrition end-to-end *(the competitive spine)*
**Rationale.** "Attrition done well" (README). Adds the blind-select phase, skip+tags
plumbing, the `Gamemode` abstraction + `get_blinds_by_ante`, lives as gamemode/lobby
config, the Nemesis blind type, big-number `Score`, server-exact PvP resolution,
nemesis snapshot, `mp_pvp_loss` context, Comeback Gold, ban sets. Closes `33`+`34` gaps.
**Depends on:** M0 (scoring + `Score`), M1 (boss/tag/voucher pools gated by bans).

### M3 — JokerDef algebra v2 (the building blocks) *(content throughput)*
**Rationale.** The 20 blocks are the constraint on content breadth. Land them in
leverage order so each unlocks the largest content slice: Tier 1 (CREATE, Stat-values,
DESTROY, Chance/Random, Nemesis) → Tier 2 → Tier 3. Includes the ~12 new RunState
counters and the NATIVE carve-outs.
**Depends on:** M0 (effect application + triggers), M1 (CREATE/queues), M2 (Nemesis
reads, PvP conditions, RunState lives/skips).

### M4 — Content transcription (jokers, consumables, decks, tags, bosses) *(breadth)*
**Rationale.** With the algebra in place, transcribe the catalogues into data
(`10–21`) — consumable subsystem (Tarot/Planet/Spectral + Black Hole), the joker pool
(159), vouchers (32), decks (23), tags (24), bosses (28). This is parallelizable per
category once its subsystem exists.
**Depends on:** M3 (algebra blocks per joker), M1 (consumable/pack queues), M2
(boss/tag lifecycle, nemesis context for MP jokers).

### M5 — Ruleset bundles, layers, reworks & curation *(governance/ranked)*
**Rationale.** Make rulesets layered compositions with bans/reworks/forced-gamemode/
lobby-lock, content hashing, and ranked curation/promotion — so curated ranked and
open custom content coexist safely. Closes `36` gaps.
**Depends on:** M2 (gamemode + ban application), M4 (a content set to ban/rework).

### M6 — Networking hardening: liveness, reconnect, ready-flow, MP-joker coupling *(robustness)*
**Rationale.** Make matches survive real networks and wire the cross-player coupling
as validated intents. Liveness + reconnect/full-resync + match event log + ready-flow
+ MP-joker intents. Closes `35` gaps 1–4 of §4.9.
**Depends on:** M2 (Match/PvP state machine), M5 (content hash for the version
handshake).

### M7 — Matchmaking, ranked queue & BMP-compat bridge *(scale; optional)*
**Rationale.** The differentiator beyond friend codes: an MMR-bucketed ranked queue,
plus an optional casual BMP-compat TCP adapter (explicitly unranked/unverified) and
admin surface. Lowest competitive urgency; highest scope.
**Depends on:** M5 (curated rulesets + content hash), M6 (lobby/reconnect, event log).

```
M0 ─┬─► M1 ─┬─► M2 ─┬─► M3 ─► M4 ─► M5 ─┬─► M6 ─► M7
    │       │       │                    │
    └───────┴───────┴────────────────────┘   (M0 underpins all)
```

---

## 7. Where the detail lives (section index)

| Section | Topic |
|---|---|
| `balatro-engine-spec.md` | Engine foundation (pipeline, context/trigger set, RNG/seed, anti-cheat) |
| `queue-model.md` | The game-long queue determinism model + migration map |
| `10`–`13` | Jokers: Common / Uncommon / Rare / Legendary+BMP-exclusive |
| `14`–`16` | Consumables: Tarots / Planets+Black Hole / Spectrals |
| `17`–`19` | Vouchers / Decks / Packs+Editions+Enhancements+Seals |
| `20`–`21` | Skip tags / Boss blinds (+ Nemesis) |
| `30`–`31` | Scoring pipeline / RNG & queue topology |
| `33`–`34` | Run lifecycle / PvP-Attrition-Nemesis |
| `35`–`36` | Networking protocol / Content authoring pipeline |
| `40` | JokerDef algebra v2 (the definitive building-block spec) |
| `01-WORK-BREAKDOWN.md` | Implementation work packages, grouped to M0–M7 |
