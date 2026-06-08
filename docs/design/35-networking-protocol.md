# 35 — Networking, Lobby/Matchmaking, Auth, Reconnect/Resync, Anti-Cheat Boundary

Target parity baseline: **Balatro Multiplayer (BMP) 0.4.0**, now on disk at
`C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/`, plus the
reference relay server `D:/BalatroMultiplayerAPI-Server-main/`.

This document specs the **transport & coordination plane** for our
server-authoritative, queue-shaped, ruleset-driven engine. It is the companion to
`balatro-engine-spec.md` (the scoring/RNG/anti-cheat philosophy) and
`queue-model.md` (determinism). Where those say *what* we compute, this says
*how the bytes move, who is allowed to say what, and how a dropped player rejoins.*

Structure per the brief: (1) how BMP 0.4.0 does it, (2) how ours does it today,
(3) the gap, (4) target design, (5) open questions, (6) new building blocks.

---

## 1. How BMP 0.4.0 does it (grounded)

### 1.1 Transport & wire protocol

BMP is **raw TCP, newline-delimited JSON, bidirectional, fire-and-forget**.

- Client side is a dedicated LÖVE thread
  (`networking/socket.lua`). It opens a `socket.tcp()`, sets
  `tcp-nodelay=true` (`socket.lua:54`), `settimeout(10)` for connect then
  `settimeout(0)` (non-blocking) for the loop (`socket.lua:52,69`). Messages are
  sent as `msg .. "\n"` (`socket.lua:106`).
- Two LÖVE channels bridge the net thread and the UI/game thread:
  `uiToNetwork` and `networkToUi` (`socket.lua:35-36`). The game thread drains
  `networkToUi` every `Game:update` (`action_handlers.lua:1382-1425`).
- Server side is `node:net` `createServer` on **port 8788** (`main.ts:44,462`),
  `socket.setNoDelay()`, OS `setKeepAlive(true, 10000)` (`main.ts:121-123`).
- **Framing**: the server buffers partial TCP reads, splits on `\n`, and keeps
  the trailing partial chunk (`main.ts:165-168`). Each line is `JSON.parse`d into
  `{ action, ...args }` and dispatched through a giant `switch` (`main.ts:184-426`).
- **Message shape**: every message is a flat JSON object with a string `action`
  discriminator and ad-hoc sibling fields. No envelope, no sequence number, no
  request/response correlation, no version field on the frame.
  Full type catalog: `actions.ts` (server↔client union types).
- There is a second serializer, `serializeAction` (`main.ts:90-96`), producing a
  `key:value,key:value` comma form — used for the **admin** channel and legacy
  `stringToJson`; the live game path uses `JSON.stringify` + `\n`.

**The complete action vocabulary** (authoritative list, from `actions.ts` +
`HANDLERS` in `action_handlers.lua:1322-1367`):

*Server→Client*: `connected`, `version` (request), `error`, `joinedLobby`,
`rejoinedLobby`, `enemyDisconnected`, `enemyReconnected`, `lobbyInfo`, `stopGame`,
`startGame` (`{deck, stake?, seed?}`), `startBlind` (`{firstPlayer}`), `winGame`,
`loseGame`, `gameInfo` (deprecated), `playerInfo` (`{lives}`), `enemyInfo`
(`{score, handsLeft, skips, lives}`), `endPvP` (`{lost, pvpTimerLost?}`),
`lobbyOptions`, `enemyLocation`, `sendPhantom`, `removePhantom`, `speedrun`,
`asteroid`, `letsGoGamblingNemesis`, `eatPizza`, `soldJoker`, `spentLastShop`,
`magnet`, `magnetResponse`, `getEndGameJokers`, `receiveEndGameJokers`,
`getNemesisDeck`, `receiveNemesisDeck`, `endGameStatsRequested`,
`nemesisEndGameStats`, `startAnteTimer`, `pauseAnteTimer`, `jimboAppear/Talk/Move/Remove`
(admin-driven mascot), `moddedAction`, `keepAlive`, `keepAliveAck`,
`tcg_compatible`/`tcgStartGame`/`tcgStartTurn`/`tcgPlayerStatus`,
`handyMPExtensionLobbyEnabled`.

*Client→Server*: `username` (`{username, modHash}`), `createLobby` (`{gameMode}`),
`joinLobby` (`{code}`), `rejoinLobby` (`{code, reconnectToken}`), `leaveLobby`,
`readyLobby`/`unreadyLobby`, `lobbyInfo`, `startGame`, `readyBlind`/`unreadyBlind`,
`playHand` (`{score, handsLeft, hasSpeedrun}`), `stopGame`, `lobbyOptions` (flat
k/v), `failRound`, `setAnte` (`{ante}`), `setFurthestBlind`, `newRound`, `skip`
(`{skips}`), `version` (`{version}`), `setLocation`, the MP-joker echoes
(`sendPhantom`, `removePhantom`, `asteroid`, `letsGoGamblingNemesis`, `eatPizza`,
`soldJoker`, `spentLastShop`, `magnet`, `magnetResponse`), the end-game transfers
(`getEndGameJokers`/`receiveEndGameJokers`, `getNemesisDeck`/`receiveNemesisDeck`,
`endGameStatsRequested`/`nemesisEndGameStats`), timer ops (`startAnteTimer`,
`pauseAnteTimer`, `failTimer`, `failPvPTimer`), `syncClient` (`{isCached}`),
`moddedAction` (`{modId, modAction, target?, ...}`), TCG, Handy extension toggles.

**Score encoding on the wire** is a string parsed by `InsaneInt` (`InsaneInt.ts`):
format is leading `e`-prefixes for tetration depth (`startingECount`), then
`coefficient[e exponent]`, with optional `#count` for very deep stacks
(`InsaneInt.ts:23-39`). The client formats `to_big(score)` and strips
commas/decimals before sending (`action_handlers.lua:1088-1095`). This exists
purely because Balatro scores overflow f64.

### 1.2 Keepalive / liveness

Symmetric application-level heartbeat (independent of TCP keepalive):

- **Server** (`main.ts:46-156`): `KEEP_ALIVE_INITIAL_TIMEOUT=15000ms`. After 15s
  idle it sends `keepAlive`, flips `isRetry`, then a `retryTimer` fires every
  `KEEP_ALIVE_RETRY_TIMEOUT=5000ms`; after `KEEP_ALIVE_RETRY_COUNT=4` unanswered
  retries it calls `socket.end()`. Any inbound data `keepAlive.refresh()`es.
- **Client** (`socket.lua:130-228`): `keepAliveInitialTimeout=20s`,
  `keepAliveRetryTimeout=5s`, `keepAliveRetryCount=4`. Crucially, the net thread
  answers a server `keepAlive` **directly on the socket** without routing through
  the UI thread, to cut latency (`socket.lua:155-157`): if the inbound data
  contains `"keepAlive"` and not `Ack`, it immediately sends
  `{"action":"keepAliveAck"}`.
- Client→server keepAlive is also acked server-side (`keepAliveAction`,
  `action_handlers.ts:119-122`).

### 1.3 Lobby / matchmaking

- **Lobby codes**: 5 uppercase letters A–Z, collision-checked
  (`Lobby.ts:12-19`). Stored in a global `Lobbies = Map<string,Lobby>`.
- A lobby is strictly **2-player host/guest** (`host`, `guest` fields,
  `Lobby.ts:59-61`). `createLobby` makes a lobby and seats the creator as host;
  `joinLobby` seats the guest, rejecting if full (`Lobby.ts:248-255`).
- **No real matchmaking / no ranked queue exists in this server.** Match creation
  is purely code-based (friend-invite style). The "Ranked" branding in the mod's
  CHANGELOG refers to *rulesets / balance*, not a server-side MMR queue.
- **Ready model**: lobby-ready (`readyLobby`/`unreadyLobby` → `isReadyLobby`) and
  per-blind ready (`readyBlind`/`unreadyBlind` → `isReady`). `startGame` is
  **host-only** (`action_handlers.ts:128`) and currently does *not* require guest
  ready (commented out, `action_handlers.ts:133-136`).
- **Game modes**: `attrition` | `showdown` | `survival` (`GameMode.ts`). Mode data
  is tiny: `startingLives` (4/2/1) and `getBlindFromAnte` returning which slots are
  `bl_pvp`. Survival/attrition win/lose resolution lives in `failRound`/
  `setFurthestBlind` handlers (`action_handlers.ts:344-457`).
- **Lobby options** are a free-form string→(string|bool) bag (`Lobby.setOptions`,
  `Lobby.ts:331-340`); only the guest is echoed `lobbyOptions`. The mod parses a
  curated set into typed fields (`starting_lives`, `pvp_start_round`,
  `timer_base_seconds`, `ruleset`, `gamemode`, `modifier_layers`, …) in
  `action_lobby_options` (`action_handlers.lua:465-526`). **The ruleset/gamemode/
  modifier_layers live entirely client-side** — the server stores the strings and
  relays them but does not enforce them.
- **Seed**: host's `startGame` generates an 8-char A–Z/1–9 seed
  (`utils.ts:generateSeed`, `Lobby.startGame` → `generateSeed()`,
  `action_handlers.ts:149`) unless `different_seeds` is set. The same seed string
  goes to both clients; **each client runs its own local Balatro with that seed**.

### 1.4 Auth & identity

- **There is none, cryptographically.** Identity is a `username` string the client
  asserts (`usernameAction`, `action_handlers.ts:44-50`), suffixed with a
  `~<blind_col>` cosmetic and accompanied by `modHash`.
- `modHash` is a 4-digit decimal hash (`hash()` in `matchmaking.lua:4-12`,
  `h = (h*31+char) % 10000`) of a sorted semicolon-joined mod list plus
  `encryptID`, `unlocked`, `preview`, `serversideConnectionID`
  (`matchmaking.lua:25-39`). It is purely a **mod-parity / version-mismatch
  warning** mechanism (`version_mismatches()`), not security.
- `Client.id` is a server-issued `uuidv4` (`Client.ts:47`). `reconnectToken` is
  `randomBytes(16).toString('hex')` (`Client.ts:51`).
- **Version check** (`versionAction`, `action_handlers.ts:398-427`): client sends
  `version`; server compares to a hardcoded `serverVersion = "0.3.2-MULTIPLAYER"`
  (note: **stale — server lags the 0.4.0 mod**) and only emits a soft
  `error` "[WARN] Server expecting version …" if the client is older. Non-blocking.
- **Admin channel** (`main.ts:466-625`): a *separate* TCP server on
  `127.0.0.1:8789`, Ed25519-style signature-verified (`crypto.verify` against
  `admin_public.pem`, `main.ts:489-496`). This is the one place BMP uses real
  crypto. Commands: `message`, `jimbo*` (mascot), `listLobbies`.

### 1.5 Reconnect / resync

This is the most developed part of the 0.4.0 server and a **0.3.3→0.4.0 delta**.

- On TCP `end`/`error`, the server calls `disconnect(client)` (`main.ts:438-459`).
- `Lobby.disconnect` (`Lobby.ts:141-194`): if no game in progress or no opponent,
  it's a plain `leave`. Otherwise it **reserves the slot for
  `RECONNECT_GRACE_PERIOD = 60000ms`** (`Lobby.ts:33`), snapshotting a
  `SavedGameState` (lives, score, handsLeft, ante, skips, furthestBlind, ready
  flags, livesBlocker, location, username, modHash — `Lobby.ts:35-49,161-175`),
  clears the live slot, and notifies the opponent with
  `enemyDisconnected {timeout: 60}`.
- `rejoinLobby {code, reconnectToken}` → `Lobby.rejoin` (`Lobby.ts:197-246`):
  validates the token against the reserved slot, restores the snapshot onto the
  *new* `Client` object, reseats it in the right role, sends `rejoinedLobby` with a
  **fresh** reconnectToken, and notifies the opponent `enemyReconnected`.
- **Client mirror** (`action_handlers.lua:53-258`): the client persists
  `reconnectToken` + `lastLobbyCode` across socket drops; on `connected` it
  auto-sends `rejoinLobby` (`action_handlers.lua:66-73`). The net thread itself
  retries the TCP connect 3× with exponential backoff `{2,4,8}s`
  (`socket.lua:38-91`). UI shows a 60s self-reconnect countdown and an opponent
  countdown (`action_handlers.lua:144-258`).
- **There is NO mid-game state resync of the actual run.** The server snapshot is
  only the *coordination* scalars (score/lives/ante). The Balatro run itself lives
  on the client; on reconnect the client keeps playing its own local game. The
  "deck/jokers transfer" actions (`getEndGameJokers`, `getNemesisDeck`) exist only
  for the **end-game results screen** (showing the nemesis's final board), not for
  resync.

### 1.6 Anti-cheat boundary (BMP)

**There is effectively none, by architecture.** This is the load-bearing fact:

- `playHandAction` (`action_handlers.ts:204-275`):
  `client.score = InsaneInt.fromString(String(score))` — the server **stores the
  client's self-reported score verbatim** and relays it to the opponent as
  `enemyInfo`. PvP win/loss is decided by comparing two *self-reported* numbers
  (`action_handlers.ts:236-274`).
- Every progression value is client-asserted: `setAnte`, `setFurthestBlind`,
  `skip {skips}`, `spentLastShop {amount}`, `failRound`, `failTimer`,
  `soldJoker`. The server is a **dumb relay + tiny win/lose arbiter**.
- `moddedAction` relays **arbitrary unvalidated payloads**
  (`[key]: unknown`, `action_handlers.ts:799-817`) to the nemesis or all.
- Consequence (already noted in `balatro-engine-spec.md` §6/§7): since the client
  runs Steamodded, the entire rules engine is mutable; a self-reported score is
  unfixable. BMP explicitly is a **trust-the-peer** design.

### 1.7 0.3.3 → 0.4.0 deltas relevant to networking

From `CHANGELOG.md` and the on-disk 0.4.0 source (0.4.0 source wins over the 0.3.3
spreadsheet where they disagree):

- **Reconnect/grace-period system** (`rejoinLobby`/`reconnectToken`/
  `enemyDisconnected`/`enemyReconnected`, 60s slot reservation) is the headline
  0.4.0 networking addition. The 0.3.3 baseline doc has no token-based rejoin.
- **`moddedAction` envelope + `MP.register_mod_action`** namespacing
  (`action_handlers.lua:1-23, 965-968, 1278-1291`) — modded actions are now
  namespaced by `modId`; `register_action` refuses to clobber core handlers.
- **Match Replays / Practice ghost** (CHANGELOG "Match Replays (Practice Mode)";
  `replays/` dir): a `.log`/`.json` of a past match replays a ghost opponent. The
  `emit_log_checksum`/`log_mem_debug_messages` calls (`action_handlers.lua:404,
  448-462`) feed this. Relevant because it implies a **canonical per-match event
  log** we should produce server-side.
- **PvP timer rework**: `startBlind {firstPlayer}`, `failPvPTimer`,
  `endPvP {lost, pvpTimerLost}`, local vs server-synced timers
  (`MP.timer_is_local()`, `action_handlers.lua:936-963`). Timer pause/resume keyed
  on score comparison (`action_handlers.lua:330-339, 1103-1111`).
- **Comeback Gold** now on any life loss again (`playerInfo` handler,
  `action_handlers.lua:429-432`) — server still just sends `{lives}`; the economy
  logic is client-side.
- **Speedrun**: granted to 2nd player within 30s of first-ready
  (`readyBlindAction`, `action_handlers.ts:166-178`); `firstReadyAt` timestamp.
- Server `serverVersion` is still `"0.3.2-MULTIPLAYER"` (`action_handlers.ts:396`)
  — **the reference server is not even pinned to 0.4.0**; version negotiation is a
  soft warning only.

---

## 2. How OUR engine does it today (cite Java)

### 2.1 Transport
- **WebSocket** (Javalin/Jetty) + **JSON** (Jackson), not raw TCP.
  `GameServer` configures `cfg.routes.ws("/game", …)` (`GameServer.java:160-168`)
  and HTTP routes for the joker/ruleset builder + `/login`. Default port 8788
  (`ServerMain.java:9`), bound to `127.0.0.1` (`GameServer.java:173`).
- **Message envelope**: client→server messages carry `{type, seq, ...}`
  (`GameServer.onMessage`, `GameServer.java:186-251`). Server→client uses
  `WsResponse {type, seq, accepted, rejection, view, replay}`
  (`GameServer.java:378-385`) — a **correlated request/response** with a sequence
  number and an explicit accept/reject, plus a `replay` (scoring event log) and a
  `view` (`ClientView`).
- Push to the opponent goes through an injected `deliver(sessionId, payload)` sink
  (`GameServer.java:333-340`) that `Match` calls.

### 2.2 Intents (the security seam)
- The client sends **intents, never outcomes**: `Intent.PlayHand(cardIndices)`,
  `Intent.Discard(cardIndices)` (`intent/Intent.java`). The server runs the
  authoritative pipeline (`run.submit(intent)` → `ServerUpdate`,
  `GameServer.java:301-310`). Shop/economy actions (`buyJoker`, `reroll`,
  `buyPlanet`, `useConsumable`, `proceed`) are index-referenced intents routed to
  `Run` (`GameServer.java:229-245`). **There is no message that carries a
  client-supplied score** — explicitly documented (`GameServer.java:32-34`).

### 2.3 Auth
- `AuthService` (`net/AuthService.java`): **HS256 JWT**, random per-process secret,
  12h expiry, `sub = playerId`. `POST /login {username}` issues a token
  (dev mode: any username, `GameServer.java:149-157`). The WS first message **must**
  be `{type:"auth", token}`; until verified, intents are rejected with
  `"unauthenticated"` (`GameServer.java:192-207`). Intended prod path:
  validate a **Steam auth ticket** at `/login`, same downstream
  (`AuthService.java:13-19`).

### 2.4 Lobby / match
- `createLobby` makes a 5-char code (chars `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`,
  ambiguity-free, `GameServer.java:342-353`) and a `Match`; `joinLobby` seats the
  guest and starts ruleset agreement (`GameServer.java:312-330`).
- `Match` (`game/Match.java`) is a **two-player same-seed race**. Both players run
  full authoritative `Run`s seeded identically (`Match.java:212-222`). Phases:
  `WAITING → AGREEING → PLAYING → FINISHED` (`Match.java:27`).
- **Ruleset agreement**: host `propose(name)`, guest `respond(accept)`
  (`Match.java:163-208`). The ruleset is **server-side and authoritative**
  (`RulesetStore`), unlike BMP where it's a client string.
- PvP/Nemesis resolution is **server-computed** from authoritative `roundScore`:
  `resolveNemesisIfDecided` (`Match.java:112-140`) compares the two runs' real
  scores and decrements lives. `STARTING_LIVES=4`, `PVP_FROM_ANTE=2`
  (`Match.java:38-39`). Opponent gets `opponentSummary` (lives, ante, blind,
  roundScore, money, phase — `Match.java:236-246`), the agreed-public subset.

### 2.5 Reconnect / liveness / keepalive
- **None.** `ws.onClose` just purges all maps for the session
  (`GameServer.java:162-167`) — `players`, `ctxs`, `runs`, `matchBySession`. There
  is no grace period, no reconnect token, no slot reservation, no keepalive/ping,
  no resync. A dropped player's `Run` is **destroyed**, and the `Match` is left
  with a dangling `Side` whose `sessionId` no longer maps to a socket.

### 2.6 Determinism substrate (already built)
- `queue-model.md` + `rng/`: `GameQueue<T>` / `QueueSet`, game-long category
  queues with per-player cursors. This is the fairness model BMP approximates;
  ours is explicit and server-side.

---

## 3. The GAP (BMP 0.4.0 parity ⇄ ours)

| Concern | BMP 0.4.0 | Ours today | Gap / action |
|---|---|---|---|
| **Trust model** | Peer-reported score relay (zero anti-cheat) | Server-authoritative intents, server computes score | **Ours is strictly better** — keep. The "parity" target is *behavioral* (same modes/blinds/effects), not *protocol-literal*. |
| Transport | Raw TCP + `\n`-JSON, no envelope | WS + JSON, `{type,seq}` envelope + accept/reject + replay | Ours better-structured. Keep WS internally; add an **optional TCP+`\n`-JSON compatibility adapter** if we ever want to serve the stock BMP client. |
| Liveness | App-level keepAlive (server 15s/5s×4; client 20s/5s×4) + TCP keepalive | Nothing | **Add** WS ping/pong + idle timeout + the same retry semantics. |
| Lobby codes | 5× A–Z | 5× unambiguous alphabet | Parity (ours avoids 0/O/1/I). Keep. |
| Matchmaking | Code-only, no ranked queue | Code-only | **Both lack a real queue.** Build the ranked/MMR queue (our differentiator). |
| Game modes | attrition/showdown/survival via tiny `GameMode.ts` | attrition-only `Match`, PVP_FROM_ANTE=2 hardcoded | **Add** showdown + survival; move mode params into ruleset. |
| Ready states | lobby-ready + per-blind ready; host-only start; speedrun-on-2nd-ready ≤30s | ruleset agreement only | **Add** lobby-ready + per-blind ready + speedrun window. |
| Auth/identity | username string + 4-digit modHash warning; no crypto (except admin) | JWT (HS256), Steam-ticket-ready | **Ours better.** Add a **mod/ruleset-hash compatibility field** for parity warnings; wire Steam ticket. |
| Version negotiation | Soft warn vs hardcoded (stale) server version | None on the frame | **Add** a protocol-version + ruleset-hash handshake (hard-reject on mismatch for ranked). |
| Reconnect | Token + 60s grace + scalar state snapshot + auto-rejoin + opponent countdown | None (drop = run destroyed) | **Build** reconnect: token, grace, and — because we're authoritative — **full run resync** (we can, BMP can't). |
| Resync depth | Only coordination scalars; run stays client-side | We own the whole run → can replay full `ClientView` | **Leverage**: on rejoin, re-send authoritative `ClientView` + opponent summary. |
| MP-joker coupling | `sendPhantom`, `magnet`, `eatPizza`, `asteroid`, `letsGoGambling`, `soldJoker`, `spentLastShop` relayed unvalidated | Not implemented; `Match` has opponentSummary only | **Build** as validated cross-player **intents** (per spec §7 conversion map). |
| End-game transfer | `getEndGameJokers`/`getNemesisDeck` ship serialized boards | We already hold both boards authoritatively | **No transfer needed** — derive nemesis board from our state for the results screen. |
| Modded actions | Arbitrary unvalidated `moddedAction` | None | **Do not** add arbitrary trusted payloads; only typed validated intents. |
| Replays | `.log`/`.json` ghost replay (0.4.0) + log checksums | Per-action `replay` exists per-update, no match log | **Add** a canonical append-only **match event log** (also powers our resync + ghost/practice). |
| Admin channel | Signed TCP on :8789 (`message`, `jimbo*`, `listLobbies`) | None | **Add** an authenticated admin/ops surface (optional, low priority). |
| Score encoding | `InsaneInt` string (`e`-prefix tetration) on wire | server-side score type (TBD big-number) | Client never sends score for us; but our **own** big-number type must match Balatro's overflow behavior for display parity (open item in engine spec). |

**Bottom line:** we are *ahead* on trust/auth/structure and *behind* on
liveness, reconnect, multi-mode, ready-flow, cross-player MP-joker coupling, and
matchmaking. Parity = reproduce BMP's **observable behavior and lobby/mode
surface** on top of our authoritative core, never its trust model.

---

## 4. Proposed target design

### 4.0 Principles
1. **Authoritative core, dumb client** (engine spec §5/§6) — unchanged. The client
   sends intents and *animates* a server replay log; it computes nothing that
   affects outcome.
2. **Two faces, one core**: (a) our native **WS protocol** (`{type,seq}` envelope,
   what our web client speaks), and (b) an optional **BMP-compat TCP adapter**
   (`\n`-JSON, the action vocabulary above) so the stock 0.4.0 Lua client can play
   against our server. The adapter is a *translation layer* over the same
   `Match`/`Run`; it must **drop** client-supplied scores/progression and recompute.
3. **Ruleset- and mode-driven**: lives, PvP-from-ante, blind schedule, timers,
   economy modifiers all come from the agreed `Ruleset` + `GameMode`, not constants.

### 4.1 Wire protocol (native)

Keep the `{type, seq}` request/response envelope; formalize it.

- **Client→Server frame**: `{ "type": <intentType>, "seq": <u64>, ...args }`.
  `seq` is a monotonic per-connection client counter for correlation + idempotency.
- **Server→Client frames**:
  - **Reply** (correlated): `WsResponse {type:"update"|"error", seq, accepted,
    rejection, view, replay}` — already exists; extend `view` to a versioned
    `ClientView` and `replay` to the scoring-event log.
  - **Push** (uncorrelated, `seq: 0` or absent): `opponentUpdate`, `matchStart`,
    `pvpResult`, `matchResult`, `lifeLost`, `enemyDisconnected`,
    `enemyReconnected`, `phantom`, `ping`. These mirror BMP's S→C actions but as
    authoritative derivations.
- **Versioning on the frame**: add `protocolVersion` to the **auth** message and a
  server `serverInfo {protocolVersion, engineVersion, contentHash}` on connect.
  Hard-reject incompatible `protocolVersion`; for ranked, also require matching
  `contentHash` (the hash of the active ruleset + joker pool — our analog of BMP's
  `modHash`, but *enforced*, not warned).
- **Native transport stays WebSocket**; the compat adapter is raw TCP+`\n`.

### 4.2 BMP-compat adapter (optional, for stock-client parity)

A `BmpCompatGateway` that speaks BMP 0.4.0's TCP+`\n`-JSON action vocabulary and
maps it onto our authoritative core, per the **conversion map** in engine spec §7:

- `playHand {score, handsLeft}` → **discard the score**; the adapter cannot trust
  it. This is the hard part: the stock client computes scoring locally. To serve
  the stock client *securely* we'd need it to send the *cards played* — which BMP
  doesn't. **Therefore the compat adapter is "relay-faithful" (insecure, casual)
  only**; ranked requires our own thin client. Document this boundary explicitly.
- `setAnte`/`setFurthestBlind`/`newRound`/`failRound`/`failTimer`/`skip` → adapter
  maps to our progression where it can; otherwise mirrors BMP relay semantics in
  casual mode.
- Lobby/keepalive/reconnect actions map 1:1 to our lobby manager.
- Conclusion: **ship the native authoritative protocol for ranked; offer the BMP
  adapter only as a casual/relay bridge with a clear "unranked, unverified" flag.**

### 4.3 Liveness / keepalive

- WebSocket native: use Jetty ping/pong. Server pings after **15s** idle; if no
  pong within **5s**, retry up to **4×**, then close — mirroring BMP's
  `15s/5s/×4`. Client answers pings immediately.
- Compat TCP adapter: implement BMP's exact `keepAlive`/`keepAliveAck` + the net
  thread's "answer on socket without UI round-trip" behavior.
- On close, **do not** purge the run immediately (see 4.5) — hand off to the
  reconnect manager.

### 4.4 Lobby, ready-flow, matchmaking

- **LobbyManager** (new): owns `Map<code, Lobby>` + `Map<sessionId, Lobby>`.
  `Lobby` holds `host`/`guest` `Seat`s, `gameMode`, agreed `Ruleset`, options,
  ready flags, and a `reconnect` sub-state. Codes: keep our unambiguous 5-char
  alphabet.
- **Ready model** (parity): `readyLobby/unreadyLobby` (lobby ready),
  `readyBlind/unreadyBlind` (per-blind ready). `startGame` host-only; **require
  guest lobby-ready** (BMP has this commented out — we should enforce it).
  Per-blind: when both ready, emit `startBlind {firstPlayer}`; grant a "speedrun"
  flag to the first-ready and to the second if within **30s** (BMP
  `firstReadyAt`/30000ms semantics, `action_handlers.ts:166-178`).
- **Game modes** (parity): a `GameMode` table like BMP's but authoritative —
  `startingLives`, `blindScheduleForAnte(ante, ruleset)` → which slots are PvP.
  Attrition (lives 4, boss PvP from `pvp_start_round`), Showdown (lives 2, all
  slots PvP after `showdown_starting_antes`), Survival (lives 1, race to furthest
  blind). Resolution logic moves into mode strategy objects, not `Match` constants.
- **Matchmaking (our differentiator, new)**: a `RankedQueue` service —
  `enqueue(playerId, ruleset/mode preference)` → MMR-bucketed pairing → on match,
  synthesize a `Lobby` server-side (no code needed) with a server-chosen ruleset
  and start. Persist results to an MMR store. This is queue-shaped at the *match*
  level just as the run is queue-shaped at the *RNG* level. (Lives outside this
  doc's hot path; spec'd in a future `36-matchmaking.md`.)

### 4.5 Reconnect / resync (full-state, our advantage)

Because the run is **server-authoritative**, we can do what BMP cannot: resync the
*actual game*, not just scalars.

- **On disconnect** (WS close / TCP error / keepalive death):
  - If not in a live match or no opponent → plain leave.
  - Else: **do not destroy the `Run`**. Move the `Seat` to a `Disconnected` state,
    keep its `Run`/`Match` alive, start a **grace timer** (default **60s**, from
    `Ruleset`), and push the opponent `enemyDisconnected {timeout}`.
  - Issue/retain a `reconnectToken` (already on auth identity; bind it to the seat).
- **On reconnect**: client connects, auths (JWT — note: must survive a 60s drop, so
  token TTL ≥ grace; our 12h JWT is fine), then sends
  `rejoinLobby {code, reconnectToken}`. Server validates token ⇄ reserved seat,
  rebinds the new `sessionId`/`WsContext` to the existing `Seat`+`Run`, and:
  - replays a **full resync**: `rejoinedLobby` + current authoritative `ClientView`
    (the whole run state the client legitimately sees) + latest `opponentUpdate`.
    No client-side run state needed — we are the source of truth.
  - pushes the opponent `enemyReconnected`.
- **Grace expiry**: opponent wins (attrition/showdown) or survival rule applies;
  emit `matchResult`. (BMP relies on the client countdown + server `stopGame`;
  we make it a clean server-timer + authoritative result.)
- **Match event log** (new, also powers ghost/practice from CHANGELOG 0.4.0): every
  authoritative transition (intent applied, replay log, life change, pvp result)
  appends to a per-match log. Resync can fast-forward from the log; the log
  export = the `.json` ghost-replay format the 0.4.0 mod consumes.

### 4.6 Auth & identity (target)

- Keep **JWT** session tokens. Production: `/login` validates a **Steam
  encrypted app ticket** (proves ownership + steamId) → `sub = steamId`. Dev mode
  stays username-based behind a flag.
- **Identity ≠ reconnect token**: the JWT proves *who you are*; the per-seat
  `reconnectToken` proves *you owned this seat*. Both required to rejoin a ranked
  match (prevents a third party from grabbing a dropped slot).
- **Content/version handshake**: on auth, exchange `protocolVersion` +
  `contentHash`. Ranked: hard-reject mismatch. Casual: soft-warn (BMP-style
  `version_mismatches` UX). Our `contentHash` = hash over (engine protocol
  version, active ruleset id+params, joker-pool keys+configs) — the *enforced*
  analog of BMP's `modHash`.
- **Cross-player intent authorization**: every MP-joker / coupling intent
  (§4.7) is checked against the sender's authoritative state before it can affect
  the nemesis. No `moddedAction`-style arbitrary trusted payloads.

### 4.7 Cross-player (MP-joker) coupling — as validated intents

Reproduce BMP's MP-joker *effects* but server-side and validated. Each BMP relay
action becomes an intent the server validates against authoritative state, then a
**derived push** to the opponent:

| BMP relay | Our intent → authority | Push to nemesis |
|---|---|---|
| `sendPhantom {key}` / `removePhantom` | server confirms the joker is in sender's authoritative board | `phantom {add/remove, key}` |
| `magnet`/`magnetResponse` | server selects the highest-sell joker from sender's *real* board (no client card payload) | `phantom`-style add |
| `eatPizza {whole}` | derived from sender's discard action | `eatPizza {discards}` |
| `asteroid` / `letsGoGamblingNemesis` / `soldJoker` / `spentLastShop {amount}` | derived from authoritative shop/sell/score events | typed push |
| `skip {skips}` | `skipBlind` intent → server applies tag, advances packs queue (queue-model.md skip-offset) | `enemyInfo` summary |

`opponentSummary` (`Match.java:236`) already carries the agreed-public subset
(score, hands left, lives, ante, blind, money, phase). PvP-aware jokers
(Defensive/Conjoined/Pizza/Penny Pincher/Speedrun) read this via an
**opponent/nemesis handle** on `EvaluationContext` (the tracked
`queue-model.md` extension), never the opponent's hidden state.

### 4.8 Anti-cheat boundary (target, restated crisply)

- **Server-only, never on any wire**: seed, `hashed_seed`, every `GameQueue`
  cursor/contents below what's revealed, unrevealed deck order, future shop/pack
  contents, boss blind before reveal.
- **Client may send only intents** referencing currently-visible handles (card
  indices in *its* hand, shop slot indices, joker indices). No scores, no money,
  no ante, no "I won".
- **Server emits**: authoritative `ClientView` (only what's legitimately visible)
  + scoring `replay` log (presentation events) + agreed-public opponent summary.
- **Ranked gate**: native thin client + enforced `contentHash` + JWT(steamId) +
  reconnect token. **Casual/compat**: BMP TCP adapter, explicitly flagged
  unverified (it can only relay the stock client's self-reported score).
- Validate **every** cross-player intent against the sender's authoritative state
  (§4.7). Rate-limit intents per connection; reject malformed/oversized frames
  (the compat adapter must bound line length — BMP buffers unbounded,
  `main.ts:165`).

### 4.9 Phased build order
1. **Liveness + reconnect on native WS**: ping/pong, grace timer, reconnect token,
   full `ClientView` resync. (Highest value; closes the worst gap.)
2. **Ready-flow + multi-mode**: lobby-ready/blind-ready, `startBlind {firstPlayer}`,
   speedrun window; mode strategy objects (attrition/showdown/survival) reading the
   ruleset.
3. **Cross-player MP-joker intents** (§4.7) + opponent/nemesis context handle.
4. **Match event log** → resync fast-forward + ghost/practice export.
5. **BMP-compat TCP adapter** (casual/unranked bridge).
6. **Ranked queue + MMR** (`36-matchmaking.md`).

---

## Open questions
1. **BMP-compat adapter security**: the stock 0.4.0 client sends `playHand
   {score}`, never the cards. Confirmed unsecurable for ranked. Is a casual
   relay-faithful bridge worth shipping, or do we require our thin client always?
2. **Score wire/representation**: BMP's `InsaneInt` (`e`-prefix tetration,
   `#count`) — do we adopt it for *display* parity, or define our own and only
   match Balatro's `mod_mult`/`mod_chips` overflow at the value level? (Ties to the
   engine-spec open item on big numbers.)
3. **Reconnect grace = 60s** (BMP). Adequate for ranked, or should it be
   ruleset/mode-tunable, and what happens to the live PvP timer during a peer's
   grace window (BMP pauses via client; we'd pause server-side)?
4. **Speedrun 30s window** (`firstReadyAt`) — keep verbatim, or fold into ruleset
   config? CHANGELOG says Speedrun joker is "out of rotation" in Standard 0.4.0 —
   confirm whether the *server-side 30s grant* is still wired even if the joker is
   pooled out.
5. **JWT vs reconnect token lifetimes**: 12h JWT vs 60s grace is fine, but for a
   *long* ranked match (Survival can run long) does the JWT need refresh mid-match?
6. **`contentHash` scope**: exactly which inputs (ruleset params, joker pool,
   protocol version, engine build) go into the enforced hash, and how do custom
   rulesets/jokers (the builder) interact with ranked eligibility?
7. **Admin channel**: do we want BMP's signed admin surface (`message`, `jimbo*`,
   `listLobbies`) for ops, and on what transport/auth?
8. **Multi-player (>2)**: BMP hardcodes host/guest with many `// TODO: >2 players`.
   Do we design the lobby/match abstraction for N seats now or stay 2-seat?
9. **Modded-action parity**: stock clients can emit `moddedAction`. Do we expose a
   **whitelisted, typed** mod-intent surface, or refuse all modded actions in our
   protocol?

---

## New building blocks needed
- **`LobbyManager`** (Java): `Map<code,Lobby>` + `Map<sessionId,Lobby>`, code gen,
  create/join/leave, ready-flow, mode selection, reconnect routing. Extracted from
  the ad-hoc maps in `GameServer`.
- **`Lobby` / `Seat`** types: host/guest seats, agreed ruleset+mode, options,
  ready flags, per-seat `reconnectToken` + disconnect state. (Replaces the inline
  `Match.Side` for connection concerns; `Match` keeps game logic.)
- **`GameMode` strategy** (interface + attrition/showdown/survival impls):
  `startingLives`, `blindScheduleForAnte`, `resolvePvp`, `resolveWinLoss` —
  reading the `Ruleset`. Replaces hardcoded `STARTING_LIVES`/`PVP_FROM_ANTE`.
- **`KeepAlive`/liveness manager**: WS ping/pong + idle/retry timers (15s/5s×4),
  and the TCP-adapter `keepAlive`/`keepAliveAck` equivalent.
- **`ReconnectManager`**: grace timers, token validation, seat rebind, full
  `ClientView` resync, opponent notifications, grace-expiry resolution.
- **`MatchLog`** (append-only authoritative event log): powers resync
  fast-forward and ghost/practice `.json` export (0.4.0 replay parity); per-match,
  with a checksum (BMP `emit_log_checksum`).
- **`VersionHandshake` / `ContentHash`**: protocol-version negotiation + enforced
  ruleset/joker-pool hash; hard-reject for ranked, soft-warn for casual (our
  enforced analog of BMP `modHash`/`version_mismatches`).
- **Cross-player intent types** (extend `Intent`): `SkipBlind`, `SellJoker`,
  `SendPhantom`/`RemovePhantom`, `Magnet`, plus the derived nemesis pushes. Each
  validated against authoritative state.
- **`OpponentContext` / nemesis handle** on `EvaluationContext`: hands-left, lives,
  spent-last-ante, score — for PvP-aware jokers (tracked in `queue-model.md`).
- **`PvpTimer`** (server-authoritative): base/increment/forgiveness from ruleset,
  pause/resume on score comparison, `failPvPTimer` → life loss; pauses during a
  peer's reconnect grace.
- **`BmpCompatGateway`** (optional): TCP+`\n`-JSON server speaking the 0.4.0 action
  vocabulary, mapping onto `LobbyManager`/`Match`, flagged casual/unverified.
- **`RankedQueue` / MMR store** (future `36-matchmaking.md`): enqueue/pair by
  rating, synthesize lobbies, persist results.
- **`AdminGateway`** (optional): signature-verified ops channel (`message`,
  `listLobbies`, mascot), mirroring BMP's :8789 admin server.
- **`SteamTicketVerifier`** (prod auth): validates the Steam encrypted app ticket
  at `/login`, feeding the existing `AuthService` JWT issuance.
