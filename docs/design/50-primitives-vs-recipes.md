# 50 — Primitives vs recipes: a joker's "kind" is just its Value

Three design conversations (money ops, scoring ops, scaling jokers) turned out to be the same realization.
Writing it down because it's the organizing principle for the whole effect vocabulary.

## The recurring smell

Every time, we found a representation that **fused two independent axes into one fixed token**, then needed
bespoke handling per token:

| place | fused token | the two axes it hid |
|---|---|---|
| money | `ADD(-1)` | operation (subtract) × magnitude (1) |
| scoring | `XMULT` | operation (TIMES) × subject (MULT) |
| scaling | "a scaling joker" | an accumulator (counter++) × a read (base + scale·counter) |

Un-fusing each into its axes is what makes the vocabulary small *and* extensible: name the axes, and the
empty cells (`TIMES_CHIPS`, `SUBTRACT money`, "scale chips instead of mult") fall out for free.

## The two layers

**1. Runtime primitives — minimal, orthogonal.** The engine knows only these:
- **arithmetic** = `operation × subject`. operation ∈ {ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER, SET};
  subject ∈ {CHIPS, MULT, MONEY, RETRIGGERS, …}. (`XMULT` = `(MULTIPLY, MULT)`.) Sparse grid: each subject
  declares which operations it supports; nonsense cells (`POWER money`) simply aren't authored.
- **state** = an *accumulator* (`MutateState`: ADD/SET/RESET, optionally per-card) + a *linear read*
  (`Value.State`: `base + scale·counter`). Together they are the entire scaling mechanism.
- **gating** = `Condition`. **magnitude/source** = `Value` (Const, State, Count, RunVar, Stat, …).

**2. Authoring recipes — named patterns over primitives, in the builder.** "static joker", "scaling joker",
"economy joker", "retrigger joker" are **not runtime types** — the engine has no idea what a "scaling joker"
is. They are builder recipes that expand to primitives. The `Jokers` builder already proves this: `add(Target,
v)` / `multiply(Target, v)` take a subject + operation and *flatten* to the fused `Op` — the decomposed model
lives one layer up; only the runtime enum hasn't caught up.

## The punchline: a joker's kind is which Value it reads

Static vs scaling vs reactive is **not** a structural distinction. It's a single knob — the `Value` feeding the
score effect:

- **static** → `Value.Const` (Joker: +4 Mult)
- **self-scaling** → `Value.State` + a `MutateState` bump (Ride the Bus: +streak Mult, streak resets on a face)
- **reactive** (reads the world, stores nothing) → `Value.RunVar` / `Count` / `Stat` (Supernova = ×hands
  played; Bootstraps = +2 per $5; Cloud 9 = $1 per 9 in deck)

Same `(trigger, condition, operation, subject, Value)` shape every time. The "scaling primitive" the engine
needs already exists: it's `Value.State` + the accumulator. What's missing is only the *recipe-level naming* so
a scaling joker reads as one declarative intent instead of two rules you have to know belong together —
e.g. `.scales(MULT).by(1).on(handPlayed).resetWhen(faceScored)` compiling to the bump-rule + State-read pair.

## Plan (diff-guarded slices, builder stays stable so the 141 jokers don't churn)

1. **Scoring `Op` → `(Operation, Subject)`.** Behavior-identical map (each old `Op` → its cell), so golden
   fixtures validate by construction. `TIMES_CHIPS`/`POWER_CHIPS` become expressible. Mirror in the client
   preview interpreter; regenerate artifacts.
2. **Unify the operation enum** so money (`AdjustMoney`) and scoring share one `Operation` vocabulary, money
   being subject = MONEY.
3. **Scaling/static recipes** in the builder — declarative sugar over `Value.State` + `MutateState`.

Genuinely structural, stays code: the interpreter, RNG, the `chips × mult` arithmetic and its canonical
within-source order (ADD before MULTIPLY before POWER). See [49](49-bespoke-logic-audit.md).
