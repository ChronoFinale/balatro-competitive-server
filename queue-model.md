# Determinism: the game-long queue model

This is how we make a 1v1 **fair**: every source of in-run randomness is a
**game-long queue** — one deterministic sequence, shared by both players on a
seed, each advancing their own cursor by their own actions. It's the same
*behavioral* model Balatro Multiplayer (BMP) uses for ranked, rebuilt on our own
RNG (we are **not** byte-compatible with BMP seeds, by design — spec §8). See
`bmp-changes-reference` memory for the source doc (SurCats, BMP v0.3.3).

## Why queues, not per-event RNG

If two players draw randomness live, timing and order desync them (player A
rerolls 3×, player B 1×; A plays a Lucky card with 0 hands left, B with 2). A
shared, append-only sequence with an independent per-player cursor removes that:
the **Nth item of a category is always the same item**, so identical choices give
identical results — and a different number of rerolls just means you're further
along the *same* list.

## The primitives (`com.balatromp.engine.rng`)

- **`GameQueue<T>`** — a lazily-generated, cached sequence with a cursor.
  - `next()` / `peek()` — consume / look at the next item.
  - `nextWhere(pred)` — **block/skip**: consume past items that can't appear right
    now (a Planet for a hand you can't level, an owned Voucher, a blocked Joker)
    and return the first acceptable one. Never re-rolls — it advances.
  - `take(n)` / `take(n, pred)` — reveal a shop-worth in order.
- **`QueueSet`** — the run-scoped bundle of all category queues, derived from the
  run seed. Lives on `RunState.queues`. Both players build one from the same seed,
  so every queue matches; cursors move independently.

## Migrated so far

| System | Queue key(s) | Behavior matched |
|---|---|---|
| Shop jokers | `jokers` (drawn from the ruleset's pool) | both players see the same shop; reroll advances the same sequence |
| Shop planets | `planets` | same as above |
| Lucky card | `lucky_mult` (1/5), `lucky_money` (1/15) | identical procs for the same cards, any order/timing; threshold honors `probabilityNumerator` |
| Glass break | `glass` (1/4) | identical breaks |

Probability queues store raw rolls `[0,1)`; the threshold is applied at read time
so modifiers (Oops! All 6s) change the threshold without disturbing the sequence.

## To build queue-shaped when those systems land (BMP mapping)

These don't exist in our engine yet; build each as a `QueueSet` queue with the
noted semantics so behavior matches BMP without a later rewrite.

- **Rarity-split jokers + main-queue-of-tags.** Today shop jokers are one uniform
  queue. BMP: separate `joker_common` / `joker_uncommon` / `joker_rare` queues,
  plus a **main queue of category tags** (CJ/UJ/RJ/Tarot/Planet/Spectral/Card)
  that decides each shop slot's category; a slot whose category can't appear
  (no Ghost deck → Spectral) is skipped and the next tag revealed. Buffoon
  packs / Judgement pull the next joker from the appropriate rarity queue.
- **Consumables: Up-Top vs Pack.** Two separate queues per type (Tarot/Planet/
  Spectral): packs advance the **Pack** queue; all other generation (Up-Top
  jokers like Superposition/Seance, The Fool, etc.) uses **Up-Top**.
- **Soul / Black Hole.** A single game-long hit/miss queue; each consumable
  created in a Tarot/Spectral pack rolls it, and on a hit The Soul is **inserted**
  (pushing the consumable queue back one).
- **Packs queue.** A game-long list of packs advanced only by *seeing a shop*, so
  skipping a blind shifts your packs a blind later rather than losing them
  (skip-offset between players).
- **Voucher queue.** Numbers **1–16** (not 32): each resolves Tier-1 → Tier-2 →
  skip based on what you already own. Voucher tags advance it (with a back-to-back
  duplicate skipped).
- **Separate joker queues** for Rare-tag/Wraith, Uncommon-tag, Riff-Raff/Top-Up —
  each its own queue, *not* taking from the shop, so they can yield jokers the
  nemesis never sees.
- **Bloodstone.** Two queues: a game-long one (advances per heart played outside
  PvP) and a **per-ante PvP queue that resets to its start after each hand**, so
  equal triggers give equal hits regardless of hands-left.
- **Idol / To-Do / Invisible.** Deterministic selection standardized so both
  players resolve identically (Idol sorts the deck then rolls 1–1000; To-Do rolls
  over unlocked hand-types; Invisible picks by a shared position rule).

## PvP-aware effects

Several BMP jokers read the **nemesis** (Defensive, Conjoined, Pizza, Let's Go
Gambling, Penny Pincher, Speedrun). Our `EvaluationContext` has no opponent
handle yet; expressing these in `JokerDef` needs an opponent/nemesis context
(hands-left, lives, spend-last-ante) plus a "send a Phantom" hook. Tracked as a
joker-builder extension, not part of the queue layer.
