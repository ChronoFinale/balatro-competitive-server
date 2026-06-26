# HANDOFF — start here

You are picking up a **server-authoritative, cheat-proof competitive Balatro engine**, clean-room
reimplemented in **Java 25 / Gradle**. This doc is the honest current state: what it is, how it actually
works, what's done, and what's still messy. Read it before the others; it points you to them.

> Read order: **this doc → `ORIENT.md` (the 5-minute code map) → `DSL.md` (the vocabulary you author in)**.
> `CONTENT.md` (content ground-truth) and `SYSTEMS.md` (target specs) are reference, pulled in as needed (§8).

---

## 1. The one-sentence north star

The server simulates the **entire** game — state, RNG, scoring. The client sends typed **intents**
(`PlayHand`, `Discard`, `BuyShopItem`, …) and renders an authoritative **`ClientView`** plus a structured
**scoring replay log**. **The client computes nothing that affects an outcome.**

**Core invariant, never violated:** the client is never trusted. RNG, scoring, and outcome decisions never
move to the client. If a feature seems to need the client to compute an outcome, it's wrong — the client
sends an intent, the server computes, the `ClientView` reflects the result.

Every cheat class is closed **structurally**, not by detection:

| Threat | Structural defense |
|---|---|
| Fabricated score / money / cards | Authoritative server re-simulates; client sends intents, never outcomes |
| Reading hidden info (deck order, next shop, seed) | `ClientView` structurally carries no deck/seed/queue state |
| Game-speed / tempo abuse | Server owns the clock |
| Logic mods on a client | No effect — the client is not the authority |

**Target:** behavioral parity with **Balatro Multiplayer (BMP) 0.4.2 ranked**, rebuilt on our own clean
PRNG. We match BMP's *behavior and queue topology*, **not** its bytes or its trust model. We are
deliberately **not** byte-compatible with vanilla/BMP seeds. AGPL-3.0; ships **no** game assets or code.

---

## 2. The two things actually being built

1. **A provably-fair competitive engine.** Near-term goal is **"Attrition done well"**: a fair 1v1 where
   both players get the same run from one seed, reconciled server-side at a **Nemesis blind** (the target is
   the opponent's score, not a chip count).
2. **A data pipeline for extensible-yet-server-interpreted content.** Every joker / consumable / boss / deck
   / voucher / tag is authored as **data** in a typed Java DSL, compiled to JSON, booted by the engine, and
   served to thin clients. A built joker is exactly as cheat-proof as a hand-coded one. The eventual real
   client is a **Lua mod that turns actual Balatro into a renderer of server output** (`tools/balatro-bridge/`,
   Stage 1 proven). The Electron app in `client/` is a fast proof-of-concept, not the product.

---

## 3. How a joker actually works (this *is* the model — nothing exotic)

A joker is **data**: `WHEN` something happens, `IF` a condition holds, `THEN` do an effect. Here is a real
one, copied from `content/jokers/BuiltinJokerDefs.java`:

```java
// Even Steven: +4 Mult per scored even-rank card
.forEachScored(card().even()).add(MULT, 4)
//   WHEN scored      IF even        THEN +4 Mult
```

That's the whole system, 141 times over. The server reads this data and computes the score; the client shows
the result. A static value is just a literal (`add(MULT, 4)`); a value that's **shared across a family** of
jokers (Greedy/Lusty/Wrathful/Gluttonous = one definition, different suit) is a named `prop`; a value that
**changes during the run** (Ride the Bus's streak) is a `counter`/`state`. That's the entire vocabulary
distinction. Sanity check that must always hold: **a pair of Kings scores 60 = `(10 + 20) × 2`.**

The full closed vocabulary (triggers / conditions / values / effects / selectors) is **`docs/DSL.md`**.

---

## 4. Architecture — four layers + one pipeline

One-directional dependency: **engine → content → dsl → grammar(model)**. The runtime never depends on the
authoring side.

| layer | package | what it is |
|---|---|---|
| **model / grammar** | `com.balatro.grammar/` | the closed vocabulary as **pure-data records** — `Effect`, `Condition`, `Value`, `Modify`, `Selector`, `Rule`, `Property`, `JokerDef`. **No behavior.** |
| **dsl** | `com.balatro.dsl/` | the fluent builders you author with — `Jokers`, `Cond`, `Val`, `Decks`, `Bosses`, `Consumables`. |
| **content** | `com.balatro.content/` | the 141 jokers (`jokers/BuiltinJokerDefs`) + `DeckDefs`/`BossDefs`/`TagDefs`/`VoucherDefs`/`ConsumableDefs`/`PlanetDefs`, authored in the dsl. |
| **engine** | `com.balatro.engine.{game,scoring,rng,net,eval,exec,…}` | interprets the model → outcomes. |

- **Interpretation lives in `engine.eval`** (`ValueResolver`, `ConditionEvaluator`, `EffectInterpreter`,
  `ModifyFolder`) — the grammar stays pure data, interpreters give it meaning.
- **Action effects** (a Tarot destroying cards, a boss taking money) resolve to a `Command`
  (`engine.exec.Command`) applied by `Run.apply(...)` — the single mutation path.
- **The pipeline:** author in the DSL → `./gradlew generateContent` compiles to JSON under
  `resources/{content,rulesets}` + the client's TS types → the engine boots from the JSON (`ContentStore`),
  the server serves it, the client delta-syncs it. `RulesetArtifactsTest` *gates* the JSON so it can never
  drift from the source. **Generate data, never the interpreter** (the interpreter stays code, by design).

---

## 5. Current state (honest, as of 2026-06-26)

**What works:** scoring core (the baseline parity invariants hold), 141 jokers authored as data, the
consumable/boss/tag subsystems, the DSL→JSON→engine pipeline, the Electron PoC client, ruleset overlays
(vanilla + bmp-0.4.2-ranked), auth/networking foundation. **Full suite: 590 tests, 0 failures.**

**Recently landed and stable — a grammar consolidation (don't undo it):**
- The op-enums were **three** (`Effect.Operation`, `Modify.Op`, `MutateState.Op`) → now **one** `Operation`.
- Several `Effect` verbs folded onto a single **`Modify` write-spine** (money, hand-size routed through
  `Effect.Write(Modify(...))`); duplicate creators collapsed (`CreateCards` → `AddCards`).
- **`ReplayEntry` is data-driven** — carries `(Kind, amount)`, not engine-built English; the client/i18n
  derives labels. The dead parallel `actionTrace`/`TraceEntry` was deleted.

**Known tech debt (from an adversarial audit — tell the next agent these are real, not clean):**
1. **#10 — the core mess.** `JokerEffect` is a ~20-public-mutable-field grab-bag, and the same scoring axes
   are modeled **three times**: `Effect.Score(Operation × Term)` (grammar) → `JokerEffect` fields →
   `ReplayEntry.Kind`. The intended fix is a typed `Contribution(Operation, Term, amount)` + `Command` so
   the fat bag dies; it's a **multi-commit refactor** (changes `calculate()`'s return type, ripples through
   `ScoringEngine`/`GameEvents`/`JokerDisplay`/Blueprint). Not started — do it staged, scoring fixtures
   guarding every step.
2. **`ReplayEntry.source`** still ships English display names (`"Red Seal"`, `"High Card"`) where keys exist
   — the `text` field was fixed, `source` wasn't.
3. **Stringly-typed** closed sets: `rarity`, `AddPack` kind/size, `ShopFlag`, `Condition.ConsumableType`,
   per-joker `state` var names — raw `String` where an enum belongs.
4. **`Condition.Cmp`** carries `holds()` comparison logic *inside* the pure-data grammar (the one spot that
   breaks "grammar has no behavior"); move it to `ConditionEvaluator`.

The grammar's *shape* is good (sealed, argument-driven, de-fused). It is **mid-refactor**, not finished —
treat it as solid but with the four items above open.

---

## 6. Milestones & content inventory

The build decomposes into **M0–M7** (M0 underpins all): M0 scoring-pipeline correctness → M1 RNG queue
topology → M2 run lifecycle + Attrition end-to-end → M3 content building-blocks → M4 content transcription →
M5 ruleset layers/curation → M6 networking hardening → M7 matchmaking/ranked. Detail for each system is in
`SYSTEMS.md`.

**Content inventory** (exact keys/numbers in `CONTENT.md`): ~159 jokers (61 common / 64 uncommon / 20 rare /
5 legendary / 9 BMP-exclusive), 22 tarots, 12 planets, 18 spectrals, 32 vouchers, 23 decks, 24 skip tags,
28 boss blinds. ~141 jokers are authored; the remaining jokers + most consumables/vouchers/tags/bosses are
the transcription work (M3→M4).

---

## 7. Invariants you must not break

- **Server authoritative.** Never move RNG/scoring/outcomes to the client (see §1).
- **Preview-mirror.** Data-driven jokers are interpreted by both the server scorer **and** the client
  `preview.js`. Any new `Condition`/`Value`/`Op` must be mirrored in `preview.js` + a fixture added.
- **Tests are the spec.** Scoring/economy/RNG changes need a test, often a golden fixture diffed against the
  real game. Baseline: pair of Kings = 60.
- **Clean-room, no guessing.** Validate mechanics against the real Balatro source at `D:\BalatroMod\Balatro`
  or the mechanics doc — never from memory.
- **Byte-exact RNG oracle.** `rng/vanilla/` holds the bit-exact vanilla PRNG used to diff against the real
  game; `round13` stays round-half-EVEN. Competitive RNG is a separate keyed-PRF path (`docs/RNG-SECURITY.md`).
- **Don't commit** `build/`, `*.log`, `web-assets/`, or the gitignored `client/src/generated/content.ts`.

---

## 8. The doc set (≈7 files — everything else was consolidated away)

- **`docs/HANDOFF.md`** (this) — current state, architecture, how-it-works, plan, debt. The one living doc.
- **`docs/DSL.md`** — the DSL dictionary: the closed `when·if·do·who` vocabulary you author content in.
- **`docs/CONTENT.md`** — ground-truth reference: every joker/consumable/voucher/deck/tag/boss with exact
  keys, numbers, costs, RNG keys, BMP-vs-vanilla deltas (for authoring the unbuilt items + validation).
- **`docs/SYSTEMS.md`** — target-behavior specs: scoring pipeline, RNG/queue topology, economy, run
  lifecycle, PvP/nemesis, networking, ruleset composition + the BMP-0.4.2-ranked overlay.
- **`docs/RNG-SECURITY.md`** — the keyed-PRF competitive RNG vs the vanilla oracle (anti-cheat).
- **`balatro-engine-spec.md`** (root) — the from-source `evaluate_play` derivation + anti-cheat seam.
- **`queue-model.md`** (root) — the game-long-queue determinism model.
- Also: **`ORIENT.md`** (the 5-minute code map) · **`docs/history/`** (*why* the grammar evolved — not spec).

Always validate content numbers against the real Balatro source at `D:\BalatroMod` before relying on them.

---

## 9. Build / test / run

```bash
./gradlew build            # compile + full test suite (JUnit 5 + AssertJ + jqwik)
./gradlew test             # tests only;  --tests 'com.balatro.engine.ScoringEngineTest' for one class
./gradlew run              # start the server: HTTP 28788 (login/REST), TCP+WS 28789
./gradlew play             # ClientCli (terminal client)
./gradlew generateContent  # recompile DSL → resources/{content,rulesets}/*.json + client TS types
# after a content/grammar change, regen the golden artifacts:
./gradlew test --tests 'com.balatro.engine.RulesetArtifactsTest' -Dregen=true
```

Java toolchain 25 is auto-provisioned by Gradle. On Windows use `gradlew.bat` or the Bash tool. Wire protocol
is newline-delimited JSON; `TcpNetworkTest` is the definitive message set.
