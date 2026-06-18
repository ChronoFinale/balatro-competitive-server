# 42 — Effects: the Rule/Action model (v3)

> Status: **proposed.** Refines the "effect-op" design of doc 40 and completes the read/write
> unification of doc 41. The read side (`Condition`) and the standing-write side (`Modify`/`Value.Var`)
> are already sealed/clean; this brings the **triggered-effect** side up to the same bar.

## The problem (what's stupid today)

A data joker carries two parallel triggered concepts plus a god-record:

```java
record Rule(Trigger when, Condition condition, EffectTemplate effect)
record Mutation(Trigger when, Condition condition, String var, Op op, double by, Condition perCard, Scope scope)
record EffectTemplate(Op op, Value value, EffectTemplate extra, CardMod cardMod, CreateSpec create)
enum EffectTemplate.Op { CHIPS, MULT, XMULT, POW_MULT, DOLLARS, REPETITIONS, HELD_MULT,
                         MUTATE_CARD, CREATE, DESTROY_SCORED, DESTROY_DISCARDED, LEVEL_UP_HAND, COPY_SCORED }
```

Three concrete smells:

1. **`EffectTemplate` is a tagged union faked with nulls.** A 12-arm `Op` enum decides which of
   `{value, extra, cardMod, create}` is meaningful; at any use ~3 are null. `MUTATE_CARD` isn't an
   *operator*, it's a *kind of effect*. This is the exact shape we deleted for `Condition`.
2. **`Rule` and `Mutation` are the same shape** — `(trigger, condition, <action>)` — kept in two
   lists and evaluated in two loops in `DataJoker`. The only difference is the action *kind*.
3. **Three overlapping `Op` enums** — `EffectTemplate.Op`, `Mutation.Op {ADD,SET,RESET}`,
   `Modify.Op {ADD,SET,MULTIPLY,MAX,MIN}` — one of which (`EffectTemplate.Op`) is really a kind-tag.

## Principle

> Every triggered effect is **`Rule(trigger, condition, [Action…])`**. `Action` is a closed sum of the
> things a joker can *do*. There is no separate `Mutation` type and no `EffectTemplate` null-bag.

This is the same move that made `Condition` clean (sealed sum, not tag+nulls), applied to the do-side.

## Target model

```java
record Rule(Trigger when, Condition condition, List<Action> actions) {}

sealed interface Action {
    // --- scoring contributions (the numeric algebra) ---
    record Score(ScoreOp op, Value value) implements Action {}     // +chips/+mult/xmult/^mult/+$/retrigger/held-mult
    record Swap()    implements Action {}                          // swap running chips & mult
    record Balance() implements Action {}                          // average chips & mult

    // --- card actions ---
    record MutateCard(CardMod mod) implements Action {}            // Hiker / Midas / Vampire
    record CopyScored()            implements Action {}            // DNA
    record Destroy(Target target)  implements Action {}            // SCORED | DISCARDED

    // --- world actions ---
    record Create(CreateSpec spec) implements Action {}            // 8 Ball / Cartomancer / Riff-Raff / Sixth Sense
    record LevelUpHand(Value levels) implements Action {}          // Space / Burnt

    // --- persistent state (was Mutation) ---
    record MutateState(String var, StateOp op, Value by,           // Square / Ride the Bus / Yorick / Gift Card
                       Condition perCard, Scope scope) implements Action {}

    enum Target { SCORED, DISCARDED }
}

enum ScoreOp { CHIPS, MULT, XMULT, POW_MULT, DOLLARS, REPETITIONS, HELD_MULT }
enum StateOp { ADD, SET, RESET }          // was Mutation.Op
// Modify.Op { ADD, SET, MULTIPLY, MAX, MIN } stays — different domain (the variable fold).
```

**The chain goes away.** `EffectTemplate.extra` existed only to express "do A *and* B in one rule"
(Scholar = +20 chips and +4 mult). That is now just a longer `actions` list:

```java
Rule(ON_SCORED, isAce, [Score(CHIPS, 20), Score(MULT, 4)])
Rule(ON_SCORED, single6, [Destroy(SCORED), Create(spec(SPECTRAL))])   // Sixth Sense
```

### The three Op enums, now justified

| Enum | Domain | Verbs |
|---|---|---|
| `ScoreOp` | a hand's running score | CHIPS, MULT, XMULT, POW_MULT, DOLLARS, REPETITIONS, HELD_MULT |
| `Modify.Op` | a `Value.Var` fold (while-owned) | ADD, SET, MULTIPLY, MAX, MIN |
| `StateOp` | a per-joker state counter | ADD, SET, RESET |

They overlap in name (ADD/SET) but operate on genuinely different things; each is now *only* its own
operators, with no kind-tags mixed in. That's the acceptable end state — three small, single-purpose
enums instead of one overloaded one.

## What does NOT change

- **`JokerEffect` (runtime).** Still the computed accumulator the scoring engine reads
  (chips/mult/…/destroyScored/create/…). `Action.apply(ctx)` populates it. Data→runtime stays a real
  boundary (preview computes a `JokerEffect` without committing). Only the *data* side changes.
- **Standing modifiers** — `JokerDef.mods()` (Value.Var), `handMods` (hand-eval), `runMod`
  (capabilities). These are "while owned," not triggered, so they are out of scope here. (`RunMod`'s
  clumsy positional capability bag is a *separate* cleanup — see Open items.)
- **`CardMod`, `CreateSpec`, `Value`, `Condition`, `Trigger`** — reused verbatim as Action payloads.

## Evaluation semantics

`DataJoker.calculate(ctx)` collapses two loops into one:

```
for each Rule r where r.when == ctx.phase && r.condition.test(ctx):
    for each Action a in r.actions:
        a.apply(ctx, effect)        // Score → accumulate into the returned JokerEffect chain
                                    // MutateState/MutateCard/Create/Destroy/… → perform (state skipped in preview/blueprint)
```

**Decision — accumulate, don't first-match.** Today rules "first match for a trigger wins" (return on
first), while mutations "all apply." Unified, **all matching rules contribute**; score actions
accumulate into one effect chain. For the ~all jokers with one rule per trigger this is identical.
*Migration must verify* no joker relies on exclusive first-match (a later rule being suppressed by an
earlier match on the same trigger) — grep for jokers with ≥2 rules on the same `Trigger` and confirm.

**Preview / blueprint gating** stays per-action, not per-list: `MutateState` and other world-mutating
actions are skipped when `ctx.preview` or `ctx.blueprintDepth > 0`; `Score`/`MutateCard` previews run.

## Serialization & the preview mirror (the real cost)

- **`Action` gets `@JsonTypeInfo`/`@JsonSubTypes`** exactly like `Condition`. The on-the-wire shape of
  an effect changes from `{ "op": "MULT", "value": {…} }` to `{ "type": "score", "op": "MULT",
  "value": {…} }`, and a rule's `effect` object becomes an `actions` array. **This is a breaking JSON
  format change** for any hand-authored ruleset; update `JsonRulesetTest`'s raw-JSON joker.
- **`preview.js` must mirror `Action`.** It currently switches on `effect.op` and walks `effect.extra`;
  it will switch on `action.type` over the `actions` array. The preview-mirror invariant (doc 36)
  holds: every new Action kind needs a `preview.js` arm + a fixture.

## Authoring (the builder barely moves)

The fluent `Jokers` builder already speaks this language; the terminals just emit `Action`s instead of
`EffectTemplate`s. `.add(MULT, 4)` → `Score(MULT, …)`; `.create(kind)` → `Create`; `.mutateCard(mod)`
→ `MutateCard`; `.gain(var, by)` → `MutateState`. Compound `of(CHIPS,20).and(MULT,4)` becomes "append
two actions." Net: authoring reads the same; the data underneath is type-safe.

## Migration stages (each its own green commit)

1. **Introduce `Action` (sealed) + `ScoreOp`/`StateOp`** alongside the old types; write `Action.apply`.
2. **Re-point `Rule`** to `List<Action>`; make `EffectTemplate` a thin adapter that emits actions
   (keeps every existing def compiling) — or migrate defs directly via the builder (defs already go
   through the builder, so this is mostly internal).
3. **Fold `Mutation` into `Action.MutateState`**; merge `rules`+`mutations` into the single loop;
   delete `Mutation` and the second list from `JokerDef`.
4. **Delete `EffectTemplate`** once nothing references it; update JSON subtypes + `preview.js` + the
   round-trip/fixture tests.
5. Verify against the full suite + the golden scoring fixtures at every stage.

## Open items (decide before stage 1)

- **First-match vs accumulate** (above) — confirm by audit; pick accumulate unless a joker breaks.
- **`Swap`/`Balance`** — keep as distinct `Action`s, or are they really `Score`-adjacent? (Plasma's
  balance is a deck flag today, not a joker action — likely leave `Balance` out of `Action`.)
- **`RunMod` capability bag** — out of scope here, but it's the *next* "tag+positional-bag" smell: a
  flat record of ~10 booleans where adding a capability touches ~10 sites. Same medicine (an open
  `Set<Capability>` / sealed capability records) should follow this work.
- **JSON back-compat** — do we need to read old `{op,…}` effect JSON, or is a clean break fine? (No
  external rulesets ship yet → clean break is fine.)
