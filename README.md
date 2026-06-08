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
engine ✅  RNG ✅  triggers ✅  run loop ✅  boss blinds ✅  shop ✅  planet/hand levels ✅  auth ✅  multiplayer: Attrition ✅  web UI ✅
data jokers ✅  joker builder ✅  custom rulesets + pools ✅  lobby agreement ✅
game-long queues ✅ (shop + lucky/glass; BMP determinism shape)
focus → Attrition done well.  next → more queue-shaped systems (vouchers/packs/soul) → ranked queue/MMR → custom jokers in ranked (curation)

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
./gradlew run                       # start the server, then open http://127.0.0.1:8788 to PLAY in the browser
./gradlew test                      # full JUnit 5 + AssertJ suite (engine + WebSocket e2e + auth)
./gradlew play --console=plain -q   # play a solo run in the terminal (embeds the server)
./gradlew loadTest -Pargs="200 10"  # load harness: <connections> <hands-per-conn>
./gradlew dependencyUpdates         # report stable dependency upgrades
```

Tests: JUnit 5 + AssertJ (example-based) **and jqwik** (property-based — fuzzes
engine invariants like RNG/scoring determinism across thousands of inputs).

**Play in the browser:** `./gradlew run`, open `http://127.0.0.1:8788`, enter a
name → Connect, then pick **Solo Run** or multiplayer. Click cards to select,
Play Hand / Discard, clear the blind, then Buy jokers / planets / Reroll / Next
Blind. It's a tiny vanilla-JS client over the same WebSocket protocol, CSS-drawn
cards (no game assets shipped).

**Multiplayer — Attrition (friend codes):** one player clicks **Create Lobby** and
shares the 5-letter code; the other **Join**s. The host **proposes a ruleset**
(curated or custom) and the guest sees its params + joker pool and **accepts or
declines** — both must agree before the match starts. Then each builds their own
run on the same seed. You have **4 lives** and lose one for
dying to any blind — failing a normal blind, or losing a **Nemesis blind** (every
Boss from ante 2: a race where running out of hands while behind costs a life).
0 lives and you're out. The opponent panel shows live progress + lives.

**Joker builder:** open `http://127.0.0.1:8788/builder.html` (or the link in the
client header). Jokers are pure data — metadata + scoring rules (`trigger →
condition → effect`) + state mutations for scaling — interpreted server-side, so
a built joker is exactly as authoritative/cheat-proof as a hand-coded one. The
form is driven by `/jokers/schema` (the real engine enums), supports the full
condition/value algebra (incl. nested AND/OR/NOT and count/run-var scaling), and
takes optional 1×/2× PNG art. Saved defs persist under
`web-assets/custom-jokers/` (git-ignored) and reload at startup, registered
through the same authoritative path as built-ins.

**Ruleset builder:** open `http://127.0.0.1:8788/ruleset-builder.html`. A ruleset
**plus the jokers it names fully dictates a match** — starting params, blind
curve, win ante, and the exact **joker pool** the shop may offer. Pick the pool
from all available jokers (curated built-ins + your custom ones), save, and it
persists under `web-assets/custom-rulesets/`. In a lobby the host proposes a
ruleset (curated or custom) and the guest must agree, so custom jokers only ever
appear in a match both players consented to.

**Real card/joker art (optional, local only):** drop Balatro's extracted atlases
(`8BitDeck.webp` for cards, `Jokers.webp` for jokers) into `./web-assets`
(git-ignored — these are game assets, not shipped). The client auto-detects them
and renders real card faces and joker sprites; without them, CSS cards + name
chips. Jokers carry our own descriptions/rarity/cost (sprite position only is
sourced from the game data). Requires owning Balatro.

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

### Multiplayer — Attrition (the competitive focus)
Two authenticated players, **same seed**. Each drives their own complete Run
(blinds, shop, jokers, planets, leveling). You have **4 lives** and lose one for
**dying to any blind**: failing a normal blind's chip requirement, **or** losing
a Nemesis blind. From ante 2 every **Boss is a Nemesis blind** — no chip
requirement, the target is your opponent's score: run out of hands while behind
and you lose a life (if both run out, the lower score loses; ties cost nothing).
0 lives = out. The server pushes each player a live opponent summary + lives.
(Attrition is the one mode we're doing well first; other formats can come later.)
- Host: `{"type":"createLobby"}` → `{"type":"lobbyCreated","code":"AB3KP"}`
- Guest: `{"type":"joinLobby","code":"AB3KP"}`
- **Ruleset agreement:** both get `{"type":"lobbyReady","youPropose":bool,"rulesets":[…]}`
  (curated + custom). The host sends `{"type":"proposeRuleset","name":"Standard"}`;
  the guest gets `{"type":"rulesetProposed","ruleset":{…pool…}}` and replies
  `{"type":"respondRuleset","accept":true}` → `{"type":"rulesetAgreed"}` →
  `matchStart` (decline returns to the host to propose again).
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
- `queue-model.md` — the game-long queue determinism model (our take on BMP's
  shared-sequence fairness), what's migrated, and the mapping for systems still to
  build queue-shaped (vouchers, packs, Soul, Bloodstone, rarity-split jokers).
- `spike/FINDINGS.md` — feasibility spikes (headless Lua, headless LÖVE) that led
  to the native-reimplementation decision.

## Roadmap

1. Multiplayer match coupling — two runs, PvP-blind score comparison, lives.
2. Shop / economy loop (curated competitive content).
3. Network transport + lobby/matchmaking (regional, persistent connection).
4. Differential-test harness vs. real Balatro for content fidelity.
5. Ranked rulesets + ladder.
