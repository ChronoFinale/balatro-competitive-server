# 48 — The content generation pipeline (DSL → JSON → engine · client · docs)

## Goal

One authoring source — the typed Java DSL — generates everything every consumer needs. Anything we would
otherwise hard-code on the client (card enums, boss effects, deck modifiers, the stake ladder, hand-scoring)
is *generated* from the server's own types, so it can never drift and the game stays extensible.

It's the Terraform/CloudFormation model:

```
Java DSL              ≈  the template      (typed, refactorable, compile-checked authoring)
   ↓ compile/reflect
JSON + TS artifacts   ≈  the rendered plan (portable, language-agnostic, moddable)
   ↓ consumed by
engine · client · docs   ≈  providers      (each renders the same plan its own way)
```

## The invariant (what keeps it sound)

**Generate data, types, and display — never the authoritative interpreter.** The client receives a boss's
`effect` text, its numbers, and its rule *shape* (the `Condition`/`Effect` discriminated unions) so it can
show and preview. It never receives the *evaluator*. Outcomes are computed only by the server scorer; the
client's `preview.js` is a preview-only mirror kept honest by shared fixtures. This is the line between
"extensible and sick" and "client decides outcomes."

## The pieces

- **Authoring (DSL):** `BuiltinJokerDefs` (jokers), `Bundles` (rulesets), `DeckCatalog`/`BossCatalog`/
  `TagCatalog`/`VoucherCatalog`/`TarotCatalog`/`PlanetCatalog` (content), all via fluent builders → pure data
  records (`JokerDef`, `BossBlind`, `Voucher`, …).
- **Overlays:** `RulesetOverlay` (remove/override-patch/add + reasons) folded by `JokerOverlays.apply`;
  `JokerOverlays.diff` → reasoned Markdown changelog. A ruleset is a *diff*, not a fork.
- **Bundles:** `RulesetBundle` composes the three axes the old `jokerVariant` label fused — content
  (`base` + `overlays`), capabilities (`variant` → `Capabilities`), mode (`SOLO`/`PVP`). `resolve()` compiles
  to the `Ruleset` the engine already runs. `BundleCatalog`/`RulesetStore` make bundles proposable rulesets;
  `Match` refuses a `SOLO` bundle.
- **Compile:** `JokerOverlays.writePretty` (LF, ordered keys → deterministic) serializes any catalog to a
  JSON artifact under `resources/rulesets/` and `resources/content/`.
- **Client codegen:** `ClientCodegen` reflects the closed vocabularies (`@JsonSubTypes` discriminators →
  string-literal unions), content records (→ TS interfaces), and enums (→ unions) into
  `client/src/generated/content-types.ts` + a `CONTENT_MANIFEST`.
- **Gates:** `RulesetArtifactsTest` fails if any committed artifact is stale; `./gradlew generateContent`
  rewrites them. `VocabRegistrationTest` fails if any sealed-vocabulary subtype is missing its
  `@JsonSubTypes` registration (it reflects `getPermittedSubclasses()`), killing the recurring
  "added a Condition but forgot to register it" bug class.

## Adding a content type — the three moves

1. **Compile:** in `RulesetArtifactsTest`, `gate(PATH, JokerOverlays.writePretty(catalog.all()))` (+ a
   round-trip assertion if the records deserialize).
2. **Reflect:** add `record(sb, "Name", Type.class)` (or `enumType(...)`) to `ClientCodegen`, and the artifact
   path to `MANIFEST`.
3. **Regenerate + commit:** `./gradlew generateContent`.

No new architecture per type — they all plug into the same machine.

## Current coverage

In the pipe (JSON artifact + generated TS, round-trip-gated unless noted): jokers (+ overlays), decks, bosses
(effect text + rule shape), tags, vouchers, consumables (tarots/planets/spectrals), planets, hand-scoring
table, bundles. Generated unions: the 4 rule vocabularies, the stake ladder, and the card/blind render
primitives (Suit/Rank/Edition/Seal/Enhancement/ConsumableKind/BlindType).

**The client consumes it end to end.** `./gradlew generateContent` also emits `client/src/generated/content.ts`
— the full compiled content as typed `const` arrays (serialized NON_NULL so it conforms to the interfaces,
whose nullable reference fields are optional). `CardView`/`PackItem` are typed from the generated card unions
(drift-proof), and `Almanac.tsx` renders every content type — jokers, decks, bosses, planets, hands, vouchers,
tags, consumables, rulesets — entirely from the generated module with zero server round-trip. The client
typechecks and `electron-vite build` succeeds.

Intentionally still code (behaviour, not data): the ante/blind-amount curve formula (`Blinds`), economy/
interest, and the rule interpreter itself (server scorer + client preview) — per the invariant above.
