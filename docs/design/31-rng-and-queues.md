# 31 — RNG and the game-long queue topology (BMP 0.4.0 parity)

Server-authoritative spec for the complete game-long queue system, mapped onto our
`QueueSet`/`GameQueue`. Target: **Balatro Multiplayer 0.4.0** ("The Order"), the
version now on disk. Every claim is cited to disk; unverified numbers are flagged.

The single most important finding, which reframes our whole `queue-model.md`:

> **BMP 0.4.0 does NOT maintain hand-rolled per-category queue data structures.**
> It reuses vanilla's `pseudoseed(key)` keyed-stream RNG and turns each key into a
> game-long stream by two tricks: (1) **forcing `G.GAME.round_resets.ante = 0`**
> around card creation so the ante drops out of every key, and (2) **dropping the
> `_resample` suffix** so a blocked/duplicate roll re-draws the *same* stream
> (advancing it) instead of branching to a side stream.
> Source: `multiplayer-0.4.0/compatibility/TheOrder.lua:1-27,44-50` and the
> vanilla `_resample` ternary at `lovely/dump/functions/common_events.lua:2449`.

So a "queue" in BMP = "a `pseudoseed` key whose state is never reset across the run
and is never perturbed by ante or resample-branching." Our `GameQueue` is the
*correct abstraction* for this, but the **key namespacing and the ante-zeroing are
the load-bearing parity details**, not the queue container itself.

---

## 1. How the REAL game / BMP 0.4.0 does it

### 1.1 The pseudohash / pseudoseed / pseudorandom model (vanilla)

`lovely/dump/functions/misc_functions.lua`:

```lua
function pseudohash(str)                                  -- :309
  local num = 1
  for i=#str, 1, -1 do
    num = ((1.1239285023/num)*string.byte(str, i)*math.pi + math.pi*i)%1
  end
  return num
end

function pseudoseed(key, predict_seed)                    -- :328
  if key == 'seed' then return math.random() end
  -- if G.SETTINGS.paused and key ~= 'to_do' then return math.random() end  -- DISABLED by MP
  if not G.GAME.pseudorandom[key] then
    G.GAME.pseudorandom[key] = pseudohash(key..(G.GAME.pseudorandom.seed or ''))
  end
  G.GAME.pseudorandom[key] =
    math.abs(tonumber(string.format("%.13f",
      (2.134453429141 + G.GAME.pseudorandom[key]*1.72431234) % 1)))
  return (G.GAME.pseudorandom[key] + (G.GAME.pseudorandom.hashed_seed or 0))/2
end

function pseudorandom(seed, min, max)                     -- :347
  if type(seed)=='string' then seed = pseudoseed(seed) end
  math.randomseed(seed)
  if min and max then return math.random(min,max) else return math.random() end
end
```

Properties that matter for us:

- **Per-key state.** `G.GAME.pseudorandom[key]` is a float advanced by a fixed
  affine-mod recurrence each call. **This *is* a queue**: call N for a key always
  yields the same value on a given seed, and consuming key A never perturbs key B.
  This is exactly our `GameQueue` semantics, one queue per key.
- **`hashed_seed`** is `pseudohash(seed)`, set once at run start
  (`balatro-engine-spec.md:305` cites `game.lua:2168`), blended into every output.
- **BMP disables the `paused` early-return** (`misc_functions.lua:330-331` — the
  line is commented out in the on-disk dump). Vanilla returns `math.random()` (a
  live, non-deterministic draw) for any key while paused; BMP must not, because
  voucher RNG can be called while paused, and live draws would desync players.
  **DELTA vs a naive port: never short-circuit a queue draw on "paused".**

### 1.2 The pool + block/skip ("UNAVAILABLE") model (vanilla)

`get_current_pool(_type,_rarity,_legendary,_append)` (`common_events.lua:2243`)
builds an *ordered* list the same way every time, then maps each entry to either
its key or the literal string `'UNAVAILABLE'`:

- Jokers split by rarity into `G.P_JOKER_RARITY_POOLS[1..4]`; pool key is
  `'Joker'..rarity..append` → keys `"Joker1"`, `"Joker2"`, `"Joker3"`, `"Joker4"`
  (Common/Uncommon/Rare/Legendary) — **this is where the rarity-split queues come
  from** (`:2253`).
- Planets are `UNAVAILABLE` if soft-locked and never played (`:2303-2306`) → the
  "skip a Planet for a hand you can't level" rule.
- Vouchers are `UNAVAILABLE` if owned / prerequisite unmet / already in shop
  (`:2284-2302`).
- The Soul / Black Hole / hidden centers are always `UNAVAILABLE` in normal pools
  (`:2317-2319`); they enter only via the dedicated soul roll (§1.4).
- `banned_keys` and `mp_include`/ruleset gating remove items (`:2326`,`:2332`).

Selection (`create_card`, `common_events.lua:2444-2452`):

```lua
local _pool, _pool_key = get_current_pool(_type, _rarity, legendary, key_append)
center = pseudorandom_element(_pool, pseudoseed(_pool_key))
local it = 1
while center == 'UNAVAILABLE' do
  it = it + 1
  center = pseudorandom_element(_pool,
    pseudoseed(_pool_key..(MP.should_use_the_order() and '' or ('_resample'..it))))
end
```

This is precisely **`GameQueue.nextWhere(notUnavailable)`**: keep drawing the same
key's stream until a usable item appears; never branch. In vanilla, a resample uses
key `Joker1_resample2` (a *different* stream) — fine for solo, but it would let two
players diverge. **The Order forces the suffix to `''`, so resample advances the
SAME `Joker1` stream** — the block/skip semantics our `queue-model.md` describes.

### 1.3 The "main queue of tags" (category selection) — `cdt`

`create_card_for_shop(area)` (`UI_definitions.lua:755-820`) decides each shop slot's
*category* before `create_card` decides the *item*:

```lua
local total_rate = G.GAME.joker_rate + G.GAME.playing_card_rate + Σ consumable rates
local polled_rate = pseudorandom(pseudoseed('cdt'..MP.ante_based())) * total_rate   -- :782
-- walk rates in FIXED order {Joker, Tarot, Planet, Base/Enhanced, Spectral, ...}
```

`MP.ante_based()` returns `0` under The Order (`TheOrder.lua:293-296`), so the key is
just `"cdt"` → **one game-long category stream**. The default rates (vanilla
`game.lua` shop init, *unverified exact values from disk here*) are roughly
Joker 20, Tarot 4, Planet 4, PlayingCard 0 (needs Magic Trick voucher), Spectral 0
(needs Ghost deck) — so most slots roll Joker, occasionally a Tarot/Planet, and a
slot whose category is gated to rate 0 simply can never be selected. That gating is
the "skip a Spectral slot when not on Ghost deck" behavior described in the
spreadsheet (`08_Shop_Queue.txt:29`). The spreadsheet's "CJ/UJ/RJ/T/P/S/C" tag list
is a *player-facing mental model* of: (cdt stream picks category) → (rarity stream
picks Joker rarity for Joker slots) → (per-rarity pool stream picks the item).

For a Joker slot, rarity is rolled by `SMODS.poll_rarity("Joker",'rarity'..ante..append)`
(`common_events.lua:2251`) → under The Order, key `"rarity"` (ante zeroed) is the
**rarity stream**; thresholds `>0.95 → Rare, >0.7 → Uncommon, else Common`
(`:2249`). So the player-facing "main queue of tags" = the interleaving of the
`cdt` stream and the `rarity` stream.

### 1.4 Soul / Black Hole insertion

`create_card` (`common_events.lua:2418-2431`):

```lua
if (_type=='Tarot' or 'Spectral' or 'Tarot_Planet') and not used(c_soul) then
  if pseudorandom('soul_'..(MP.should_use_the_order() and 'c_soul' or _type)..ante) > 0.997
    then forced_key = 'c_soul' end
end
if (_type=='Planet' or 'Spectral') and not used(c_black_hole) then
  if pseudorandom('soul_'..(MP.should_use_the_order() and 'c_black_hole' or _type)..ante) > 0.997
    then if not (the_order and forced_key) then forced_key = 'c_black_hole' end end
end
```

- Vanilla uses key `soul_<type><ante>` → many streams. **The Order collapses to two
  game-long streams: `"soul_c_soul"` and `"soul_c_black_hole"`** (ante zeroed,
  type-independent). This is the "single game-long hit/miss queue" in
  `08_Shop_Queue.txt:56`.
- Threshold is `> 0.997` → ~0.3% per consumable created. (Vanilla nominal Soul rate.)
- **Insertion semantics** (`08_Shop_Queue.txt:56-61`): every consumable *created in a
  Tarot/Spectral pack* rolls the soul stream; on a hit, the consumable that would
  have occupied that slot is *pushed back one* and The Soul/Black Hole takes the
  slot. Because the soul stream is consumed once per created card and is independent
  of the consumable pool stream, a hit doesn't consume a pool item — it *inserts*.
  Both players, opening the same pack at the same queue positions, see the soul at
  the same slot.
- Mutual exclusion: under The Order, if `c_soul` already won the slot, `c_black_hole`
  cannot also overwrite it (`:2427` guard).

### 1.5 Up-Top vs Pack consumable queues

The split (`08_Shop_Queue.txt:46-53`) is realized by **`key_append`** in
`TheOrder.lua:9-14`:

```lua
if _type=="Tarot"/"Planet"/"Spectral" then
  if area == G.pack_cards then key_append = _type.."_pack"   -- Pack queue
  else                          key_append = _type           -- Up-Top queue
end
```

So pool key becomes `Tarot` (up-top) vs `Tarot_pack` (pack) — **two distinct
game-long streams per consumable type**. Up-Top covers The Fool, Superposition,
Séance, etc. (any non-pack consumable creation); Pack covers booster contents.
This matches our `queue-model.md` §"Up-Top vs Pack".

### 1.6 Packs queue (the booster *types*, with skip-offset)

`get_pack(_key,_type)` (`common_events.lua:2224-2241`) weight-rolls a booster from
`G.P_CENTER_POOLS['Booster']` using `pseudoseed((_key or 'pack_generic')..ante)`.
Under The Order, ante=0 → game-long packs stream. The first-ever shop is hard-pinned
to a random normal Buffoon pack (`:2225-2228`, `math.random(1,2)` — **note this is a
raw `math.random`, a parity hazard**, see Open Questions).

Skip-offset (`08_Shop_Queue.txt:62-66`): the packs stream only advances **when a
shop is actually seen**. Skipping a blind means you don't draw, so your packs shift
one shop later relative to a nemesis who didn't skip. This is the canonical reason
the packs queue is a *queue keyed to shop-views*, not to ante.

### 1.7 Voucher queue 1–16

`get_culled` + `SMODS.get_next_vouchers` / `get_next_voucher_key` overridden in
`TheOrder.lua:222-290`:

- The voucher pool is **paired Tier-1/Tier-2** (`get_culled` walks `i=1,#pool,2`,
  `:224`). The "1–16" framing in `10_Vouchers.txt`/`08_Shop_Queue.txt:67-73` is the
  16 *base* voucher families; each number resolves to its Tier-1, or Tier-2 if T1 is
  owned, or is skipped if both owned (the `UNAVAILABLE` culling at `:230-241`).
- **Single key `"Voucher0"`** for every draw (`pseudoseed("Voucher0")`, `:256,278`)
  → one game-long voucher stream, ante-independent by construction.
- Resample on `UNAVAILABLE`/already-spawned re-draws the SAME `Voucher0` stream
  (`:258-264`); only after 1000 failures does it append `it` as a fallback.
- Voucher **tags** advance the same stream; the doc's "back-to-back duplicate
  skipped" rule (`08_Shop_Queue.txt:73`) maps to the `vouchers.spawn[center]`
  dedupe guard (`:258`).

### 1.8 Side joker queues (Rare-tag/Wraith, Uncommon-tag, Riff-Raff/Top-Up)

These are tag/joker effects that create jokers **outside** the shop. In vanilla each
forces a rarity and calls `create_card('Joker', area, false, rarity, ...)`. Under The
Order the rarity pool key is still `Joker1/2/3` — **but** the spreadsheet's intent
(`queue-model.md:66-68`) is that these draw from *their own* streams so a player can
get a joker their nemesis never saw. In BMP 0.4.0 this is partly achieved because
side creations happen at different *positions* in the same per-rarity stream than
shop creations would — they share the `JokerN` stream but advance it at points the
opponent may not. Judgement is special-cased: `TheOrder.lua:16-19` gives Judgement
its own rarity sub-roll (`pseudorandom("order_jud_rarity")`) only when eternals are
enabled, "to avoid jank and create a little [variety]". **DELTA/ambiguity:** whether
Riff-Raff/Wraith use a *truly separate* pool key is not explicit in TheOrder.lua;
they ride the shared `JokerN` streams. Flagged in Open Questions — our model can be
stricter (separate keys) if we want true side-independence.

### 1.9 Bloodstone / Lucky / Glass (in-scoring probability)

These are SMODS `pseudorandom_probability` calls inside `calculate`. Example
Bloodstone (`objects/jokers/sandbox/bloodstone.lua:28`):
`SMODS.pseudorandom_probability(card, "j_mp_bloodstone_sandbox", 1, odds)` with
`odds=3`, `Xmult=2`. Lucky/Glass use vanilla keys `lucky_money`/`lucky_mult` and the
glass break key. The parity property we need: **equal triggers → equal hits
regardless of hands-left or order.** BMP gets this from the per-key stream model
(each probability key is its own game-long stream). The spreadsheet adds
(`queue-model.md:70-71`) a **Bloodstone PvP nuance**: a per-ante PvP queue that
*resets to its start after each hand*, so two players who each trigger Bloodstone the
same number of times in a hand get identical hits even if one had more hands left.
This reset-per-hand behavior is **not visible in the on-disk 0.4.0 sandbox joker**
(it uses a plain probability call); flagged as a 0.3.3-doc behavior to verify.

### 1.10 Shuffle / pseudorandom_element determinism (The Order)

`TheOrder.lua:358-461` replaces `pseudoshuffle` and `pseudorandom_element` for
playing-card and joker collections with a **value-keyed sort** (`give_shufflevals`):
each card gets a `mp_shuffleval = pseudorandom(suit+rank+seed)` and the list is
sorted by it, after first grouping identical cards (by enhancement/seal/edition
"stdval", `:326-356`). This makes "pick a random card from the deck/jokers"
**insensitive to the table iteration order** (`pairs()` is unordered in Lua), which
would otherwise desync players whose internal table layout differs. This is why Idol
(`:31-109`) and Mail (`:113-180`) are reworked to sort-then-weighted-pick instead of
relying on deck order.

### 1.11 CHANGELOG 0.3.3 → 0.4.0 deltas relevant to queues

`multiplayer-0.4.0/CHANGELOG.md`:
- **To Do List** reworked: target hand chosen from **all** hands, not just
  discovered (`:9`). Affects the To-Do selection stream's candidate set.
- **Speedrun out of rotation** (`:11`) — drop its nemesis-spend stream.
- **Justice back in rotation** (`:17`) — re-add to Up-Top/Pack tarot pools.
- **Ouija/Ectoplasm cost fix $4** (`:12`) — economy only, no queue effect.
- Judgement own-queue rule (from 0.3.0, `:48-49`): "draws from its own queue
  (vanilla behavior) on Orange Stake and above; uses shop queue on lower stakes" —
  **stake-gated queue selection**, relevant to §1.8.

---

## 2. How OUR engine does it today

`com.balatro.engine.rng`:

- **`Rng`** (`Rng.java`) — xoshiro256** + SplitMix64 seeder. Clean, documented,
  **deliberately NOT byte-compatible** with vanilla `pseudohash` (spec §8,
  `balatro-engine-spec.md:323`). Has `nextDouble`, `nextInt`, `chance(num,den)`.
- **`RandomStreams`** (`RandomStreams.java`) — keyed streams: `stream(key)` derives
  an independent `Rng` from `mix(masterSeed,key)` (FNV-1a). This is our analog of
  per-purpose `pseudoseed` keying. Also `shuffle(list,key)` (Fisher–Yates) and
  `stringToSeed`.
- **`QueueSet`** (`QueueSet.java`) — `queue(key, drawOne)` lazily builds a
  `GameQueue` backed by `rng.stream("queue:"+key)`. One queue per key, cached.
- **`GameQueue<T>`** (`GameQueue.java`) — lazily generated, cached, cursored
  sequence. `next()/peek()`, `nextWhere(pred)` (block/skip, never re-rolls — exactly
  vanilla's UNAVAILABLE loop), `take(n)`, `take(n,pred)`.

Currently *wired up* (`queue-model.md:33-41`, `ScoringEngine.java`):
- `jokers`, `planets` shop queues (uniform, not rarity-split).
- `lucky_mult` (1/5), `lucky_money` (1/15), `glass` (1/4) probability queues:
  `ScoringEngine.lucky()` (`ScoringEngine.java:256-260`) pops `q.next()` (a raw
  `[0,1)`), threshold applied at read time honoring `run.probabilityNumerator`
  (Oops! All 6s) — `:190,194,202`. This is the right shape (raw roll stored, threshold
  at read).

Not yet built: rarity-split joker queues, main category (`cdt`) stream, Up-Top vs
Pack split, Soul/Black Hole insertion, packs (booster-type) queue, voucher 1–16
queue, side joker queues, Bloodstone PvP-reset queue, Idol/To-Do/Mail/Invisible
deterministic selection.

---

## 3. The GAP

| Concern | BMP 0.4.0 | Ours today | Gap |
|---|---|---|---|
| Per-category stream | `pseudoseed(key)` with ante zeroed | `QueueSet.queue(key,…)` | **Right shape; missing the keys & ante-zeroing discipline.** |
| Joker rarity split | `Joker1/2/3/4` pool streams + `rarity` stream | one uniform `jokers` queue | Need 4 rarity queues + a rarity-selection stream. |
| Main category (tags) | `cdt` rate-weighted stream | none (shop generation not modeled) | Need a `cdt` category stream + fixed rate-walk order. |
| Up-Top vs Pack | `Tarot` vs `Tarot_pack` keys (×3 types) | single `planets`, no tarot/spectral split | Need 6 streams (3 types × 2 contexts). |
| Soul / Black Hole | `soul_c_soul`, `soul_c_black_hole` streams, **insert on hit** | none | Need 2 hit/miss streams + insertion logic in pack fill. |
| Packs (booster type) | `pack_generic` weighted stream, advanced per **shop-view** | none | Need a booster-type queue advanced on shop reveal (skip-offset). |
| Voucher 1–16 | single `Voucher0` stream over culled T1/T2 pairs | none | Need voucher queue + tier-resolution + tag-advance + dup-skip. |
| Side joker queues | shared `JokerN` streams (Judgement special) | none | Need policy: shared vs separate keys (we can be stricter). |
| Bloodstone PvP | per-key prob stream (+ doc's per-hand reset) | generic prob queue ok for non-PvP | Need PvP per-ante queue with **reset-to-start each hand**. |
| Deterministic pick | sort-then-pick (Idol/Mail/shuffle/element) | `RandomStreams.shuffle` exists; no sort-stable element pick | Need order-insensitive element selection for deck/joker picks. |
| Paused draws | `paused` short-circuit **disabled** | n/a (server never "pauses") | No gap — but document that server must never substitute a live draw. |
| Resample branching | suffix forced to `''` (same stream) | `nextWhere` already advances same queue | **No gap — our `nextWhere` is already correct.** |

---

## 4. Proposed target design (authoritative, queue-shaped, ruleset-driven)

### 4.1 Canonical queue keys (the run's queue topology)

Define a single enum/registry of queue keys so both the engine and the ruleset agree.
All keys are **ante-independent** (the ante-zeroing trick is baked in by *not putting
ante in the key*). One `GameQueue` per key on `RunState.queues`:

```
joker.common        joker.uncommon      joker.rare       joker.legendary
joker.rarity        // rate stream: maps roll → rarity bucket
shop.category       // "cdt": maps roll → {JOKER,TAROT,PLANET,PLAYING,SPECTRAL,...}
tarot.uptop         planet.uptop        spectral.uptop
tarot.pack          planet.pack         spectral.pack
soul.c_soul         soul.c_black_hole   // hit/miss, threshold at read
packs               // booster-type queue, advanced on shop reveal
voucher             // single stream over culled T1/T2 pairs ("Voucher0")
playing_card        // Magic Trick / standard pack faces
prob.lucky_mult  prob.lucky_money  prob.glass  prob.<joker_key>   // existing pattern
bloodstone.global   bloodstone.pvp     // pvp resets each hand
select.idol  select.todo  select.mail  select.invisible          // sort-then-pick
```

`QueueSet` already namespaces with `"queue:"+key`; keep that. Add a `QueueKeys`
constants holder + javadoc table mirroring §1 cites.

### 4.2 Block/skip = `nextWhere`, item pools owned by the ruleset

- The ruleset supplies the **ordered, culled pool** per category (the analog of
  `get_current_pool`): a `List<Item>` where blocked entries are marked
  `UNAVAILABLE` (owned voucher, soft-locked planet, banned/`mp_include`-gated joker,
  hidden Soul/Black Hole). The pool order MUST be deterministic and identical for
  both players (sort by a stable id, as vanilla does at `misc_functions.lua:289-293`).
- Selection = `queue.nextWhere(item -> item != UNAVAILABLE && acceptable(item))`.
  This already matches vanilla's resample-same-stream-under-The-Order loop. Our
  `GameQueue.nextWhere` is correct; we only need to feed it the right pool predicate.
- **Important:** the pool must be recomputed at the moment of draw (ownership changes
  between rolls), but the *stream* is never reset — exactly like vanilla recomputing
  `get_current_pool` each `create_card` while `G.GAME.pseudorandom["Joker1"]` keeps
  advancing.

### 4.3 Shop generation pipeline (per slot)

```
slot:
  category = shop.category.next()  mapped via fixed rate-walk order
             {JOKER, TAROT, PLANET, PLAYING/ENHANCED, SPECTRAL, …}   // §1.3 cdt
  if category gated to rate 0 for this run (no Ghost→Spectral, no MagicTrick→Playing):
       it can never be selected (rate 0), matching the "skip" in the doc
  if category == JOKER:
       rarity = bucketize(joker.rarity.next())  // >.95 Rare, >.7 Uncommon else Common
       item   = joker.<rarity>.nextWhere(pool)
  else if TAROT/PLANET/SPECTRAL:
       item   = <type>.uptop.nextWhere(pool)
       // shop consumables are Up-Top, NOT pack
  else PLAYING: playing_card.nextWhere(pool)
```

Reroll = generate the next N slots = advance the same streams further. Both players
on a seed see identical slots; a player who rerolls more is simply further along the
same streams (the core fairness property, `queue-model.md:10-17`).

### 4.4 Packs (booster types) — shop-view-advanced

- `packs` queue yields booster *types* (Normal/Jumbo/Mega × Arcana/Celestial/
  Spectral/Buffoon/Standard) by the vanilla weight table.
- **Advance only on shop reveal** (2 per shop). Skipping a blind = no advance =
  skip-offset vs nemesis (§1.6). Model this by advancing `packs` exactly when a shop
  is materialized for that player, keyed to *that player's* cursor.
- First-shop Buffoon pin (`get_pack:2225`): replicate, but replace the raw
  `math.random(1,2)` with a dedicated `packs.firstBuffoon` draw so it stays
  deterministic per player (see Open Questions on whether both players must get the
  *same* pin — they should).

### 4.5 Pack opening + Soul/Black Hole insertion

```
open Tarot/Spectral pack of size k:
  produced = []
  while len(produced) < k:
     if pack is tarot/spectral and soul.c_soul.next()  >= 0.997-threshold: push c_soul
     else if pack is planet/spectral and soul.c_black_hole.next() >= threshold: push c_black_hole
     else push <type>.pack.nextWhere(pool)
  // a soul HIT inserts and does NOT consume the type.pack stream → push-back-one
```

The soul stream is advanced **once per produced card** (not per pool draw), so a hit
inserts the legendary and defers the would-be consumable to the next slot — exactly
`08_Shop_Queue.txt:56-61`. Both players, at equal pack-queue positions, see the soul
at the same index. Mutual-exclusion guard (Soul beats Black Hole, §1.4).

### 4.6 Voucher queue

- Build the **culled paired pool** (T1/T2 by family) once per draw from current
  ownership (`get_culled`, `TheOrder.lua:222-244`).
- `voucher` stream: `pick = pool.pickWeighted(voucher.next())`; resample SAME stream
  while `UNAVAILABLE` or already-in-shop (mirror `:258-264`, incl. the 1000-iter
  fallback only if we hit it — log if we do).
- Tier resolution: chosen family → T1 if unowned, else T2 if unowned, else skip.
- Voucher **tag** advances the same stream, with **back-to-back duplicate skipped**
  (`08_Shop_Queue.txt:73`) → track last-spawned key, re-draw if equal.

### 4.7 Side joker queues — make the policy explicit

Decision point we should resolve up front (ruleset-config):
- **Parity-faithful:** Riff-Raff/Wraith/Uncommon-tag/Top-Up ride the shared
  `joker.<rarity>` streams (what 0.4.0 TheOrder.lua does, modulo Judgement). Cheaper,
  matches BMP exactly.
- **Stricter (our option):** give each side source its own key
  (`joker.rare.wraith`, `joker.uncommon.tag`, `joker.riffraff`) so they truly can
  yield jokers the nemesis never sees, as `queue-model.md:66-68` aspires to.
- Judgement: own rarity sub-roll only when eternals enabled (`TheOrder.lua:16-19`),
  and own-queue vs shop-queue **stake-gated** (CHANGELOG 0.3.0 `:48-49`). Encode as
  ruleset flags `judgement_own_queue_min_stake`, `judgement_own_rarity_roll`.

### 4.8 Bloodstone / Lucky / Glass

- Lucky/Glass: keep current `prob.*` queues storing raw `[0,1)`, threshold at read
  honoring `probabilityNumerator` — already correct.
- Bloodstone: **two** queues. `bloodstone.global` advances per heart played outside
  PvP; `bloodstone.pvp` is a per-ante queue **whose cursor resets to its ante-start
  position after each hand** so equal triggers → equal hits regardless of hands-left
  (`queue-model.md:70-71`). Implement reset as: snapshot cursor at hand start,
  restore at hand end (or model as `queue.resetTo(anteStartCursor)`), keeping the
  underlying generated items cached. **FLAG: verify this reset behavior against
  0.4.0** — the on-disk sandbox Bloodstone uses a plain prob call; the per-hand reset
  is from the 0.3.3 doc and may have changed.

### 4.9 Deterministic selection (Idol / To-Do / Mail / Invisible / shuffle)

Port the **sort-then-pick** discipline (`TheOrder.lua`):
- Build a canonical-sorted candidate list (by stable id / count / stdval — never by
  hash-map iteration order).
- Roll one value from the selection stream; pick by cumulative weight (Idol/Mail) or
  by sorted position (Invisible) or over the unlocked-hand set (To-Do; **all** hands
  per 0.4.0 CHANGELOG, not just discovered).
- For deck shuffles and "random card" picks, replicate `give_shufflevals`: assign
  each card a per-stream value and sort, so the result is independent of internal
  list order. Our `RandomStreams.shuffle` is order-dependent on input order — fine if
  both players' input lists are themselves canonicalized first; otherwise add a
  `shuffleByValue` that sorts by a per-card stream draw.

### 4.10 Cursor/state, persistence, hidden-info

- Each queue keeps a per-player cursor. The master seed and all stream state are
  **server-only** (spec §8 hidden-information boundary, `RandomStreams.java:13-16`).
- Snapshot = (masterSeed, per-queue cursor, plus any reset-anchors like Bloodstone
  ante-start). The cached item lists are regenerable from seed, so snapshots need
  only cursors. Replays restore cursors and replay intents.

---

## Open questions

1. **First-shop Buffoon pin parity.** Vanilla `get_pack:2227` uses raw
   `math.random(1,2)` (live, *outside* pseudoseed). Under BMP do both players get the
   same pin, or is it desynced? If it must match, we route it through a dedicated
   deterministic draw. Need to confirm whether BMP server pre-seeds `math.random` so
   the two `math.random(1,2)` calls agree.
2. **Side joker queue independence.** Does 0.4.0 actually give Riff-Raff / Wraith /
   Uncommon-tag their own pool keys, or do they share `JokerN` with the shop? Disk
   evidence (TheOrder.lua) only special-cases Judgement. Confirm by inspecting the tag
   objects in `objects/tags/` (not yet read) before choosing §4.7 policy.
3. **Bloodstone PvP per-hand reset.** Is the "reset to start each hand" rule from the
   0.3.3 doc still present in 0.4.0? The on-disk sandbox joker
   (`bloodstone.lua`) shows only a plain `pseudorandom_probability`; need to find
   where (if anywhere) PvP hearts reset the stream.
4. **Exact shop category rates.** The `cdt` rate-walk needs the real default
   `joker_rate / tarot_rate / planet_rate / spectral_rate / playing_card_rate`
   values and how vouchers (Tarot Merchant, Planet Merchant, Magic Trick, Illusion)
   mutate them. Not yet pulled from `game.lua` shop init — currently *unverified*.
5. **Resample fallback realism.** Voucher draw has a 1000-iteration fallback to a
   suffixed stream (`TheOrder.lua:261-263`). Do we ever hit it in normal play? If so
   our "advance same stream" guarantee needs the identical fallback to stay in sync.
6. **`hashed_seed`/`pseudohash` byte-compat.** Confirmed we are intentionally NOT
   byte-compatible (spec §8). Re-affirm this is acceptable for ranked parity (we only
   need *our* server to be deterministic, not to match vanilla seeds) — assumed yes.
7. **Judgement stake gating** values: which stakes flip own-queue vs shop-queue, and
   does 0.4.0 keep the 0.3.0 "Orange and above" threshold?

## New building blocks needed

- **`QueueKeys`** — central constants + javadoc registry of every queue key in §4.1,
  each cited to its BMP/vanilla source. Single source of truth for both engine and
  rulesets.
- **`ShopGenerator`** — owns the per-slot pipeline (§4.3): consumes `shop.category`,
  `joker.rarity`, and the per-category pools; produces shop slots and reroll batches.
- **`Pool` / `PoolEntry` + `UNAVAILABLE` marker** — ruleset-supplied ordered, culled
  candidate lists with a stable sort, recomputed per draw from current ownership;
  drives `GameQueue.nextWhere`. The analog of `get_current_pool`.
- **`RarityRoller`** — `bucketize(double)` → Common/Uncommon/Rare/Legendary with the
  vanilla thresholds; isolated so rulesets can override weights.
- **`PackQueue`** — booster-type queue advanced on shop reveal (skip-offset), with
  first-shop Buffoon pin.
- **`PackOpener` + soul insertion** — pack-fill loop that advances `soul.c_soul` /
  `soul.c_black_hole` per produced card and inserts on hit (push-back-one), with the
  Soul-beats-Black-Hole guard.
- **`VoucherQueue`** — culled T1/T2 paired pool, single-stream draw with
  resample-same-stream, tier resolution, tag-advance, back-to-back-dup skip.
- **`GameQueue.resetTo(int cursor)`** (or a `Bookmark`) — to support Bloodstone PvP
  per-hand reset without discarding cached items.
- **`DeterministicSelector`** — sort-then-pick utility (canonical-sort + weighted/
  positional pick) for Idol, Mail, To-Do, Invisible; plus `RandomStreams.shuffleByValue`
  for order-insensitive shuffles/element picks (port of `give_shufflevals`).
- **Ruleset hooks** — flags for: banned vouchers (Director's Cut/Retcon/Hieroglyph/
  Petroglyph per `10_Vouchers.txt:2-3`), Justice in-rotation, Speedrun out, To-Do
  all-hands, Judgement own-queue stake gate + eternal-rarity roll, side-queue
  independence policy. Driven by `ruleset/RulesetConfig`, not hard-coded.
- **Paused-draw guard doc/test** — assert the server never substitutes a live draw
  for a queue read (the behavior BMP secured by disabling `pseudoseed`'s `paused`
  branch, `misc_functions.lua:330-331`).
