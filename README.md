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

Java 25, Gradle. A single-player run is playable end-to-end **over a real
WebSocket**, server-authoritative. **Full JUnit 5 + AssertJ suite green**
(engine + network).

```
engine ✅  RNG ✅  triggers ✅  run loop ✅  WS+JWT auth ✅  multiplayer ✅  pick/ban ✅  shop ✅  CLI client ✅
next → shop in multiplayer → ranked queue/MMR → Lua client
```

Stack: Java 25 · Gradle · **Javalin** (WebSocket + HTTP, Jetty-backed) ·
**Jackson** (JSON) · **JUnit 5 + AssertJ** (tests).

What's implemented:
- **`rng/`** — deterministic xoshiro256** + per-purpose keyed streams; seed is server-only.
- **`card/`, `hand/`** — cards + faithful `evaluate_poker_hand` port (low/high-ace straights, all categories).
- **`joker/`** — unified `Trigger` event set + `calculate(context)` dispatch; representative jokers across every effect path (flat/per-card/conditional/stateful/retrigger/copy/economy/discard/consumable).
- **`scoring/`** — `ScoringEngine`, a faithful transcription of `evaluate_play`'s ordered pipeline; emits a replay log.
- **`state/`** — `RunState`, `Deck`, and `Ruleset` (competitive rulesets as data).
- **`game/`** — `Blinds` (real `get_blind_amount` curve) + `Run` state machine (blind → score-to-beat → win/lose → ante progression + economy) + lifecycle `GameEvents`.
- **`net/`** — `ClientView` (the only thing a client may see) + `ServerUpdate`
  (accept/reject + view + replay log to animate) + **`GameServer`** (Javalin
  WebSocket adapter, Jackson JSON) + `ServerMain`. The snappy-feel + info-hiding
  boundary in code; the transport does no game logic.
- **`intent/`** — `Intent` (PlayHand/Discard) + validating `IntentHandler`. There
  is **no protocol path to submit a score** — the server computes it.

## Build & run

Requires JDK 25 (Gradle wrapper handles the rest).

```bash
./gradlew test                      # full JUnit 5 + AssertJ suite (engine + WebSocket e2e + auth)
./gradlew run                       # start the server: http+ws on 127.0.0.1:8788
./gradlew play --console=plain -q   # play a solo run in the terminal (embeds the server)
./gradlew loadTest -Pargs="200 10"  # load harness: <connections> <hands-per-conn>
```

`play` is a tiny REPL client: `new [seed]`, `play 0 1 2 3 4`, `discard 0 1`,
`buy 0`, `reroll`, `proceed`, `quit`. Example turn:
`new ABC` → deals a hand; `play 0 1 2 3 4` → the server scores it and shows
`scored: 58 x 2.0` and your new total. A real, authoritative game over WebSocket.

### Wire protocol (JSON over WebSocket)
1. `POST /login {"username":"alice"}` → `{"token":"…","playerId":"alice"}`
   (dev: any username; intended to validate a Steam ticket later).
2. Connect `ws://…/game`, then authenticate (first message):
   `{"type":"auth","token":"…"}` → `{"type":"authed","playerId":"alice"}`.
3. Play: `{"type":"newRun","seed":"ABC"}`, `{"type":"playHand","cards":[0,1,2,3,4]}`,
   `{"type":"discard","cards":[…]}` → `{"type":"update","accepted":true,"view":{…},"replay":[…]}`.
4. On clearing a blind the view enters the shop (`view.shop` lists offerings):
   `{"type":"buyJoker","index":0}`, `{"type":"reroll"}`, then `{"type":"proceed"}`
   to the next blind. Money/slots/affordability are all server-enforced.

There is **no `score` field a client can send** — the server computes it.

### Multiplayer (lobby + duel)
Two authenticated players, **same seed** each round (identical cards — pure
skill), score race, lives, first to 0 loses. The server pushes each player live
opponent state — the first use of WebSocket server-push.
- Host: `{"type":"createLobby"}` → `{"type":"lobbyCreated","code":"AB3KP"}`
- Guest: `{"type":"joinLobby","code":"AB3KP"}`
- **Pick/ban draft:** both get `{"type":"draftState","pool":[…],"yourTurn":bool}`;
  the player whose turn it is sends `{"type":"ban","ruleset":"Blitz"}`, alternating
  until one remains → `{"type":"draftResult","ruleset":"Standard"}` → `matchStart`.
  Server-enforced turn order — no Discord needed.
- Play: same `playHand`/`discard` intents → you get `update`, your opponent gets
  `opponentUpdate` (score/handsLeft/done). When both finish: `roundResult`
  (scores + winner + lives), then `roundStart` or `matchResult`.

### Poking it manually
```bash
TOKEN=$(curl -s -XPOST localhost:8788/login -H 'content-type: application/json' \
  -d '{"username":"alice"}' | jq -r .token)
websocat ws://127.0.0.1:8788/game
> {"type":"auth","token":"<paste TOKEN>"}
> {"type":"newRun","seed":"ABC"}
> {"type":"playHand","cards":[0,1,2,3,4]}
```

### Performance
A turn-based card game is light: tiny JSON messages, a few actions/min per player.
The load harness on a single dev machine (loopback) sustains thousands of msg/s
at hundreds of concurrent connections with single-digit-ms server-side
processing. Real-world latency is dominated by network RTT, addressed by regional
deployment — not the framework. Javalin/Jetty is comfortably sufficient; scaling
is horizontal (more instances + matchmaker).

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
