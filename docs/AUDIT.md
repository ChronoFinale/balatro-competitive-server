# Audit: what exists today vs. the goal (read-only findings)

**Date:** 2026-06-26 · **Scope:** duplication, dead vocabulary, exists-vs-goal. No code was changed.
**Context:** produced right after **Phase 0 (perfect the DSL) completed** — so the grammar-level debt that older
docs (e.g. `HANDOFF.md` §pre-0) list as open is now **resolved**: `Cmp.holds` moved to `eval`, stringly sets →
enums, bespoke booleans → enums, single-use verbs decomposed, the `JokerEffect` 20-field bag deleted (→
`JokerResult` of `Contribution`+`Command`), and the rails are locked by `DslRailsTest`. Don't re-open those.

The goal: a **server-authoritative, cheat-proof, competitive multiplayer Balatro engine** (Java 25), currently
~85% a playable Attrition game. This audit maps the remaining structural debt and the gap to "finished."

---

## 1. Duplication

### 1.1 The `Effect` grammar is interpreted by THREE switch statements ★ (the headline)
The 22 `Effect` verbs are dispatched in three places:
- `EffectInterpreter.apply` (`engine/eval`) — scoring → `JokerResult` (8 verbs)
- `Run.applyRunLoopEffect` (`engine/game/Run.java`) — boss/joker lifecycle (~13 verbs)
- `Run.applyConsumableEffect` (`Run.java`) — tarots/spectrals (~10 verbs)

**Verdict (nuanced):** most of the *per-verb* overlap is **legitimate selector-specialization**, not copy-paste —
`Destroy`/`Create`/`Copy`/`MutateCard` branch on the `Selector` (Focus vs Self vs Selected vs Others), and each
site only handles the selectors that make sense there. The genuinely shared write path (`Write` → `applyWrite`)
is already a single helper called from both run-loop and consumable sites. **The real duplication is structural,
not per-verb:** three dispatch tables over one sealed type, and one true logic dup —

- **`LevelHands` — genuine duplication.** Its scope logic (PLAYED / ALL / MOST_PLAYED, + the `Side.OPPONENT`
  nemesis route) is re-implemented in all three sites (`EffectInterpreter.java`, `Run.java` run-loop ~935,
  consumable ~1448). Worth unifying.

**The structural fix (where 0.7 was already heading):** converge on **one `Effect → List<Command>` interpreter**;
then scoring/run-loop/consumable each just *apply commands*, not re-interpret effects. `EffectInterpreter`
already produces `Command`s for the scoring path (0.7); extending it to emit the run-loop/consumable commands
(and deleting the two `Run` switches) is the natural next refactor. **Blocker:** the three contexts resolve
`Selector`s against different worlds (scored focus vs whole hand vs joker row) and need different runtime data;
unifying means threading one resolution context through. Medium effort, high payoff (kills the triplication and
makes "what can a verb do" answerable in one place).

### 1.2 Not duplication (verified clean)
- **`CardModifiers` (enhancements/editions/seals as `Effect` lists)** — *elegant reuse*, not a parallel system:
  the same `EffectInterpreter.applyAll` scores them. Keep.
- **`ReplayEntry.Kind` vs `Command`** — different domains (client animation vocabulary vs server execution).
  Intentionally separate (the 5d decision: `ReplayEntry` is the wire boundary, like `ClientView`). Keep.
- **`BuilderSchema`** — reflects the grammar to keep the UI in sync; introspection, not duplication.

### 1.3 Minor / low-priority
- **Catalog boilerplate** — `TagCatalog`/`BossCatalog`/`TarotCatalog`/… repeat the same
  `ContentStore.x()`-with-DSL-fallback shape. Thin and type-safe; a generic base class isn't worth it yet.
- **`JokerLibrary` vs `JokerDefLibrary`** — two load paths over the same `BuiltinJokerDefs` (runtime factory +
  variants/snapshot vs read-only def facade). No longer copies data; could share one internal load. Low priority.

---

## 2. Dead vocabulary

Grammar words that are defined, interpreted, schema'd, and often unit-tested — but **used by zero content def**.
`DslRailsTest.everyEffectAndConditionWordIsUsedByContent` now guards the Effect/Condition layer; this sweep
extends to `Value`/`Selector` and enum constants.

**Purely speculative — safe to delete (no content, no legit-axis argument):**
- `Condition.scoredEdition`, `Condition.scoredSeal` — already tracked as `KNOWN_DEAD_PENDING_AUDIT`. No joker
  keys off a scored card's edition/seal. *(~7 files: grammar record + JsonSubTypes + ConditionEvaluator +
  BuilderSchema + RuleValidator + preview.js + the ad-hoc unit test.)*
- `Selector.allInHand` — no content uses it.
- `Value.Extreme.HIGHEST` + its only builder `Val.highestHeld()` — Raised Fist uses LOWEST; nothing uses HIGHEST.
- `Value.Which.DECK_SIZE` — DECK_REMAINING is used; DECK_SIZE isn't.
- `CreateSpec.SealStrategy.FIXED`, `AddCards.RankClass.ANY` — no content path.

**Present but no content YET — keep (legit scoring axes a future joker would use), don't delete:**
- `Effect.Term.HELD_MULT` (flat +mult per held card), `Effect.Operation.POWER` (^mult, Cryptid-style),
  `Value.Source.HELD` — these are real, tested scoring capabilities; no *vanilla* joker happens to use them yet.
  Deleting them would re-remove capability we'd just re-add. Treat as "awaiting a card," not dead.
- `Effect.Operation.DIVIDE/MAX/MIN`, `SUBTRACT` (SUBTRACT *is* used) — economy/modify ops; verify per-op before
  touching (some are used by `Modify` folds, not by `"type"` discriminators, so the grep can miss them).

**Recommendation:** delete the ~6 purely-speculative items in one small PR (each is a sealed-subtype/enum removal
+ regen); leave the legit-axis items and instead let `DslRailsTest` flag them if they're *still* unused when
content breadth lands. (Caveat: the enum-constant findings came from a grep sweep, not the test — double-check
each isn't referenced via a `Modify`/builder before deleting.)

---

## 3. Exists vs. goal

### 3.1 Subsystem map
**CORE (the authoritative game):** `engine/net` (GameServer/ClientView/auth), `engine/game` (Run/Match/Shop +
catalogs), `engine/scoring` (ScoringEngine/ReplayEntry/BigNum), `engine/rng` (composable RngSource model),
`engine/eval` (the interpreters), `engine/exec` (Command), `engine/consumable`, `engine/joker`, `engine/card`,
`engine/hand`, `engine/state`, `engine/intent`, `grammar`, `dsl`, `content`, `resources/{content,rulesets}`,
`engine/codegen` (TS + manifest), `engine/i18n`.

**SCAFFOLDING / dev-only (keep, but not the product):**
- `engine/rng/vanilla/` — bit-exact Balatro PRNG **oracle**. Load-bearing for the golden-fixture tests (our
  ground truth for deal order / blind sizes / pools). Dev-only (not in the public API). **Keep.**
- `tools/balatro-pooldump/` — real-game pool/shop capture feeding those fixtures. **Keep** (test data).
- `tools/balatro-bridge/` — the Lua mod making **real Balatro a thin client** (Stage 1 works; Stage 2 stubbed).
  This is the *intended long-term client*. **Keep + advance.**
- `client/` (Electron/React) — a PoC that proved the data pipeline + preview-mirror. **Candidate to deprecate**
  once the Lua bridge is the canonical client — but it's currently the only thing exercising the preview-mirror
  invariant (347 fixtures) and is a fast local-play harness. Decide later; don't drop yet.
- `engine/ui/` — server-driven-UI vocabulary (menus/screens). Keep.

### 3.2 The concrete gap to "finished" (verify-against-code findings)
*Scoring pipeline (the highest-value gap — it's where data-driven breaks down):*
- **`MODIFY_SCORING_HAND` phase not executed** — documented in `ScoringEngine`'s ordering contract but no
  `effectPass(MODIFY_SCORING_HAND, …)`. Jokers that add/remove cards mid-hand can't be expressed as data yet.
- **Boss `modify_hand` step** — bosses fire per-hand effects, but the "adjust the base chips/mult before per-card
  scoring" seam isn't in `ScoringEngine`. (The Flint's halve-base *is* there as a flag; the generic seam isn't.)
- **`AFTER` phase** — documented, not called. No current joker needs it; it's a seam for future content.
- *(These three are exactly `HANDOFF.md` §6's deferred "boss scoring phases / destruction-after seams.")*

*Content breadth (data, not structure):* ~142/≈159 jokers; vouchers ~20/32; decks 16/23. Consumables/bosses/
tags/planets effectively complete. Each missing item is a few DSL lines gated by the stat-audit tests.

*Modes & networking:* the `Gamemode` abstraction (Attrition/Showdown/Survival) isn't generalized; no ranked
queue/matchmaking/Elo; reconnect-integrity + rate-limiting are partial. (Milestones M6/M7 — deferred.)

### 3.3 Nothing needs architectural rework
The 4-layer model (`grammar → dsl → content → engine`) is sound and the core invariant (client never computes
outcomes) holds. The gap is **feature-completeness + the one structural cleanup (§1.1), not redesign.**

---

## 4. Suggested priority order (post-audit)
1. **§1.1 — unify the three `Effect` dispatchers into one `Effect → Command` interpreter** (kills the
   triplication + the `LevelHands` dup; finishes the direction 0.7 set). Highest structural payoff.
2. **§2 — delete the ~6 purely-speculative dead words** (small, satisfying, shrinks the surface). Keep the
   legit-axis ones; let `DslRailsTest` watch them.
3. **§3.2 scoring phases** (`MODIFY_SCORING_HAND` / boss `modify_hand` / `AFTER`) — unblocks data-driven jokers
   that today would need hand-coding. This is the real "make the DSL able to express the rest of the game" gap.
4. Content breadth + modes/networking — data and milestones, after the above.
