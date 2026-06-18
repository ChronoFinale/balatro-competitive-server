# 14 — Tarot Cards (Balatro Multiplayer 0.4.0)

Definitive catalogue of all **22** vanilla Tarot consumables, their exact effects,
targets, RNG touch-points, and how each maps onto our (not-yet-existent) consumable
model in `com.balatro.engine`.

## Sources (all claims grounded here)

- **Centers / config numbers** — base game decompiled dump:
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/game.lua` lines **542–563**
  (every `c_*` Tarot center: `set="Tarot"`, `cost=3`, `cost_mult=1.0`, `config={...}`).
- **Use logic (what each Tarot actually does)** —
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/lovely/dump/card.lua`:
  `Card:use_consumeable` body **lines 1370–1801**, `Card:can_use_consumeable` **lines 1803–1859**.
- **The Fool's "last used" tracking** —
  `.../dump/functions/misc_functions.lua:1359` (`G.GAME.last_tarot_planet = card.config.center_key`)
  and `.../dump/functions/common_events.lua:3126-3136` (Fool tooltip; never tracks Fool itself).
- **Ranked pool / bans (0.4.0)** —
  `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/layers/standard.lua` (the ranked content layer),
  `.../layers/ranked.lua` (no consumable changes), `CHANGELOG.md`.
- **Ranked-baseline cross-check (0.3.3, flagged where it differs)** —
  `C:/Users/micha/AppData/Local/Temp/xlsx_out2/03_Consumables.txt`.
- **Our model vocabulary** — `D:/NewServer/queue-model.md`, `D:/NewServer/balatro-engine-spec.md`,
  `.../engine/card/{Enhancement,Suit,Rank,Seal,Edition}.java`.

All 22 share: `set = "Tarot"`, `cost = 3`, `cost_mult = 1.0`, `consumeable = true`,
`discovered = false` (must be seen once). None are banned by base game; ranked bans are noted per row.

---

## The common "enhance / convert" pipeline

15 of the 22 Tarots route through one shared branch in `use_consumeable`
(`card.lua:1380-1431`), gated by `self.ability.consumeable.mod_conv` (set an
enhancement) **or** `suit_conv` (change suit). The branch:

1. Flips the highlighted cards face-down (`card1` sound), then
2. Applies the per-Tarot mutation, then
3. Flips them back (`tarot2` sound) and `unhighlight_all`.

`max_highlighted` (the center config) is the cap on how many hand cards you may
select; `can_use_consumeable` (`card.lua:1852-1853`) enforces
`mod_num >= #highlighted >= (min_highlighted or 1)` where `mod_num` is derived
from `max_highlighted`. **None of these enhance/convert Tarots consume RNG** — the
target cards are player-selected, the mutation is deterministic.

Enhancement keys used (`mod_conv`) and their mapping to our `Enhancement` enum
(`engine/card/Enhancement.java`):

| `mod_conv` key | Our `Enhancement` | Vanilla effect of the enhancement |
|---|---|---|
| `m_lucky` | `LUCKY` | 1-in-5 +20 mult, 1-in-15 +$20 when scored |
| `m_mult` | `MULT` | +4 Mult when scored |
| `m_bonus` | `BONUS` | +30 Chips when scored |
| `m_wild` | `WILD` | counts as every suit |
| `m_steel` | `STEEL` | x1.5 Mult while held in hand |
| `m_glass` | `GLASS` | x2 Mult when scored; 1-in-4 shatter |
| `m_gold` | `GOLD` | +$3 held at end of round — **0.4.0: raised to $4** (CHANGELOG "Gold Card → $4"; our enum comment still says $3, fix when consumables land) |
| `m_stone` | `STONE` | +50 Chips, always scores, no rank/suit |

---

## Catalogue (all 22, in `order`)

| # | Name | Key | `effect` tag | `config` (verbatim) | Exact effect & targets | RNG / queue |
|---|---|---|---|---|---|---|
| 1 | The Fool | `c_fool` | Disable Blind Effect (legacy tag; **unused**) | `{}` | Creates a copy of **the last Tarot or Planet card used this run** (`G.GAME.last_tarot_planet`) into the consumable area, if there's room. **Cannot copy itself** and cannot be the very first consumable used (`can_use` requires `last_tarot_planet` set and `~= 'c_fool'`). The `effect="Disable Blind Effect"` string is a dead legacy label — actual behavior is the `name=='The Fool'` branch (`card.lua:1648-1659`). | Produces an exact named copy (`create_card('Tarot_Planet', ..., G.GAME.last_tarot_planet, 'fool')`) — **no RNG**, the key is fixed. |
| 2 | The Magician | `c_magician` | Enhance | `{mod_conv='m_lucky', max_highlighted=2}` | Enhances up to **2** selected hand cards to **Lucky** (`m_lucky`). | none |
| 3 | The High Priestess | `c_high_priestess` | Round Bonus (tag) | `{planets=2}` | Creates up to **2 random Planet cards** in the consumable area, capped by free consumable slots (`card.lua:1676-1689`). | **RNG**: each created Planet is `create_card('Planet', ..., 'pri')` — pulls from Planet generation pool. → Up-Top **Planet** queue. |
| 4 | The Empress | `c_empress` | Enhance | `{mod_conv='m_mult', max_highlighted=2}` | Enhances up to **2** selected cards to **Mult** (`m_mult`, +4 Mult each when scored). | none |
| 5 | The Emperor | `c_emperor` | Round Bonus (tag) | `{tarots=2}` | Creates up to **2 random Tarot cards** in the consumable area, capped by free slots (`card.lua:1676-1689`, `create_card('Tarot', ..., 'emp')`). | **RNG** → Up-Top **Tarot** queue. |
| 6 | The Hierophant | `c_heirophant` (note misspelled key) | Enhance | `{mod_conv='m_bonus', max_highlighted=2}` | Enhances up to **2** selected cards to **Bonus** (`m_bonus`, +30 Chips each). | none |
| 7 | The Lovers | `c_lovers` | Enhance | `{mod_conv='m_wild', max_highlighted=1}` | Enhances **1** selected card to **Wild** (any suit). | none |
| 8 | The Chariot | `c_chariot` | Enhance | `{mod_conv='m_steel', max_highlighted=1}` | Enhances **1** selected card to **Steel** (x1.5 Mult held). | none |
| 9 | Justice | `c_justice` | Enhance | `{mod_conv='m_glass', max_highlighted=1}` | Enhances **1** selected card to **Glass** (x2 Mult, 1-in-4 shatter). **Ranked-ban status is contradictory in 0.4.0 — see Open questions.** | none (the *break* later consumes the `glass` 1/4 queue, not this Tarot) |
| 10 | The Hermit | `c_hermit` | Dollar Doubler | `{extra=20}` | **Doubles your money**, capped at **+$20**: `ease_dollars(max(0, min(G.GAME.dollars, 20)))` (`card.lua:1660-1667`). At $0 gives $0; at $7 gives +$7; at $50+ gives +$20. | none |
| 11 | The Wheel of Fortune | `c_wheel_of_fortune` | Round Bonus (tag) | `{extra=4}` | **1-in-`extra` (= 1 in 4)** chance to add a random edition (Foil/Holo/Polychrome via `poll_edition`) to a random **eligible** Joker (one with no edition). On fail, shows "Nope!" (`card.lua:1747-1800`). `can_use` requires ≥1 eligible Joker. | **RNG (two rolls on hit)**: `SMODS.pseudorandom_probability(self,'wheel_of_fortune',1,4)` then `pseudoseed('wheel_of_fortune')` for the target, then `poll_edition('wheel_of_fortune')`. → a `wheel_of_fortune` probability queue (1/4) + a target-pick + an edition-poll. |
| 12 | Strength | `c_strength` | Round Bonus (tag) | `{mod_conv='up_rank', max_highlighted=2}` | **Increases the rank by 1** of up to **2** selected cards (`card.lua:1400-1415`). A→2 (wraps), K→A, else `min(id+1,14)`. Uses the shared flip pipeline but a dedicated `name=='Strength'` branch (does *not* call `set_ability`). | none |
| 13 | The Hanged Man | `c_hanged_man` | Card Removal | `{remove_card=true, max_highlighted=2}` | **Destroys** up to **2** selected hand cards (`card.lua:1546-1568`; shatters if glass, else `start_dissolve`). Fires `SMODS.calculate_context{remove_playing_cards=true}` (`card.lua:1646`). | none (targets are selected) |
| 14 | Death | `c_death` | Card Conversion | `{mod_conv='card', max_highlighted=2, min_highlighted=2}` | Requires **exactly 2** selected (`min=max=2`). **Converts the left card into the right card** (rightmost by screen X is the template; the other is `copy_card`'d from it) — `card.lua:1390-1399`. Result: two identical cards (rank, suit, enhancement, edition, seal). | none |
| 15 | Temperance | `c_temperance` | Joker Payout | `{extra=50}` | Gives money equal to the **total sell value of all your Jokers**, capped at **$50** (`self.ability.money` is computed from joker sell values; payout `ease_dollars(self.ability.money)`, `card.lua:1668-1675`). With no jokers, $0. | none |
| 16 | The Devil | `c_devil` | Enhance | `{mod_conv='m_gold', max_highlighted=1}` | Enhances **1** selected card to **Gold** (`m_gold`; +$ held end-of-round — 0.4.0 value $4, see enhancement table). | none |
| 17 | The Tower | `c_tower` | Enhance | `{mod_conv='m_stone', max_highlighted=1}` | Enhances **1** selected card to **Stone** (`m_stone`; +50 Chips, no rank/suit, always scores). | none |
| 18 | The Star | `c_star` | Suit Conversion | `{suit_conv='Diamonds', max_highlighted=3}` | Changes up to **3** selected cards' suit to **Diamonds** (`change_suit`, `card.lua:1416-1419`). | none |
| 19 | The Moon | `c_moon` | Suit Conversion | `{suit_conv='Clubs', max_highlighted=3}` | Changes up to **3** selected cards' suit to **Clubs**. | none |
| 20 | The Sun | `c_sun` | Suit Conversion | `{suit_conv='Hearts', max_highlighted=3}` | Changes up to **3** selected cards' suit to **Hearts**. | none |
| 21 | Judgement | `c_judgement` | Random Joker | `{}` | Creates a **random Joker** (any rarity, respecting joker slots) — `create_card('Joker', G.jokers, ... 'jud')` (`card.lua:1690-1700`). `can_use` requires a free Joker slot. | **RNG → Joker queue.** **0.4.0 stake split (from 0.3.0 CHANGELOG, still in effect):** on **Orange Stake+** it draws from **its own dedicated Judgement queue** (vanilla behavior); on lower stakes it pulls the next Joker from the **shop queue**. |
| 22 | The World | `c_world` | Suit Conversion | `{suit_conv='Spades', max_highlighted=3}` | Changes up to **3** selected cards' suit to **Spades**. | none |

### Targeting summary

- **Hand cards selected by player (deterministic):** Magician, Empress, Hierophant,
  Lovers, Chariot, Justice, Strength, Hanged Man, Death, Devil, Tower, Star, Moon,
  Sun, World. (`max_highlighted` 1, 2, or 3; Death also `min_highlighted=2`.)
- **Jokers:** Wheel of Fortune (random eligible, RNG), Judgement (creates random).
- **Consumable area:** Fool (copy last), Emperor (2 Tarots), High Priestess (2 Planets).
- **Economy only:** Hermit (double $, cap 20), Temperance (joker sell sum, cap 50).

---

## 0.4.0 vs 0.3.3 deltas (Tarot-relevant)

- **Justice — "Back in rotation" (CHANGELOG 0.4.0).** This is the headline Tarot
  change. **BUT** the on-disk 0.4.0 source `layers/standard.lua` lines 12–14 still
  lists `c_justice` under `banned_consumables`, and the 0.3.3 baseline doc
  (`03_Consumables.txt:5-6`) says Justice is banned so Glass is pack-only. Per the
  task rule "0.4.0 SOURCE wins," the code currently still bans it. Treated as a
  discrepancy below.
- **Judgement queue split** (introduced 0.3.0, unchanged in 0.4.0): own queue on
  Orange+, shop queue below. Still authoritative.
- **Gold enhancement payout** raised $3 → **$4** in 0.4.0 (affects The Devil's
  output value, not the Tarot itself).
- **Ouija** (Spectral, not Tarot) reworked — out of scope here but it's the only
  consumable in `standard.lua reworked_consumables` (`c_mp_ouija_standard`).
- No numeric change to any Tarot's own `config` between 0.3.3 and 0.4.0 — the
  centers in `game.lua:542-563` match the 0.3.3 baseline values.

---

## Mapping to our consumable model

We have **no consumable subsystem yet** (`engine/` has `card/`, `joker/`, `scoring/`,
`rng/`, `state/` — grep for "Consumable"/"Tarot" finds only Planet/Seal/joker refs,
no Tarot type). The 22 Tarots decompose into a small set of deterministic
operations plus three RNG touch-points that must be queue-backed per `queue-model.md`.

**Operation classes (proposed `TarotEffect` variants):**

1. `EnhanceSelected(Enhancement, maxTargets)` — Magician(LUCKY,2), Empress(MULT,2),
   Hierophant(BONUS,2), Lovers(WILD,1), Chariot(STEEL,1), Justice(GLASS,1),
   Devil(GOLD,1), Tower(STONE,1). Pure mutation on selected `Card`s. All enums
   already exist in `Enhancement.java`.
2. `ConvertSuit(Suit, maxTargets=3)` — Star(DIAMONDS), Moon(CLUBS), Sun(HEARTS),
   World(SPADES). Uses existing `Suit` enum. Pure.
3. `IncrementRank(maxTargets=2)` — Strength. A→2 wrap, K→A, else +1 capped at A.
   Needs a rank-step helper on `Rank.java`.
4. `CopyCardToTemplate(min=2,max=2)` — Death. Copy full ability of rightmost
   selected onto the other. Pure.
5. `DestroySelected(max=2)` — Hanged Man. Removes selected hand cards; must emit a
   "cards removed" scoring/joker context.
6. `DoubleMoney(cap=20)` — Hermit. `min(money, cap)`. Pure economy.
7. `JokerSellSumPayout(cap=50)` — Temperance. Needs joker sell-value accessor. Pure.
8. `CopyLastConsumable` — Fool. Needs run-scoped `lastTarotOrPlanetUsed` tracking
   (set on every Tarot/Planet use, never set to Fool). Deterministic given the key.
9. `CreateConsumables(type, n=2)` — Emperor(Tarot,2), High Priestess(Planet,2).
   **RNG** → Up-Top Tarot / Up-Top Planet queues.
10. `CreateRandomJoker` — Judgement. **RNG** → Joker queue, with **stake-gated source**
    (own queue Orange+, shop queue below).
11. `RandomEditionOnJoker(prob=1/4)` — Wheel of Fortune. **RNG**: 1/4 hit queue +
    eligible-joker pick + edition poll.

**Queue wiring (per `queue-model.md`):**

- High Priestess / Emperor → the planned **Up-Top** Planet/Tarot queues (distinct
  from Pack queues). The Fool's copy is *not* a queue draw (fixed key).
- Judgement → Joker rarity queues; honor the Orange-stake "own queue vs shop queue"
  split as a stake flag selecting which queue feeds it.
- Wheel of Fortune → a new `wheel_of_fortune` probability queue (1/4, raw roll
  stored, threshold applied at read like `glass`/`lucky_*`), plus a deterministic
  eligible-joker index pick, plus an `edition` poll queue.

**Selection model:** every selected-target Tarot takes a player-supplied list of
hand-card indices; the engine validates `min_highlighted..max_highlighted`
server-side (mirror of `can_use_consumeable`). No RNG for any selected-target Tarot,
so they need no queue — they're pure replayable mutations.

---

## Open questions

1. **Justice ban discrepancy.** CHANGELOG.md (0.4.0) says "Justice — Back in
   rotation," but `layers/standard.lua:12-14` still lists `c_justice` in
   `banned_consumables`, and `ranked.lua` adds no consumable overrides. Which is
   authoritative for live 0.4.0 ranked — is the CHANGELOG aspirational/ahead of the
   shipped layer, or is the ban lifted elsewhere (e.g. a server-side pool we don't
   have on disk: `D:/BalatroMultiplayerAPI-Server-main/`)? Needs confirmation before
   we enable Glass in ranked.
2. **Temperance cap exactness.** `self.ability.money` is computed elsewhere (not in
   the `use` branch). Confirmed cap is $50 from `config.extra=50`, but the exact
   sell-value summation (does it include edition/seal sell bonuses, eternal jokers?)
   wasn't located in the dump — flagged **unverified** beyond "sum of joker sell
   values, capped 50."
3. **Wheel of Fortune eligible pool composition.** `self.eligible_strength_jokers`
   is precomputed (jokers without an edition). Need to confirm whether negative/
   `mp_phantom` editions count as "has edition" for eligibility in 0.4.0.
4. **Judgement own-queue contents.** On Orange+ it uses a dedicated queue — its
   rarity weighting / pool (does it match shop joker odds?) isn't defined in the
   files read; the server repo likely holds it.
5. **Exact base-game description strings** are not in the lovely dump (they ship in
   the game's own `localization/`); all numbers above come from `config` + use-logic,
   not from display text.

## New building blocks needed

1. **`Consumable` type + `ConsumableType{TAROT, PLANET, SPECTRAL}`** — none exists in
   `engine/`. Tarot is the first to land; model as data + an effect dispatch.
2. **`TarotEffect` sealed hierarchy** implementing the 11 operation classes above.
3. **Hand-card target selection + validation** mirroring `can_use_consumeable`
   (`min/max_highlighted`, state gates).
4. **`Rank` step helper** (A→2 wrap, K→A) for Strength.
5. **`Enhancement.GOLD` value fix** to $4 for 0.4.0 (comment currently says $3).
6. **Run-scoped `lastTarotOrPlanetUsed`** tracker on `RunState` (for The Fool),
   updated on every Tarot/Planet use, never recording Fool itself.
7. **Up-Top Tarot & Up-Top Planet queues** in `QueueSet` (Emperor, High Priestess) —
   distinct from Pack queues.
8. **`wheel_of_fortune` 1/4 probability queue** (+ eligible-joker pick + edition poll).
9. **Joker-creation path for consumables** (Judgement) with the **Orange-stake
   queue-source switch**.
10. **Joker sell-value accessor + economy cap helpers** (Temperance $50, Hermit $20).
11. **"Cards removed" calculate-context** emission (Hanged Man) so jokers reacting to
    destruction fire.
12. **Per-ruleset consumable ban list** plumbed from the active layer (to honor /
    resolve the Justice question).
