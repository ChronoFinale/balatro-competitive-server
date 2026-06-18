# 41 — Game variables & modifiers (the unified read/write vocabulary)

> Status: the per-blind **resource** layer is fully on this model. Scoring (chips/mult/dollars) and
> the shop-economy/slot layers are noted as the remaining frontier.

## The idea

The game has a set of **variables** — its nouns / resources / state: money, hands per round,
discards, hand size, ante, joker slots, the blind requirement, and so on. A card (joker, boss, deck,
voucher) does not do a fundamentally different *kind* of thing from any other card. They all:

- **read** variables — that is a **`Condition`** (`money >= 5`, `+2 mult per $5 you have`), and
- **write** variables — that is a **`Modify`** (`add(MONEY, 4)`, `add(HAND_SIZE, 1)`, `set(HANDS_LEFT, 1)`).

Same nouns, opposite verbs. The variables are one vocabulary: **`Value.Var`** (in
`com.balatro.engine.joker.def`). Conditions read it via `Value.readVar(...)`; modifiers write it via
`Modify`. There is no separate "aspect" enum — the writable resources are a subset of the variables
conditions already name.

```
Condition  : read(variable)  compared/tested        money >= 5
Modify     : write(variable) add | set | multiply   add(MONEY, 4)
```

## `Modify`

```java
record Modify(Value.Var variable, Op op, double value) { enum Op { ADD, SET, MULTIPLY } }
```

`Modify.fold(base, variable, mods)` resolves one variable from a flat list of modifiers in
op-priority order — **SET** (last wins; a boss override beats the ruleset default), then **ADD**
(accumulate), then **MULTIPLY** (scale). Order-independent within an op, so the *source* order never
matters.

## Every card contributes through `mods()`

| Source | how it carries its resource changes |
|---|---|
| `RunMod` (joker) | `statMods` — `RunMod.stats(add(HAND_SIZE, 1))` (Juggler), `stats(true, add(HANDS_LEFT,3))` (Burglar) |
| `BossBlind` | `List<Modify> mods` — `set(HANDS_LEFT,1)` (Needle), `add(HAND_SIZE,-1)` (Manacle) |
| `DeckType` | `resourceMods` — `add(HANDS_LEFT,1)` (Blue), `add(MONEY,10)` (Yellow) |
| `VoucherCatalog.Voucher` | `mods` — `add(HANDS_LEFT,1)` (Grabber), `add(HAND_SIZE,1)` (Paint Brush) |

The field `handSizeDelta` used to exist on `RunMod` **and** `BossBlind` **and** `DeckType`, each with
its own application code in `Run`. That cross-record duplication is gone — it exists on none of them.

## One gather, three folds (`Run`)

`Run.resourceMods()` flattens every source's `mods()` (plus the dynamic ones — Turtle Bean's decaying
bonus, Skip-Off, Pizza) into one list. `applyResourceMods()` folds it three times:

```java
handsLeft    = max(1, fold(ruleset.hands(),    HANDS_LEFT,    mods))
discardsLeft = fold(baseDiscards(),            DISCARDS_LEFT, mods); if (Burglar) 0   // final override
handSize     = max(1, fold(ruleset.handSize(), HAND_SIZE,     mods))
```

No source is special-cased; `Run` has no `if (joker == "j_juggler")` / `vouchers.contains("v_grabber")`
for resources. `MONEY` is folded once at run init (Yellow Deck); the per-blind folds ignore it (the
fold filters by variable, so a `MONEY` modifier in a deck's list is simply not seen by the `HANDS_LEFT`
fold).

`CONSUMABLE_SLOTS` is on the model too, but as a **derived recompute** rather than a per-blind fold:
`recomputeConsumableSlots()` = `fold(2, CONSUMABLE_SLOTS, deck.mods() + owned voucher mods)`, run at
init and on each voucher grant. It's a pure function of what you own (deck + Crystal Ball/Omen Globe),
so it's idempotent — no incremental `+=`.

## `MAX` / `MIN` — tiered & "best wins" modifiers

`fold` order is **SET → ADD → MULTIPLY → MAX → MIN**. `MAX`/`MIN` express *tiered* and *best-wins*
effects without key-strings: the **highest** tier owned wins a `MAX`, the **deepest** wins a `MIN`.

```
Seed Money  -> max(INTEREST_CAP, 10)     Money Tree -> max(INTEREST_CAP, 20)   // own both -> 20, not 30
Overstock   -> max(SHOP_SLOTS, 3)        Overstock+ -> max(SHOP_SLOTS, 4)
Clearance   -> min(PRICE_MULTIPLIER,.75) Liquidation-> min(PRICE_MULTIPLIER,.5)
```

This is order-independent (unlike last-`SET`-wins) and needs no "which tier do I own" check — the
voucher just carries `max/min(value)` and `fold` does the rest. `Value.Var` gained the policy
variables (`INTEREST_CAP`, `SHOP_SLOTS`, `PRICE_MULTIPLIER`, `REROLL_DISCOUNT`, `EDITION/POLY_MULTIPLIER`,
`MONEY_PER_HAND/DISCARD`, `MIN_MONEY`) as **write-targets** — folded by the derived configs, not yet
read by any condition (`readVar` throws for them until one needs reading).

## Sibling: derived configs for things that aren't per-blind folds

Some state isn't reset-and-recomputed each blind; it's a pure function of *what you own*, resolved on
demand. These use the same "derived, no mutation" pattern, not the fold:

- **`EconomyConfig`** — round money: per-hand/discard payout, interest cap, debt floor (Credit Card),
  To the Moon. `resolve(deck, vouchers, jokers)`.
- **`ShopConfig`** — shop rules from owned jokers: Showman (dups), Astronomer (free planets), Chaos
  (free reroll).
- **`ShopEconomy`** — shop economy from owned vouchers: Overstock (slots), Clearance/Liquidation
  (price), Reroll Surplus/Glut (reroll cost), Hone/Glow Up (edition odds). **`resolve()` is now a
  `fold` over the owned vouchers' `Modify` data — zero key-strings.**

`ShopEconomy.resolve` and `EconomyConfig`'s interest cap fold the vouchers' own `Modify`s, so a new
voucher adds a shop/economy effect by carrying data, not by editing `resolve()`. Still key-string in
`resolve()` (a deliberate not-yet): the *deck*-driven economy (Green's payout rates / no-interest) and
*joker*-driven bits (Credit Card's debt floor; To the Moon's **uncapped interest**, which is gated by
`noInterest` so it can't become a clean standalone end-of-round Rule without an "interest-enabled"
condition the deck doesn't currently expose).

## Frontier (not yet on the model)

1. **Scoring** — chips / mult / dollars are *also* writes to game variables (during scoring). A
   joker's `add(MULT, 3)` is the same shape as `add(HAND_SIZE, 1)`, just on a transient per-hand
   variable. Unifying `EffectTemplate` with `Modify` is the deep prize, but the scoring engine's
   accumulator + ordering + replay make it a large, careful refactor — not a rename.
2. **Joker slots** — derivable from owned (deck + Antimatter + negative jokers) but *incrementally
   mutated* on edition change (a negative joker grants a slot), and the recompute would need `deck`
   (on `Run`) at every joker-mutation point, so it's a behaviour change with many touch-points. Left
   incremental. (Consumable slots — no negative-joker entanglement — are already derived; see above.)
3. **`BLIND_REQUIREMENT`** — boss `reqMult` × deck `blindSizeMult`. Foldable, but the requirement
   isn't a `RunState` field conditions read (it lives on `Run`), so adding it to the read vocabulary
   would break the read/write symmetry; and folding `reqMult` into `boss.mods()` makes every boss's
   default ×2 register as an "ability" (Matador). Left as fields for now.

## Why this matters

The same realization that made **conditions** one shared vocabulary now applies to what they act on:
one set of game variables, read by conditions and written by modifiers, with jokers / bosses / decks /
vouchers all being lists of `(trigger, condition, modify)` over it. A boss's "−1 hand size" and a
joker's "+1 hand size" are the same sentence pointed at the same variable — which is exactly what a
*card-design language* should make true.
