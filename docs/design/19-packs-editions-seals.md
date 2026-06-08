# 19 — Booster Packs, Editions, Enhancements & Seals (BMP 0.4.0)

Definitive catalogue of booster packs, card editions, card enhancements, and seals as they exist in **Balatro Multiplayer 0.4.0** (the version on disk). Every numeric claim below is grounded in a real source file; citations are inline. Where 0.4.0 diverges from the vanilla base game or from the 0.3.3 ranked-changes spreadsheet, the divergence is flagged.

**Primary sources used:**
- Base-game center/data dump: `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` (identical copy at `.../lovely/game-dump/game.lua`). This is the data table (`P_CENTERS`, `P_SEALS`, etc.), not the scoring logic.
- BMP 0.4.0 mod root: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/`
  - `objects/boosters/standard_giga.lua` — the MP-exclusive Giga pack
  - `objects/enhancements/mp_glass.lua` — Glass rework
  - `objects/seals/mp_gold_seal.lua` — Gold Seal rework (**commented out** in 0.4.0)
  - `objects/editions/phantom.lua` — MP-exclusive Phantom edition
  - `rulesets/release.lua`, `layers/release.lua`, `layers/standard.lua`
  - `compatibility/Preview/EngineSimulate.lua` — MP's own scoring simulator (authoritative for exact runtime effect math)
  - `CHANGELOG.md`
- 0.3.3 baseline ranked-changes (parsed spreadsheet): `C:/Users/micha/AppData/Local/Temp/xlsx_out2/07_Packs.txt`, `02_Card_Modifiers.txt`

---

## 1. Booster Packs

All vanilla packs share `set = 'Booster'`, `atlas = 'Booster'`. `config.extra` = number of cards generated/shown; `config.choose` = number you may pick. Source for every row below: `dump/game.lua:674-705`.

| Internal key | Name | Kind | Cost | `extra` (cards) | `choose` (picks) | Shop weight |
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

**Pack size pattern (vanilla):**
- Arcana / Celestial / Standard: Normal `3 / pick 1`, Jumbo `5 / pick 1`, Mega `5 / pick 2`.
- Spectral: Normal `2 / pick 1`, Jumbo `4 / pick 1`, Mega `4 / pick 2`.
- Buffoon: Normal `2 / pick 1`, Jumbo `4 / pick 1`, Mega `4 / pick 2`.

### 1.1 MP-exclusive: Giga Standard Pack

Source: `multiplayer-0.4.0/objects/boosters/standard_giga.lua`.

| Field | Value | Source line |
|---|---|---|
| key | `standard_giga` (full center key `p_mp_standard_giga` once SMODS-prefixed) | L9 |
| kind | `Standard` | L10 |
| `config.extra` | **10** cards | L14 |
| `config.choose` | **4** picks | L14 |
| cost | **16** | L15 |
| weight | **0** (never appears randomly in the shop) | L16 |
| `unskippable` | **true** | L17 |

The pack is exclusive to the **Orange Deck** (`objects/decks/02_orange.lua`). Because it is `unskippable`, BMP patches `G.FUNCS.can_skip_booster` (L38-46): while a Giga pack is open the Skip button is disabled (greyed `BACKGROUND_INACTIVE`, no callback), so you must take all 4 picks. The changes spreadsheet (`07_Packs.txt` L2-3) confirms: "Contains 10 Standard Cards, of which you can Choose 4. This pack is Unskippable. This means you cannot take less than 4 cards."

**Giga card generation (`create_card`, L18-34):**
- Per card: edition rolled via `poll_edition("standard_edition"..append, 2, true)` and seal via `SMODS.poll_seal({ mod = 10, key = "stdseal"..append })`.
- `set = (pseudorandom(pseudoseed("stdset"..append)) > 0.6) and "Enhanced" or "Base"` → ~40% chance each card is Enhanced, ~60% plain. (This is the same `>0.6` enhancement gate vanilla Standard packs use.)
- `append = MP.ante_based() .. s_append` — the RNG seed is ante-based and shared, so both players' Giga packs are deterministic for a given ante.

### 1.2 MP pack-queue model (0.4.0 behaviour, not size data)

From `07_Packs.txt` (L4-14) and the shop-queue tab; this is *how* the contents are drawn, which differs from singleplayer:
- **Game-long shared pack queue.** Every time any player *sees* a pack the queue advances. If P1 skips and P2 opens, P1 still sees the same packs but offset by a blind.
- **Arcana / Celestial / Spectral** each draw from their own consumable pack-queue.
- **Buffoon** packs draw directly from the **Shop Queue**: Normal takes the next 2 Jokers, Jumbo & Mega take the next 4 Jokers.
- **Standard** packs draw from a game-long **Playing-Card queue**, separate from the Magic Trick playing-card queue. Order matters: opening Normal-then-Jumbo can split desirable cards differently than Jumbo-then-Normal.

> **0.4.0 vs 0.3.3:** Pack sizes/picks are unchanged from the 0.3.3 spreadsheet. The Giga Standard pack (10/pick 4, cost 16, unskippable, Orange-only) is present in 0.4.0 exactly as the spreadsheet describes. No size deltas found in `CHANGELOG.md`.

---

## 2. Editions

Vanilla editions: `dump/game.lua:668-671`. Tag odds: `dump/game.lua:236-239`. Runtime application in MP's simulator: `compatibility/Preview/EngineSimulate.lua:489-494`.

| Internal key | Name | `config.extra` | Exact effect | Trigger / where applied |
|---|---|---|---|---|
| `e_foil` | Foil | 50 | **+50 Chips** | Added when the carrier scores (cards: on play; jokers: flat chip add). `EngineSimulate.lua:491` (`edition.chips`). |
| `e_holo` | Holographic | 10 | **+10 Mult** | Added on score. `EngineSimulate.lua:492` (`edition.mult`). |
| `e_polychrome` | Polychrome | 1.5 | **×1.5 Mult** | Multiplicative on score. `EngineSimulate.lua:493` (`edition.x_mult`). |
| `e_negative` | Negative | 1 | On a **Joker:** +1 joker slot (the joker takes no slot). On a **consumable:** +1 consumable slot. No scoring effect. | Slot accounting; not in the scoring loop. `config.extra = 1` at `dump/game.lua:671`. |

**Edition tags** (grant a free edition on a shop joker; `dump/game.lua:236-239`):
- `tag_negative` → `edition = 'negative'`, `min_ante = 2`, `odds = 5`
- `tag_foil` → `edition = 'foil'`, `odds = 2`
- `tag_holo` → `edition = 'holo'`, `odds = 3`
- `tag_polychrome` → `edition = 'polychrome'`, `odds = 4`

### 2.1 MP-exclusive edition: Phantom

Source: `multiplayer-0.4.0/objects/editions/phantom.lua`.
- key `phantom` (full `e_mp_phantom`), `in_shop = false` (cannot be rolled/bought), uses the `voucher` shader.
- `on_apply` (L1-4): sets `card.ability.eternal = true` and `card.ability.mp_sticker_nemesis = true`. So a Phantom card is **Eternal** (cannot be sold/destroyed) and flagged as a nemesis sticker card.
- `extra_cost = 0` plus a hack: BMP sets global min sell value to `-1` so a Phantom card shows **no sell value** (L23 comment).
- No chips/mult/x_mult — purely a structural/ownership marker used by MP's "Nemesis"/shared-joker mechanics (`SMODS.get_card_areas` override at L35-43 inserts `MP.shared` into the joker areas).

> **0.4.0 vs 0.3.3:** The four vanilla editions are numerically unchanged (Foil +50 chips, Holo +10 mult, Poly ×1.5, Negative +1 slot). Phantom is an MP construct tied to shared/nemesis jokers; treat its exact gameplay surface as **partially unverified** (only the on-apply effects are in source).

---

## 3. Enhancements

Vanilla enhancement centers: `dump/game.lua:657-664`. All have `set = "Enhanced"`, `max = 500`. Runtime scoring math is confirmed in `compatibility/Preview/EngineSimulate.lua:441-499`.

| Internal key | Name | Config | Exact effect | Trigger |
|---|---|---|---|---|
| `m_bonus` | Bonus Card | `bonus = 30` | **+30 Chips** (added to base chips of the card) | When the card scores in `G.play`. `EngineSimulate.lua:450`. |
| `m_mult` | Mult Card | `mult = 4` | **+4 Mult** | On score. `EngineSimulate.lua:463`. |
| `m_wild` | Wild Card | `{}` | Card **counts as every suit** for hand/flush/suit checks. No direct chip/mult. | Suit evaluation (hand detection & suit-gated jokers). |
| `m_glass` | Glass Card | vanilla `Xmult = 2, extra = 4` | **×Mult on score; 1/`extra` chance to shatter (destroy)** after scoring. Vanilla: ×2, 1-in-4 break. **BMP 0.4.0 reworks this — see §3.1.** | On score (x_mult), then break roll. `EngineSimulate.lua:467` applies `x_mult`. |
| `m_steel` | Steel Card | `h_x_mult = 1.5` | **×1.5 Mult while held in hand** (not when played) | Held-in-hand context. `EngineSimulate.lua:498` (`h_x_mult`). |
| `m_stone` | Stone Card | `bonus = 50` | **+50 Chips**, has **no rank/suit** (always scores regardless of poker hand) | On score; chips substitute base chips. `EngineSimulate.lua:447-448`. |
| `m_gold` | Gold Card | `h_dollars = 3` (vanilla) | **+$3 at end of round if held in hand.** See §3.2 for the 0.4.0 $4 question. | End-of-round, held-in-hand. `h_dollars` applied at `ui/game/game_state.lua:145-147`. |
| `m_lucky` | Lucky Card | `mult = 20, p_dollars = 20` | **1 in 5 chance: +20 Mult.** **1 in 15 chance: +$20.** | On score. `EngineSimulate.lua:454-457` (mod 5 → +20 mult) and `472-479` (mod 15 → +$20). |

The probability denominators are hard-coded in the MP simulator: `get_probabilistic_extremes(..., 5, card.ability.mult, 0)` for the mult roll and `..., 15, card.ability.p_dollars, 0` for the dollar roll (`EngineSimulate.lua:456, 475`). The 0.3.3 doc (`02_Card_Modifiers.txt` L8-9) confirms Lucky uses two game-long queues (1/5 mult, 1/15 $20) — vanilla behaviour, unchanged.

### 3.1 Glass rework (BMP 0.4.0)

Source: `multiplayer-0.4.0/objects/enhancements/mp_glass.lua`.
```
MP.ReworkCenter("m_glass", { layers = { "standard", "classic" }, config = { Xmult = 1.5, extra = 4 } })
MP.ReworkCenter("m_glass", { layers = "sandbox",               config = { Xmult = 1.5, extra = 3 } })
```
- **Standard/Classic rulesets: Glass = ×1.5 Mult** (down from vanilla ×2), break chance unchanged at 1-in-`extra=4`.
- **Sandbox ruleset: ×1.5 Mult, 1-in-3 break** (`extra = 3`).
- Glass breaks operate on a **game-long shared break queue** read left-to-right, independent of hand ordering (`02_Card_Modifiers.txt` L3-6). Despite Justice being banned historically, Glass remained obtainable via Standard packs and the Familiar/Grim/Incantation Spectral cards (`02_Card_Modifiers.txt` L3).

> **0.4.0 vs 0.3.3:** The ×1.5 Glass nerf described in the 0.3.3 spreadsheet is **still in force in 0.4.0** and is now expressed as a `ReworkCenter` keyed to the `standard`/`classic`/`sandbox` layers. The `standard` layer (`layers/standard.lua:25-27`) still lists `m_glass` in `reworked_enhancements`.

### 3.2 Gold Card payout — 0.4.0 discrepancy (FLAGGED)

- `CHANGELOG.md:20` ("Economy & Enhancements"): **"Gold Card (Enhancement) — Payout increased from $3 to $4."**
- Vanilla data still shows `m_gold = { config = { h_dollars = 3 } }` (`dump/game.lua:663`).
- **No `ReworkCenter("m_gold", ...)` exists anywhere in the 0.4.0 source on disk.** Grep of `objects/`, `layers/`, `rulesets/` finds only references to `m_gold` for joker gating (Golden Ticket, Midas Mask, Alloy), never a `h_dollars`/`config` override.
- MP's own scoring simulator still hard-codes **Gold Seal = +$3** (`EngineSimulate.lua:470`) but that is the *seal*, not the enhancement.

**Conclusion / flag:** Per the rule "0.4.0 source wins," the on-disk source does **not** implement the Gold Card $4 bump — there is no override and the base value is `h_dollars = 3`. The $4 change is documented in the CHANGELOG but appears unimplemented in the files provided (or implemented somewhere not in these sources). **Treat Gold Card payout as $3 in code / $4 per CHANGELOG — unresolved; verify against runtime.**

> Note: `j_ticket` (Golden Ticket) **is** in source (`objects/jokers/standard/ticket.lua`), and `CHANGELOG.md:10` reverts its payout to $4 (the per-Gold-card joker payout, distinct from the enhancement's end-of-round payout).

### 3.3 MP enhancement bans

- `layers/ban_mutators.lua:12-13` lists `m_gold` and `m_lucky` as bannable mutators (used by mutator-based rulesets, not a blanket ban).
- The `standard` layer does **not** ban any enhancement outright; it only reworks `m_glass`.

---

## 4. Seals

Vanilla seal centers are bare markers (`dump/game.lua:226-230`); all four have `set = "Seal"` and **no config** — their effects are hard-coded in scoring/round logic, captured by MP's simulator.

| Internal key | Name | Exact effect | Trigger | Source |
|---|---|---|---|---|
| `Red` | Red Seal | **Retrigger the card once** (+1 repetition) when it scores | On score, repetition phase | `EngineSimulate.lua:430` (`if card.seal == "Red" then add_reps(1)`) |
| `Blue` | Blue Seal | Creates the **Planet card** for the final played poker hand when the card is held in hand at end of round (if you have a free consumable slot) | End of round, held in hand | Vanilla behaviour; not numerically parameterised in dump. **Effect text unverified in source** (logic lives in round-end code not in the provided dump). |
| `Gold` | Gold Seal | **+$3 when the card scores** | On score (per trigger, so it stacks with Red-seal retriggers) | `EngineSimulate.lua:470` (`if card.seal == "Gold" then add_dollars(3)`) |
| `Purple` | Purple Seal | Creates a **Tarot card** when the card is **discarded** (if free consumable slot) | On discard | `02_Card_Modifiers.txt` L11: "Purple Seals take from the Up Top Tarot queue." Numeric is "1 Tarot." **Logic not in provided dump.** |

P_SEALS order (`dump/game.lua:226-230`): Red=1, Blue=2, Gold=3, Purple=4.

### 4.1 Gold Seal payout — 0.4.0 status (FLAGGED)

Source: `multiplayer-0.4.0/objects/seals/mp_gold_seal.lua` — the entire file is **commented out**:
```
-- Gold Seal $3 -> $4 rework commented out for 0.4.0; revisit later.
-- MP.ReworkCenter("Gold", { center_table = "P_SEALS", layers = "standard",
--   config = { extra = { p_dollars = 4 } }, get_p_dollars = ... })
```
So in **0.4.0 the Gold Seal still pays $3**, confirmed by both the disabled rework and the live simulator value (`EngineSimulate.lua:470`). The intended $4 bump was deferred.

> **0.4.0 vs 0.3.3:** Gold Seal is unchanged at +$3 (the $4 rework is explicitly disabled for 0.4.0). Red Seal retrigger interacts with MP's game-long queues (e.g. a Red-seal Glass card triggers the break queue twice — see `02_Card_Modifiers.txt` L4-6). Purple Seal draws from the shared "Up Top Tarot" queue rather than rolling independently (MP queue model).

---

## Open questions

1. **Gold Card enhancement $4 (CHANGELOG L20) vs source:** No `ReworkCenter("m_gold")` exists on disk and base value is `h_dollars = 3`. Is the $4 applied at runtime by some path not in these files (e.g. a generic balance table, or applied in compiled/non-dumped code), or is the CHANGELOG ahead of the shipped code? Needs runtime verification.
2. **Blue Seal & Purple Seal exact wording/conditions** are not present in `dump/game.lua` (that file is the center/data table, not round-end logic). Confirm "needs free consumable slot," and confirm Blue creates the Planet for the *final* scored hand. Logic likely lives in a non-provided `functions/`/`back.lua` dump.
3. **`poll_edition("standard_edition", 2, ...)` odds** in the Giga pack (and vanilla Standard packs): the `2` is a poll argument; need the `poll_edition` implementation to state exact Foil/Holo/Poly probabilities for Standard-pack cards. Not in the provided dump.
4. **`SMODS.poll_seal({ mod = 10 })`** — confirm the seal probability table (base ~10% with `mod=10`?) and per-seal split. SMODS source under `smods/src/` was not yet read.
5. **Phantom edition** gameplay surface beyond `eternal` + `mp_sticker_nemesis` (how it interacts with shared/nemesis jokers, sell value −1) — partially unverified.
6. **Mega Spectral weight 0.07 / Mega Buffoon 0.15** — confirm these vanilla shop weights aren't overridden by an MP shop-queue ruleset (the MP pack queue may bypass weights entirely; weights may only matter for the rare random-pack path).

## New building blocks needed

1. **Pack model** (`Pack` enum/record): `kind` (Arcana/Celestial/Spectral/Standard/Buffoon), `size` (Normal/Jumbo/Mega/Giga), `cardCount` (`extra`), `picks` (`choose`), `cost`, `unskippable` flag. Must support the Giga special-case (10/pick4/cost16/Orange-only/unskippable).
2. **Shared pack-queue subsystem**: game-long, advances on *view* (not just open); separate sub-queues per consumable type, a Standard playing-card queue, and a Buffoon→Shop-Queue passthrough (Normal=next 2 jokers, Jumbo/Mega=next 4). Needs to be deterministic from ante-based seed (`MP.ante_based()`).
3. **Edition model**: Foil(+50 chip), Holo(+10 mult), Poly(×1.5 mult), Negative(+1 slot), plus MP **Phantom**(eternal+nemesis, no sell value, not in shop).
4. **Enhancement model** with per-ruleset overrides: base values (Bonus +30 chip, Mult +4, Stone +50, Steel ×1.5 held, Gold $3/$4-TBD held, Lucky 1/5→+20 mult & 1/15→+$20, Wild=all-suits) and a **Glass layer override** (×1.5 / 1-in-4 standard, 1-in-3 sandbox).
5. **Glass break queue**: game-long, left-to-right, deterministic and independent of hand ordering; must support retrigger interactions (Red-seal Glass consumes two queue entries).
6. **Lucky queues**: two independent game-long 1/5 (mult) and 1/15 ($) queues, vanilla-style.
7. **Seal model**: Red(+1 rep, stacks with retriggers), Gold(+$3 on each trigger), Blue(Planet at round end if slot free), Purple(Tarot on discard, from shared "Up Top" Tarot queue). Gold must be parameterised so the deferred $4 bump can be toggled per ruleset.
8. **`unskippable` booster handling**: disable the skip action while such a pack is open (mirror `can_skip_booster` patch).
9. **Ruleset/layer mechanism**: an override system mirroring `MP.ReworkCenter(key, { layers = ... })` so enhancement/seal/edition values can differ per ruleset (standard vs sandbox vs classic vs release).
