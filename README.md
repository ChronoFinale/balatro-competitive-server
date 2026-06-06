# Balatro Competitive Server

A ground-up, **server-authoritative** game engine for **competitive Balatro**.
The goal is fair play above all: the server simulates the entire game — state,
RNG, scoring — and the client only sends *intents* and renders authoritative
results. It deliberately **trades flexibility for provably fair competition**,
focused on a small set of well-defined ranked rulesets rather than open-ended
modding.

> Personal pet side project, licensed **AGPL-3.0** (see `LICENSE`). Not affiliated
> with or endorsed by LocalThunk / Playstack. Clean-room reimplementation of game
> *mechanics* only — ships no game assets or code; requires owning Balatro to play.

## Why

Today's Balatro multiplayer is a **relay**: each client computes its own game and
reports its score, and the server trusts it (`client.score = whatever the client
claimed`). Because the modding stack (Steamodded) makes the client a fully
programmable VM that can even redefine the scoring function, **no client
computation can be trusted**. The only architecture that survives is an
authoritative server. This project is that server.

Cheat model, and how each class is closed:

| threat | defense |
|---|---|
| Fabricated score / money / cards | **Authoritative server** — client sends intents, never outcomes |
| Reading hidden info (deck order, next shop, seed) | **Info-hiding** — `ClientView` structurally carries no deck/seed |
| Game-speed / tempo abuse in timed modes | **Server owns the clock** — client speed is cosmetic |
| Logic mods on a client | **No effect** — the client isn't the authority |

## Status

Java 25 engine core, **57 self-tests passing**. Single-player run is playable
end-to-end server-side; multiplayer coupling is next.

```
engine core ✅  triggers ✅  RNG ✅  intents ✅  run loop ✅  client/server contract ✅
next → multiplayer match coupling → shop → matchmaking/network transport
```

What's implemented:
- **`rng/`** — deterministic xoshiro256** + per-purpose keyed streams; seed is server-only.
- **`card/`, `hand/`** — cards + faithful `evaluate_poker_hand` port (low/high-ace straights, all categories).
- **`joker/`** — unified `Trigger` event set + `calculate(context)` dispatch; representative jokers across every effect path (flat/per-card/conditional/stateful/retrigger/copy/economy/discard/consumable).
- **`scoring/`** — `ScoringEngine`, a faithful transcription of `evaluate_play`'s ordered pipeline; emits a replay log.
- **`state/`** — `RunState`, `Deck`, and `Ruleset` (competitive rulesets as data).
- **`game/`** — `Blinds` (real `get_blind_amount` curve) + `Run` state machine (blind → score-to-beat → win/lose → ante progression + economy) + lifecycle `GameEvents`.
- **`net/`** — `ClientView` (the only thing a client may see) + `ServerUpdate` (accept/reject + view + replay log to animate). The snappy-feel + info-hiding boundary in code.
- **`intent/`** — `Intent` (PlayHand/Discard) + validating `IntentHandler`. There is **no protocol path to submit a score** — the server computes it.

## Build & run

Requires JDK 25.

```powershell
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin"
$src = Get-ChildItem -Recurse -Filter *.java src\main\java | % { $_.FullName }
& "$jdk\javac.exe" --release 25 -d out $src
& "$jdk\java.exe" -cp out com.balatromp.engine.SelfTest
```

(A `build.gradle` is included; `gradle run` invokes the self-test.)

## Design docs

- `balatro-engine-spec.md` — the engine foundation, grounded in how the real game
  works (scoring pipeline, event/trigger set, RNG/seed model, hidden-info boundary).
- `spike/FINDINGS.md` — feasibility spikes (headless Lua, headless LÖVE) that led
  to the native-reimplementation decision.

## Roadmap

1. Multiplayer match coupling — two runs, PvP-blind score comparison, lives.
2. Shop / economy loop (curated competitive content).
3. Network transport + lobby/matchmaking (regional, persistent connection).
4. Differential-test harness vs. real Balatro for content fidelity.
5. Ranked rulesets + ladder.
