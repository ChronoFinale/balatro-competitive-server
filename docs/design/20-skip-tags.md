# Skip Tags — Balatro Multiplayer 0.4.0 catalogue

Scope: all 24 vanilla Skip Tags, their **exact effect with real numbers**, the
in-game **trigger/context** that fires each, and — critically for our 1v1 engine
— the **BMP queue behavior** (which tags pull from a queue *separate* from the
shop, and which advance shared queues).

## Sources (every claim grounded)

- Tag effect logic (vanilla `Tag:apply_to_run`): `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/game-dump/tag.lua`
- Tag prototypes (keys, `config`, `min_ante`, `order`): `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` lines 234–257
- Tag UI / `loc_vars` (display numbers): `tag.lua` `Tag:get_uibox_table` lines 570–587
- **BMP 0.4.0 tag reworks** (`MP.ReworkCenter` overrides): `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/rulesets/release.lua` lines 495–647
- BMP 0.4.0 reworked tag text: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/localization/en-us.lua` lines 19–66
- BMP queue semantics (0.3.3 baseline doc): `C:/Users/micha/AppData/Local/Temp/xlsx_out2/09_Skip_Tags.txt`, `.../08_Shop_Queue.txt`
- RNG / separate-stream mechanism (`pseudoseed`, `pseudohash`): `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/game-dump/functions/misc_functions.lua` lines 309–343
- 0.4.0 deltas: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/CHANGELOG.md`
- Our queue model: `D:/NewServer/queue-model.md`

## How "separate queue" works mechanically

In Balatro, every random draw is keyed by a **seed string** passed to
`pseudoseed(key)`. `G.GAME.pseudorandom[key]` is a per-key cursor that advances
only when that key is rolled (`misc_functions.lua:339-343`). So the *seed string
is the queue*. Two different strings = two independent, game-long sequences.

`create_card('Joker', area, nil, rarity, nil, nil, nil, KEY)` passes `KEY` as the
joker-pool seed. The shop uses keys like `'rarity'` / `'Joker1'`; the rarity tags
use **their own** keys:

- **Uncommon Tag** → `create_card(..., 'uta')` (`release.lua:501`, vanilla `tag.lua:392`)
- **Rare Tag** → `create_card(..., 'rta')` (`release.lua:530`, vanilla `tag.lua:378`)

Because `'uta'` and `'rta'` are distinct from the shop's keys, these tags pull
from a sequence the shop never touches — the "separate game-long queue" the BMP
docs describe. The only way to advance the `uta`/`rta` streams is to take more of
those tags (or, for `rta`, a Wraith spectral, which BMP also keys to the rare
stream — `09_Skip_Tags.txt`: "Rare Tags share a game long queue with Wraith").

The **pack tags** (Charm/Meteor/Standard/Ethereal/Buffoon) call `G.FUNCS.use_card`
on a forced pack center, so they consume from the *Pack* queue of that consumable
type — same queue a real pack of that type would (`09_Skip_Tags.txt`).

---

## Master table — all 24 tags

`min_ante` = earliest ante the tag can appear. `order` = collection order.
"Effect" reflects **BMP 0.4.0** where it diverges from vanilla (deltas flagged).

| # (order) | Name | Key | `config.type` | Trigger context | Exact effect (BMP 0.4.0) | min_ante | Queue behavior |
|---|---|---|---|---|---|---|---|
| 1 | Uncommon Tag | `tag_uncommon` | `store_joker_create` | On shop generation (next shop) | Adds an **Uncommon** joker to the shop. **0.4.0:** at full price (vanilla made it free via `couponed`). | — | **Separate `uta` queue** — not the shop's. Only Uncommon Tags advance it. |
| 2 | Rare Tag | `tag_rare` | `store_joker_create` (`odds = 3`) | On shop generation | Adds a **Rare** joker to the shop. Guarded: if you already own every distinct Rare in the pool, fires `nope()` (nothing). **0.4.0:** at full price (vanilla made it free). Requires `j_blueprint` discovered. | — | **Separate `rta` queue**, *shared with Wraith*. Only Rare Tags / Wraiths advance it. |
| 3 | Negative Tag | `tag_negative` | `store_joker_modify` (`edition='negative'`, `odds=5`) | On each shop joker as it's created | Next base-edition shop **Joker** becomes **Negative**. **0.4.0:** no cost change (vanilla also made it free). Requires `e_negative`. | 2 | Modifies a shop joker in place; rides the shop joker queue (no separate queue). |
| 4 | Foil Tag | `tag_foil` | `store_joker_modify` (`edition='foil'`, `odds=2`) | On each shop joker as it's created | Next base-edition shop **Joker** becomes **Foil** (+50 chips). **0.4.0:** no cost change. Requires `e_foil`. | — | Modifies shop joker in place; no separate queue. |
| 5 | Holographic Tag | `tag_holo` | `store_joker_modify` (`edition='holo'`, `odds=3`) | On each shop joker | Next base-edition shop **Joker** becomes **Holographic** (+10 Mult). **0.4.0:** no cost change. Requires `e_holo`. | — | Shop joker, in place; no separate queue. |
| 6 | Polychrome Tag | `tag_polychrome` | `store_joker_modify` (`edition='polychrome'`, `odds=4`) | On each shop joker | Next base-edition shop **Joker** becomes **Polychrome** (×1.5 Mult). **0.4.0:** no cost change. Requires `e_polychrome`. | — | Shop joker, in place; no separate queue. |
| 7 | Investment Tag | `tag_investment` | `eval` | End of round, **if last blind was a Boss** | Gain **$15** after defeating the Boss Blind. **0.4.0 DELTA: $15** (vanilla = **$25**), see `release.lua:646`. | — | No queue. Flat payout. |
| 8 | Voucher Tag | `tag_voucher` | `voucher_add` | When shop voucher slot is built | Adds **one** Voucher to the shop (`get_next_voucher_key(true)`; `card_limit += 1`). | — | **Advances the Voucher queue** (1–16). Same advance as seeing a new ante; back-to-back identical voucher is skipped. |
| 9 | Boss Tag | `tag_boss` | `new_blind_choice` | On blind-select screen | **Rerolls the Boss Blind** (`G.FUNCS.reroll_boss()`, `G.from_boss_tag`). | — | No queue. **BANNED in ranked** (interacts with PvP blind) — `09_Skip_Tags.txt`. |
| 10 | Standard Tag | `tag_standard` | `new_blind_choice` | On blind-select screen | Opens a free **Mega Standard Pack** (`p_standard_mega_1`): 5 cards, pick 2. | 2 | **Pack queue (Standard)** — same as opening a Standard pack. Giga packs on Orange Deck also take from this queue. |
| 11 | Charm Tag | `tag_charm` | `new_blind_choice` | On blind-select screen | Opens a free **Mega Arcana Pack** (`p_arcana_mega_1` or `_2`, `math.random(1,2)`): 5 Tarots, pick 2. | — | **Pack queue (Arcana/Tarot).** |
| 12 | Meteor Tag | `tag_meteor` | `new_blind_choice` | On blind-select screen | Opens a free **Mega Celestial Pack** (`p_celestial_mega_1` or `_2`): 5 Planets, pick 2. | 2 | **Pack queue (Celestial/Planet).** |
| 13 | Buffoon Tag | `tag_buffoon` | `new_blind_choice` | On blind-select screen | Opens a free **Mega Buffoon Pack** (`p_buffoon_mega_1`): 4 Jokers, pick 2. | 2 | **Pack queue (Buffoon).** Jokers pulled per the rarity queues (`08_Shop_Queue.txt`). |
| 14 | Handy Tag | `tag_handy` | `immediate` (`dollars_per_hand = 1`) | On blind skip (immediate) | Gain **$1 per hand played** this run (`G.GAME.hands_played * 1`). | 2 | No queue. Scales with lifetime hands. |
| 15 | Garbage Tag | `tag_garbage` | `immediate` (`dollars_per_discard = 1`) | On blind skip (immediate) | Gain **$1 per unused discard** this run (`G.GAME.unused_discards * 1`). | 2 | No queue. |
| 16 | Ethereal Tag | `tag_ethereal` | `new_blind_choice` | On blind-select screen | Opens a free **Spectral Pack** (`p_spectral_normal_1`): 2 Spectrals, pick 1. | 2 | **Pack queue (Spectral).** |
| 17 | Coupon Tag | `tag_coupon` | `shop_final_pass` | When shop opens (final pass) | All **shop Jokers and Booster Packs** cost **$0** this shop (`G.GAME.shop_free`; sets `couponed` on each). Playing cards/vouchers unaffected. | — | No queue. One-shot per shop. |
| 18 | Double Tag | `tag_double` | `tag_add` | When the *next* tag is added | Copies the next selected tag (`add_tag(Tag(_context.tag.key))`); will not copy another Double Tag. Carries `orbital_hand` to a copied Orbital. | — | No queue itself; the **copy** then runs its own trigger/queue. |
| 19 | Juggle Tag | `tag_juggle` | `round_start_bonus` | At start of next round | **+3 hand size** for that round only (`h_size = 3`; stored in `round_resets.temp_handsize`). | — | No queue. |
| 20 | D6 Tag | `tag_d_six` | `shop_start` | When shop starts | Shop **rerolls start at $0** this shop (`temp_reroll_cost = 0`; once per shop via `shop_d6ed`). | — | No queue. |
| 21 | Top-up Tag | `tag_top_up` | `immediate` (`spawn_jokers = 2`) | On blind skip (immediate) | Creates up to **2 Common Jokers** directly into your joker area (respects `card_limit`). | 2 | **Separate Common-joker queue** shared with **Riff-Raff** — *not* the shop queue (`09_Skip_Tags.txt`). Can yield commons the nemesis never sees. |
| 22 | Skip Tag | `tag_skip` | `immediate` (`skip_bonus = 5`) | On blind skip (immediate) | Gain **$5 × number of blinds skipped this run** (`G.GAME.skips * 5`). Display preview uses `skips+1`. | — | No queue. Scales with run-long skip count. |
| 23 | Orbital Tag | `tag_orbital` | `immediate` (`levels = 3`) | On blind skip (immediate) | **+3 levels** to a poker hand (`level_up_hand(..., 3)`). The hand is chosen at tag creation: a fixed `orbital_hand`, else `pseudorandom_element(visible hands, pseudoseed('orbital'))`. | 2 | Uses `pseudoseed('orbital')` for hand pick. **0.4.0:** Mac/Windows standardized; still desyncs if players have different hands unlocked (`09_Skip_Tags.txt`). |
| 24 | Economy Tag | `tag_economy` | `immediate` (`max = 40`) | On blind skip (immediate) | **Doubles your money**, capped at **+$40** (`ease_dollars(min(40, max(0, dollars)))`). Negative money gives $0. | — | No queue. |

---

## Detail notes per effect (grounded line refs)

**Rare Tag guard** (`tag.lua:368-390`, `release.lua:521-541`): counts *distinct*
Rare jokers you own; if `#G.P_JOKER_RARITY_POOLS[3] > owned_distinct_rares` it
spawns a Rare (seed `'rta'`), else `tag:nope()`. So with the whole Rare pool owned
the tag does nothing but still consumes the tag slot.

**Economy Tag math** (`tag.lua:197-210`): `ease_dollars(math.min(40, math.max(0,
G.GAME.dollars)), true)`. It *adds* up to your current balance (i.e. doubles),
capped at +$40. At $25 → +$25; at $60 → +$40; at -$3 → +$0.

**Skip / Handy / Garbage scaling** (`tag.lua:170-196`): all read run-long counters
(`G.GAME.skips`, `hands_played`, `unused_discards`) at trigger time, times the
per-unit config ($5 / $1 / $1).

**Double Tag** (`tag.lua:341-355`): guarded by `_context.tag.key ~= 'tag_double'`
so it never copies itself. The copy is added via `add_tag(Tag(key))` and then runs
its own `apply_to_run` on the appropriate context — so a doubled Orbital advances
`pseudoseed('orbital')` a second time, a doubled Charm pulls the Arcana pack queue
again, etc.

**Pack tags** (`tag.lua:230-304`): each constructs a card from a fixed mega/normal
pack center and calls `G.FUNCS.use_card`. `from_tag = true`, `cost = 0`. Because
they go through normal pack-opening, they draw from the **Pack** sub-queue of that
consumable type, *not* the Up-Top queue (`08_Shop_Queue.txt` Part 2).

---

## BMP queue-behavior summary (what matters for our engine)

Mapping to `D:/NewServer/queue-model.md` "To build queue-shaped" list:

| Tag(s) | Queue | Shared with | Advanced by | Separate from shop? |
|---|---|---|---|---|
| Uncommon Tag | `joker_uncommon` (`uta`) | — | Uncommon Tags only | **Yes** |
| Rare Tag | `joker_rare` (`rta`) | **Wraith** spectral | Rare Tags + Wraiths | **Yes** |
| Top-up Tag | `joker_common` (tag/joker stream) | **Riff-Raff** joker | Top-Up Tags + Riff-Raff | **Yes** |
| Charm / Meteor / Standard / Ethereal / Buffoon | Pack queue of that type | real packs of that type (incl. Giga on Orange Deck) | seeing packs of that type | shares the **Pack** queue, not the main shop reroll queue |
| Voucher Tag | Voucher queue (1–16) | new-ante voucher reveals | Voucher Tags + antes | shares Voucher queue |
| Orbital Tag | `pseudoseed('orbital')` | — | each Orbital fire | own seed stream |

All other tags (Investment, Boss, Handy, Garbage, Coupon, Double, Juggle, D6,
Skip, Economy, and the 4 edition tags Negative/Foil/Holo/Poly) are **stateless or
in-place** and need no dedicated queue: edition tags mutate a shop joker that came
from the existing shop queue; the rest are deterministic arithmetic/flags.

---

## 0.4.0 vs 0.3.3 deltas (0.4.0 source wins)

1. **Investment Tag: $15, not $25.** `release.lua:646` sets `config = {type='eval',
   dollars=15}`, overriding the vanilla `dollars = 25` (`game.lua:240`). The 0.3.3
   changes doc did not flag this; **0.4.0 source is authoritative.**
2. **Uncommon / Rare / Negative / Foil / Holo / Poly tags reworked** via
   `MP.ReworkCenter` (`release.lua:495-628`). Behavioral effect is the same draw
   (still `uta` / `rta` for the joker tags; same edition for the modifier tags),
   but the reworks **drop the vanilla `card.ability.couponed = true` + `set_cost()`
   calls** — so in 0.4.0 ranked these tags do **not** make the joker free; you pay
   full price. Reworked text confirms: "Shop has an Uncommon Joker" / "Next base
   edition shop Joker becomes Foil" with no "free" wording (`en-us.lua:28-66`).
3. **Orbital Tag** standardized between Mac/Windows; still desyncs if players have
   different poker hands unlocked (`09_Skip_Tags.txt`). No number change (`levels=3`).
4. **Boss (Reroll) Tag** banned from ranked play (PvP-blind interaction) —
   `09_Skip_Tags.txt`. Not a code change to the tag itself; a ranked ban-list item.
5. **Sandbox layer** bans `tag_rare`, `tag_juggle`, `tag_investment`
   (`layers/sandbox.lua:165`) — layer-specific, not a global 0.4.0 change.
6. The CHANGELOG `tag`-grep hits (Comeback Gold, Golden Ticket, Judgment) are
   **not** Skip-Tag changes; Judgment "draws from its own queue on Orange+ stake"
   is a consumable queue change relevant to Buffoon-tag joker sourcing but does not
   alter any tag's effect.

---

## Open questions

- **Riff-Raff / Top-Up shared common queue:** the 0.3.3 doc states they share a
  common-joker queue separate from the shop, but I did not find an explicit
  `MP.ReworkCenter("tag_top_up")` or a Riff-Raff override in 0.4.0 `release.lua`.
  Confirm whether 0.4.0 keeps vanilla `create_card('Joker', G.jokers, ... 'top')`
  for Top-Up (seed `'top'`) and whether Riff-Raff uses the same seed string — i.e.
  is the "shared queue" still literally a shared seed string in 0.4.0, or only an
  emergent property of identical seed keys? (vanilla uses `'top'` for Top-Up.)
- **Wraith ↔ Rare-tag shared `rta` stream:** confirm in 0.4.0 that Wraith's joker
  creation is keyed to `'rta'` (not the shop key). The 0.3.3 doc asserts the shared
  queue; the 0.4.0 Wraith object/override was not read here.
- **Negative Tag `odds`/eligibility:** vanilla `min_ante = 2`; verify 0.4.0 keeps
  the ante gate and `e_negative` requirement under ranked rulesets.
- **Double Tag + rarity tags:** does doubling an Uncommon/Rare tag advance the
  `uta`/`rta` stream twice (expected from `add_tag`→fresh `apply`), and does our
  engine need to model that double-advance explicitly?
- **Coupon vs Buffoon/booster interaction** under BMP free-shop rules — confirm
  Coupon's `shop_free` flag still zeroes booster pack cost in 0.4.0 ranked.

## New building blocks needed

- **Separate rarity joker queues** keyed independently of the shop: `joker_uncommon`
  (`uta`), `joker_rare` (`rta`), `joker_common` (Top-Up/Riff-Raff). Must NOT share a
  cursor with the shop joker queue. (Extends `QueueSet` per `queue-model.md`.)
- **Cross-source shared cursors:** Rare-tag and Wraith must advance the *same*
  `joker_rare` cursor; Top-Up and Riff-Raff the same `joker_common` cursor. Need a
  way to bind multiple effect-sources to one queue key.
- **Tag trigger-context model:** an enum mirroring `config.type`
  (`immediate`, `new_blind_choice`, `eval`, `store_joker_create`,
  `store_joker_modify`, `voucher_add`, `tag_add`, `round_start_bonus`,
  `shop_start`, `shop_final_pass`) so the engine fires each tag at the right phase
  (skip vs blind-select vs shop-open vs end-of-round).
- **Pack-tag → Pack-queue routing:** Charm/Meteor/Standard/Ethereal/Buffoon must
  pull from the same Pack sub-queue as a real pack of that type (incl. Giga on
  Orange Deck), distinct from the Up-Top queue.
- **Run-long counters as effect inputs:** `skips`, `hands_played`, `unused_discards`
  exposed to tag resolution for Skip/Handy/Garbage.
- **Double-Tag copy mechanism:** ability to enqueue a fresh copy of an arbitrary
  tag that then resolves through its own context/queue (carrying `orbital_hand`).
- **Per-stake/ruleset tag overrides:** model the 0.4.0 reworks — Investment $15,
  rarity/edition tags non-free, Boss-tag ranked ban, sandbox bans — as
  data-driven ruleset overrides rather than hardcoded vanilla values.
- **Orbital hand-pick determinism:** a `pseudoseed('orbital')`-equivalent stream,
  plus handling for unlocked-hand divergence (flagged desync risk).
