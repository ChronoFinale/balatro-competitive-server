# DSL — the vocabulary (the word list)

> Orientation + current state: `HANDOFF.md`. Content ground-truth: `CONTENT.md`. The live grammar is the
> `com.balatro.grammar` package; this is its dictionary.

> The closed vocabulary of the card-effect DSL. A card is one sentence: **`when` · `if` · `do` (`how-much`,
> `who`)**. This doc is the *dictionary* — every word, its domain meaning, and whether it's a **core word**
> of the language, **sugar** (composes core words), **engine** (pipeline-internal, not author-facing), or
> an **escape** (genuinely code, `behaviorInCode`). Curate against the four rails (doc 42): *declarative ·
> closed-domain · total · escape-hatch*. Add a word only when ≥3 cards need it and the game thinks in it.

Legend: **● core** · **○ sugar** · **⚙ engine** · **◆ escape**

---

## WHEN — the moment (`Trigger`)

The clock. A rule fires at exactly one moment. Grouped by what's in focus.

**Per-scored-card** (focus = one card, engine loops):
- ● `ON_SCORED` — a played card is being scored (Greedy, Even Steven, Gold Ticket)
- ● `ON_HELD` — a held-in-hand card (Steel, Baron, Shoot the Moon)
- ● `REPETITION_PLAYED` / `REPETITION_HELD` — the retrigger pass for a scored/held card (Hack, Mime)

**Per-hand** (focus = the whole played hand):
- ● `JOKER_MAIN` — once per hand, the joker's main contribution (the default)
- ⚙ `BEFORE` / `AFTER` / `INITIAL_SCORING_STEP` / `FINAL_SCORING_STEP` / `MODIFY_SCORING_HAND` /
  `DEBUFFED_HAND` — fine-grained scoring-pipeline hooks; engine ordering, rarely author-facing
- ⚙ `DESTROYING_CARD` / `REMOVE_PLAYING_CARDS` — mid-pipeline card-removal hooks

**Discard** (focus = the discarded set):
- ● `PRE_DISCARD` — before a discard resolves (Faceless, Trading)
- ○ `ON_DISCARD` — after (mostly covered by PRE_DISCARD)

**Lifecycle / round:**
- ● `BLIND_SELECTED` — a blind is chosen (Marble, Cartomancer, Madness)
- ● `FIRST_HAND_DRAWN` — opening hand dealt
- ● `END_OF_ROUND` — round won (Golden, Rocket, Gold-card payout)
- ● `CARD_ADDED` / `CARD_DESTROYED` — a card joined/left the deck (Hologram, Glass Joker, Canio)

**Player actions / shop:**
- ● `USE_CONSUMABLE` — a Tarot/Planet/Spectral used (Constellation, Fortune Teller)
- ● `BUY_CARD` / `SELL_CARD` — a shop buy/sell (Campfire counts sells)
- ● `SELL_SELF` — *this* joker sold (Diet Cola, Invisible, Luchador) → today a `RunMod.OnSell`
- ● `REROLL_SHOP` / `SHOP_EXIT` — reroll / leaving the shop (Flash Card, Perkeo)
- ● `SKIP_BLIND` — a blind skipped
- ● `OPEN_BOOSTER` / `SKIP_BOOSTER` — a pack opened/skipped (Hallucination, Red Card)

**Meta:**
- ● `ON_OTHER_JOKER` — reacting to another joker scoring (Baseball Card)
- ⚙ `WHILE_OWNED` — *proposed* standing trigger for folded modifiers (Juggler, Four Fingers); today
  these live as `mods()`/`handMods`/`RunMod`, not a trigger

---

## IF — the gate (`Condition`)

Declarative predicates. Read state/attributes, compare, combine. Never control flow.

**The card in focus:**
- ● `card().suit(S)` `ScoredSuit` — also matches Wild; targetKey variant = round's rolled suit
- ● `card().rankBetween(lo,hi)` `ScoredRankBetween` · ● `card().rankIsTarget(key)` `ScoredRankIsTarget`
- ● `card().even()/odd()` `ScoredParity` · ● `card().isFace()` `ScoredIsFace` (honours Pareidolia)
- ● `card().enhancement(E)` / `.edition(E)` / `.seal(S)` `Scored{Enhancement,Edition,Seal}`
- ○ `card().isFirst()` `ScoredFirst` · `card().amongFirst(n)` `ScoredAmongFirst` · `card().firstFace()`
  `ScoredFirstFace` (= isFirst ∧ isFace) · `card().playedThisAnte()` `ScoredPlayedThisAnte`

**The played hand:**
- ● `playedHand().contains(type)` `HandContains` · ○ `.containsPair()` `HandContainsPair` (= contains PAIR)
- ● `playedHand().is(type)` `HandIs` (literal or round-target) · ● `.sizeAtMost/AtLeast/Exactly(n)` `PlayedCount`
- ● `playedHand().hasSuit(S)` `ScoringContainsSuit` · ○ `.hasFace()` `ScoringAnyFace`
- ○ `playedHand().repeatedThisRound()` `HandPlayedThisRound` · `.matchesRoundType()` `RoundHandTypeConsistent`

**Held / discarded sets:**
- ● `held().allSuits(…)` `HeldAllSuits` (Blackboard) · ● `discard().faces(n)` `DiscardedFaceCount`

**Run / economy** (one primitive after the cleanup):
- ● `runVar(V).atLeast/atMost/exactly(n)` `Compare` — money, hands, discards, ante, …
- ● `state(x).atLeast(n)` `Compare` — a joker's own counter
- ● `value(v).atLeast(n)` `Compare` — any `Value`
- ● `runVarModulo(V, mod, rem)` `RunVarModulo` — "every 6 hands" (Loyalty Card)

**Probability:**
- ● `chance(num, den, seed)` `Chance` — 1-in-N, honours Oops! All 6s

**Boss / PvP / meta:**
- ● `bossBlind()` `BossBlindSelected` · `bossDefeated()` `BossDefeated` · `bossAbilityActive()` `BossAbilityActive`
- ● `inPvpBlind()` `InPvpBlind` · `otherJokerRarity(r)` `OtherJokerRarity`
- ○ `handsSinceAcquired(n)` `HandsSinceAcquire` (Seltzer) · `using(type)` `ConsumableType`

**Combinators** (cheap, declarative — the only "logic"):
- ● `all(…)` `And` · `any(…)` `Or` · `not(…)` `Not` · `always()` `Always`

---

## DO — the verb (`Effect`)  ← the part being cleaned up

Today a 13-arm `EffectTemplate.Op` null-bag. Target: a sealed set at the **domain** level.

- ● **`Score(op, value)`** — the numeric contribution. `op ∈ {CHIPS, MULT, XMULT, POW_MULT, DOLLARS,
  REPETITIONS, HELD_MULT}`. (`+chips/+mult/x mult/+$/retrigger`.) This is `Modify(scoring.slot)`
  underneath, but authored as a domain verb.
- ● **`MutateCard(who, CardMod)`** — enhance/convert/seal/edition/+chips on a card (Hiker, Midas, the Tarots)
- ● **`Create(spec)`** — cards / consumables / jokers (8 Ball, Cartomancer, Emperor)
- ● **`Destroy(who)`** — a card set or the joker itself (Sixth Sense, Trading; self for Gros Michel)
- ● **`LevelUpHand(who, n)`** — raise a hand's level (Space, Burnt, **and every Planet**)
- ● **`Copy(who → dest)`** — a card → deck (DNA); a joker → row (Ankh)
- ● **`Retrigger(who, n)`** — the one control-flow verb (re-score the focus card)
- ● **`MutateState(self.var, op, value)`** — write the joker's own counter (Ride the Bus, Yorick).
  `op ∈ {ADD, SET, RESET}`. *This replaces the separate `Mutation` type.*
- ◆ `behaviorInCode` — not a verb; the escape hatch (Blueprint, Pizza, Speedrun, To the Moon)

---

## HOW-MUCH — the magnitude (`Value`)

How a number is computed. Each is a domain *shape* (we add a shape when ≥3 jokers need it; we do **not**
build a generic expression evaluator).

- ● `of(n)` `Const` · ● `prop(name)` `Prop` (a declared named constant) · ● `state(x)` `State` (a counter)
- ● `runVar(V)` `RunVar` — read a run variable (×scale) · ● `runVarStep(V,…,per)` `RunVarStep` (per $5)
- ● `count(source, match)` `Count` — number of cards in played/scoring/held/event matching a condition
- ● `stat(which)` `Stat` — a deck/joker aggregate (cards remaining, owned jokers, enhanced count)
- ● `clamp(inner,min,max)` `Clamp` (decay floors) · `stateStep(x,…,per)` `StateStep` (Yorick: per 23)
- ● `random(min,max,seed)` `Random` (Misprint) · `lowestHeld/highestHeld` `HeldExtreme` (Raised Fist)
- ○ `handTypePlays` `HandTypePlays` (Supernova) · `otherJokersSellSum` (Swashbuckler) · `deckRankCount` (Cloud 9)

---

## WHO — the subject (`Selector`)  ← the new, deliberately-small primitive

What an effect acts on. **Keep tiny.** The exotic ones are named-on-demand, not a general machine.

- ● `focus` — the entity this moment is about (the scored card, the discarded set, the sold joker).
  Its cardinality/existence comes from the `when`.
- ● `self` — this joker (its state, its sell value, destroy-self)
- ● `selected` — player-chosen cards (consumables only)
- ○ `cardsMatching(condition)` — held Kings, scoring Hearts (only where a card actually needs it)
- ○ `jokers(self | others | neighbor | random | all)` — Madness/Ceremonial/Hex/Ankh/Gift Card
- ⚙ `run | scoring | shop | boss | hands[key]` — the singleton/registry entities a `Modify` can target
  (e.g. Four Fingers → `hands[flush].size`); engine-level, not a content authoring word

---

## How to read a card in this dictionary

```
Greedy Joker   : when ON_SCORED · if card().suit(DIAMONDS) · do Score(MULT, of(3))
Banner         : when JOKER_MAIN · if always()            · do Score(CHIPS, runVar(DISCARDS_LEFT)·30)
Sixth Sense    : when ON_SCORED · if all(playedHand().sizeExactly(1), card().rankBetween(6,6))
                                · do [Destroy(focus), Create(spectral)]
Four Fingers   : when WHILE_OWNED · if always() · do Modify(hands[flush].size, set, 4)   (standing)
Trading Card   : when PRE_DISCARD · if all(runVar(DISCARDS_USED).exactly(0),
                                           value(count(EVENT, always())).exactly(1))
                                  · do [Score(DOLLARS, of(3)), Destroy(focus)]
```

That's the language. Everything authorable is **● core**; the **○ sugar** rows are helpers built from
core words (so they don't grow the engine); **⚙ engine** rows are the interpreter's internals (hand
predicates, pipeline steps, registry slots) — understood, not exposed; **◆ escape** is the small set of
cards that are honestly just code. The curation rule is fixed: a new ● word must be a domain concept
that ≥3 cards need, or it's ○ / ◆ instead.
