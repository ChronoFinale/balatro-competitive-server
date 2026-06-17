# Bridge Stage 2 — Complete a full blind, server-authoritative, native rendering

Status: **design + slice 1 landed (unverified in-game).** Read alongside `CLAUDE.md` ("Real-Balatro
thin client") and `balatrobridge.lua`.

**Implementation status (2026-06-12):**
- ✅ Slice 1 (in `balatrobridge.lua`, syntax-checked via luajit `loadfile`, **not yet run in the real
  game**): `parse_view()` reads the authoritative `phase/requirement/roundScore/handsLeft/discardsLeft/
  money`; `reconcile(view)` runs after every play/discard and **owns the three decision inputs**
  (`G.GAME.blind.chips`, `current_round.hands_left`, `current_round.discards_left`) synchronously, then
  schedules a **read-only** deferred tripwire logging native-chips-vs-server-roundScore + the server
  `phase`. No risky mutation of `G.GAME.chips` yet → cannot soft-lock; native scoring + native
  `end_round`/`ROUND_EVAL`/`GAME_OVER` render the outcome. Next: run a real blind, read
  `build/balatro-bridge.txt` for the `reconcile:` lines, confirm zero `DIVERGENCE`, then decide whether
  the replay-driven count-up (§6.2 target) is needed before boss/Stage 3.
- ⬜ Slice 2: replay-driven count-up (force `G.GAME.chips` from `ReplayEntry` running totals) — the one
  timing-sensitive mutation; only build once slice-1 in-game data is in hand.

 This doc captures *where we actually are* and the precise plan for making
the REAL game play an entire blind — start to win/lose — with the server as the sole authority and
Balatro's own UI/animations doing the rendering.

---

## 1. Goal

Today the bridge plays **hands** server-authoritatively (Stage 1). Stage 2 is the full **blind**:

- The blind's requirement, hands, discards, score, and money are the **server's** numbers.
- Winning the blind (`roundScore >= requirement`, possibly before hands are exhausted), losing it
  (hands exhausted, score short), and the resulting screens (round-eval / cash-out, game-over) are
  driven by the server's decision and **rendered by Balatro's native flow**.
- We reimplement none of round-end. We feed Balatro server truth and let its own state machine run.

Out of scope (later stages): shop & economy interactions (Stage 3), jokers/editions where local
scoring diverges and the server `replay` must drive the count-up (Stage 4), multi-blind / ante
progression and PvP.

---

## 2. Where we are (grounded)

### Server — already does the whole blind

`Run` runs the full lifecycle. The phase machine (`game/Run.java:55`):

```
BLIND_SELECT → selectBlind() → BLIND_ACTIVE → (play/discard)
   roundScore >= requirement → winBlind() → SHOP
   handsLeft <= 0 & short     → RUN_LOST   (or BLIND_FAILED in Attrition; Mr Bones can save)
```

`winBlind()` (`Run.java:596`) awards reward + interest and sets `phase = SHOP`. Loss sets
`phase = RUN_LOST` (`Run.java:517`). **All of this is already computed and exposed.**

`ClientView` (`net/ClientView.java:15`) already carries everything the renderer needs:
`ante, blind, requirement, roundScore, handsLeft, discardsLeft, money, handSize, phase, hand[…],
jokers, shop, boss, bossEffect, …`. After every action the server returns
`WsResponse("update", seq, accepted, rejection, view, replay)` (`GameServer.java:605,676`), where
`view` is the full `ClientView` and `replay` is the `List<ReplayEntry>` count-up stream
(`scoring/ReplayEntry.java:9`: `source, kind, text, runningChips, runningMult`).

Wire already supports the actions a full single-blind run needs: `selectBlind`, `playHand`,
`discard`, plus `proceed`/shop/`buyShopItem`/etc. for later (`GameServer.java:444–551`).

**Server change needed for Stage 2: essentially none.** The blind already ends correctly on the
server. Stage 2 is a client-rendering problem. (One small server nicety is listed in §7.)

### Client (the mod) — ignores almost all of it

`balatrobridge.lua` does **not** read `phase` or the structured `view`. It regex-scrapes the
concatenated JSON for a handful of fields:

- `parse_hand()` (`:69`) pulls `uid/rank/suit`.
- `server_play()` (`:175`) scrapes `roundScore`, `handsLeft`, `accepted`, `rejection`, and — fragile —
  takes the **last** `runningChips`/`runningMult` it sees, which happens to be the final `ReplayEntry`'s
  running totals. There is no structured parse of `view`.

When the blind ends, the mod only logs and stops:

```lua
-- balatrobridge.lua:272
SERVER_HAND = (res.handsLeft and res.handsLeft > 0) and res.hand or {}
-- :282
if res.handsLeft == 0 then logln("server: blind hands exhausted (round-end is Stage 2)") end
```

So Balatro is running its **own** local game in parallel: its own `G.GAME.blind.chips`, its own
`hands_left`/`discards_left`, its own `G.GAME.chips`, and its own `end_round()` win/lose. Stage 1
"works" only because the defaults coincide (ante-1 small blind = 300 on both; same hand/discard
counts; vanilla scoring matches). **Nothing today makes Balatro's blind agree with the server's** — it
is luck of the defaults.

---

## 3. Core principle — the server decides; Balatro only renders

This is the project invariant (`CLAUDE.md`: "the client is never trusted; never move outcome
decisions to the client") applied to the round. The client computes **no** outcome and triggers
**no** state transition on its own. The control flow is inverted:

```
player input (highlight / Play / Discard / pick blind)  →  INTENT to server
server computes EVERYTHING (score, counters, money, win/lose, next phase)  →  ClientView + replay
client renders that state by driving Balatro's NATIVE animations — values & transitions are the server's
```

Balatro's local game logic — scoring, win/lose check, counters, RNG, shop generation — is treated as
**bypassed**. Its animation/UI machinery is reused as a *playback library*: the same `end_round` /
`ROUND_EVAL` / `GAME_OVER` / count-up routines, but **triggered by the server's `phase`**, never by
Balatro re-deriving the result.

Concretely, two things must hold to call it server-driven, not "Balatro happens to agree":

1. **Transitions are gated on `view.phase`, not on a local comparison.** When the server returns
   `phase == SHOP` we drive the native win flow; `phase == RUN_LOST` → native game-over. We do still
   set `G.GAME.chips` / `G.GAME.blind.chips` from the server so the screens *display* right and so
   Balatro's own `chips - blind.chips >= 0` check (`functions/state_events.lua:~95`) lands the same
   way — but that agreement is a **consequence** of server-owned inputs plus a divergence assert
   (§4.4), not the decision mechanism. If the two ever disagree, the server is right and we log loudly.
2. **Score values come from the server, not the local scorer.** The count-up is rendered with
   Balatro's native animation but fed the server's running totals from the `replay` stream
   (`ReplayEntry.runningChips/runningMult`). On a vanilla deck these equal Balatro's local count-up,
   so it looks identical today; wiring it to the server now means Stage 4 (jokers/editions, where
   local ≠ server) is "already done" rather than a re-architecture. See §6.2.

Native symbols reused as the playback library (verified in `D:\BalatroMod\Balatro`):
counters are live-bound `DynaText` over `G.GAME.current_round.hands_left` / `discards_left`
(`UI_definitions.lua:1290,1300`); round-end UI is `evaluate_round` / `create_UIBox_round_evaluation`;
game-over is `create_UIBox_game_over`.

The relevant Balatro symbols (verified in `D:\BalatroMod\Balatro`):

| Concept                 | Symbol                                                        |
|-------------------------|--------------------------------------------------------------|
| Game states             | `G.STATES.{SELECTING_HAND, ROUND_EVAL, SHOP, GAME_OVER, …}` (`globals.lua:284`) |
| Blind requirement       | `G.GAME.blind.chips` (`blind.lua:set_blind`)                  |
| Round score             | `G.GAME.chips`                                                |
| Hands / discards left   | `G.GAME.current_round.hands_left` / `.discards_left`         |
| Money                   | `G.GAME.dollars` (eased via `ease_dollars`)                  |
| Win/lose evaluation     | `end_round()` (`functions/state_events.lua`)                 |
| Cash-out screen         | `G.FUNCS.evaluate_round`, `create_UIBox_round_evaluation`     |
| Game-over screen        | `create_UIBox_game_over`, `Game:update_game_over`            |
| Counter mutators        | `ease_hands(mod)`, `ease_discard(mod)`                       |

---

## 4. Client work (the actual Stage 2)

### 4.0 The integration point (verified against real source)

The whole blind hinges on **two state updaters in `game.lua`** reading **three values**:

```lua
-- Game:update_hand_played (game.lua:3197) — "is this hand the last of the round?"
if G.GAME.chips - G.GAME.blind.chips >= 0 or G.GAME.current_round.hands_left < 1 then
    G.STATE = G.STATES.NEW_ROUND            -- end the round
else
    G.STATE = G.STATES.DRAW_TO_HAND         -- keep playing
end
-- Game:update_new_round (game.lua:3254) → end_round() → win/lose:
--   if G.GAME.chips - G.GAME.blind.chips >= 0 then  (win → ROUND_EVAL) else (lose → GAME_OVER)
```

So **owning `G.GAME.blind.chips`, `G.GAME.chips`, and `G.GAME.current_round.hands_left`** is
*sufficient* to make Balatro's native state machine render the server's exact outcome — win, lose,
*and* mid-blind win (the `chips >= blind.chips` branch fires regardless of hands remaining).

Timing facts that matter (real source):
- `hands_left` is decremented by Balatro's own `ease_hands_played(-1)` in
  `play_cards_from_highlighted` (`state_events.lua:475`), **before** scoring — and the server also
  decrements `handsLeft` by 1 per play, so the two stay in lockstep from equal starts.
- `evaluate_play` is scheduled as an immediate `E_MANAGER` event (`state_events.lua:512`); native
  scoring eases `G.GAME.chips` to its computed total via an `'ease'` event. The `update_hand_played`
  check runs from the main `Game:update` loop after that drains.
- Therefore a server correction to `G.GAME.chips` must land **after** native scoring's ease and
  **before** `update_hand_played` evaluates — i.e. an `E_MANAGER` event queued right after `_eval(e)`
  (FIFO → after the scoring events). This is the one piece that needs real-game tuning (§6.2).

### 4.1 Parse the structured `view` (replace regex scraping)

Add a small JSON decode (or a focused field reader) so we hold the whole `ClientView` after each
response instead of scraping individual keys. We need at least:
`phase, requirement, roundScore, handsLeft, discardsLeft, money, ante, blind, boss, hand[]`.
Keep `parse_hand()` for the card list. This removes the brittle "last `runningChips` wins" hack.

> Decision needed: bundle a tiny Lua JSON decoder vs. extend the regex reader. Recommendation: a
> minimal decoder — Stage 3/4 will need the nested `jokers`/`shop`/`replay` arrays anyway.

### 4.2 Authoritative blind setup at engage time

In the `select_blind` hook (`:206`), after `open_run()` succeeds, overwrite Balatro's blind with the
server's: `G.GAME.blind.chips = view.requirement` (and ideally name/boss for display). This makes the
*win line* the server's, not Balatro's default. Do this **after** `_sel(e)` has built the blind object.

### 4.3 Reconcile after every play / discard

Create one `reconcile(view)` helper, called at the end of the `evaluate_play` and
`discard_cards_from_highlighted` hooks (and after the initial deal):

- `G.GAME.current_round.hands_left  = view.handsLeft`
- `G.GAME.current_round.discards_left = view.discardsLeft`
- `G.GAME.chips = view.roundScore` (the running blind score the win check reads)
- money: ease `G.GAME.dollars` toward `view.money` (display only until Stage 3 owns economy)

Set the counters directly (the `DynaText` is bound by reference, so the UI updates), or go through
`ease_hands`/`ease_discard` for the little juice animation — see §6 on ordering vs. Balatro's own
decrement.

### 4.4 Drive the round-end the server chose (gate on `phase`)

The trigger is `view.phase`, not a local comparison. After the reconcile drains:

- `phase == "SHOP"`  → the server won the blind → render the native win flow (`ROUND_EVAL` /
  `evaluate_round` cash-out).
- `phase == "RUN_LOST"` → the server lost → render the native game-over (`create_UIBox_game_over`).
- `phase == "BLIND_ACTIVE"` → blind continues; do nothing but the value sync.

In practice, because we set `G.GAME.chips` / `G.GAME.blind.chips` from the server, Balatro's own
post-hand `end_round` will reach the same branch on its own — so the lightest implementation lets it
fire and simply guarantees the inputs are server-true. The point of phrasing it as "gate on phase" is
the **divergence assert**: compute what Balatro's local check *would* decide and compare it to
`view.phase`; if they differ (a boss effect, a joker, a counter race), **the server wins and we log a
loud divergence** to the wire file. That assert is the single most important tripwire in testing — it
is what makes this server-driven rather than server-coincident.

### 4.5 Stop after the blind (Stage 2 boundary)

When `phase` becomes `SHOP` or `RUN_LOST`, the bridge has delivered a complete blind. For Stage 2 we
let Balatro show its native cash-out / game-over and **disengage** (`ENGAGED = false`, close `CONN`).
Continuing into the shop on the server is Stage 3.

---

## 5. Win/lose walkthrough (target behavior)

**Win (score reaches requirement):**
1. Player plays a hand → mod sends `playHand` → server scores, sets `roundScore`, and (if
   `roundScore >= requirement`) `phase = SHOP`.
2. Mod `reconcile(view)`: `G.GAME.chips = roundScore` (now ≥ `blind.chips`), counters synced.
3. Balatro's native post-hand flow runs `end_round()`, sees `chips - blind.chips >= 0`, goes to
   `ROUND_EVAL`; `evaluate_round` renders reward/interest rows natively.
4. Mod sees `phase == SHOP`, disengages. (Stage 3 would instead drive the server shop.)

**Lose (hands exhausted, short):**
1. Final hand played → server `handsLeft == 0`, `roundScore < requirement`, `phase = RUN_LOST`.
2. Mod `reconcile(view)`: `hands_left = 0`, `chips = roundScore`.
3. Balatro `end_round()` sees `chips < blind.chips` and no hands → `GAME_OVER`;
   `create_UIBox_game_over` renders natively.
4. Mod sees `phase == RUN_LOST`, disengages.

**Win mid-blind (before hands run out)** is the case Stage 1 never exercised and the reason we must
sync `blind.chips`: the server can declare the blind won on hand 2 of 4; Balatro must agree because
its `chips >= blind.chips` check now uses the server's requirement.

---

## 6. Risks / open questions

1. **Timing of the counter sync vs. Balatro's own decrement.** Balatro's `evaluate_play` path will
   itself `ease_hands(-1)`. If we *also* set `hands_left = view.handsLeft`, we must sync **after**
   Balatro's decrement settles, or set absolutely (not relatively) to avoid double-counting. The
   `evaluate_play` hook wraps `_eval(e)`; `_eval` queues animated events, so the decrement is not
   immediate. **Likely need to defer `reconcile` into an `E_MANAGER` event** that runs after the
   hand's scoring events, rather than inline. This is the trickiest part — prototype first.
2. **`G.GAME.chips` vs. the count-up (must end as a server value).** Balatro animates `G.GAME.chips`
   up during scoring; setting it mid-animation fights the ease. Two levels:
   - *Minimum (Stage 2):* let the native count-up animate, then reconcile the **final** `chips` to the
     server `roundScore` after the scoring events drain (deferred-event approach). Guarantees the
     decided value is the server's. On a vanilla deck the animated steps already match, so this looks
     identical and is enough for the §8 acceptance criteria.
   - *Target (same code path Stage 4 needs):* drive the count-up itself from the server `replay`
     stream — step the chips/mult to each `ReplayEntry.runningChips/runningMult` and reuse Balatro's
     `juice`/ease per step. This is the fully server-driven count-up; build it now if we want zero
     local scoring in the loop, since it's the exact mechanism jokers/editions will require.
3. **Boss blinds.** `requirement`/effect differ; we set `blind.chips` from the server, but boss
   *effects* (debuffs, hand-size deltas) are still Balatro-local in Stage 2. Restrict Stage 2
   acceptance to **small/big blinds**; boss-effect authority is its own slice.
4. **Disengage cleanliness.** After the blind, Balatro's native flow wants to continue (shop, next
   blind). We disengage so it runs vanilla from there. Verify no half-synced state leaks (stale
   `blind.chips`) into the next round once we *do* continue in Stage 3.

---

## 7. Optional server nicety

To make the client's divergence check trivial, `GameServer` could echo a top-level `phase` and the
flat `roundScore`/`requirement` alongside `view` in the `WsResponse` (they're already inside `view`;
this is just convenience for the regex-light path). Not required if §4.1 lands a real JSON decode.

---

## 8. Acceptance criteria

A blind is "Stage 2 complete" when, against the live server (`./gradlew run`) with the bridge mod:

- [ ] Small **and** big blind, both **win** (reach requirement) and **lose** (hands out, short),
      render Balatro's native `ROUND_EVAL` / `GAME_OVER` with the **server's** numbers.
- [ ] Winning **before** hands are exhausted ends the blind immediately (mid-blind win works).
- [ ] `hands_left` / `discards_left` shown in the HUD match the server `view` at every step.
- [ ] The round score the win is decided on equals the server `roundScore` (no local-scoring drift on
      a vanilla deck).
- [ ] The wire log shows **zero** phase-divergence asserts across a full blind.
- [ ] Server down → silent vanilla fallback still holds (no crash on engage failure).

Verification is manual via the real game; capture the wire log (`build/balatro-bridge-wire.txt`) and
the dump (`build/balatro-bridge.txt`) as evidence, mirroring the Stage-1 proof.

---

## 9. Staged roadmap (recap)

| Stage | Scope                                                        | State        |
|-------|-------------------------------------------------------------|--------------|
| 1     | Server-authoritative **hands** in a blind (identity-override) | **done**     |
| **2** | **Full blind**: requirement/counters/score synced, native win/lose render | **this doc** |
| 3     | Server-driven **shop & economy**, proceed to next blind, ante progression | next        |
| 4     | **Jokers/editions**: server `replay` drives the native count-up where local scoring diverges | later       |
