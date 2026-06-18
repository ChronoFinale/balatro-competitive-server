# 16 — Spectral Cards (Balatro Multiplayer 0.4.0)

Definitive catalogue of **every** Spectral card: internal key, cost, exact effect with real numbers, RNG seeds, eligibility (`can_use`) rules, and the BMP 0.4.0 deltas vs. vanilla / vs. the 0.3.3 changes doc. Spectral cards are `consumeable` centers with `set = "Spectral"`. There are **18** of them, two of which (`The Soul`, `Black Hole`) are `hidden` "soul" cards that never appear in the normal Spectral pool.

## Sources

- Center definitions (key, cost, `config`, `pos`, `order`, `hidden`): `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua:580-597` (`s_undiscovered` Spectral placeholder at `:369`; pool sort at `:850`).
- **Use / effect logic** (the real per-card behaviour): `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/card.lua:1432-1850` (`Card:use_consumeable`), eligibility `can_use` at `:1811-1850`, eligible-joker pools rebuilt at `:4628-4643`.
- Soul/Black Hole forced-spawn roll + soul pool math: `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/functions/common_events.lua:2391-2432`; `poll_edition` (used by Aura) `:2364-2389`.
- BMP 0.4.0 reworks/overrides:
  - Standard ruleset bans/reworks: `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/layers/standard.lua` (bans base `c_ouija`, swaps in `c_mp_ouija_standard`).
  - Reworked Ouija: `.../objects/consumables/ouija.lua`.
  - Sandbox Ectoplasm variant: `.../objects/consumables/sandbox/ectoplasm.lua`.
  - "The Order" deterministic RNG patch (affects every Spectral that shuffles / picks elements, plus Wraith rarity): `.../compatibility/TheOrder.lua`.
  - Changelog: `.../CHANGELOG.md` (only Spectral line in 0.4.0: "**Ouija** and **Ectoplasm** — Now cost $4 (bug fix)").
- 0.3.3 changes doc (baseline, flagged where 0.4 differs): `C:/Users/micha/AppData/Local/Temp/xlsx_out2/03_Consumables.txt`.
- Existing engine building blocks: `D:/NewServer/src/main/java/com/balatro/engine/card/{Edition,Seal}.java`.

## Common mechanics

- **Cost:** every Spectral is `cost = 4` (including the two hidden ones). The 0.4.0 changelog explicitly fixes Ouija + Ectoplasm to `$4` (they had drifted off). Vanilla cost is `4`.
- **`max_highlighted = 1`** on seal-givers + Cryptid means they require exactly one selected hand card.
- **`remove_card = true`** (Familiar/Grim/Incantation/Immolate) routes them through the shared destroy block at `card.lua:1546-1647`; destroyed cards are then fed to `SMODS.calculate_context({ remove_playing_cards = true, removed = destroyed_cards })`.
- **Shatter vs dissolve:** when destroying a playing card, `SMODS.shatters(card)` (Glass cards) → `card:shatter()`, otherwise `card:start_dissolve()`. Same animation branch for joker destruction (Hex/Ankh) via `getting_sliced`.
- **Seals** map directly to existing `Seal.java` enum (RED/BLUE/GOLD/PURPLE). **Editions** map to `Edition.java` (FOIL/HOLOGRAPHIC/POLYCHROME/NEGATIVE).

---

## A. Seal-givers (add a seal to 1 selected card)

These four share one code path (`card.lua:1457-1470`): `conv_card = G.hand.highlighted[1]`, then `conv_card:set_seal(self.ability.extra, nil, true)`. `config.extra` is the seal name string. `max_highlighted = 1`. `can_use` (the generic seal/conversion fallback) requires exactly one highlighted card.

| Name | Key | `config` | Effect | Maps to |
|---|---|---|---|---|
| Talisman | `c_talisman` | `extra='Gold', max_highlighted=1` | Adds a **Gold seal** to 1 selected card | `Seal.GOLD` |
| Deja Vu | `c_deja_vu` | `extra='Red', max_highlighted=1` | Adds a **Red seal** to 1 selected card | `Seal.RED` |
| Trance | `c_trance` | `extra='Blue', max_highlighted=1` | Adds a **Blue seal** to 1 selected card | `Seal.BLUE` |
| Medium | `c_medium` | `extra='Purple', max_highlighted=1` | Adds a **Purple seal** to 1 selected card | `Seal.PURPLE` |

(`order`: Talisman 4, Deja Vu 12, Trance 14, Medium 15.) No 0.4.0 changes.

---

## B. Edition-givers / joker-edition effects

### Aura — `c_aura` (order 5)
`config = {}`. `card.lua:1471-1479`. Adds a random edition to **1 selected card in hand** (a playing card, not a joker): `edition = poll_edition('aura', nil, true, true)` then `aura_card:set_edition(edition, true)`.

`poll_edition(_key='aura', _mod=nil, _no_neg=true, _guaranteed=true)` (`common_events.lua:2364-2389`). With `_guaranteed=true` and `_no_neg=true`, the thresholds are:
- polychrome if roll `> 1 - 0.006*25 = 0.85` → **15%**
- holographic if roll `> 1 - 0.02*25 = 0.50` → **35%**
- foil otherwise → **50%**
- (negative branch is skipped because `_no_neg=true`).

So Aura gives **Foil 50% / Holo 35% / Poly 15%**, no Negative. **`can_use`** (`card.lua:1831`): exactly one highlighted card AND that card has **no existing edition**.

> 0.3.3 changes doc note: "Aura works on a game-long queue. This is how Aura works in Vanilla." This is the deterministic RNG queue, not an effect change. Under "The Order" the `pseudoseed('aura')` draw is part of the shared deterministic stream.

### Ectoplasm — `c_ectoplasm` (order 9)
`config = {}`. Shares the Wheel-of-Fortune/Hex code path (`card.lua:1747-1780`). Picks a random **editionless joker** (`self.eligible_editionless_jokers`, rebuilt at `:4636-4642`: jokers with `ability.set=='Joker'` and no edition) via `pseudoseed('ectoplasm')`, sets `edition = {negative = true}` → **adds Negative to a random joker**. **Downside:** reduces hand size by an **escalating** amount: `G.GAME.ecto_minus` starts at 1, `G.hand:change_size(-ecto_minus)`, then `ecto_minus = ecto_minus + 1`. So 1st Ectoplasm = −1 hand size, 2nd = −2, 3rd = −3, … cumulative. `can_use`: at least one editionless joker exists.

> **0.4.0 delta:** changelog confirms cost fixed to **$4** (bug fix). Effect numbers unchanged.

### Hex — `c_hex` (order 13)
`config = {extra = 2}` (the `extra` is vestigial here; it's used as a Wheel resample count but Hex always fires). `card.lua:1747-1780`. Picks a random **editionless joker** via `pseudoseed('hex')`, sets `edition = {polychrome = true}` → **adds Polychrome to a random joker**, **then destroys every OTHER joker** that is not eternal (`for k,v in pairs(G.jokers.cards) do if v ~= eligible_card and not SMODS.is_eternal(v) then v.getting_sliced=true; v:start_dissolve() end`). `can_use`: at least one editionless joker. Note: the **Ghost Deck** (`b_ghost`) starts with one `c_hex` in its consumable slot (`game.lua:644`).

---

## C. Joker creation / duplication / destruction

### Wraith — `c_wraith` (order 6)
`config = {}`. `card.lua:1734-1746`. Creates a **random Rare Joker** for free, then sets your money to $0 (`ease_dollars(-G.GAME.dollars)`).
- Rarity forced via `create_card('Joker', G.jokers, nil, <rarity>, nil, nil, nil, 'wra')` where the 4th arg `_rarity = MP.should_use_the_order() and 1 or 0.99`.
  - Vanilla/non-Order: `0.99` → resolves to Rare in `create_card`.
  - **The Order ruleset:** passes `_rarity = 1` (explicit Rare index) so the deterministic joker queue is used — `TheOrder.lua` patches `create_card` to ignore ante and use a single pool per type/rarity.
- `can_use` (`card.lua:1844`): joker slot free (`#G.jokers.cards < card_limit`) or used from the joker area.

> **0.3.3 changes doc:** "Wraith shares a game-long queue with Rare Skips. This queue does NOT take from the shop, but a completely separate queue. The only way to advance this queue is by taking Rare skips or Wraiths." In 0.4.0 this is implemented through the deterministic `create_card` patch + the `'wra'` key-append rather than a hand-rolled queue, but the design intent (separate Rare queue) is preserved. Effect (money → 0, free Rare) unchanged.

### Ankh — `c_ankh` (order 11)
`config = {extra = 2}`. `card.lua:1701-1733`. **Creates a copy of one of your jokers, then destroys all other (non-eternal) jokers.** Details:
- `copyable_jokers` = all jokers EXCEPT those whose edition type is `"mp_phantom"` (BMP phantom-edition jokers cannot be copied).
- `chosen_joker = pseudorandom_element(copyable_jokers, pseudoseed('ankh_choice'))`.
- All `deletable_jokers` (non-eternal) other than the chosen one are dissolved.
- The copy via `copy_card(chosen_joker, ...)`: if the original was **Negative**, the copy is created with the negative edition flag but then **stripped to non-negative** (`card:set_edition(nil, true)`) — i.e. the duplicate loses Negative. Eternal jokers are kept but not copied unless chosen.
- `can_use` (`card.lua:1819-1830`): there is at least one copyable joker with `ability.set=='Joker'` AND `G.jokers.config.card_limit > 1`.

`extra = 2` is the displayed "copies"/strength var in the vanilla loc string; mechanically Ankh makes exactly **one** copy. No 0.4.0 effect change. The `mp_phantom` exclusion is a BMP addition.

### The Soul — `c_soul` (order 17, **hidden**)
`config = {}`, `effect = "Unlocker"`, `hidden = true`. `card.lua:1690-1700` (shared with Judgement). Creates a **random Legendary joker**: `create_card('Joker', G.jokers, true, …, 'sou')` (3rd arg `legendary=true`), `add_to_deck`, `emplace`, then `check_for_unlock{type='spawn_legendary'}`. `can_use`: joker slot free.
- **Spawn mechanic** (`common_events.lua:2418-2423`): inside `create_card`, when `_type` is `Tarot`/`Spectral`/`Tarot_Planet` and Soul hasn't been used (unless Showman), roll `pseudorandom('soul_'..(MP.should_use_the_order() and 'c_soul' or _type)..ante) > 0.997` → **0.3%** chance to force `forced_key='c_soul'`. Under The Order the seed is keyed on `'c_soul'` instead of the pack type for determinism.

### Black Hole — `c_black_hole` (order 18, **hidden**)
See also `docs/design/15-planets.md`. `config = {}`, `hidden = true`. `card.lua:1432-1456`: **levels up EVERY poker hand by +1** (`for k,v in pairs(G.GAME.hands) do level_up_hand(self, k, true) end`). `can_use`: always `true` (`:1813`).
- **Spawn mechanic** (`common_events.lua:2424-2431`): when `_type` is `Planet` or `Spectral` and Black Hole not yet used (unless Showman), roll `pseudorandom('soul_'..(...'c_black_hole' or _type)..ante) > 0.997` → **0.3%**. Note: under The Order, if a `forced_key` was already set (e.g. Soul won the roll), Black Hole will NOT overwrite it (`if not (MP.should_use_the_order() and forced_key)`), preventing double-soul desync.

---

## D. Playing-card creators (destroy 1 random, then make enhanced cards)

These three share the destroy-then-create path (`card.lua:1569-1616`). All have `remove_card = true`. They destroy **1 random card from hand** (`pseudorandom_element(G.hand.cards, pseudoseed('random_destroy'))`), then create `config.extra` new **enhanced** playing cards in hand. The enhancement of each created card is a random Enhanced center **excluding Stone** (`cen_pool` built from `G.P_CENTER_POOLS["Enhanced"]` minus `m_stone`, picked with `pseudoseed('spe_card')`).

| Name | Key | `config` | Creates | Rank pool (seed) | Suit pool (seed) |
|---|---|---|---|---|---|
| Familiar | `c_familiar` | `remove_card=true, extra=3` | **3** random enhanced **face** cards | `{J,Q,K}` (`familiar_create`) | `{S,H,D,C}` (`familiar_create`) |
| Grim | `c_grim` | `remove_card=true, extra=2` | **2** random enhanced **Aces** | `'A'` (fixed) | `{S,H,D,C}` (`grim_create`) |
| Incantation | `c_incantation` | `remove_card=true, extra=4` | **4** random enhanced **numbered** cards | `{2,3,4,5,6,7,8,9,T}` (`incantation_create`) | `{S,H,D,C}` (`incantation_create`) |

`can_use` for these falls through to the generic "needs a hand" check (hand exists; `remove_card` cards require cards in hand). Created cards run `playing_card_joker_effects`. No 0.4.0 effect change.

### Immolate — `c_immolate` (order 10)
`config = {remove_card=true, extra = {destroy = 5, dollars = 20}}`. `card.lua:1617-1644`. **Destroys 5 random cards in hand and gives $20.** Implementation: copies hand into `temp_hand`, sorts by `playing_card` id, `pseudoshuffle(temp_hand, pseudoseed('immolate'))`, takes first `extra.destroy = 5` as `destroyed_cards`, dissolves/shatters them, then `ease_dollars(extra.dollars = 20)`. **The Order delta:** `pseudoshuffle` is patched (`TheOrder.lua:398-423`) so the 5 destroyed cards are chosen deterministically (sorted by `mp_stdval` then a stable per-key seed) rather than truly random — same count, deterministic selection. Effect numbers (5 destroyed / +$20) unchanged.

---

## E. Whole-hand transformers

### Sigil — `c_sigil` (order 7)
`config = {}`. `card.lua:1501-1524, 1537-1542`. **Converts ALL cards in hand to a single random suit** (ranks unchanged). Picks `_suit = pseudorandom_element({'S','H','D','C'}, pseudoseed('sigil'))`, then for every hand card rebuilds its base to `<suit>_<rank>` (`card:set_base(G.P_CARDS[suit_prefix..rank_suffix])`). Flips cards down then up with sound for the animation. `can_use`: generic (needs a hand). No 0.4.0 change.

### Ouija — `c_ouija` (order 8) — **BMP REWORKED in MP**
**Vanilla behaviour** (`card.lua:1525-1536`): `config = {}`. Converts **ALL cards in hand to a single random rank** (`_rank = pseudorandom_element({'2'..'A'}, pseudoseed('ouija'))`, suits unchanged), **AND permanently reduces hand size by 1** (`G.hand:change_size(-1)`).

**BMP 0.4.0 standard ruleset:** base `c_ouija` is **banned** and replaced by **`c_mp_ouija_standard`** (`layers/standard.lua:10,23`; def in `objects/consumables/ouija.lua`):
- `set='Spectral'`, `cost=4`, `config = {extra = {destroy = 3}, mp_sticker_balanced = true}`, atlas `ouija_2`.
- **Effect:** destroys **3 random cards** in hand (sorted + `pseudoshuffle(temp_hand, pseudoseed('ouija_destroy'))`, first 3 flagged `ouija_queue_destroy`, then `SMODS.destroy_cards`), THEN converts the **surviving** cards to a single random rank: `_rank = pseudorandom_element(SMODS.Ranks, "ouija")`, `SMODS.change_base(_card, nil, _rank.key)` for each non-destroyed card. **Hand-size reduction is REMOVED.**
- `can_use`: `#G.hand.cards >= card.ability.extra.destroy` (≥ 3 cards in hand).
- Credits: art "aura!", code "steph".

> **0.4.0 vs 0.3.3:** the 0.3.3 changes doc already describes this exact rework ("destroy 3 random Cards and convert the rest to a single random Rank, hand-size reduction removed"). 0.4.0 keeps it and standardizes the key as `c_mp_ouija_standard` (vanilla `c_ouija` banned in the standard layer; still present in `experimental.lua` swap list). Changelog also notes the cost bug-fix to $4.

### Cryptid — `c_cryptid` (order 16)
`config = {extra = 2, max_highlighted = 1}`. `card.lua:1480-1500`. **Creates 2 exact copies of 1 selected card** (`copy_card(G.hand.highlighted[1], ...)`), each added to deck (`_card:add_to_deck()`), increments `G.deck.config.card_limit` by 1 per copy, emplaced into hand and materialized. Runs `playing_card_joker_effects(new_cards)`. `can_use`: generic single-highlight requirement (`max_highlighted = 1`). No 0.4.0 change.

---

## F. Full key/cost reference table

| # (order) | Name | Key | Cost | `hidden` | One-line effect |
|---|---|---|---|---|---|
| 1 | Familiar | `c_familiar` | 4 | – | Destroy 1 random card, create 3 enhanced face cards |
| 2 | Grim | `c_grim` | 4 | – | Destroy 1 random card, create 2 enhanced Aces |
| 3 | Incantation | `c_incantation` | 4 | – | Destroy 1 random card, create 4 enhanced numbered cards |
| 4 | Talisman | `c_talisman` | 4 | – | Add Gold seal to 1 selected card |
| 5 | Aura | `c_aura` | 4 | – | Add random edition (Foil 50/Holo 35/Poly 15) to 1 selected card |
| 6 | Wraith | `c_wraith` | 4 | – | Create free random **Rare** joker, set money to $0 |
| 7 | Sigil | `c_sigil` | 4 | – | Convert all hand cards to a single random **suit** |
| 8 | Ouija | `c_ouija` → `c_mp_ouija_standard` | 4 | – | **Vanilla:** all hand→1 rank, −1 hand size. **BMP:** destroy 3, rest→1 rank, no size loss |
| 9 | Ectoplasm | `c_ectoplasm` | 4 | – | Add Negative to a random joker, escalating −hand size (−1,−2,−3,…) |
| 10 | Immolate | `c_immolate` | 4 | – | Destroy 5 random cards, gain $20 |
| 11 | Ankh | `c_ankh` | 4 | – | Copy 1 joker, destroy all other non-eternal jokers (copy loses Negative) |
| 12 | Deja Vu | `c_deja_vu` | 4 | – | Add Red seal to 1 selected card |
| 13 | Hex | `c_hex` | 4 | – | Add Polychrome to a random joker, destroy all other non-eternal jokers |
| 14 | Trance | `c_trance` | 4 | – | Add Blue seal to 1 selected card |
| 15 | Medium | `c_medium` | 4 | – | Add Purple seal to 1 selected card |
| 16 | Cryptid | `c_cryptid` | 4 | – | Create 2 copies of 1 selected card |
| 17 | The Soul | `c_soul` | 4 | **yes** | Create a random **Legendary** joker (0.3% pack roll) |
| 18 | Black Hole | `c_black_hole` | 4 | **yes** | Level up **every** poker hand by +1 (0.3% pack roll) |

---

## G. RNG seeds (for deterministic re-implementation)

| Card | `pseudoseed`/`pseudorandom` keys |
|---|---|
| Familiar | `random_destroy` (destroy), `familiar_create` (rank+suit), `spe_card` (enhancement) |
| Grim | `random_destroy`, `grim_create` (suit), `spe_card` |
| Incantation | `random_destroy`, `incantation_create` (rank+suit), `spe_card` |
| Aura | `aura` (edition poll) |
| Wraith | joker queue via `create_card(..., 'wra')` |
| Sigil | `sigil` (suit) |
| Ouija (vanilla) | `ouija` (rank) |
| Ouija (BMP) | `ouija_destroy` (which 3 destroyed), `ouija` (new rank via `SMODS.Ranks`) |
| Ectoplasm | `ectoplasm` (joker pick) |
| Immolate | `immolate` (shuffle to pick 5) |
| Ankh | `ankh_choice` (joker to copy) |
| Hex | `hex` (joker pick) |
| Cryptid | (no roll — copies the highlighted card) |
| The Soul | `soul_<type>ante` (forced spawn) / joker `'sou'` |
| Black Hole | `soul_<type>ante` (forced spawn) |

**The Order (`MP.should_use_the_order()`) global RNG patches** that change Spectral determinism (`TheOrder.lua`):
- `pseudoshuffle` (affects **Immolate**, BMP **Ouija**) → deterministic sort by `mp_stdval`/`mp_shuffleval` instead of random shuffle (`:398-423`).
- `pseudorandom_element` for joker/playing-card lists (affects **Familiar/Grim/Incantation** card pick, **Ankh/Hex/Ectoplasm/Wraith** joker pick) → picks the top of a deterministic value sort (`:426-461`).
- `create_card` patched to ante-independent single pool (affects **Wraith**, **Soul**, and the soul spawn seeds keyed on `'c_soul'`/`'c_black_hole'`) (`:3-27`).
- Soul/Black-Hole forced-spawn seeds switch from `_type` to the card key when The Order is active (`common_events.lua:2420,2426`).

---

## Open questions

1. **Ruleset scope of variants.** `c_mp_ouija_standard` and `ectoplasm_sandbox` are registered objects. Confirmed `c_mp_ouija_standard` replaces base `c_ouija` in the **standard** (and experimental) layers. The **sandbox** Ectoplasm (`ectoplasm_sandbox`: random pick of −1 hand / −1 discard / −1 hand-size + Negative on a random joker) — is it active in any shipped 0.4.0 competitive ruleset, or sandbox-only? Need to grep the sandbox rotation list (`layers/sandbox.lua` mappings did not include a consumable entry; sandbox consumables may be loaded separately). Flagged: which Ectoplasm is canonical in ranked.
2. **Wraith "separate Rare queue".** The 0.3.3 doc describes a queue shared with Rare skip tags. In 0.4.0 source I see only the deterministic `create_card` + `'wra'` key-append; I did **not** find an explicit shared-with-rare-skip queue object. Need to confirm whether the shared queue is implemented elsewhere (skip-tag handler) or was folded into The Order's pooling. (Unverified that Wraith and Rare-tag draw from the same advancing index in 0.4.0.)
3. **Ectoplasm escalation persistence.** `G.GAME.ecto_minus` escalation — confirm it resets per-run only (it does in vanilla); verify BMP doesn't reset it on ante/round in any ruleset.
4. **Soul/Black Hole rate stacking with modded souls.** `common_events.lua:2398-2417` computes a combined soul rate across `SMODS.Consumable.legendaries`; with only vanilla content the 0.3% per-card rolls apply. Verify no BMP-added legendaries change the effective Soul rate in ranked.
5. **`mp_sticker_balanced` semantics.** Both reworked Ouija and sandbox Ectoplasm set `mp_sticker_balanced = true`. Confirm this is purely cosmetic (the "Balanced" tooltip from the changelog) and does not alter effect numbers.

## New building blocks needed

The current engine has `Edition` (FOIL/HOLO/POLY/NEGATIVE) and `Seal` (RED/BLUE/GOLD/PURPLE) enums and a `PlanetCatalog`/Black-Hole hook (15-planets.md). To support Spectrals we need:

1. **`SpectralCatalog`** — 18 entries keyed by `c_*`, each with `cost=4`, `hidden` flag, `max_highlighted`, and an effect handler. Mirror `PlanetCatalog`.
2. **Consumable use pipeline / `Intent`** — a "use spectral on selected card(s)" intent carrying highlighted card ids; enforce per-card `can_use` (single highlight, editionless requirement for Aura, joker-count for Ankh/Wraith, ≥N hand cards for Ouija/destroyers).
3. **Hand-card mutation primitives:**
   - `setSeal(cardId, Seal)` — Talisman/Deja Vu/Trance/Medium.
   - `setEdition(cardId, Edition)` — Aura (playing card), and on jokers for Ectoplasm/Hex.
   - `setBaseSuit` / `setBaseRank` (rebuild base, keep enhancement/seal/edition) — Sigil/Ouija.
   - `createPlayingCard(suit, rank, enhancement)` with random Enhanced enhancement excluding Stone — Familiar/Grim/Incantation.
   - `copyPlayingCard(cardId)` + grow deck `card_limit` — Cryptid.
   - `destroyCards(cardIds)` emitting a `remove_playing_cards` scoring context — Familiar/Grim/Incantation/Immolate/Ouija.
4. **Joker mutation primitives:**
   - `addJokerEdition(jokerId, Edition.NEGATIVE/POLYCHROME)` over an editionless-joker pool — Ectoplasm/Hex.
   - `copyJoker(jokerId)` honoring eternal/`mp_phantom` exclusions and stripping Negative on the copy — Ankh.
   - `destroyOtherJokers(keepId)` respecting `eternal` — Hex/Ankh.
   - `createRandomJoker(rarity)` — Wraith (Rare), Soul (Legendary).
5. **`handSize` modifier with escalation state** (`ecto_minus`) and **permanent hand-size delta** (vanilla Ouija) — even if BMP Ouija drops it, the engine should model `change_size`.
6. **Economy hook** — `easeDollars(+20)` (Immolate), `setDollars(0)` (Wraith).
7. **Black Hole hand-level hook** — "level up every hand by +1" (already partially covered by PlanetCatalog/Black Hole in 15-planets.md; ensure the Spectral path reuses it).
8. **Forced-spawn (soul) roll** — 0.3% `pseudorandom('soul_…ante') > 0.997` gate on Tarot/Spectral/Planet pack generation, with the Soul-before-Black-Hole precedence and Showman/`used_jokers` suppression. Needed in the shop/pack generation module, not the use-time module.
9. **The Order determinism shims** — if the server supports the deterministic ruleset, `pseudoshuffle` and `pseudorandom_element` must be replaceable with the `mp_stdval`/`mp_shuffleval` stable-sort variants for parity (affects Immolate, BMP Ouija, Ankh/Hex/Ectoplasm/Wraith/Familiar-family selection).
