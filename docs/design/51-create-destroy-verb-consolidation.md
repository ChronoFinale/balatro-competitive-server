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

## Un-fusing tally (what's been collapsed)

Homogeneous fused-axis clusters — verbs that baked the target/scope into the name — are un-fused onto an
argument, each byte-identical (golden + replay green):

| Was (fused verbs) | Now | Note |
|---|---|---|
| GrantJokers, CreateShopJoker | `Create(CreateSpec)` | stream/destination/dedup on the spec |
| DestroyScored, DestroyDiscarded, DestroySelf, DestroyTargets, DestroyOtherJoker | `Destroy(Selector)` | Focus/Discarded/Self/Selected/OtherJoker |
| CopyScored, CopySelected | `Copy(Selector, count)` | the card copies (homogeneous) |
| LevelUpHand, LevelAllHands, LevelMostPlayedHand | `LevelHands(Scope, Value)` | PLAYED/ALL/MOST_PLAYED |
| ConvertHand(bool, bool, delta) | `ConvertHand(Axis, delta)` | SUIT/RANK |
| Generate(create,0,null,null) [pure-create] | `Create(spec)` | Emperor/High Priestess/Judgement/Soul |

Effect subtypes: ~35 → ~27.

## Remaining fused verbs (the RNG-provenance / heterogeneous tier — NOT quick renames)

- **`Generate` (5 mixed consumables left).** Fully eliminating it needs: (a) the decomposed random-destroy
  to use Generate's `consumable(key).sub("destroy:i")` stream — `Selector.RandomInHand` currently routes to
  `sub("select:i")` (an unused branch, so changeable, but must be verified against golden); (b) a standalone
  `AddRankCards` effect for Familiar/Grim's rank-class adds (`rank:i`/`suit:i`/`enh:i` streams); (c) the two
  special money ops `DOUBLE_CAP` (Hermit) and `SELL_VALUE_CAP` (Temperance) — these are recipes, not
  ADD/SUB/MUL, so they don't fit `AdjustMoney` and need their own money effect (or stay as a small MoneyOp).
- **The 4 duplicators** (`CopyRandomJoker`, `DuplicateRandomJoker`, `CopyLastConsumable`,
  `DuplicateRandomConsumable`): heterogeneous — distinct streams (`consumable(key).sub("joker")` /
  `INVISIBLE_DUP` / `PERKEO_DUP`) + riders (destroyOthers, minRoundsOwned-gate→Condition, negative). Folding
  to one `Copy` would carry 4+ riders = the parameter-fusion the design warns against. Need spec/provenance.
- **`JokerEdition(edition, chanceDenominator, handSizeDelta, destroyOtherJokers)`**: packs a chance-gate
  (→Condition), a hand-size delta (→AdjustHandSize), and a destroy (→Destroy) — decomposable but RNG-sensitive
  (Wheel's 1-in-4 stream).

## Genuinely atomic (leave — un-fusing would ADD types, the over-unification trap)

`DelevelPlayedHand`, `DiscardRandomHeld`, `DisableRandomJoker`, `DisableBoss`, `FlipAndShuffleJokers`,
`OverwriteSelected`, `NemesisDelevel` — each a single-use boss/MP action with no sibling sharing its axis.

## Progress / status

- **Step 1 DONE** (commits "step 1a/1b"). All three joker-creators now fold onto `Create(CreateSpec)`:
  - `GrantJokers` (Top-Up) → `Create` with `stream=TOPUP`, `dedup=false`, `destination=PLAYER`. The
    `tag:topup` queue stays byte-identical; threading `RunState.jokerVariant` also fixed a latent bug
    (server-side `Creation` ignored the MP joker variant).
  - `CreateShopJoker` (Rare/Uncommon/editioned tags) → `Create` with `destination=SHOP`, `stream=TAG_SHOP`,
    `edition`. `Run`'s `Effect.Create` case branches on `destination`; the `tag:joker`/shop-pool streams
    are byte-identical (golden green). `Effect.GrantJokers` and `Effect.CreateShopJoker` are deleted.
  - The consumable-grant path (`Creation`) was already `CreateSpec`-driven, so joker creation is unified.

- **STOP flat-folding here.** `CreateSpec` is now a 10-field flat record. Each further fold surfaces more
  hidden distinctions that would pile on as flags: `CreateCards` (Incantation) draws from a *per-consumable
  `create:<key>`* stream (NOT `CREATE_CARD`), restricts to numbered ranks, and adds to **hand+deck** (not
  deck-only) — i.e. it needs `streamSource` + `numberedOnly` + `addToHand`. Adding those to the flat record
  makes it worse. **Steps 2–4 should land together with the sealed `CreateTarget`/`Selector` hierarchy**
  (each per-kind record carries only its own fields), not by extending the flat record further.

### Destroy/Copy cluster — confirmed NOT sliceable (do as one coherent pass)

Investigated the destroyers' actual interpreters (not just the verb list). Unlike Create — which had a
ready unified `Creation.apply` to route onto — the Destroy/Copy verbs split three ways, none independently
foldable:

- **`JokerEffect`-flag, scoring/event-time:** `DestroyScored` (`e.destroyScored`, Sixth Sense, mid-count-up),
  `DestroyDiscarded` (`e.destroyEventCards`, Trading Card, PRE_DISCARD), `DestroySelf` (`e.destroySelf`,
  Pizza, via `GameEvents`). These fire as boolean flags on the effect chain DURING scoring — folding them to
  `Destroy(Selector.Scored/Discarded/Self)` means the interpreter must **route scoring-time selectors through
  the scorer + replay stream** (design step 4, "last, carefully"). High blast radius (replay golden fixtures).
- **Bespoke blind-select machinery:** `DestroyOtherJoker(scope, gainMult)` is *scanned out of joker rules*
  (`jokerDestroyer`) and applied in two timing-specific loops (Ceremonial RIGHT_NEIGHBOR at any blind;
  Madness RANDOM_OTHER at Small/Big), with `gainMult` doing a sell-value→mult write. Folding the verb name
  doesn't remove this bespoke interpreter — the scope string + rider + special timing stay.
- **Unfused control:** only `DestroyTargets(Selector)` goes through the unified `apply`/`resolveTargets`.

The Copy cluster is the same shape (`CopyScored` scoring-time flag; `CopyRandomJoker`/`CopyLastConsumable`/
`DuplicateRandom*` bespoke run-loop). **Conclusion:** this is one coherent refactor — build the sealed
`Selector` extensions (Scored/Discarded/Self/OtherJoker, CopyMode) AND the scoring-time selector-routing in
the scorer together, with the replay golden fixtures as the guardrail. It does NOT yield safe verb-by-verb
commits the way Create 1a/1b did, so it should be its own focused pass, not sliced in piecemeal.
