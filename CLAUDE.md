# CLAUDE.md

Guidance for working in this repo. **New here? Read `docs/HANDOFF.md` first** — the honest current state,
how a joker works, and the known tech debt. Read alongside `README.md` and `ORIENT.md` (the code map).

## What this is

A **server-authoritative**, cheat-proof competitive Balatro engine, clean-room reimplemented in
**Java 25** (Gradle). The server simulates the entire game — state, RNG, scoring — and the client
only sends **intents** and renders authoritative results. `ClientView` is the info-hiding boundary:
it structurally carries no deck order, seed, or future shop. AGPL-3.0; ships no game assets or code.

**Core invariant:** the client is never trusted. Never move RNG, scoring, or outcome decisions to the
client. If a feature seems to need the client to compute an outcome, it's wrong — the client sends an
intent, the server computes, the `ClientView` reflects the result.

## Build / test / run

```bash
./gradlew build            # compile + full test suite
./gradlew test             # JUnit 5 + AssertJ + jqwik (property tests)
./gradlew test --tests 'com.balatro.engine.HandTypeScoringTest'   # one class
./gradlew run              # start the server: HTTP 28788 (login/REST), TCP+WS 28789 (non-standard, avoids BMP's 8788)
./gradlew play             # ClientCli (terminal client)
```
Java toolchain 25 is auto-provisioned by Gradle. On Windows use `gradlew.bat` (or the Bash tool).
Wire protocol is newline-delimited JSON; see `TcpNetworkTest` for the definitive message set
(`auth` → `newRun` → `selectBlind` → `playHand`/`discard`/`buyShopItem`/…; server replies `update`
with `accepted`, `view` (ClientView), `replay`).

## Layout

Four layers, one-directional deps (`engine → content → dsl → model`). **See `ORIENT.md` (repo root) for the
5-minute map** — read it first if the structure feels unclear.

- `src/main/java/com/balatro/engine/` — **model + runtime**
  - `net/` — `ServerMain`, `GameServer` (routing + serialization), `ClientView` (the hiding boundary),
    `IntentHandler`, `ServerUpdate`/`ReplayEntry`.
  - `game/` — `Run` (the run loop), `Shop`, `Match` (PvP/Attrition), and the `*Catalog` runtime (catalogs
    now load content from the compiled JSON; their authoring lives in `content/`).
  - `scoring/` — `ScoringEngine`, `ScoreResult`, `ReplayEntry` (the animation stream the client renders).
  - `rng/` — the **composable RngSource model** (see below) + `rng/vanilla/` bit-exact Balatro PRNG.
  - `eval/` — the interpreters (`ValueResolver`/`ConditionEvaluator`/`EffectInterpreter`/`ModifyFolder`);
    `exec/` — the `Command` model + `Run.apply` (the single action-mutation path).
  - `consumable/`, `joker/` (`joker/def/` now holds only overlay/library/schema infra — `DataJoker`,
    `RulesetOverlay`, `BuilderSchema`; the **data model** moved to `com.balatro.grammar/`), `state/`,
    `codegen/` (TS + manifest), `content/ContentStore`, `i18n/Loc`, `ui/` (server-driven-UI vocabulary).
- `src/main/java/com/balatro/grammar/` — **the pure-data DSL grammar** (`Effect`, `Condition`, `Value`,
  `Modify`, `Selector`, `Rule`, `Property`, `JokerDef`) — no behavior; interpreted by `engine.eval`.
- `src/main/java/com/balatro/dsl/` — **the fluent builders** (`Jokers`, `Cond`, `Val`, `Decks`, `Bosses`,
  `Consumables`) you author content with.
- `src/main/java/com/balatro/content/` — **the content authored in the dsl**: `jokers/BuiltinJokerDefs`
  (the 141 jokers), `DeckDefs`/`BossDefs`/`TagDefs`/`VoucherDefs`/`ConsumableDefs`/`PlanetDefs`, `Bundles`,
  `Screens`. Compiled to `resources/{content,rulesets}/*.json` by `./gradlew generateContent` (gated).
- `src/test/` — JUnit suites + golden fixtures in `src/test/resources/` (real-game pool/shop/PRNG vectors).
- `client/` — Electron/React renderer (`client/src/renderer`). Mirrors scoring previews; see invariant below.
  `client/src/generated/content-types.ts` is committed (the type contract); `content.ts` is generated.
- `tools/` — dev/integration: see "Real-Balatro thin client" below.

## RNG model (read before touching randomness)

Randomness goes through the **`RngSource`** model (`rng/RngSource.java`, `RngSources.java`,
`RngContext.java`, `QueueSet.resolve`): a source declares Scope (GAME_LONG/PER_ANTE/PER_BLIND),
PvpMode (NONE/PER_HAND), and Selection (SEQUENTIAL/COMPOSITION/WEIGHTED_COUNT), composed via
`.perBlind()/.pvpPerHand()/.composition()/.sub()`. This is for **variance reduction** ("The Order")
and PvP fairness — don't call PRNGs ad hoc; add/extend an `RngSource`. `rng/vanilla/` holds the
bit-exact vanilla PRNG (`BalatroPrng` pseudohash/pseudoseed, `LuaJitRandom` TW223) used to diff against
the real game; `round13` must stay round-half-EVEN (BigDecimal), matching C `printf %.13f`.

## Conventions

- **Clean-room, no guessing.** Validate mechanics against the real game / the authoritative mechanics
  doc, not memory. When in doubt, read the actual Balatro source at `D:\BalatroMod\Balatro` or run it.
- **Preview-mirror invariant:** data-driven jokers are interpreted by both the server scorer AND the
  client `preview.js`. Any new Condition/Value/Op must be mirrored in `preview.js` + a fixture added.
- **Tests are the spec.** Scoring/economy/RNG changes need a test (often a golden fixture diffed against
  the real game). Baseline sanity: pair of Kings = 60 = `(10 + 20) × 2`.
- Don't commit `build/` (gitignored — that's where the bridge wire logs land), `*.log`, or `web-assets/`.

## Real-Balatro thin client (`tools/balatro-bridge/`)

An in-progress effort to make the REAL Balatro game a thin renderer of this server (smods/lovely mod).
**Stage 1 works:** real Balatro plays a server-authoritative blind with native animations. Technique =
**identity-override**: let Balatro draw/discard from its own deck (animations + bookkeeping intact) and
only set what each drawn card IS, tracked by the server's stable card `uid`. Hooks: `select_blind`
(open run), `draw_from_deck_to_hand` (prime deck-end card identities before the draw),
`discard_cards_from_highlighted`, `evaluate_play` (server scores). Logs raw wire traffic to
`build/balatro-bridge-wire.txt`. Do NOT rebuild `G.hand` wholesale and do NOT stage onto `G.deck` — both
break Balatro's animations/deck. Next: server-driven blind/round (Stage 2), shop/economy (Stage 3),
jokers/editions (Stage 4 — where local scoring diverges and the server `replay` must drive the count-up).
`tools/balatro-pooldump/` dumps real-game pools/shops for the golden fixtures.
