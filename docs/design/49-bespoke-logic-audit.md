# 49 — Bespoke-logic audit: where the DSL stops, and the one root cause

A sweep for every place the engine falls back to imperative code instead of the data vocabulary. The finding:
**it's not scattered randomly — it has one root cause.** The vocabulary only runs during *scoring*; anything
that happens in the *run loop* has no interpreter, so it's hand-coded.

## What's already clean (the DSL works)

- **Jokers** — 33 `Trigger`s · 37 `Condition`s · 23 `Effect`s, one interpreter (`ScoringEngine`). Add a joker =
  compose data, no code.
- **Consumables** — literally `List<Effect>`, same vocabulary.
- **The `Var`/`Modify` system** — 58 shared game-variables (`HANDS_LEFT`, `SHOP_SLOTS`, `PRICE_MULTIPLIER`, …).
  Vouchers, deck resource-mods, and boss resource-mods all fold `Modify`s onto these Vars; the engine reads the
  folded value. This is a clean data abstraction — vouchers are *fully* data.
- **`EvaluationContext`** — 27 fields; the read side is rich, not the problem.

## What's bespoke — and the ROOT CAUSE

**The vocabulary's interpreter only fires during scoring.** Everything that happens elsewhere in the run loop
(blind-start, per-hand-played, draw, on-sell, end-of-round, blind-select) has no rule engine, so it's a field +
a builder method + a scattered `if`. Every offender below is the same shape:

| subsystem | bespoke surface | the run-loop moments it needs |
|---|---|---|
| **Bosses** | 18 fields, **36 sites in `Run`** | per-hand (Tooth/Ox/Arm/Hook), draw (Mark/House/Fish/Wheel/Serpent), blind-start (Acorn/Bell), on-sell (Leaf) |
| **`RunMod`** (joker run-hooks) | 10 bespoke capabilities | blind-select (Madness eats a joker), on-sell, end-of-round, shop-exit, prob-doubling |
| **Decks** | `greenEconomy`, `blindSizeMult`, `onBossDefeatTags` | end-of-round economy, on-boss-defeat |
| **Stakes** | `scaling`, `discardDelta`, sticker flags | blind-scaling, per-round |

These aren't four separate problems. They're one: **no run-level rule interpreter.**

## What's genuinely structural (leave as code — NOT vocabulary)

The line is narrower than it first looks. **Deck composition is NOT an exception** — Erratic ("randomize each
card"), Checkered ("set suit by color"), Abandoned ("remove faces") are exactly the card-mutation effects tarots
already use (`MutateCard`/`DestroyTargets`/`Generate`), just fired at the *earliest* trigger — deck-build /
run-start — over an `ALL_DECK` selector. `deck = fold(buildEffects, standard52)`. "Runs once at construction"
≠ "isn't an effect" (consumables run once and mutate cards too). So deck composition is *more* evidence for the
unification, not against it.

The only things that genuinely can't be vocabulary are **the machine**:
- the **interpreter** (the code that reads an `Effect` and performs it),
- the **RNG streams**, and
- the **scoring arithmetic** (the chips × mult loop).

Expressing *those* as data would mean a Turing-complete data language — the exact thing we refuse, because it
re-introduces shipped code and breaks serialization / codegen / server-authority (that's Balatro's Lua model).

Gray-zone, tier-2 (a value/flag the engine reads, not a transform): `balanceChipsMult` (Plasma branches the
score math), `blindSizeMult`, `discardDelta` — these are knobs, closer to `Var`s than to effects.
Mode `Capabilities` (`restrictedPools`, `idolDeckPosition`) are mode config, not content.

## The one fix that absorbs most of it

**Extend the effect interpreter from scoring-only to the whole run loop.** A run-level rule dispatcher that, at
each run-loop moment (blind-start, after-hand, on-draw, on-sell, end-of-round, blind-select), evaluates the
relevant rules — from the boss, from each joker's `RunMod`, from the deck — through the *same* effect applier
the scorer and consumables already use. Then:

- A boss is a `List<Rule>` (a "joker" the blind owns). RunMod jokers become rules. Deck-on-boss-defeat is a rule.
- The 36 boss sites + 10 RunMod capabilities + the deck/stake run-loop bits collapse into ~6 trigger dispatch
  points + a handful of new `Effect`s (money-per-card, delevel-played-hand, face-down-draw, …) — each
  implemented once, then reusable by *any* content (a joker could use a boss effect, and vice-versa).

That's the move that makes the DSL *whole* — "jokers, plus a pile of run-loop code next to them" becomes "one
language, fired everywhere." Sequencing: per-hand cluster → draw-time → blind-start/on-sell → fold RunMod in.
Test-guarded, one slice at a time, since it touches `Run`'s core loop.
