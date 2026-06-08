# 17 — Vouchers (Balatro Multiplayer 0.4.0)

Definitive catalogue of all 32 vouchers: the 16 Tier-1 vouchers and their 16 Tier-2
upgrades (8 columns × 2 rows in the collection grid). Every base effect, the exact
config numbers, the Tier-1↔Tier-2 relationship, and the BMP 0.4.0 ranked ban set.

**Scope note.** BMP 0.4.0 does **not** redefine voucher *centers*. All voucher
objects, costs, and effects come straight from the base game. `objects/` in the mod
has no `vouchers/` directory (verified: `objects/` contains only blinds, boosters,
challenges, consumables, decks, editions, enhancements, jokers, seals, stakes,
stickers, tags). BMP only changes (a) which vouchers are **banned** in a gamemode,
and (b) **how/when** vouchers appear in the shop (the deterministic 1–16 voucher
queue under "The Order"). Both are documented below.

## Sources

- Base centers + costs + config + `requires`/`unlock_condition`:
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` lines 601–633
  (and identical in `.../lovely/game-dump/game.lua`).
- Base run parameters the vouchers mutate (`tarot_rate=4`, `planet_rate=4`,
  `edition_rate=1`, `interest_cap=25`, `joker_buffer`, `consumeable_buffer`,
  `discount_percent`, `inflation`, `base_reroll_cost=5`):
  `.../lovely/game-dump/game.lua` lines 1934–1958.
- Ban list (authoritative for 0.4.0): `multiplayer-0.4.0/gamemodes/attrition.lua`
  lines 20–25 and `multiplayer-0.4.0/gamemodes/showdown.lua` lines 16–21.
- "The Order" deterministic shop/voucher queue: `multiplayer-0.4.0/core.lua`
  (`MP.should_use_the_order`, lines 104–105, `the_order = true` default line 176),
  `multiplayer-0.4.0/lovely/TheOrder.toml`, `multiplayer-0.4.0/compatibility/TheOrder.lua`,
  `multiplayer-0.4.0/layers/ranked.lua` (forces `the_order = true`).
- Small World voucher handling: `multiplayer-0.4.0/layers/smallworld.lua`.
- 0.3.3 ranked baseline (cross-check only): `xlsx_out2/10_Vouchers.txt`,
  `xlsx_out2/08_Shop_Queue.txt`.

---

## 1. How vouchers work (mechanics)

- **Cost:** every voucher is `cost = 10` (base). All 32 are identical-priced.
- **Effect application:** redeeming a voucher calls `Card:apply_to_run` and sets
  `G.GAME.used_vouchers[key] = true`. Most effects mutate `G.GAME.modifiers.*` or the
  `G.GAME.*` rate fields whose base values are in `starting_params` (above). The
  effects below are the standard base-game effects; the `config` numbers are quoted
  directly from disk and are the source of truth for magnitudes.
- **Tier pairs:** each Tier-2 voucher carries `requires = {'<tier1_key>'}`. A Tier-2
  can only be redeemed (and only appears in shop) after its Tier-1 has been redeemed.
  Tier-2 vouchers also have an `unlock_condition` (single-player discovery gate); in
  ranked/MP the relevant gate is purely the `requires` Tier-1 prerequisite.
- **Stacking:** Tier-2 effects generally *add to* the Tier-1 effect (they do not
  replace it). E.g. Hone gives editions ×2 chance, Glow Up adds ×4 more for ×4 total
  (rate effectively ×4 in base game — see per-row notes), the merchants double then
  quadruple consumable appearance rate, etc.

### The Order — deterministic voucher queue (BMP 0.4.0)

Ranked (and MP lobbies by default; `the_order = true` in `core.lua` line 176, forced
by `layers/ranked.lua`) makes the shop fully deterministic by prefixing the run seed
with `"*"` (`lovely/TheOrder.toml`). Vouchers come from a **single game-long queue of
numbers 1–16** (one slot per Tier-1 column). Per `xlsx_out2/08_Shop_Queue.txt` (still
accurate for 0.4.0 — The Order is the productionized version of this 0.3.3 design):

- The queue tries to spawn the **Tier-1** voucher for the rolled number.
- If that Tier-1 is already owned, it spawns the **Tier-2** instead.
- If both Tier-1 and Tier-2 are owned, it skips to the next queue entry and repeats.
- **Voucher Tags** advance the queue the same way, except two identical back-to-back
  vouchers collapse: the second is skipped and the next queue entry is shown alongside.
- Small World (`layers/smallworld.lua`) injects deck-granted "fake back vouchers"
  and, on banned/used keys, pulls `get_next_voucher_key()` so banned vouchers are
  transparently replaced rather than shown.

So the 16-number queue is exactly the 16 Tier-1↔Tier-2 columns enumerated below.

---

## 2. Tier-1 / Tier-2 catalogue (16 columns)

All vouchers: `set = "Voucher"`, `cost = 10`. Keys, `order`, and `config` are verbatim
from `dump/game.lua` 601–633. "extra_disp" is the display value when `extra` is the
raw probability weight (the game shows `extra_disp`).

| Col | Tier-1 (key) | order | config | Tier-2 (key) | order | config | requires |
|----|--------------|------|--------|--------------|------|--------|----------|
| 1 | Overstock (`v_overstock_norm`) | 1 | `{}` | Overstock Plus (`v_overstock_plus`) | 2 | `{}` | `v_overstock_norm` |
| 2 | Tarot Merchant (`v_tarot_merchant`) | 17 | `extra=9.6/4, extra_disp=2` | Tarot Tycoon (`v_tarot_tycoon`) | 18 | `extra=32/4, extra_disp=4` | `v_tarot_merchant` |
| 3 | Planet Merchant (`v_planet_merchant`) | 19 | `extra=9.6/4, extra_disp=2` | Planet Tycoon (`v_planet_tycoon`) | 20 | `extra=32/4, extra_disp=4` | `v_planet_merchant` |
| 4 | Clearance Sale (`v_clearance_sale`) | 3 | `extra=25` | Liquidation (`v_liquidation`) | 4 | `extra=50` | `v_clearance_sale` |
| 5 | Hone (`v_hone`) | 5 | `extra=2` | Glow Up (`v_glow_up`) | 6 | `extra=4` | `v_hone` |
| 6 | Reroll Surplus (`v_reroll_surplus`) | 7 | `extra=2` | Reroll Glut (`v_reroll_glut`) | 8 | `extra=2` | `v_reroll_surplus` |
| 7 | Crystal Ball (`v_crystal_ball`) | 9 | `extra=3` | Omen Globe (`v_omen_globe`) | 10 | `extra=4` | `v_crystal_ball` |
| 8 | Telescope (`v_telescope`) | 11 | `extra=3` | Observatory (`v_observatory`) | 12 | `extra=1.5` | `v_telescope` |
| 9 | Grabber (`v_grabber`) | 13 | `extra=1` | Nacho Tong (`v_nacho_tong`) | 14 | `extra=1` | `v_grabber` |
| 10 | Wasteful (`v_wasteful`) | 15 | `extra=1` | Recyclomancy (`v_recyclomancy`) | 16 | `extra=1` | `v_wasteful` |
| 11 | Seed Money (`v_seed_money`) | 21 | `extra=50` | Money Tree (`v_money_tree`) | 22 | `extra=100` | `v_seed_money` |
| 12 | Blank (`v_blank`) | 23 | `extra=5` | Antimatter (`v_antimatter`) | 24 | `extra=15` | `v_blank` |
| 13 | Magic Trick (`v_magic_trick`) | 25 | `extra=4` | Illusion (`v_illusion`) | 26 | `extra=4` | `v_magic_trick` |
| 14 | Hieroglyph (`v_hieroglyph`) ⛔ | 27 | `extra=1` | Petroglyph (`v_petroglyph`) ⛔ | 28 | `extra=1` | `v_hieroglyph` |
| 15 | Director's Cut (`v_directors_cut`) ⛔ | 29 | `extra=10` | Retcon (`v_retcon`) ⛔ | 30 | `extra=10` | `v_directors_cut` |
| 16 | Paint Brush (`v_paint_brush`) | 31 | `extra=1` | Palette (`v_palette`) | 32 | `extra=1` | `v_paint_brush` |

⛔ = banned in ranked (attrition) and showdown gamemodes — see §3.

### Exact effects per column

Numbers in **bold** are the gameplay magnitudes (the `config.extra`/`extra_disp` or
the resulting modifier). Base run parameters referenced: `tarot_rate=4`,
`planet_rate=4`, `edition_rate=1`, `interest_cap=25`, `base_reroll_cost=5`.

**Column 1 — Overstock / Overstock Plus**
- *Overstock* (`v_overstock_norm`): Shop has **+1 card slot** in the main shop (3 → 4
  cards). Sets `G.GAME.modifiers.extra_jokers`/shop card-slot count +1. `config={}`
  (effect is hard-coded, no numeric config).
- *Overstock Plus* (`v_overstock_plus`): **+1 more** card slot (4 → 5). Additive on top
  of Overstock; requires Overstock.

**Column 2 — Tarot Merchant / Tarot Tycoon**
- *Tarot Merchant* (`v_tarot_merchant`): Tarot cards appear **2× more frequently** in
  the shop. `extra_disp=2`; internally `extra=9.6/4` (=2.4) is the new weight relative
  to base `tarot_rate=4`.
- *Tarot Tycoon* (`v_tarot_tycoon`): Tarot cards appear **4× more frequently**.
  `extra_disp=4`; `extra=32/4` (=8). Replaces Merchant's weight (it is the higher
  tier rate), requires Tarot Merchant.

**Column 3 — Planet Merchant / Planet Tycoon**
- *Planet Merchant* (`v_planet_merchant`): Planet cards appear **2× more frequently**.
  `extra_disp=2`, `extra=9.6/4`.
- *Planet Tycoon* (`v_planet_tycoon`): Planet cards appear **4× more frequently**.
  `extra_disp=4`, `extra=32/4`. Requires Planet Merchant.

**Column 4 — Clearance Sale / Liquidation**
- *Clearance Sale* (`v_clearance_sale`): All cards and packs in shop are **25% off**.
  Sets `discount_percent = 25`. `extra=25`.
- *Liquidation* (`v_liquidation`): All cards and packs in shop are **50% off**.
  `discount_percent = 50`. `extra=50`. Requires Clearance Sale.

**Column 5 — Hone / Glow Up**
- *Hone* (`v_hone`): Foil, Holographic, and Polychrome cards appear **2× more often**.
  Multiplies `edition_rate` (base 1) → effective ×2. `extra=2`.
- *Glow Up* (`v_glow_up`): Those editions appear **4× more often**. `extra=4`
  (effective ×4 total). Requires Hone.

**Column 6 — Reroll Surplus / Reroll Glut**
- *Reroll Surplus* (`v_reroll_surplus`): Rerolls cost **$2 less** (`base_reroll_cost`
  reduced by 2). `extra=2`.
- *Reroll Glut* (`v_reroll_glut`): Rerolls cost **$2 less** again (additional −2, −4
  total). `extra=2`. Requires Reroll Surplus.

**Column 7 — Crystal Ball / Omen Globe**
- *Crystal Ball* (`v_crystal_ball`): **+1 consumable slot** (`consumeable_buffer`/
  consumable slot count +1, base 2 → 3). `extra` field used as display; effect is +1
  slot. `extra=3` (the resulting slot total display).
- *Omen Globe* (`v_omen_globe`): **Spectral cards may appear** in Arcana Packs.
  `extra=4`. Requires Crystal Ball.

**Column 8 — Telescope / Observatory**
- *Telescope* (`v_telescope`): The first Celestial Pack of each round always contains
  the **Planet card for your most-played poker hand**. `extra=3`.
- *Observatory* (`v_observatory`): Planet cards in your **consumable slots** give
  **×1.5 Mult** for their hand type when that hand is scored. `extra=1.5`. Requires
  Telescope.

**Column 9 — Grabber / Nacho Tong**
- *Grabber* (`v_grabber`): **+1 hand** per round (permanent). `extra=1`.
- *Nacho Tong* (`v_nacho_tong`): **+1 hand** per round (additional, +2 total).
  `extra=1`. Requires Grabber.

**Column 10 — Wasteful / Recyclomancy**
- *Wasteful* (`v_wasteful`): **+1 discard** per round (permanent). `extra=1`.
- *Recyclomancy* (`v_recyclomancy`): **+1 discard** per round (additional, +2 total).
  `extra=1`. Requires Wasteful.

**Column 11 — Seed Money / Money Tree**
- *Seed Money* (`v_seed_money`): Raises the **interest cap to $10** (5 interest payouts
  at $1 each → `interest_cap` 25 → 50). `extra=50`.
- *Money Tree* (`v_money_tree`): Raises the **interest cap to $20**. `extra=100`
  (`interest_cap` → 100). Requires Seed Money.

**Column 12 — Blank / Antimatter**
- *Blank* (`v_blank`): Does **nothing** (intentional). `extra=5` is unused by effect;
  it exists only as the unlock-progress counter (`blank_redeems`). Strategically used
  to advance the voucher queue / unlock Antimatter.
- *Antimatter* (`v_antimatter`): **+1 Joker slot** (`joker_buffer`/`max_jokers` +1,
  base 5 → 6). `extra=15` is unlock display; effect is +1 slot. Requires Blank.

**Column 13 — Magic Trick / Illusion**
- *Magic Trick* (`v_magic_trick`): **Playing cards can be purchased** from the shop.
  Enables the playing-card shop queue. `extra=4`.
- *Illusion* (`v_illusion`): Playing cards in the shop **may have Enhancements,
  Editions, and/or Seals**. `extra=4`. Requires Magic Trick.

**Column 14 — Hieroglyph / Petroglyph** ⛔ (banned)
- *Hieroglyph* (`v_hieroglyph`): **−1 Ante**, but **−1 hand** per round.
  `extra=1` (the −1 ante / −1 hand magnitude).
- *Petroglyph* (`v_petroglyph`): **−1 Ante**, but **−1 discard** per round.
  `extra=1`. Requires Hieroglyph.

**Column 15 — Director's Cut / Retcon** ⛔ (banned)
- *Director's Cut* (`v_directors_cut`): Reroll the **Boss Blind once per ante** for
  **$10**. `extra=10` (the $10 reroll cost).
- *Retcon* (`v_retcon`): Reroll the **Boss Blind unlimited times** for **$10** each.
  `extra=10`. Requires Director's Cut.

**Column 16 — Paint Brush / Palette**
- *Paint Brush* (`v_paint_brush`): **+1 hand size** (cards held in hand, base 8 → 9).
  `extra=1`.
- *Palette* (`v_palette`): **+1 hand size** (additional, +2 total / base 8 → 10).
  `extra=1`. Requires Paint Brush.

---

## 3. BMP 0.4.0 ranked bans (authoritative)

The 0.4.0 ban set lives in the **gamemode** definitions, not the rulesets/layers.
Both the ranked gamemode (`gamemode_mp_attrition`, forced by `rulesets/ranked.lua`)
and `showdown` ban the **same four** vouchers:

```
banned_vouchers = { "v_hieroglyph", "v_petroglyph", "v_directors_cut", "v_retcon" }
```
(`gamemodes/attrition.lua` lines 20–25; `gamemodes/showdown.lua` lines 16–21.)

`survival` (`gamemodes/survival.lua` line 21) has `banned_vouchers = {}` — **no
voucher bans**. Banned keys are unioned into `ApplyBans` and, in the deterministic
queue / Small World, replaced via `get_next_voucher_key()` rather than offered.

### Ranked ban ranking (most → least impactful)

1. **Hieroglyph / Petroglyph (Column 14)** — Hardest bans. The "−1 Ante" effect
   directly collapses the attrition race: skipping an ante in a PvP-blind-paced game
   is enormously swingy, and the hand/discard penalty barely matters when you are
   racing to a fixed nemesis. Banned in attrition + showdown.
2. **Director's Cut / Retcon (Column 15)** — Boss-blind rerolling. Against the MP
   nemesis blind (`bl_mp_nemesis`), the ability to reroll the boss (Retcon =
   unlimited at $10) trivializes adverse PvP blind effects and breaks the intended
   blind interaction. Banned in attrition + showdown.

No other vouchers are banned in 0.4.0. (Per `xlsx_out2/10_Vouchers.txt`, these were
already the four banned in the 0.3.3 ranked design "due to how they interact with the
PvP blind and gameplay in general" — **0.4.0 keeps the same four bans**.)

---

## 4. 0.4.0 vs 0.3.3 deltas

- **Ban list:** unchanged. Same four vouchers
  (Hieroglyph, Petroglyph, Director's Cut, Retcon). 0.3.3 doc
  (`10_Vouchers.txt`) and 0.4.0 `attrition.lua`/`showdown.lua` agree exactly.
- **Voucher effects/costs/config:** unchanged from vanilla (0.3.3 doc explicitly says
  "No vouchers are changed from Vanilla itself"; 0.4.0 confirms by not overriding any
  voucher center).
- **Architecture change (new in 0.4.0):** bans moved into the structured
  **gamemode** objects (`banned_vouchers` arrays in `gamemodes/*.lua`) and the
  layer/ruleset system (`layers/_layers.lua` lists `banned_vouchers` and
  `reworked_vouchers` as recognized keys; `reworked_vouchers = {}` exists but is
  empty for all shipped gamemodes). 0.3.3 expressed this only as a flat spreadsheet
  list.
- **The Order (deterministic shop) is now a first-class toggle** (`core.lua`
  `MP.should_use_the_order`, default `the_order = true`, forced on in ranked). The
  1–16 voucher queue described in the 0.3.3 Shop Queue doc is the behavior The Order
  implements. `compatibility/TheOrder.lua` and `lovely/TheOrder.toml` are the 0.4.0
  implementation surface.
- **Small World layer** (`layers/smallworld.lua`) is a 0.4.0 mode that reshapes
  voucher availability (deck "fake back vouchers", banned-key substitution). Not part
  of standard ranked, but it touches the voucher pool and is worth modeling.

---

## Open questions

1. **Exact internal modifier field per voucher.** The `Card:apply_to_run` voucher
   switch (mapping each key to its `G.GAME.modifiers.*` mutation) was **not found in
   the provided `game.lua` dumps** (only the centers table + `starting_params` are
   present; `apply_to_run` is referenced but its voucher-effect body is elsewhere).
   The effect *descriptions* and magnitudes here are grounded in the `config` numbers
   on disk + standard Balatro behavior. The precise field names (e.g. does Overstock
   bump a `shop_size` modifier vs. a `extra_jokers` count?) are **unverified against
   disk** and should be confirmed against `functions/common_events.lua` or the
   SMODS voucher definitions before the engine hard-codes them.
2. **Display-vs-effect ambiguity on `extra`/`extra_disp`.** For Crystal Ball
   (`extra=3`), Antimatter (`extra=15`), Blank (`extra=5`), the `extra` field is an
   unlock/display counter, not the live effect magnitude (slots are +1). Confirm the
   engine reads the +slot effect, not the raw `extra`.
3. **Observatory's ×1.5 stacking.** Whether multiple copies of the same Planet in
   consumable slots multiply (×1.5 each) needs verification for scoring engine parity.
4. **Survival mode intent.** `survival` bans no vouchers — confirm Hieroglyph/
   Director's Cut are *intended* to be legal there (not an oversight) before exposing
   survival in ranked tooling.
5. **Tarot/Planet Tycoon stacking semantics.** Confirm Tycoon *replaces* the Merchant
   rate (×4 absolute) rather than multiplying Merchant's ×2 to ×8. Base config
   (`extra=32/4=8` vs `9.6/4=2.4`) suggests absolute replacement, but the engine
   must match base game's `apply_to_run` ordering.

## New building blocks needed

1. **Voucher registry** (32 entries) keyed by `v_*` with: cost (10), `order`,
   `config.extra`/`extra_disp`, tier (1/2), and `requires` (Tier-1 key for Tier-2s).
2. **Voucher effect resolver** mapping each key to its run-state mutation
   (shop card slots, consumable slots, joker slots, hand size, hands, discards,
   interest cap, reroll cost, discount %, edition/tarot/planet rates, enable-flags
   for playing-card shop / spectral-in-arcana / boss reroll). Must encode whether a
   Tier-2 is *additive* (Grabber/Wasteful/Reroll/Overstock/Paint Brush) or
   *replacement* (Tarot/Planet rate, Clearance/Liquidation discount, Seed/Money cap).
3. **Tier-gating check:** a Tier-2 voucher is only redeemable if `used_vouchers`
   contains its `requires` Tier-1 key. Mirror in shop-eligibility logic.
4. **Per-gamemode ban set:** a `bannedVouchers` set on the gamemode/ruleset config,
   pre-loaded with `{v_hieroglyph, v_petroglyph, v_directors_cut, v_retcon}` for
   attrition + showdown, empty for survival. Engine must union ruleset/layer ban
   arrays.
5. **Deterministic voucher queue (The Order):** a game-long 1–16 column queue with
   the spawn rule (Tier-1 → Tier-2 if owned → skip if both owned), banned-key
   substitution (`get_next_voucher_key()`), and Voucher-Tag advancement with the
   "collapse duplicate back-to-back" rule. Seed prefix `"*"` to mark deterministic
   mode.
6. **Voucher-Tag interaction** hook that advances the voucher queue identically to a
   natural shop progression.
