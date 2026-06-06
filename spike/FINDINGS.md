# Spike: can Balatro's real scoring Lua run headless? — YES (with a caveat)

**Question:** Instead of reimplementing 150 jokers in Java (parity-risk forever),
can we run Balatro's *actual* `calculate_joker` / scoring Lua server-side, with no
graphics, as the authoritative "scoring brain"?

**Verdict: feasible, and it's the fidelity-correct path.** But the right shape is
"boot the game's data+logic layer headless," not "extract one function."

## What was proven (evidence in `headless_spike.lua`)
- The machine already has **LuaJIT 2.1** — the *exact* runtime LÖVE/Balatro ships.
- Balatro's real **`card.lua` loads under standalone LuaJIT** once the engine
  class lib (`engine/object.lua`, real) is present and the UI/sound/event/
  localization surface is stubbed with no-ops. (`Card = Moveable:extend()` is the
  only load-time dependency; everything else is method definitions.)
- The real **`Card:calculate_joker` executes headless** and returns correct
  scoring values. Confirmed: **Joker Stencil → `Xmult_mod = 4`** straight from the
  unmodified game code, no LÖVE, no rendering. We never ran the UI-heavy
  `Card:init` — just attached the `Card` metatable and called the method.

## The caveat (why it's "boot the data layer," not "extract a function")
The generic `joker_main` chain reads a full set of `ability` config defaults
(`mult, x_mult, t_mult, t_chips, h_mult, type, extra.*`, …). The real game builds
these from `G.P_CENTERS[key].config` via `Card:set_ability`. Hand-feeding ability
tables is whack-a-mole (we hit `x_mult` → `t_chips` → … nil one at a time).

So the production approach is:
1. Load the game's real data tables (`G.P_CENTERS` / `init_item_prototypes`).
2. Build jokers/cards with the game's own `create_card` / `set_ability`.
3. Stub ONLY rendering / sound / event-manager / UI (no-ops).
4. Drive `evaluate_play` (or call `calculate_joker` per the pipeline) and read
   `chips/mult` + a replay log out.

This is more than a handful of stubs but is clearly tractable — it's essentially
what existing headless-Balatro projects (e.g. Balatrobot) already do.

## What this means for the architecture
**Hybrid wins for the stated priority (exact fidelity, no parity grind):**

- **Lua sidecar = the scoring brain.** A headless LuaJIT process boots Balatro's
  real data+logic, exposes a tiny RPC: `intent in -> {authoritative state, score,
  replayLog} out`. Exact behavior for *all* content, forever. Vanilla-compatible
  seeds come for free (we run the real `pseudohash/pseudoseed`). It's still
  server-side, so still 100% cheat-proof.
- **Java = the authoritative server shell.** Lobby, intent validation, run/round
  orchestration, multiplayer coupling (PvP compare, opponent reads, Trap sends),
  persistence, anti-cheat boundary. Java never trusts the client; it calls the
  Lua brain.

The Java engine already built is **not wasted**: it's a clean reference + fallback
and it nailed down the exact pipeline/spec. But for production scoring, the real
Lua brain removes the entire "did we replicate joker #137 correctly?" risk class —
which was the core concern.

### Honest tradeoffs
- ➕ Exact fidelity; zero reimplementation of content; Steamodded jokers/mods load
  via their own definitions; real seeded RNG (vanilla seed compatibility).
- ➖ You run ~10k lines of someone else's code headless; must maintain the LÖVE
  stub surface across Balatro updates; ops cost of a Lua process pool per game;
  determinism requires the server to own the seed (it does — spec §8).

## Spike-1b: headless LÖVE probe (gating the "run real modded game" plan)
Installed LÖVE 11.5 (the line Balatro ships on) and probed GL context creation
(`spike/loveheadless/`):

- **Hidden window, real GPU:** boots fine — `renderer=OpenGL`, offscreen
  `Canvas` creation succeeds. The game runs with no *visible* window.
- **SDL dummy video driver (true windowless):** **FAILS** —
  `Unable to create OpenGL window ... dummy driver has no GL`. LÖVE requires an
  OpenGL 2.1+ context; graphics cannot be disabled (Balatro builds atlases/
  canvases/shaders at load).

**Conclusion:** running the real modded stack headless is feasible, but it needs a
GL context — not "no graphics." Implications:
- **Production = Linux + Xvfb + Mesa llvmpipe (software OpenGL).** Virtual display +
  CPU GL → LÖVE boots with no physical GPU. This is the standard, proven path for
  headless LÖVE (CI, Balatrobot, etc.). Lovely + SMODS + mods load normally into
  that process.
- The server cares about **state transitions** (intents → scoring via the update
  loop + event manager), not the rendered pixels — render output is thrown away.
  Run low-res, cap/curtail draw work; the GL context just has to *exist*.
- **One LÖVE process per active run** (G is a singleton) → managed process pool on
  the Linux host. This is the concrete "ops reality."
- Windows dev box: use a hidden window (visible=false) + real GPU for local
  testing; true windowless is a Windows limitation, not a project blocker.

Still **unproven (next spike):** that Lovely+SMODS+mods boot under Xvfb on Linux
and a custom modded joker's `calculate` runs end-to-end. That needs a Linux env.

## Recommendation
Adopt the **hybrid**: thin authoritative shell + the real (modded) Lua game run
headless as the scoring brain (Linux + Xvfb + llvmpipe).
Next concrete step is a focused spike-2: boot `G.P_CENTERS` headless and get
`evaluate_play` to return a full hand score for a real joker loadout end-to-end.
If that lands, the parity problem is gone and Java is "just" the secure server.
