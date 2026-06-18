# 40 — JokerDef Algebra v2 (Definitive Spec)

The data-driven algebra (`com.balatro.engine.joker` + `joker/def`) that must express the
**entire** vanilla + BMP 0.4.0 content set: 61 Common + 64 Uncommon + 20 Rare + 5 Legendary
jokers, 9 BMP-exclusive jokers, 22 Tarots, 12 Planets + Black Hole, 18 Spectrals, and the
pack/edition/enhancement/seal layer (docs 10–16, 19).

This spec enumerates **every** new building block required on top of v1 (the algebra in the task
brief: 24 Triggers, 21 Conditions, 4 Value shapes, 6 EffectOps, 3 Mutation ops). Each block has a
precise definition, the exact jokers/cards it unlocks (counted), and determinism/authority notes.
Ordered by **leverage** (jokers+cards unlocked per block).

## v1 coverage baseline (what already works — do not rebuild)

Fully expressible today across the four catalogues (~63 items): Joker, 4 Suit-Mult, 5 Type-Mult,
5 Type-Chips, Half, Mystic Summit, Banner, Scary Face, Even Steven, Odd Todd, Scholar, Walkie
Talkie, Smiley, Shoot the Moon, Ride the Bus, Runner, Square, Golden Joker, Delayed Gratification,
Faceless, Golden Ticket, Gros Michel (+Mult part), Cavendish (XMult part); Mime, Dusk, Hack,
Hologram, Acrobat, Sock & Buskin, Rough Gem, Arrowhead, Onyx Agate, Throwback, Flash Card, Spare
Trousers, Constellation; Baron, Wee, Hit the Road, The Duo/Trio/Family/Order/Tribe; Triboulet,
Yorick. Plus the **engine already ships** `RunState.probabilityNumerator`, `QueueSet`,
`levelUpHand`, `consumableSlots`, `RandomStreams.stream(key)` — the substrate the v2 blocks plug
into.

Notation: **[T]** new Trigger, **[C]** new Condition, **[V]** new Value source, **[O]** new
EffectOp / effect-TYPE, **[M]** new Mutation op, **[X]** new EvaluationContext field, **[F]**
new RunState field.

---

# Tier 1 — Highest leverage (unlock 10+ items each)

## 1. [O] `CREATE` effect-type — create card / consumable / joker (queue-backed)
**Definition.** New op family: `Create(what, spec, count, sourceKey)` where `what ∈
{PLAYING_CARD, TAROT, PLANET, SPECTRAL, JOKER, TAG}`. `spec` carries optional constraints
(rarity, enhancement pool, suit/rank pool, edition, "copy of card X"). Each create draws from a
named deterministic queue (`QueueSet`) keyed by `sourceKey` ("emp", "pri", "wra", "sou", "jud",
"8ball", "fool", "speedrun", "cartomancer", …) and is gated on free-slot preconditions
(`consumableSlots`, joker `card_limit`).
**Unlocks (count ≈ 38):**
- Jokers (9): 8 Ball, Hallucination, Riff-raff (create joker), Marble Joker (Stone→deck),
  Certificate (sealed card), Cartomancer (Tarot), Sixth Sense + Seance (Spectral), Vagabond
  (Tarot), DNA (copy of played card), Cryptid-via-joker n/a; plus Perkeo (Negative consumable),
  SPEEDRUN (Spectral), Wraith/Soul/Judgement created via consumables.
- Tarots (5): Emperor (2 Tarot), High Priestess (2 Planet), Judgement (random Joker), Fool (copy
  last consumable), Wheel-of-Fortune target side n/a.
- Spectrals (9): Familiar, Grim, Incantation (create enhanced playing cards), Cryptid (copy
  selected card), Wraith (Rare joker), The Soul (Legendary joker), plus Ankh's copy path.
- Planets: the Blue-seal "create Planet for most-played hand" generator.
**Design notes (authority/determinism).** Creation is server-authoritative; all randomness routes
through `RandomStreams.stream(sourceKey)` / `QueueSet`, never client input. Slot checks happen
server-side before the queue advances (mirrors `can_use_consumeable`). The "copy of card X" form
(DNA, Cryptid, Fool) is RNG-free — it clones a referenced entity, so it is deterministic by
construction. Queue advancement must be ordered identically across both players (the shared "Up
Top" queues) so PvP stays in sync.

## 2. [V] Deck / run-stat aggregate Value sources
**Definition.** New `Value.Stat(which, base, scale)` reading authoritative run aggregates:
`DECK_SIZE`, `DECK_REMAINING`, `DECK_ENH_COUNT(enh)`, `DECK_RANK_COUNT(rankSet)`,
`DECK_SUIT_COUNT(suit)`, `STARTING_DECK_DELTA`, `ENHANCED_CARD_COUNT`, `OWNED_JOKERS`,
`OWNED_JOKERS_SELL_SUM`, `EMPTY_JOKER_SLOTS`, `HAND_TYPE_PLAYED(currentOrNamed)`,
`HAND_TYPE_PLAYED_THIS_ROUND`, `CONSUMABLE_USED(set)`, `UNIQUE_PLANETS_USED`,
`CARDS_SOLD_THIS_RUN`. All are pure reads of `RunState`/`Deck`.
**Unlocks (count ≈ 17):** Blue Joker (DECK_REMAINING), Steel Joker (DECK_ENH_COUNT STEEL), Stone
Joker (DECK_ENH_COUNT STONE), Cloud 9 (DECK_RANK_COUNT 9), Erosion (STARTING_DECK_DELTA),
Driver's License (ENHANCED_CARD_COUNT≥16), Abstract Joker (OWNED_JOKERS), Swashbuckler
(OWNED_JOKERS_SELL_SUM), Joker Stencil (EMPTY_JOKER_SLOTS), Supernova (HAND_TYPE_PLAYED),
Card Sharp (HAND_TYPE_PLAYED_THIS_ROUND), Fortune Teller (CONSUMABLE_USED Tarot), Satellite
(UNIQUE_PLANETS_USED), Campfire + Taxes (CARDS_SOLD…), Ceremonial Dagger (sell-value read),
Temperance/Swashbuckler (joker sell sum).
**Design notes.** Read-only over server state; no RNG, fully deterministic. Several require new
`RunState` counters maintained by lifecycle hooks (`handTypePlays`, `uniquePlanetsUsed`,
`cardsSoldThisRun`) — see block 17 [F]. `OWNED_JOKERS_SELL_SUM` excludes `self`.

## 3. [O] `DESTROY` effect-type (self, other-joker, played/held card)
**Definition.** `Destroy(target, selector, condition)` where `target ∈
{SELF, OTHER_JOKER, PLAYING_CARD}`. Selectors: `SELF`, `NEIGHBOR_RIGHT`, `RANDOM_JOKER`,
`ALL_OTHER_JOKERS(keepSelector)`, `SELECTED_CARDS`, `RANDOM_CARDS(n)`, `THIS_SCORED_CARD`.
Honors `eternal`/`mp_phantom` exclusions. Self-destroy supports a `when` predicate (at-zero).
Emits a `remove_playing_cards` calculate-context (block 13 [T]).
**Unlocks (count ≈ 15):** Ice Cream + Popcorn (self-destroy at 0), Gros Michel + Cavendish
(probabilistic self-destroy — with block 4), Seltzer (self-destroy after N), Ceremonial Dagger +
Madness (destroy a joker), Hex + Ankh (destroy all other jokers), Trading Card + Sixth Sense +
Hanged Man (destroy played/held card), Familiar/Grim/Incantation/Immolate/Ouija (destroy random
hand cards), Vampire (destroy enhancement — see block 12), Pizza (self-consume).
**Design notes.** Server applies destruction after effect resolution; "random" selection uses a
keyed stream (`random_destroy`, `immolate`, `ankh_choice`). Joker-state for destroyed instances is
GC'd by identity (`IdentityHashMap`). PvP: destruction of phantom mirrors is suppressed by the
existing phantom guard.

## 4. [C]/[V] `Chance` probabilistic proc + `Random` value source
**Definition.** Two related blocks sharing the keyed-RNG substrate:
- **[C] `Chance(numerator, denominator, seedKey)`** — true iff a draw on
  `RandomStreams.stream(seedKey)` clears the threshold, scaled by
  `RunState.probabilityNumerator` (Oops! All 6s already present). A gate condition.
- **[V] `Random(min, max, seedKey)`** — a uniform magnitude (Misprint).
**Unlocks (count ≈ 13):** Business Card, 8 Ball, Hallucination, Reserved Parking, Gros Michel,
Cavendish, Space Joker, Bloodstone, Lucky Cat, Let's Go Gambling, Misprint (Random),
Wheel-of-Fortune (1/4), plus Lucky-card enhancement procs (1/5, 1/15).
**Design notes.** All probability flows through server-only seeded streams (existing
`RandomStreams`) — the master seed never leaves the server (hidden-information boundary). The
`probabilityNumerator` field already models Oops! All 6s. For PvP fairness, "queue-backed" procs
(Bloodstone per-ante queue, Lucky game-long queues, glass-break queue) use `QueueSet` entries that
both players advance identically; `seedKey` selects the queue. Determinism: identical seed + key +
draw order ⇒ identical result on every server replay.

## 5. [V]/[X] NEMESIS reads (PvP opponent state)
**Definition.** New `Value.Nemesis(which, base, scale)` + matching `[X]` context field
`nemesis` (an opponent-projection struct). Variables: `NEMESIS_LIVES`, `NEMESIS_HANDS_LEFT`,
`NEMESIS_SKIPS`, `NEMESIS_SELLS_SINCE_PVP`, `NEMESIS_SHOP_SPEND(ante)`, plus our own `LIVES`,
`SKIPS`. Reads a server-maintained opponent snapshot.
**Unlocks (count = 6):** Defensive Joker (NEMESIS_LIVES − LIVES), Conjoined (NEMESIS_HANDS_LEFT),
Skip-Off (NEMESIS_SKIPS, SKIPS), Penny Pincher (NEMESIS_SHOP_SPEND), Taxes
(NEMESIS_SELLS_SINCE_PVP), Pacifist/Conjoined gating shares the InPvP condition (block 6).
**Design notes.** The nemesis snapshot is server-owned and pushed at sync points (blind select,
end of round); it is the projection the server already trusts for PvP, not client-reported. All
reads are deterministic given the synced snapshot. This is the single largest gap for the
BMP-exclusive set.

---

# Tier 2 — Medium leverage (3–9 items each)

## 6. [C] PvP / blind-key conditions
**Definition.** `InPvPBlind()`, and `BlindKeyIs(String)` (e.g. `"bl_mp_nemesis"`). Read
`RunState`/blind context. `Not(InPvPBlind())` composes via existing `Not`.
**Unlocks (count = 5):** Conjoined (only in PvP), Pacifist (only NOT in PvP), Taxes (commit on
nemesis blind), and gates for Let's Go Gambling's misfire + Pizza's end-of-pvp.
**Design notes.** Pure read of authoritative blind state; deterministic.

## 7. [O] Run-resource modifiers (hands/discards/hand-size/debt/interest/slots)
**Definition.** Passive/active op `ModifyResource(resource, delta, scope)` where `resource ∈
{HAND_SIZE, HANDS_PER_ROUND, DISCARDS_PER_ROUND, HANDS_LEFT, DISCARDS_LEFT, DEBT_FLOOR,
INTEREST_PER_5, CONSUMABLE_SLOTS, JOKER_SLOTS}` and `scope ∈ {PASSIVE, THIS_ROUND, ONCE}`.
Also covers granting a resource to the **nemesis** (`grantNemesis`).
**Unlocks (count ≈ 13):** Juggler, Drunkard, Credit Card (debt floor), Stuntman (−2 hand size),
Turtle Bean, Troubadour, Merry Andy, Burglar (+3 hands/−discards), Skip-Off (+hands/+discards),
Pizza (+2 self / +1 nemesis discard), Ectoplasm (escalating −hand size), Ouija (vanilla −1 hand
size), To the Moon (interest).
**Design notes.** Server mutates `RunState` directly; `THIS_ROUND` deltas are reverted at
round end. Nemesis grants are pushed via the trusted PvP channel (the `eat_pizza`/
`lets_go_gambling_nemesis` actions). Deterministic — no RNG. Self-decrementing buffs (Turtle Bean
−1/round, Ectoplasm escalation) use a per-joker state counter (block 16 [M]).

## 8. [M] `ADD_CLAMP` mutation (floored/capped accumulators)
**Definition.** Extend `Mutation.Op` with `ADD_CLAMP(by, min, max)` — `next = clamp(n+by,min,max)`.
Subsumes floored decrements and capped growth. Add an optional `min`/`max` to `Value.State` reads
so the cap is enforced on read too.
**Unlocks (count = 4):** Green Joker (−1 on discard, floor 0), Ramen (−0.01/card, floor 1),
Conjoined (clamp 1..3 — also via block 9), Loyalty Card (cycle counter).
**Design notes.** Pure arithmetic on server state; deterministic. Keeps the "return null at
identity" property of `EffectTemplate`.

## 9. [V] Value combinators: clamp, floor-div, scaled-runvar
**Definition.** `Value.Clamp(inner, min, max)`, `Value.FloorDiv(inner, divisor)`,
and confirm `RunVar`/`Stat` support a non-zero `scale` (already shaped `base+scale*n`) so
`scale×MONEY` and `floor(MONEY/5)` are expressible.
**Unlocks (count = 5):** Bull (2×MONEY — already shapeable), Bootstraps (floor(MONEY/5)×2),
Conjoined (clamp X-mult ≤3), Penny Pincher (floor(spend/3)), Defensive (max(0, …)).
**Design notes.** Pure functional composition over existing Value tree; deterministic.

## 10. [O] `LEVEL_UP_HAND` effect-type + per-hand-type reads
**Definition.** `LevelUpHand(target)` where `target ∈ {PLAYED_HAND, NAMED(hand), ALL,
FIRST_DISCARDED_HAND, MOST_PLAYED}`. Reuses `RunState.levelUpHand`. Pairs with the
`HAND_TYPE_PLAYED`/`MOST_PLAYED` reads (block 2 [V]) and a `[C] IsMostPlayedHand()` condition.
**Unlocks (count = 7):** Space Joker (level played hand, probabilistic), Burnt Joker (level first
discarded hand), Black Hole (level ALL), Obelisk (reset on most-played), Card Sharp
(played-this-round), Supernova, Blue-seal Planet generation.
**Design notes.** `levelUpHand` already exists and is server-authoritative; this just exposes it
as a data op. "Most-played" ties resolved by the documented `>=` iteration (Obelisk). Deterministic.

## 11. [O] Copy / retrigger another joker (Blueprint/Brainstorm/Ankh/Invisible)
**Definition.** `CopyJoker(selector)` where `selector ∈ {NEIGHBOR_RIGHT, LEFTMOST, RANDOM_OWNED,
SELF_DUPLICATE}`. Re-enters the target's rules via `EvaluationContext.forCopy` (already exists,
with `blueprintDepth` guard). `SELF_DUPLICATE`/`RANDOM_OWNED` create a persistent copy (uses
block 1 create + block 3 destroy semantics for on-sell).
**Unlocks (count = 4):** Blueprint, Brainstorm (live copy), Invisible Joker (sell→copy),
Ankh (copy joker). Indirectly multiplies the value of every other block.
**Design notes.** `forCopy` already increments `blueprintDepth` and the engine caps recursion at
`#jokers+1`; mutations are skipped at `blueprintDepth>0` (already in `Mutation`). Phantom copies
excluded by edition guard. Deterministic.

## 12. [O]/[C] Mutate a card (enhancement/seal/edition/rank/suit/perma-chip)
**Definition.** `MutateCard(target, change)` where `change ∈ {SET_ENHANCEMENT(e),
REMOVE_ENHANCEMENT, SET_SEAL(s), SET_EDITION(e), SET_SUIT(suit), SET_RANK(rank), INC_RANK(n),
ADD_PERMA_CHIPS(n)}`, target = selected/scored/random/all-in-hand. Plus a `[C]
DestroyedCardWas(condition)` and `[C] HeldCardsAllSuit(suitSet)` for read sites.
**Unlocks (count ≈ 25):** Hiker (perma-chips), Midas Mask (face→Gold), Vampire (remove
enhancement), the 15 enhance/convert Tarots (Magician, Empress, Hierophant, Lovers, Chariot,
Justice, Devil, Tower, Star, Moon, Sun, World, Strength=INC_RANK, Death=copy, Hanged Man=destroy),
the 4 seal Spectrals (Talisman/Deja Vu/Trance/Medium), Aura (edition on card), Sigil (all→suit),
Ouija (all→rank), Ectoplasm/Hex (edition on joker), Blackboard/Glass-Joker read conditions, Canio
(DestroyedCardWas face).
**Design notes.** Selected-target mutations take a server-validated highlight list (mirror
`can_use_consumeable` min/max); no RNG, fully replayable. Random-target uses keyed streams.
Enhancement/Seal/Edition enums already exist (`engine/card/`). The 0.4.0 Gold-card $4 question is a
data value, not a new block.

## 13. [T] Lifecycle trigger gaps
**Definition.** Add Triggers: `SKIP_BOOSTER` (Red Card), `END_OF_PVP` (Pizza, `mp_end_of_pvp`),
`MP_SPEEDRUN` (server timing proc), `BOSS_DEFEATED` (Rocket payout / Campfire reset),
`CARDS_REMOVED` (the `remove_playing_cards` context emitted by destroyers). Also a
`[X] removedCards` context field.
**Unlocks (count = 6):** Red Card (SKIP_BOOSTER), Pizza (END_OF_PVP), SPEEDRUN (MP_SPEEDRUN),
Rocket + Campfire (BOSS_DEFEATED), any joker reacting to Hanged Man/Immolate (CARDS_REMOVED).
**Design notes.** Each is one raise-point in `GameEvents`/PvP sync, per the Trigger enum's own doc
("add a value + one raise-point"). `MP_SPEEDRUN`/`END_OF_PVP` are server-pushed timing events, not
client-driven. Deterministic given the synced PvP timeline.

## 14. [C] Set-membership & positional conditions
**Definition.** `ScoredRankIn(int[] ranks)` (rank-set, not range), `ScoredPositionIs(n)` /
`FirstScoredMatching(condition)` (positional), `HandContainsAnyOf(handType…)`,
`ScoringContainsAllSuits()` / `ScoringContainsClubPlusOther()` (suit-composition with Wild
resolution).
**Unlocks (count ≈ 9):** Fibonacci (ranks {A,2,3,5,8}), Walkie Talkie (already via Or, cleaner
here), Photograph (first scored face), Hanging Chad (scored #1 & #2), Spare Trousers
(Two Pair OR Full House), Flower Pot (all 4 suits), Seeing Double (Club + other), Triboulet
(K/Q set), The Idol (rank+suit match).
**Design notes.** Pure per-card/per-hand predicates reusing `EvaluationContext`. Wild-card suit
resolution must match hand-eval; deterministic. Positional selection reads `scoringCards` order
(server-fixed).

## 15. [V]/[M] Round-seeded random targets
**Definition.** `[M] SET_RANDOM(domain, seedKey)` mutation that, on a round trigger, stores a
random target into joker state; domains: `RANK`, `SUIT`, `RANK_AND_SUIT`, `POKER_HAND`. Read back
via `Value.State`/a new `[C] ScoredMatchesStateTarget`.
**Unlocks (count = 6):** Mail-In Rebate (round rank), To Do List (round hand), Castle (round suit),
Ancient Joker (round suit), The Idol (round rank+suit), Wheel-of-Fortune target pick.
**Design notes.** The per-round re-roll uses a keyed stream seeded by ante/round so both players
get the **same** target (PvP-synced, per the BMP Idol/Ancient determinism note). The Idol's
deterministic sort-based pick is a `seedKey` variant. Server-authoritative.

---

# Tier 3 — Lower leverage / specialized (1–2 items each)

## 16. [M] Self-decrement / countdown state on lifecycle triggers
**Definition.** Allow `Mutation` on any lifecycle trigger with `ADD_CLAMP` (block 8) to implement
self-decrementing buffs and countdowns; pair with self-destroy `when` (block 3).
**Unlocks (count = 4):** Turtle Bean (−1 hand size/round), Seltzer (countdown N hands), Ice Cream
(−5 chips/hand), Popcorn (−4 mult/round). (Effect values already expressible via `State`.)
**Design notes.** Pure server-state; deterministic.

## 17. [F] New RunState counters & sell-value model
**Definition.** Add server-only fields: `lives`, `skips`, `cardsSoldThisRun`,
`handTypePlays(EnumMap)`, `handTypePlaysThisRound`, `uniquePlanetsUsed(Set)`,
`consumableUsage(EnumMap)`, `lastTarotOrPlanetUsed`, `ecto_minus`, per-joker `sellValue` +
`addSellValue`. Maintained by lifecycle hooks; read by block 2 [V].
**Unlocks (count ≈ 12):** Egg + Gift Card (sell value), Fortune Teller, Satellite, Supernova,
Card Sharp, Obelisk, Campfire, Taxes, Skip-Off, The Fool (lastTarotOrPlanetUsed), Ectoplasm.
**Design notes.** All server-owned; never serialized to client. Deterministic accumulation.

## 18. [O] Economy / sell-value ops
**Definition.** `Economy(kind, value, cap)` where `kind ∈ {EASE_DOLLARS, SET_DOLLARS,
DOUBLE_MONEY, ADD_SELL_VALUE(target), JOKER_SELL_SUM_PAYOUT, FREE_REROLL}`.
**Unlocks (count = 8):** Hermit (double, cap 20), Temperance (sell-sum, cap 50), Immolate (+$20),
Wraith (set $0), Egg + Gift Card (add sell value), Chaos the Clown (free reroll), Diet Cola
(create tag — via block 1). 
**Design notes.** Deterministic economy mutations on `RunState.money`/sell values.

## 19. [O] Blind/global rule modifiers (meta hand-eval & shop)
**Definition.** `GlobalModifier(kind)` — a passive flag set on the run: `FOUR_CARD_FLUSH_STRAIGHT`
(Four Fingers), `GAPPED_STRAIGHT` (Shortcut), `SUIT_EQUIVALENCE` (Smeared), `ALL_FACE`
(Pareidolia), `ALL_CARDS_SCORE` (Splash), `ALLOW_DUPLICATES` (Showman), `FREE_PLANETS` (Astronomer),
`ODDS_MULTIPLIER` (Oops! All 6s — already via `probabilityNumerator`), `DISABLE_BOSS` (Chicot /
Luchador-on-sell), `PREVENT_DEATH` (Mr. Bones).
**Unlocks (count ≈ 12):** Four Fingers, Shortcut, Smeared, Pareidolia, Splash, Showman, Astronomer,
Oops! All 6s, Chicot, Luchador, Mr. Bones, Matador (boss-ability-triggered read).
**Design notes.** These mutate global evaluation rules, not per-hand scoring lines. They are
applied at hand-eval time server-side. `DISABLE_BOSS`/`PREVENT_DEATH` need a blind hook;
`MOST` are pure flags. Deterministic. Several (Four Fingers, Shortcut, Smeared, Pareidolia) touch
the hand evaluator and are the strongest candidates to remain **NATIVE** (see below).

## 20. [O] Edition control on create (Negative/Polychrome) + [C] StakeAtLeast
**Definition.** Extend block 1's `spec` with `edition` (FOIL/HOLO/POLY/NEGATIVE) and add
`[C] StakeAtLeast(n)` reading the run stake.
**Unlocks (count = 4):** Perkeo (Negative consumable copy), Ectoplasm/Hex (negative/poly on
joker — overlaps block 12), Defensive Joker (+125 vs +75 at stake ≥6), Judgement (Orange-stake
queue source).
**Design notes.** Stake is server-owned; deterministic.

---

# Items that remain NATIVE (not data-expressible) — and why

These stay hand-coded behind a thin native-joker interface; the data layer can *reference* them by
key but not *define* them, because they alter the hand evaluator, the RNG/queue substrate, or
cross-player networking in ways that are not safely expressible as closed-form data:

1. **Hand-evaluation rule changes** — Four Fingers, Shortcut, Smeared, Pareidolia (and Splash's
   all-cards-score). These rewrite *how a poker hand is detected/which cards score*, upstream of
   the per-card/per-hand condition vocabulary. Exposing them as data would mean making the
   evaluator itself data-driven (out of scope). Implemented as native global flags (block 19) but
   the *evaluator integration* is native.
2. **The Order deterministic RNG shims** — `pseudoshuffle`/`pseudorandom_element` stable-sort
   variants (Immolate, BMP Ouija, Ankh/Hex/Ectoplasm/Wraith, Familiar-family). This is a
   ruleset-level swap of the RNG primitive, not a joker effect; lives in `RandomStreams`/`QueueSet`.
3. **Soul / Black Hole 0.3% forced-spawn roll** — lives in pack/shop *generation*, not in any
   joker's calculate; a native shop-gen concern.
4. **Invisible Joker's MP position-assignment algorithm** — the deterministic
   type→recency→position mapping for fair cross-player duplication is a bespoke networked
   algorithm, not a closed-form value.
5. **Blueprint/Brainstorm recursion engine** — block 11 exposes the *intent*, but the re-entrant
   `forCopy` traversal + depth guard is native engine plumbing.
6. **Boss-blind effect disabling / death prevention** — Chicot, Luchador, Mr. Bones hook the
   blind/lose pipeline; exposed as native global modifiers (block 19), referenced by key.
7. **Phantom-edition / shared-joker networking** — the `mp_phantom` send/remove-phantom machinery
   is pure networking; data jokers only see its guard.

Everything else in docs 10–16/19 becomes data-expressible once Tiers 1–3 land.

---

# Schema summary (v2 delta over v1)

| Kind | v1 count | New in v2 | Notes |
|---|---|---|---|
| **Trigger [T]** | 24 | +5 | `SKIP_BOOSTER`, `END_OF_PVP`, `MP_SPEEDRUN`, `BOSS_DEFEATED`, `CARDS_REMOVED` |
| **Condition [C]** | 21 | +13 | `Chance`, `InPvPBlind`, `BlindKeyIs`, `StakeAtLeast`, `IsMostPlayedHand`, `ScoredRankIn`, `ScoredPositionIs`/`FirstScoredMatching`, `HandContainsAnyOf`, `ScoringContainsAllSuits`, `ScoringContainsClubPlusOther`, `HeldCardsAllSuit`, `DestroyedCardWas`, `ScoredMatchesStateTarget` |
| **Value [V]** | 4 shapes | +5 sources/combinators | `Stat` (deck/run aggregates), `Nemesis`, `Random`, `Clamp`, `FloorDiv` |
| **EffectOp / effect-TYPE [O]** | 6 | +11 types | `CREATE`, `DESTROY`, `LEVEL_UP_HAND`, `COPY_JOKER`, `MUTATE_CARD`, `MODIFY_RESOURCE`, `ECONOMY`, `GLOBAL_MODIFIER`, edition-on-create, grant-nemesis, free-reroll |
| **Mutation op [M]** | 3 (ADD/SET/RESET) | +2 | `ADD_CLAMP`, `SET_RANDOM` |
| **EvaluationContext field [X]** | — | +2 | `nemesis`, `removedCards` |
| **RunState field [F]** | (existing) | +~12 | `lives`, `skips`, `cardsSoldThisRun`, `handTypePlays(+thisRound)`, `uniquePlanetsUsed`, `consumableUsage`, `lastTarotOrPlanetUsed`, `ecto_minus`, per-joker sellValue |

**Leverage ranking (items unlocked):** 1.CREATE (~38) > 12.MUTATE_CARD (~25) > 2.Stat-Values (~17) >
3.DESTROY (~15) > 4.Chance/Random (~13), 7.Resource (~13), 19.GlobalModifier (~12), 17.RunState (~12) >
14.Set/Positional (~9), 18.Economy (~8) > 10.LevelUp (~7) > 5.Nemesis (~6), 6.PvP-cond (~5),
9.Value-combinators (~5), 13.Trigger-gaps (~6), 15.Round-seeded (~6) > 8.ADD_CLAMP (~4),
11.CopyJoker (~4), 16.Self-decrement (~4), 20.Edition/Stake (~4).

**Substrate already present (no new infra needed):** keyed RNG streams (`RandomStreams.stream`),
deterministic queues (`QueueSet`), `probabilityNumerator` (Oops! All 6s), `levelUpHand`,
`consumableSlots`, per-joker identity state bag, `forCopy` Blueprint re-entry + depth guard. v2 is
overwhelmingly additive: new sealed-interface variants + new RunState counters + new GameEvents
raise-points, with the hand-evaluator-coupled and networking-coupled effects staying NATIVE.
