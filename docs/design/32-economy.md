# 32 — Economy (Money, Interest, Rewards, Shop Pricing)

Parity target: **Balatro base game** + **Balatro Multiplayer (BMP) 0.4.0** ("Ranked Update").
Status legend: ✅ implemented in our engine · ⚠️ partial · ❌ missing · ❓ unverified.

This section is the authoritative reference for every dollar that enters or leaves a
run. All money mutations must remain **server-authoritative**: the client never tells
the server "I now have $N" — it sends intents (buy / sell / reroll / skip), and the
server recomputes balances. The only economy value BMP transmits is the *opponent's*
shop spend (`spentLastShop`), used purely as input to nemesis-aware jokers.

---

## 1. How vanilla / BMP 0.4.0 does it

### 1.1 Starting money (per deck)

Base default: `G.GAME.starting_params.dollars = 0` but `previous_round.dollars = 4`,
i.e. the Red Deck and most decks start the run with **$4**
(`dump/game.lua:1965`, `:1973`).

Deck modifiers (`dump/game.lua:645-661`, applied in `back.lua`):

| Deck | Economy effect | Source |
|------|----------------|--------|
| Red / most | start $4 | `game.lua:1973` |
| Yellow Deck | start **$14** ($4 + `config.dollars = 10`) | `game.lua:649` |
| Green Deck | **no interest**; instead $2/leftover hand + $1/leftover discard at end of round (`extra_hand_bonus=2, extra_discard_bonus=1, no_interest=true`) | `game.lua:650`, `back.lua:343` |
| Plasma Deck | `ante_scaling = 2` (doubles blind requirements; indirect economy pressure) | `game.lua:660` |
| Magic / Nebula / Zodiac | start with free voucher(s) (`v_crystal_ball`, `v_telescope`, `v_tarot_merchant`+`v_planet_merchant`+`v_overstock_norm`) — change shop economy, not starting cash | `game.lua:652,653,657` |

BMP decks (`multiplayer-0.4.0/objects/decks/`):

| BMP Deck | Economy effect | Source |
|----------|----------------|--------|
| Oracle Deck | starts with `v_clearance_sale` voucher; **money hard-capped at `oracle_max = 50` + `interest_cap` (25) = $75** when adding positive money. Negative mods (spending) bypass the cap. | `objects/decks/AA_oracle.lua:28`, `lovely/decks.toml:17-20` |
| others (Violet/Indigo/Orange/White/Gradient/Heidelberg/Echo/Cocktail) | no special starting-money rule found (❓ verify Echo/Cocktail) | `objects/decks/` |

### 1.2 Interest

Vanilla rule: at end of round, **+$1 per $5 held, capped at $5 of interest**
(`functions/state_events.lua:1127-1138`):

```lua
if G.GAME.dollars >= 5 and not G.GAME.modifiers.no_interest then
  dollars = dollars + G.GAME.interest_amount * math.min(math.floor(G.GAME.dollars/5), G.GAME.interest_cap/5)
end
```

Defaults (`game.lua:1954-1955`): `interest_cap = 25`, `interest_amount = 1`.
So max interest = `interest_amount * (interest_cap/5)` = `1 * 5` = **$5**.

Economy modifiers to interest:

| Source | Effect | Mechanism | Cite |
|--------|--------|-----------|------|
| **To the Moon** (joker, Uncommon, $5) | `interest_amount += 1` (raises **$ per $5** to $2) | add/remove on `add_to_deck`/`remove_from_deck` | `game.lua:475`, `card.lua:808-810,870-872` |
| **Seed Money** (voucher, $10) | raises `interest_cap` to **50** (max interest $10) — `config.extra = 50` | `card.lua:2302` sets `interest_cap = center_table.extra` | `game.lua:621`, `card.lua:2301-2302` |
| **Money Tree** (voucher, $10, requires Seed Money) | raises `interest_cap` to **100** (max interest $20) — `config.extra = 100` | same path | `game.lua:638` |
| **Green Deck** | `no_interest = true` (no interest at all) | `back.lua:343-344` | `game.lua:650` |
| BMP `no_interest` mutator layer | sets `G.GAME.modifiers.no_interest` (disables interest entirely) | `layers/economy_mutators.lua:20-22` | — |
| BMP `mp_modified_interest_rate` | alternative interest band: pays per `rate` dollars instead of per $5, with `cap = interest_cap/(5/rate)` | `state_events.lua:1167-1181` | — |

Note: interest is computed off `G.GAME.dollars` at round-eval time (held cash), so the
interest cap implicitly soft-caps how much hoarding pays.

### 1.3 Blind rewards ($ per blind)

`game.lua:273-303` (blind definitions, `dollars` field):

| Blind | Reward | Score mult | Cite |
|-------|--------|-----------|------|
| Small Blind | **$3** | ×1 | `game.lua:273` |
| Big Blind | **$4** | ×1.5 | `game.lua:274` |
| Boss (regular) | **$5** | ×2 (varies; Wall ×4, Needle ×1, Violet Vessel ×6) | `game.lua:275-301` |
| Finisher bosses (Acorn / Leaf / Vessel / Bell / Heart — ante 8 showdown) | **$8** | ×2 (Vessel ×6) | `game.lua:286,296,297,302,303` |

Reward is awarded only if the blind is **beaten** (`chips - blind.chips >= 0`); a saved/
unbeaten blind pays $0 (`state_events.lua:1036-1043`). Skipping a Small/Big blind awards
the blind's **skip tag** instead of money (see §1.10).

Round-eval bonus money on top of the blind reward (`state_events.lua:1063-1071`):

- **Leftover hands**: `+$ (hands_left × money_per_hand)`, default `money_per_hand = 1`
  → **$1 per unused hand** (suppressed by `no_extra_hand_money`, e.g. some bosses; never paid during a PvP/Nemesis blind).
- **Leftover discards**: `+$ (discards_left × money_per_discard)`, but `money_per_discard`
  defaults to **0** (no payout) unless a deck/voucher/mutator enables it (Green Deck $1, Recyclomancy, BMP `frugal` layer).

### 1.4 Boss rewards & finisher rewards

Same `dollars` field as §1.3: regular bosses $5, finishers $8. Bosses have no separate
"defeat bonus" beyond their blind reward. The high-mult finishers (×2–×6) gate ante 8.

### 1.5 Sell values

`card.lua:532-533`:

```lua
function Card:set_sell_value()
  self.sell_cost = math.max(1, math.floor(self.cost/2)) + (self.ability.extra_value or 0)
end
```

- **Jokers/consumables**: sell value = `max(1, floor(cost/2)) + extra_value`.
- `extra_value` accumulates from value-adding effects (e.g. cards that bank money into a
  joker's sell value — `card.lua:3440-3459`).
- **Egg** (joker, $4): gains `+$3 sell value each round` (`extra_value` grows; config `extra = 3`, `game.lua:435`).
- **Gift Card** (joker, $6): `+$1 sell value to every joker & consumable each round` (`config.extra = 1`, `game.lua:470`).
- Phantom-edition (BMP) cards have `sell_cost = 0` (`card.lua:509`).
- Selling a card calls `ease_dollars(self.sell_cost)` (`card.lua:1973`); BMP hooks
  `Card:sell_card()` to broadcast `sold_joker` for Taxes (`lovely/game.toml:116-120`).

### 1.6 Shop pricing

Base cost formula (`card.lua:513-530`):

```lua
self.cost = math.max(1, math.floor((self.base_cost + self.extra_cost + 0.5) * (100 - G.GAME.discount_percent)/100))
```

- `base_cost` comes from each center's `cost` field.
- `extra_cost = G.GAME.inflation + edition premium` (`card.lua:504,514-521`).
- `discount_percent` from Clearance Sale / Liquidation vouchers (see below).

**Joker cost by rarity** (base `cost` in `game.lua` joker defs; typical values):
Common ≈ **$4–6**, Uncommon ≈ **$5–8**, Rare ≈ **$8**, Legendary ≈ **$20**.
(Costs are per-joker in data, not a flat rarity constant — e.g. Credit Card $1, Golden Joker $6, Rocket $6, Bull $6, Bootstraps $7, To the Moon $5, Egg/Mail-In/Business/Faceless/To-Do/Delayed-Grat/Chaos $4.)

**Consumables / packs / vouchers** (base `cost`):

| Item | Base cost | Cite |
|------|-----------|------|
| Tarot / Planet / Spectral (in shop slot) | **$3** | standard center costs (❓ confirm individual values; Immolate spectral `cost = 4`, `game.lua:599`) |
| Voucher | **$10** | all `v_*` defs `cost = 10`, `game.lua:611-643` |
| Booster packs | Normal $4 / Jumbo $6 / Mega $8 (vanilla pack costs; ❓ confirm exact in pack defs) | — |
| BMP Ouija / Ectoplasm | **$4** (0.4.0 bug fix) | `CHANGELOG.md:12` |

**Edition price premiums** (`extra_cost`, `SMODS/.../game_object.lua:3570-3669`):

| Edition | `extra_cost` (added to base) | `config.extra` (gameplay) | Cite |
|---------|------------------------------|---------------------------|------|
| Foil | **+$2** | +50 chips | `game.lua:678`, `game_object.lua:3570` |
| Holographic | **+$3** | +10 mult | `game.lua:679`, `game_object.lua:3603` |
| Polychrome | **+$5** | ×1.5 mult | `game.lua:680`, `game_object.lua:3636` |
| Negative | **+$5** | +1 joker/consumable slot | `game.lua:681`, `game_object.lua:3669` |

Special pricing rules:
- Couponed cards (Coupon tag) cost **$0** while in shop (`card.lua:509`).
- Rental cards cost **$1** in shop, `rental_rate = 3`/round upkeep (`card.lua:529`, `game.lua:1960`).
- Astronomer makes all Planets & Celestial packs **free** (`card.lua:528`).
- Booster ante-scaling (`booster_ante_scaling`): pack cost `+ (ante - 1)` (BMP `pricey_packs` layer; `card.lua:524`).
- Inflation: each purchase raises `G.GAME.inflation` by $1, compounding all future `extra_cost` (BMP `inflation` layer; `card.lua` set_cost).

### 1.7 Reroll cost scaling

`functions/common_events.lua:2759-2765`:

```lua
function calculate_reroll_cost(skip_increment)
  if free_rerolls > 0 then reroll_cost = 0; return end
  if not skip_increment then reroll_cost_increase = reroll_cost_increase + 1 end
  reroll_cost = (temp_reroll_cost or round_resets.reroll_cost) + reroll_cost_increase
end
```

- **Base reroll cost = $5** (`game.lua:1969 base_reroll_cost = 5`, `:2003 reroll_cost = 5`).
- Each paid reroll increments `reroll_cost_increase` by **+1** (next reroll +$1, compounding within the round).
- `reroll_cost_increase` and `free_rerolls` reset each round (`state_events.lua:297`).

Modifiers:

| Source | Effect | Cite |
|--------|--------|------|
| **Reroll Surplus** (voucher, $10) | base reroll cost **−$2** (`config.extra = 2`, applied to round_resets.reroll_cost) | `game.lua:614`, `card.lua:2297` |
| **Reroll Glut** (voucher, $10, requires Surplus) | base reroll cost **another −$2** (total −$4) | `game.lua:631` |
| **Chaos the Clown** (joker, $4) | **1 free reroll per shop** (`free_rerolls += 1`, sets reroll_cost = 0 until used) | `game.lua:417`, `card.lua:796-798,858-860` |
| Coupon-style tags | temporary reroll discounts via `temp_reroll_cost` | `state_events.lua:251` |

### 1.8 Cash-out screen (round evaluation)

`G.FUNCS.evaluate_round` (`state_events.lua:1031-1204`) builds the cash-out rows in order:

1. **Blind reward** (`blind.dollars`, or $0 if saved). `state_events.lua:1037`
2. **Remaining hands** × `money_per_hand` (default $1). `:1064`
3. **Remaining discards** × `money_per_discard` (default $0). `:1069`
4. **Per-joker `calculate_dollar_bonus`** rows (Golden Joker, Rocket, Cloud 9, etc.). `:1074-1089`
5. **Per-object `calc_dollar_bonus`** (custom/individual). `:1091-1118`
6. **Tag eval bonuses** (Investment Tag etc.). `:1119-1126`
7. **Interest**. `:1127-1139`
8. **(BMP) Comeback bonus** — injected here. `:1140-1165` / `lovely/game.toml:22-46`
9. **(BMP) `mp_modified_interest`** band if active. `:1167-1181`
10. Cash-out rows beyond 7 are collapsed into a hidden "+N more" row. `:1184-1204`

The sum is applied to `G.GAME.dollars`. **Credit Card** sets the bankrupt floor so the
balance can go negative (see §1.9).

### 1.9 Economy-modifying jokers / vouchers / tags

**Jokers** (`game.lua` defs + `card.lua` behavior):

| Joker | Rarity / cost | Economy effect | Cite |
|-------|---------------|----------------|------|
| **Golden Joker** | C / $6 | **+$4 at end of round** (`config.extra = 4`) | `game.lua:481`, `card.lua:972` |
| **Rocket** | U / $6 | **+$1 at end of round, +$2 each boss defeated** (`dollars=1, increase=2`) | `game.lua:464`, `card.lua:1074` |
| **Bull** | U / $6 | **+2 chips per $1 held** (`extra = 2 × dollars`) — scaling chips off money | `game.lua:485`, `card.lua:1099` |
| **Bootstraps** | U / $7 | **+2 mult per $5 held** (`mult=2 per dollars=5`) | `game.lua:540`, `card.lua:1129` |
| **Cloud 9** | U / $7 | **+$1 per 9 in deck at end of round** (`extra = 1 × nine_tally`) | `game.lua:463`, `card.lua:1073` |
| **To the Moon** | U / $5 | **interest_amount +1** ($2 per $5 interest) | `game.lua:475`, `card.lua:808-810` |
| **Gift Card** | U / $6 | **+$1 sell value to all jokers/consumables each round** | `game.lua:470`, `card.lua:1090` |
| **Egg** | C / $4 | **+$3 sell value each round** | `game.lua:435`, `card.lua:1011` |
| **Credit Card** | C / $1 | **debt floor: go up to −$20** (`config.extra = 20`; sets `bankrupt_at` deeper) | `game.lua:407` |
| **Mail-In Rebate** | C / $4 | **+$5 per discarded card of the round's rank** (`extra = 5`) | `game.lua:474`, `card.lua:1094` |
| **Delayed Gratification** | C / $4 | **+$2 per discard if no discards used this round** (`extra = 2`) | `game.lua:423`, `card.lua:1001` |
| **Business Card** | C / $4 | **face cards have chance to give $2** (`extra = 2`) | `game.lua:430`, `card.lua:1006` |
| **Faceless Joker** | C / $4 | **+$5 if ≥3 face cards discarded** (`dollars=5, faces=3`) | `game.lua:446` |
| **To Do List** | C / $4 | **+$5** (0.4.0: raised from $4) if scored hand matches target (`dollars`) | `game.lua:449`, `CHANGELOG.md:9` |
| **Reserved Parking** | C / $6 | face cards chance to give $1 (`dollars=1, odds=2`) | `game.lua:473` |
| **Matador** | U / $7 | **+$8 if played hand triggers boss ability** (`extra = 8`) | `game.lua:523` |
| **Chaos the Clown** | C / $4 | 1 free reroll/shop (see §1.7) | `game.lua:417` |

**Vouchers** (all base $10, `game.lua:611-643`):

| Voucher | Economy effect | Cite |
|---------|----------------|------|
| Clearance Sale | shop discount **−25%** (`discount_percent`, `extra = 25`) | `game.lua:612` |
| Liquidation | discount **−50%** (`extra = 50`, requires Clearance Sale) | `game.lua:629` |
| Seed Money | interest cap → **$10** (`extra = 50`) | `game.lua:621` |
| Money Tree | interest cap → **$20** (`extra = 100`) | `game.lua:638` |
| Reroll Surplus / Glut | base reroll −$2 / −$4 | `game.lua:614,631` |
| Overstock / Overstock Plus | +1 / +2 shop card slots | `game.lua:611,628` |
| Tarot/Planet Merchant + Tycoon | raise tarot/planet appearance rate in shop | `game.lua:619,620,636,637` |
| Director's Cut / Retcon | reroll boss blind ($10 each reroll) | `game.lua:625,642` |

**Tags** (`game.lua:236-257`):

| Tag | Economy effect | Cite |
|-----|----------------|------|
| **Investment Tag** | **+$25 after defeating next boss** (`type='eval', dollars=25`) | `game.lua:240` |
| **Economy Tag** | **doubles your money**, capped at +$40 (`type='immediate', max=40`) | `game.lua:257` |
| **Handy Tag** | **+$1 per hand played this run** (`dollars_per_hand=1`, immediate) | `game.lua:247` |
| **Garbage Tag** | **+$1 per unused discard this run** (`dollars_per_discard=1`) | `game.lua:248` |
| **Skip Tag** | **+$5** immediate (`skip_bonus=5`) | `game.lua:255` |
| **Top-up Tag** | spawns 2 free Common jokers (value, not cash) | `game.lua:254` |
| Foil/Holo/Poly/Negative Tags | next shop joker gets that edition free (saves the edition premium) | `game.lua:236-239` |

### 1.10 BMP-specific economy

**Comeback bonus / "Comeback Gold"** (`lovely/game.toml:22-46`, mirrored in dump
`state_events.lua:1140-1165`): awarded once per round at cash-out, *given on any life
loss* (0.4.0 change — previously only PvP-boss losses; `CHANGELOG.md:21`).

```
sandbox ruleset:        comeback = 3 * (ante - 1)
major-league ruleset:   comeback = 4 * MP.GAME.comeback_bonus   (comeback_bonus increments per life lost)
default:                comeback = 2 if stake >= 6 else 4
```

`MP.GAME.comeback_bonus` increments each time you lose a life (`action_handlers.lua:430-431`);
`comeback_bonus_given` resets per round so it pays at most once per round.

**Nemesis (PvP) blind** (`objects/blinds/nemesis.lua:21`): `dollars = 5`, `mult = 1`
(mult kept at 1 to avoid a crash). Beating/ending the PvP blind pays the $5 reward.
During a Nemesis blind, **leftover-hand money is suppressed** (`is_pvp_boss()` guard,
`state_events.lua:1063`), and the blind reward is always paid even if you "lose" on
chips (`MP.is_pvp_boss()` branch, `:1036`).

**Cash-out timing in PvP**: the cash-out / round-eval runs **after the PvP result is
decided** (our engine: `endPvp()` awards reward+interest, then runs `endOfRound`).
Both players resolve their own cash-out independently; the only cross-player economy
signal is `spentLastShop`.

**Opponent shop-spend tracking** (`lovely/game.toml:129-144`):
- Every time money decreases (`mod < 0` in `ease_dollars`), `MP.GAME.spent_total += -mod`.
- On leaving the shop, the client sends `spentLastShop(spent_total - spent_before_shop)`.
- Server (`BalatroMultiplayerAPI-Server-main/src/actionHandlers.ts:535-542`) simply
  **relays** the amount to the opponent (no validation). Opponent stores it in
  `MP.GAME.enemy.spent_in_shop[]` (`action_handlers.lua:640`).

**Penny Pincher** (`objects/jokers/penny_pincher.lua`, $4 Common): at cash-out, pays
**$1 per $3 your nemesis spent in their last shop** (`floor(spent / nemesis_dollars=3)`,
`config = {dollars=1, nemesis_dollars=3}`). Reads `enemy.spent_in_shop[pincher_index]`.
Unlocks via `MP.GAME.pincher_unlock`.

**Taxes** (`objects/jokers/taxes.lua`, $5 Common): when the Nemesis blind is set,
gains **+4 Mult per joker/consumable the nemesis sold this ante** (`mult_gain = 4 ×
sells_per_ante[ante]`). Pre-PvP, accumulates sells across earlier antes. Nemesis sells
tracked via `sold_joker` action (`game.toml:116-120`).

**Pizza** (`objects/jokers/pizza.lua`, $4 Common): at `mp_end_of_pvp`, **+2 discards
to you, −1 discard to nemesis** (`discards=2, discards_nemesis=1`); then self-destructs
(eaten). Sends a phantom copy to the opponent's view.

**BMP economy mutator layers** (`layers/economy_mutators.lua`) — pure data knobs set
on `G.GAME.modifiers` at run start, deterministic across both clients:

| Layer | Modifier | Effect |
|-------|----------|--------|
| `inflation` | `inflation = true` | shop prices +$1 per purchase, compounding |
| `no_interest` | `no_interest = true` | no interest paid |
| `discard_tax` | `discard_cost = 1` | each discard costs $1 |
| `frugal` | `money_per_discard = 1` | leftover discards pay $1 each |
| `spartan` | `no_blind_reward = {Small, Big}` | Small/Big blinds pay $0 (Boss/PvP intact) |
| `pricey_packs` | `booster_ante_scaling = true` | packs cost +$1 per ante into run |

**0.3.x → 0.4.0 economy deltas** (`CHANGELOG.md:3-21`):
- To Do List payout **$4 → $5**, target hand chosen from all hands.
- Golden Ticket payout reverted **$3 → $4**.
- Gold Card enhancement payout **$3 → $4**.
- Ouija & Ectoplasm cost fixed to **$4**.
- Comeback Gold now awarded on **any** life loss (payouts $4 / $2-on-high-stake unchanged).
- Speedrun out of rotation; Justice consumable back in rotation.

---

## 2. How OUR engine does it today

Files: `engine/game/Run.java`, `engine/game/Shop.java`, `engine/game/GameEvents.java`,
`engine/game/Blinds.java`, `engine/game/BossCatalog.java`, `engine/state/Ruleset.java`,
`engine/state/RulesetCatalog.java`.

| Rule | Our implementation | Cite |
|------|--------------------|------|
| Starting money | `state.money = ruleset.startingMoney()` = **$4** for Standard/Blitz/Marathon | `Run.java:67`, `RulesetCatalog.java:23-25` |
| Interest | `interest = min(5, money/5)` — $1 per $5, cap $5. **Hardcoded constants** | `Run.java:165,174` |
| Blind rewards | Small $3 / Big $4 / Boss $5 (enum constants) | `Blinds.java:16-19` |
| Boss reward | `boss.reward()` (Nemesis = $5) | `Run.java:175`, `Run.java:40-41` |
| Nemesis (PvP) reward | $5 + interest, then `endOfRound` | `Run.java:162-169` |
| Gold-card payout | **+$3 per Gold card held** at end of round | `GameEvents.java:55-57` |
| Joker dollar payouts | `applyEconomy` adds `effect.dollars` to money (END_OF_ROUND trigger) | `GameEvents.java:73-79` |
| Shop joker cost | `item.cost()` = joker's `info.cost()` (per-joker base) | `Shop.java:30`, `Run.java:187-189` |
| Reroll cost | **flat `REROLL_COST = 5`** (no scaling, no increase) | `Shop.java:23`, `Run.java:196-201` |
| Planet purchase | `PlanetCatalog.COST` flat | `Run.java:209-210` |
| Joker slot limit | `JOKER_SLOT_LIMIT = 5` | `Shop.java:24` |
| Buy guard | `money < cost → "not enough money"` (no debt allowed) | `Run.java:187,198,209` |

**Not implemented at all:** sell mechanic (no sell value, no `SELL_CARD`/`SELL_SELF`
economy path beyond the trigger enum existing in `Trigger.java:34-35`); leftover-hand /
leftover-discard cash; comeback bonus; interest-cap vouchers; edition price premiums;
consumable/voucher/booster shop pricing tiers; reroll-cost scaling and reroll vouchers;
discount vouchers; inflation; economy tags; the BMP nemesis-spend jokers
(Penny Pincher / Taxes / Pizza); `spentLastShop` tracking on our side; Credit Card debt
floor; Oracle money cap.

---

## 3. The gap

| Area | Vanilla / BMP | Ours | Gap |
|------|---------------|------|-----|
| Starting money per deck | $4 default, Yellow $14, deck-specific | flat $4 from ruleset | ❌ no per-deck money |
| Interest cap configurable | `interest_cap`/`interest_amount` modifiable by vouchers/jokers | hardcoded $5 cap, rate $1/$5 | ❌ Seed Money / Money Tree / To the Moon / Green Deck no_interest |
| Blind rewards | $3/$4/$5, finishers $8, `no_blind_reward` mutator | $3/$4/$5 | ⚠️ no finisher $8, no `spartan` layer |
| Leftover hands/discards | $1/hand (default), $0/discard (deck/voucher-gated) | none | ❌ missing entirely |
| Sell values | `max(1, floor(cost/2)) + extra_value` | none | ❌ no selling |
| Shop pricing | base + inflation + edition premium + discount% | per-joker base only | ❌ no editions/discounts/inflation/consumable&voucher&booster tiers |
| Reroll cost | $5 base + $1/reroll, voucher/Chaos mods | flat $5 | ❌ no scaling, no vouchers, no free rerolls |
| Cash-out ordering | 10-step ordered eval | sum of reward+interest+jokers+gold | ⚠️ no ordered cash-out, no comeback row |
| Comeback bonus (BMP) | per-life-loss gold, ruleset-scaled | none | ❌ missing |
| Penny Pincher / Taxes / Pizza | nemesis-spend/sell/discard mechanics | none | ❌ missing |
| `spentLastShop` | client→server→opponent relay | none | ❌ missing |
| Credit Card debt floor | money can go to −$20 | hard $0 floor | ❌ missing |
| Economy jokers (Golden/Rocket/Bull/Bootstraps/Cloud9/etc.) | full set | only generic `effect.dollars` path | ⚠️ depends on joker library coverage |
| Economy tags (Investment/Economy/Handy/Garbage/Skip) | full set | none | ❌ missing |
| Oracle money cap (BMP) | cap $75 on positive mods | none | ❌ missing |

---

## 4. Proposed target design

### 4.1 Centralize money mutation

Introduce a single authoritative `RunState.addMoney(int delta, String source)` that:
- clamps the **lower bound** to `-bankruptFloor` (default 0; Credit Card → 20),
- clamps the **upper bound** to `oracleMax + interestCap` *only for positive deltas* when
  the Oracle cap modifier is active,
- on negative deltas, increments `spentTotal` (mirrors `game.toml:140-142`).

All purchases, rerolls, payouts, and sells route through it. This makes the
`spentLastShop` value and debt floor fall out naturally.

### 4.2 Make economy constants ruleset/modifier-driven

Replace the hardcoded `min(5, money/5)` and `REROLL_COST = 5` with `RunState` fields
seeded from the deck + active modifiers, mirroring vanilla:

```
interestAmount   (default 1)         // To the Moon +1
interestCap      (default 25)        // Seed Money 50, Money Tree 100
noInterest       (default false)     // Green Deck, no_interest layer
baseRerollCost   (default 5)         // Reroll Surplus -2, Glut -4
rerollIncrease   (per-round, +1/reroll, reset each round)
freeRerolls      (per-round; Chaos the Clown sets 1)
moneyPerHand     (default 1)
moneyPerDiscard  (default 0)         // Green Deck/Recyclomancy/frugal → 1
discardCost      (default 0)         // discard_tax → 1
discountPercent  (default 0)         // Clearance 25, Liquidation 50
inflation        (default 0; +1 per purchase if inflation modifier)
bankruptFloor    (default 0; Credit Card 20)
oracleMax        (default null; Oracle 50)
noBlindReward    (per blind type; spartan layer)
```

Interest at cash-out:
`noInterest ? 0 : interestAmount * min(floor(money/5), interestCap/5)`.

Reroll cost: `freeRerolls>0 ? 0 : baseRerollCost + rerollIncrease`, incrementing
`rerollIncrease` on each paid reroll; reset `rerollIncrease`/`freeRerolls` each round.

### 4.3 Ordered cash-out evaluator

Add a `RunState.evaluateRound()` that produces an **ordered** list of cash-out rows
matching §1.8: blind reward → hands → discards → joker `$bonus` → tag bonuses →
interest → comeback (BMP). Emit each as a `ReplayEntry` so the client can animate the
cash-out screen deterministically and the parity harness can diff row-by-row.

### 4.4 Sell mechanic

Add `Card.sellCost = max(1, floor(cost/2)) + extraValue` and an `Intent.SELL_JOKER`/
`SELL_CONSUMABLE` handled server-side: validate ownership, `addMoney(+sellCost)`, raise
`SELL_SELF`/`SELL_CARD` triggers, broadcast `soldJoker` (for opponent Taxes), remove the
card. Egg increments `extraValue` by 3/round; Gift Card +1 to all per round.

### 4.5 Shop pricing model

Compute `cost = max(1, floor((baseCost + inflation + editionPremium + 0.5) * (100 -
discountPercent)/100))`, with edition premiums Foil $2 / Holo $3 / Poly $5 / Negative $5.
Add consumable ($3), voucher ($10), and booster ($4/$6/$8, +ante if `pricey_packs`)
shop slots with their own cost tiers. Inflation increments on each purchase.

### 4.6 BMP nemesis economy

- Track `enemy.spentInShop[]`, `enemy.sellsPerAnte[]`, `pincherIndex` in `Match`/`Run`,
  fed by the relayed `spentLastShop` / `soldJoker` actions.
- Implement Penny Pincher (cash-out: `floor(spent/3)`), Taxes (mult on PvP set), Pizza
  (discard swap at end of PvP) as jokers reading that state.
- Implement comeback bonus at cash-out keyed off the match's per-player life-loss count,
  ruleset-scaled (sandbox `3*(ante-1)`, major-league `4*lossCount`, default `stake>=6 ? 2 : 4`).

### 4.7 Economy tags & vouchers

Wire Investment ($25 post-boss), Economy (double money, cap +$40), Handy/Garbage
(per-hand/discard run totals), Skip (+$5); and the interest/reroll/discount vouchers
into the modifier fields from §4.2.

---

## Open questions

1. **Exact consumable shop prices.** Vanilla Tarot/Planet/Spectral shop slot prices were
   inferred as $3; only Immolate ($4 spectral, `game.lua:599`) was directly verified.
   Need the actual `cost` of the shop's consumable slot offerings (❓).
2. **Booster pack base costs** ($4/$6/$8 Normal/Jumbo/Mega) were not located in the dump
   we searched — confirm against the booster pack definitions (❓).
3. **Stake-scaled blind rewards / comeback**: the default comeback uses `stake >= 6 ? 2 : 4`.
   Do we expose stake in our `RunState`? Currently rulesets don't carry a stake value.
4. **Oracle cap interaction with interest**: cap is `oracleMax + interestCap` and applied
   per positive `ease_dollars` mod — does interest itself get capped, or is it applied
   in one lump that the cap then clamps? (`decks.toml:17-20` wraps the generic mod path.)
5. **`mp_modified_interest_rate`** — which BMP ruleset/layer actually sets this? It exists
   in `state_events.lua:1167` but no layer in `economy_mutators.lua` sets it. Possibly a
   ruleset field or challenge (❓).
6. **Per-deck starting money for BMP decks** (Echo, Cocktail, Gradient, Heidelberg) — only
   Oracle (cap) and the vanilla-derived decks were verified; the rest need a config sweep.
7. **Credit Card `bankrupt_at`** — exact floor math (`bankrupt_at` vs `extra = 20`) and
   whether multiple Credit Cards stack the debt floor (❓; not fully traced in `card.lua`).
8. **Joker rarity → cost**: vanilla stores cost per-joker, not by rarity. Do we want a
   rarity-default fallback for custom/modded jokers that omit `cost`?

## New building blocks needed

1. **`RunState.addMoney(delta, source)`** — single clamped money mutator (debt floor,
   Oracle cap, spent-total tracking). Replaces every raw `state.money +=`/`-=`.
2. **Economy modifier fields on `RunState`** (§4.2): `interestAmount`, `interestCap`,
   `noInterest`, `baseRerollCost`, `rerollIncrease`, `freeRerolls`, `moneyPerHand`,
   `moneyPerDiscard`, `discardCost`, `discountPercent`, `inflation`, `bankruptFloor`,
   `oracleMax`, `noBlindReward[]`. Seeded from deck + ruleset + redeemed vouchers + layers.
3. **`Run.evaluateRound()` ordered cash-out** producing `ReplayEntry` rows in vanilla order.
4. **Sell path**: `Card.sellCost`, `extraValue`, `Intent.SELL_*`, `SELL_SELF`/`SELL_CARD`
   trigger raising, and a `soldJoker` broadcast.
5. **`Shop` pricing model**: edition premiums, discount %, inflation, plus consumable /
   voucher / booster slots with their own cost tiers and reroll-cost scaling state.
6. **Voucher system** (at least the economy vouchers): Clearance Sale, Liquidation,
   Seed Money, Money Tree, Reroll Surplus, Reroll Glut, Overstock — each writing into the
   §4.2 fields on redeem.
7. **Economy tags**: Investment, Economy, Handy, Garbage, Skip — and a tag-eval hook in
   the cash-out evaluator.
8. **BMP nemesis-economy state**: `Match`/`Run` tracking of `enemy.spentInShop[]`,
   `enemy.sellsPerAnte[]`, `pincherIndex`, plus relayed `spentLastShop` / `soldJoker`
   action handling; jokers Penny Pincher, Taxes, Pizza.
9. **Comeback-bonus hook** keyed to per-player life-loss count, ruleset-scaled.
10. **Credit Card debt floor** and **Oracle money cap** as deck/joker-driven bounds on
    the `addMoney` clamp.
