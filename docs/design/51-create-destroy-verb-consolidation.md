# 51 — Create/Destroy verb consolidation (the half-built Selector/CreateSpec primitives)

## The smell

The Effect axis has ~47 verbs. A large cluster is `<Verb>(<Target>, <Selector>)` with the
target and selection **baked into the verb name** instead of being arguments — the same fusion
`XMULT` had before it became `(MULTIPLY, MULT)`.

```
CREATE   Create(CreateSpec), CreateCards, CreateShopJoker, GrantJokers, AddPack,
         AddShopVoucher, CreateTag, generate                         (pool-draws + shop/run adds)
DESTROY  DestroyTargets(Selector), DestroyScored, DestroyDiscarded, DestroySelf,
         DiscardRandomHeld, DestroyOtherJoker                        (run-loop + scoring-time)
COPY     CopyScored, CopySelected, CopyRandomJoker, CopyLastConsumable, duplicateRandomJoker,
         duplicateRandomConsumable
```

`Selector` (Focus/Selected/AllInHand/RandomInHand/RandomJoker) and `CreateSpec`
(Kind ∈ {TAROT,PLANET,SPECTRAL,JOKER,PLAYING_CARD}) are **primitives someone started and
abandoned**: `DestroyTargets`/`MutateCard`/consumables use `Selector`; `Create`/`Generate` use
`CreateSpec` — but the sibling verbs above never folded onto them. Compiles, tests pass, *looks*
organized. It isn't. This is the concrete answer to "why do we keep thinking things are fine."

## Why it stalled (the real obstacle — not laziness)

The holdout verbs encode information the general primitive's spec **can't currently carry**:

- **RNG provenance.** Three joker-creators draw from three different game-long streams *by design*
  (so they don't correlate): `GrantJokers`→`TAG_TOPUP`, `CreateShopJoker`→`tagJoker(rarity)`,
  `Creation.addJokers`→`createJoker(rarity)`. A naive fold changes the queue key → determinism /
  golden fixtures break.
- **Destination.** PLAYER (Riff Raff, Top-Up) vs SHOP (free-joker tags).
- **Policy.** slot-check + no-dedup (Top-Up) vs dedup + BMP empty-pool fallback (Creation).
- **Riders.** `DestroyOtherJoker.gainMult` (Ceremonial), `CreateShopJoker.edition`.

So this is a **careful refactor, not a rename**: move each hidden distinction onto the spec as an
explicit parameter, keep the interpreter bodies + RNG sources byte-identical, and let the
determinism + golden tests be the guardrail. That's why it's real work and got skipped.

## What is NOT in scope (avoid the opposite error — over-unifying)

- **`Modify.Op` vs `Effect.Operation`.** Looks like "two arithmetic enums → merge." But `Modify.fold`
  resolves in **priority order** (SET→ADD→MUL→MAX→MIN, order-independent); `Operation` in scoring is
  **sequential** (order-dependent). MAX/MIN are meaningful only in a fold; POWER only in scoring.
  Different evaluation models — merging conflates them. Leave separate.
- **Scoring-time destroyers** (`DestroyScored`/`DestroySelf`) fire mid-count-up via JokerEffect flags,
  not the run-loop interpreter. The grammar can still say `Destroy(Selector.Scored)`, but the
  interpreter must route scoring-time selectors to the scoring path — do this last, carefully.

## Target grammar

`Create(spec)` / `Destroy(selector)` / `Copy(selector)` where the spec/selector carry
`{kind/target, count, rarity, destination, edition, seedKey, policy}` — the hidden distinctions made
explicit. Sealed `CreateTarget`/`Selector` hierarchies (not one fat record) keep per-kind fields tidy.

## Order of work (each step: build + determinism + golden + preview-mirror green)

1. Joker-creators → one `Create` with `destination` + `seedKey` (preserve all three RNG streams).
2. Card/consumable creators (`CreateCards`, `Generate.AddCards`) → fold onto `CreateSpec`.
3. Run-loop destroyers (`DestroyOtherJoker` rider → param).
4. Scoring-time destroyers via selector-routing (last; touches the scorer + replay stream).
