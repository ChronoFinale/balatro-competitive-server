# 42 — The instruction set (effects, selectors, and where the line is)

> Status: **foundation spec.** Completes the read/write unification of doc 41 (variables) and replaces
> the "effect-op" sketch of doc 40. This is the *model* the content layer is built to. It also draws an
> explicit line: **data for content, code for the engine** — and documents the deepest generalizations
> as understood-but-deliberately-not-built.

## The grammar (the whole game)

Every piece of Balatro content is a **Source** — a joker, a card, a held planet, a boss, a deck, a
voucher, a tag, a hand-type definition. There is **no privileged source.** A source contributes:

- **Rules** — `(Trigger, Condition, [Effect])` — "at this moment, if this holds, do these."
- **Standing modifiers** — effects with a *while-present* trigger, resolved by folding (the doc-41 model).

The engine, at each moment, gathers rules from every active source matching the trigger and runs their
effects in order. It never asks "is this a joker?"

## The collapse: there is basically one effect

The move that makes the model small: the running **chips / mult** of a hand are just **variables**
(transient, reset each hand). So a "score contribution" is a variable write, and once you see that, most
effects are the same instruction:

```
joker "+4 Mult"        Add( scoring.mult, 4 )
Bonus card "+30 Chips"  Add( scoring.chips, 30 )
Polychrome "x1.5"       Mul( scoring.mult, 1.5 )
Flint "halve base"      Mul( scoring.chips, 0.5 ); Mul( scoring.mult, 0.5 )
Juggler "+1 hand size"  Add( run.handSize, 1 )           (standing → folded)
Burglar "no discards"   Set( run.discards, 0 )
Four Fingers            Set( hands[flush].size, 4 ); Set( hands[straight].length, 4 )
Pluto                   Add( hands[highCard].level, 1 )
Hiker "+5 chips a card" Add( card[focus].chips, 5 )       ("mutate a card" is a slot write)
Midas                   Set( card[focus].enhancement, GOLD )
final score             scoring.chips x scoring.mult
```

`Score` was never its own thing; neither was `MutateCard`. They are **`Modify(selector.slot, value)`**.

## The primitives

Eight, orthogonal:

1. **Entity** — a thing with slots: `run`, a `card`, a `joker`, a hand-type **definition**, `scoring`,
   `shop`, `boss`.
2. **Slot** — a named, **typed** property of an entity. Numeric (`chips`, `money`, `size`, `level`) or
   categorical (`suit`, `enhancement`). *This is what "variable" actually is — qualified by its owner.*
3. **Registry** — a named catalog of definitions (the hand types; also the joker pool, vouchers, …).
   Registry entries are entities you can select and modify (that is the "somewhere" Four Fingers needs).
4. **Value** — how to compute a number: literal · read `selector.slot` · `count(Selector)` · scale
   (`base + per·X`) · random. *(have it: `Value`)*
5. **Condition** — a boolean over Values/slots; and/or/not; plus the set-shape predicates below. *(have it)*
6. **Selector** — which entities a thing refers to (see vocabulary below). *(the genuinely new primitive)*
7. **Effect** — the verbs. Score folded into the first, so the list is tiny:
   - `Modify(Selector.slot, Op, Value)` — resources, **score**, hand-eval knobs, card stats, joker state
   - `Create(spec)` · `Destroy(Selector)` · `Copy(Selector → dest)` — change the *set* of entities
   - `Retrigger(Selector, n)` — the one control-flow verb
8. **Rule** = `(Trigger, Condition, [Effect])`; a **Source** = identity + `[Rule]` + its state.

### The `Selector` vocabulary (closed set)

The "what does this act on" that kept reappearing as `Target`/`Scope`/hardcoded keys. One vocabulary,
reused by `Modify`/`Destroy`/`Copy`/`Create`:

```
focus            the entity this moment is about (the scored card, the discarded set, the sold joker)
self             this source
run | scoring | shop | boss      the singleton entities
selected         player-chosen cards (consumables)
cardsMatching(Condition)         e.g. held Kings, scoring Hearts
jokers(which)    self | others | neighbor | random | all
registry[key]    a definition, e.g. hands[flush]
randomInHand(n)
```

Cardinality and existence of `focus` come from the **Trigger** (no `focus` on a whole-hand moment).

### The set-shape predicate library (engine primitives, parameters are data)

A poker hand is **not** a primitive — it is a registry row whose `match` is built from two predicates:

```
groupSizes(attribute) ⊇ pattern     pair {2} · two pair {2,2} · full house {3,2} · flush {size}
isRun(length, maxGap)               straights
```

over a **resolved** attribute (Wild → every suit-group, Smeared → merged, Stone → none; Pareidolia →
all face). The same library powers jokers (Flower Pot = `distinctCount(suit)==4`). Hand definitions
live in the registry; their thresholds (`size`, `length`, `gap`) are **slots** modifiers can write.

## The two structural facts (not "just effects")

1. **Per-variable resolution mode.** Many effects hit one slot — how do they combine?
   - **sequential** — transient scoring slots (`scoring.chips/mult`): apply in pipeline order; order matters.
   - **fold** — persistent slots (`run.handSize`, caps): order-independent by op precedence
     `SET → ADD → MUL → MAX → MIN` (the existing `Modify.fold`).
   The **slot declares** its mode. Same `Modify` instruction.
2. **Trigger → focus.** The moment sets the focus entity (and whether one exists), which then constrains
   which effects are even legal (`Modify(card[focus]…)` is meaningless on a whole-hand moment).

## THE LINE — data for content, code for the engine

This is the most important section. Pushing "everything is `Modify` data" *into the engine* builds an
inner platform: a slow, hard-to-debug interpreter re-implementing programming. The heuristic that keeps
us out of it is **count the instances**:

- **Many instances of a pattern → data.** Jokers (~150), consumables (~40), vouchers (32), tags, seals,
  enhancements, editions. These are content. Express them as `Rule`/`Effect` data over the primitives.
- **One fixed mechanic → code.** The scorer, the hand evaluator, the fold resolver. 12 hand types and
  one pipeline, forever. This is the **interpreter**, and an interpreter being *code is correct.*

### Built (the content altitude)

- The variable/slot space — **including** the hand-eval knobs (`hands[flush].size`, `STRAIGHT_GAP`) and
  the scoring accumulators — so modifiers and score are uniform.
- **One `Effect` vocabulary** (`Modify`/`Create`/`Destroy`/`Copy`/`Retrigger`) over a `Selector`, shared
  across jokers, consumables, vouchers, decks, bosses.
- `Rule(trigger, condition, [Effect])` — sealed `Effect`, no `EffectTemplate` null-bag, no separate
  `Mutation` type (state writes are `Modify(self.state.X, …)`).
- Helpers on top: the fluent builder, the catalogs, families, coverage nets, JSON schema, preview mirror.

### Deliberately NOT built (the deep turtles — understood, not implemented)

- **Data-driven hand evaluator.** The hand *registry rows + thresholds* are data (so Four Fingers is a
  `Modify`); the *match interpreter* (`groupSizes`/`isRun`) stays code. We do **not** turn the evaluator
  into interpreted match predicates — its entire payoff would be ~2 jokers, against a bit-exact,
  golden-fixture-tested core.
- **Generic-interpreted scoring spine.** The scorer stays direct arithmetic that *gathers* effect
  contributions and reads modifiable knobs. We do **not** route every base-chip through a `Modify`
  interpreter in the hot path (hands score thousands of times in tests/autoplay).
- **Enhancements/editions/seals as data rules.** Conceptually they *are* `ON_SCORED → Modify(scoring…)`;
  pragmatically they stay fast code contributing to the same scoring slots. Revisit only if a content
  reason (not purity) appears.

> Rule of thumb: **make the knob a variable, not the mechanic a program.** Four Fingers needs
> `hands[flush].size` to be a readable slot — it does **not** need the flush *checker* to be data.

## What this replaces

`EffectTemplate` (the `(Op, Value, extra, CardMod, CreateSpec)` null-bag), `Mutation` (now
`Modify(self.state…)`), `Consumable.Effect`'s parallel vocabulary, and the per-`Target`/`Scope` enums —
all become `Rule(trigger, condition, [Effect])` + the one `Selector`. `JokerEffect` (runtime
accumulator) stays; preview still mirrors it.

## Bounded next build (the only thing to actually code next)

1. Introduce sealed **`Effect`** + the **`Selector`** vocabulary; write the interpreter (`apply`).
2. Re-point **`Rule`** to `[Effect]`; migrate the builder terminals (they already speak this).
3. Fold **`Mutation`** into `Modify(self.state…)`; merge `rules`+`mutations`; delete `Mutation`.
4. Delete `EffectTemplate`; update JSON `@JsonSubTypes` + `preview.js` + round-trip/fixture tests.
5. Unify `Consumable.Effect` onto the same `Effect`+`Selector` (selected-target instead of focus).

Each stage its own green commit; verify against the golden scoring fixtures throughout. The hand
evaluator and scoring arithmetic are **out of scope** by the line above — they stay code, exposing slots.

## Open items (small, decide at stage 1)

- **Accumulate vs first-match** for multiple rules on one trigger — audit for any joker with ≥2 rules on
  the same trigger; default to *accumulate* unless one breaks.
- **Clean JSON break** (no external rulesets ship yet) — no back-compat deserializer.
- **`RunMod` capability bag** — the residual: capabilities that aren't `Modify` (disablesBoss, survives,
  sell-hooks). Out of scope here; its positional-bag clumsiness is the *next* cleanup, not this one.
