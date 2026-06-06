# Balatro Engine Spec (from source)

Derived by reading the actual game source at
`D:\SteamLibrary\steamapps\common\Balatro\Balatro` — primarily
`functions/state_events.lua` (`evaluate_play`) and `card.lua` (`Card:calculate_joker`).
This is the foundation for a server-authoritative reimplementation.

---

## 0. The big realization

Balatro's "engine" is much smaller than it looks. The entire content/effect
system is **one dispatch function** plus **one scoring pipeline**:

- `Card:calculate_joker(context)` — every joker/card effect is a big branch on
  `context.*` flags. The *context table is the event system.* There is no
  effect taxonomy; there's a fixed set of moments (context flags) and a fixed
  set of return fields that mutate score/money/state.
- `G.FUNCS.evaluate_play(e)` — the ordered scoring pipeline that raises those
  contexts in a specific sequence and applies the returned mutations.

So "rebuild Balatro" really means: **reimplement these two things faithfully,
then transcribe each joker's branch as data + a small effect.** Everything else
(shop, blinds, deck) is bookkeeping around them.

---

## 1. The scoring pipeline — exact order

From `evaluate_play` (`state_events.lua:571`). This order is the whole ballgame;
get it right and ~90% of jokers fall out of it. Each numbered step is sequential.

1. **Identify the hand** — `get_poker_hand_info(play.cards)` →
   `(handName, scoring_hand, poker_hands)`. `scoring_hand` = which played cards
   actually count.
2. **Assemble `scoring_hand`:**
   - `Splash` joker → *all* played cards score.
   - `Stone Card`s always score (added as "pures").
   - Sort `scoring_hand` by physical x-position (left→right). **Card order matters.**
3. **Blind debuff check** — `blind:debuff_hand(...)`. If the boss blind debuffs
   the whole hand → score is 0 (skip to debuff branch). Otherwise continue.
4. **Base chips/mult** — from the *played hand's level*:
   `mult = hands[hand].mult`, `chips = hands[hand].chips`.
5. **`before` pass** — every joker, left→right, called with `{before=true}`
   (setup effects, hand level-ups).
6. **Blind `modify_hand`** — boss blind may alter base mult/chips here.
7. **Score each played card** (`scoring_hand`, left→right):
   1. If the card is debuffed → skip it (flag blind triggered).
   2. **Compute repetitions** for this card: start `{1}`, then add reps from
      the card's **Red Seal** (`{repetition=true}`), then from each joker
      (`{repetition=true, other_card=card}`) — e.g. Hack, Sock and Buskin,
      Hanging Chad. Each rep re-runs the card's full scoring.
   3. For each repetition:
      - **Base card effect** via `eval_card(card, {cardarea=G.play, ...})`:
        rank chips, enhancement bonuses (Bonus/Mult/Glass/Lucky/etc.).
      - **Per-card joker effects**: every joker, left→right, with
        `{individual=true, cardarea=G.play, other_card=card}`.
      - **Apply collected effects, in this fixed field order** (lines 702–777):
        `chips` → `mult` → `p_dollars` → `dollars` →
        `extra{mult_mod, chip_mod, swap, func}` → `x_mult` →
        `edition{chip_mod, mult_mod, x_mult_mod}`.
8. **Score held-in-hand cards** (`G.hand.cards`, each):
   - Reps from Red Seal + jokers (`{repetition=true, cardarea=G.hand}`),
     e.g. Mime, Sock and Buskin.
   - Per-card joker effects (`{individual=true, cardarea=G.hand, other_card=card}`).
   - Apply `h_mult`, `x_mult`, `dollars`, messages. (Steel = x_mult while held;
     Gold = $ while held.)
9. **Main joker effects** (every joker, then consumables; left→right) — lines 877–944:
   1. **Edition effects** (`{edition=true}`): Foil `chip_mod`, Holographic
      `mult_mod` applied now; Polychrome `x_mult_mod` deferred to 9.4.
   2. **`joker_main`** (`{joker_main=true}`): the joker's primary effect.
      Applied in order: `mult_mod` (additive) → `chip_mod` → `Xmult_mod` (×).
   3. **Joker-on-joker** (`{other_joker=_card}`): every joker reacts to this
      joker. Apply `mult_mod`/`chip_mod`/`Xmult_mod`. (How "× per joker" type
      effects read neighbors.)
   4. **Deferred edition x_mult** (Polychrome) applied here.
   - **Joker array order is left→right**, so position interleaves +mult vs ×mult.
10. **Deck final step** — `selected_back:trigger_effect{context='final_scoring_step', ...}`
    may adjust final chips/mult (challenge decks).
11. **Card destruction** — jokers may destroy scored cards (`{destroying_card=card}`);
    Glass cards shatter on a `pseudorandom('glass')` roll. Then a
    `{remove_playing_cards=true, removed=...}` pass notifies jokers.
12. **Final score = `chips × mult`.** Compared against the blind requirement.

> Note `mod_mult`/`mod_chips` wrap every assignment — that's where overflow /
> precision handling lives. Reproduce it (Balatro uses a custom big-number scheme
> for very high scores).

---

## 2. The context catalog (the event set)

Every entry is a key set on the `context` table passed to `calculate_joker`.
A joker "listens" to a moment by branching on that key. Grouped by phase.

### Scoring contexts (raised inside `evaluate_play`)
| context flag | when | typical use |
|---|---|---|
| `before` | before any card scores | setup, hand level-up |
| `individual` + `cardarea=G.play` | per played scoring card | +chips/+mult per card |
| `individual` + `cardarea=G.hand` | per held-in-hand card | Steel/Gold, held effects |
| `repetition` (+`other_card`) | computing a card's retriggers | Hack, Mime, Sock & Buskin |
| `repetition_only` | red-seal retrigger probe | seal repetitions |
| `joker_main` | main joker pass | the joker's headline effect |
| `other_joker` | each joker reacts to another | "× per joker", copy logic |
| `edition` | joker edition pass | Foil/Holo/Polychrome on jokers |
| `destroying_card` | post-score destruction check | self-destruct / consume cards |
| `remove_playing_cards` (+`removed`) | after cards destroyed | react to destruction |

### Lifecycle contexts (raised elsewhere; grep `calculate_joker(` to find all)
| context flag | source file | when |
|---|---|---|
| `setting_blind` (+`blind`) | state_events.lua | blind selected |
| `first_hand_drawn` | game.lua | first hand of the round drawn |
| `pre_discard` (+`full_hand`,`hook`) | state_events.lua | before a discard resolves |
| `discard` (+`other_card`,`full_hand`) | state_events.lua | per discarded card |
| `end_of_round` (+`game_over`) | state_events.lua | round won |
| `using_consumeable` (+`consumeable`) | button_callbacks.lua | tarot/planet/spectral used |
| `buying_card` (+`card`) | card.lua / button_callbacks | card bought in shop |
| `selling_card` (+`card`) | button_callbacks.lua | another card sold |
| `selling_self` | card.lua | this joker sold |
| `reroll_shop` | button_callbacks.lua | shop rerolled |
| `ending_shop` | button_callbacks.lua | leaving shop |
| `skip_blind` | button_callbacks.lua | blind skipped (tag) |
| `open_booster` (+`card`) | card.lua | booster pack opened |
| `skipping_booster` | button_callbacks.lua | booster skipped |
| `playing_card_added` (+`cards`) | misc_functions.lua | card added to deck |

> This table is the **server's event bus**. Reimplement these as named hooks.

---

## 3. The return-field catalog (the mutation API)

What a `calculate_joker`/`eval_card` return table may contain. These ARE the
"capability API" — the complete set of ways an effect touches score/state.

| field | effect |
|---|---|
| `chips` | + chips (per-card context) |
| `mult` | + additive mult (per-card) |
| `x_mult` | × mult (per-card / held) |
| `h_mult` | + mult from held-in-hand card |
| `p_dollars` / `dollars` | + money during scoring |
| `chip_mod` | + chips (main/edition/joker-on-joker context) |
| `mult_mod` | + mult (main/edition/joker-on-joker) |
| `Xmult_mod` | × mult (main joker context) |
| `x_mult_mod` | × mult (edition context, deferred) |
| `repetitions` | retrigger count (repetition context) |
| `extra.mult_mod` / `extra.chip_mod` | + mult / + chips (per-card extra) |
| `extra.swap` | swap chips ↔ mult |
| `extra.func` | arbitrary side-effecting closure |
| `edition.{chip_mod,mult_mod,x_mult_mod}` | card edition contributions |
| `level_up` | level up the played hand |
| `message` / `card` / `colour` | UI: what to animate and on which card |

> `message`/`card`/`colour` are *presentation only*. On the server they become
> entries in a **scoring replay log** sent to the client to animate — the client
> never computes; it plays back. This is the clean client/server seam (see §5).

---

## 4. Joker-on-joker copying (Blueprint / Brainstorm)

`card.lua:2291` — elegant and worth copying exactly:

- **Blueprint** copies the joker to its **right**; **Brainstorm** copies the
  **leftmost** joker.
- Implemented by **re-calling the target's `calculate_joker(context)`** with the
  same context, then **re-attributing** the result's `card` to the blueprint
  card (so the animation points at Blueprint).
- A `context.blueprint` counter guards against infinite recursion
  (`> #jokers + 1` → stop). So Blueprint→Blueprint→target chains work and terminate.

Design takeaway: "copy" effects are just **re-entrant dispatch with attribution
swap.** No special-casing in the pipeline.

---

## 5. Mapping to a secure Java server

The structure above translates almost 1:1, and it's why this is securable:

- **`EvaluationContext`** — a Java object carrying the same fields
  (`cardarea`, `otherCard`, phase flag, `fullHand`, `scoringHand`, blueprint
  counter…). One per dispatch.
- **`JokerEffect`** (return) — a record with the §3 fields. Jokers return it;
  the pipeline applies it in the §1 order.
- **`Joker`** — `info()` (data: id/name/rarity/cost/artHash/desc template) +
  the effect logic keyed by context phase. The weird 20% drop to real code; the
  common 80% can later be a declarative `condition → modifier` DSL that compiles
  to this.
- **The pipeline** = a direct transcription of §1. Deterministic. Runs only on
  the server.
- **RNG** = reimplement `pseudorandom(seed_key)` exactly (it's a seeded,
  keyed PRNG). Seed lives server-side only; never sent to clients.
- **Client boundary** = the server runs `evaluate_play`, accumulates the
  `message/card/colour` events into a **scoring replay log**, and sends
  `{finalChips, finalMult, replayLog}`. The Lua client animates the log. It has
  no rules, no seed, no hidden state → nothing to cheat.

### Build order (recalibrated for "competitive, 100% secure")
1. Per-player authoritative sim: deck + `pseudorandom` + base scoring pipeline
   (§1) with a tiny joker set. Single blind. **Security is won here.**
2. One competitive gamemode end-to-end (Survival/Attrition): server-computed
   score comparison at PvP/Nemesis blinds + lives. Now it's secure multiplayer.
3. Narrow multiplayer coupling API: read opponent summary (score, hands left),
   send Trap cards. Validate every cross-player intent server-side.
4. Content breadth (transcribe jokers via §2/§3), then the registration/codegen
   platform once the patterns are clear.

---

## 6. Steamodded (SMODS): the threat and the toolkit

Source: `C:\Users\micha\AppData\Roaming\Balatro\Mods\smods-1.0.0-beta-1503a`.
In Balatro everything is an object, and SMODS makes nearly all of it
**registrable and overridable at runtime.** This is the strongest argument for
server authority *and* the mechanism for the thin client.

### Why the client can never be trusted (threat)
- `SMODS.Scoring_Calculation.func(self, chips, mult, flames)` — a mod can
  **replace the final scoring function itself.** `chips × mult` is not sacred.
- `SMODS.Scoring_Parameter` — add brand-new scoring quantities with custom
  `calc_effect` / `level_up_hand`.
- `take_ownership(key, obj)` (on every `SMODS.GameObject` subclass: Joker,
  Edition, Seal, Enhancement, Consumable, Blind, Rank, Suit, PokerHand…) — a mod
  can **seize and rewrite any vanilla object.**
- `Card:calculate_joker(context)` can be overridden wholesale.

⇒ On a Steamodded client, **the rules engine is mutable.** No client-computed
value — score, hand detection, money, RNG outcome — is trustworthy. The only
safe design is one where the client computes nothing that affects outcome.
This is why "compare reported scores" (the existing relay mod) is unfixable, and
why the §5 server-authoritative model is mandatory, not optional.

### Why that same power is the delivery mechanism (toolkit)
- **Codegen target** = generated `SMODS.Joker{ key, loc_txt, config, calculate }`
  definitions whose `calculate` only *displays the server's scoring replay log*.
  Real logic stays in the Java engine.
- **`take_ownership` = the neutering tool.** The client mod seizes every vanilla
  joker/blind/scoring function and replaces it with a display-only stub. The
  client physically cannot compute a score because the functions that would have
  are overwritten — it can only animate server output.
- **Registration/runtime-download** = ship these generated SMODS objects +
  content-addressed art; SMODS's `inject` pipeline loads them.

Net: SMODS flexibility forces server authority and simultaneously provides the
clean way to build the dumb-renderer client.

---

## 7. Prior art: the existing server's trust model (and your conversion map)

Source read: `D:\BalatroMultiplayerAPI-Server-main\src` (TypeScript, TCP relay).

**Its entire security model, verbatim** (`actionHandlers.ts:220`):
```ts
client.score = InsaneInt.fromString(String(score)); // store the client's CLAIM
// then relayed to the opponent as `enemyInfo`. No validation anywhere.
```
`GameMode.ts` is the whole server-side "game logic": starting lives + which antes
are `bl_pvp`. `InsaneInt` is only a big-number type to *compare reported scores*
(Balatro scores overflow f64 — note this for your Java score type, ties to the
`mod_mult`/`mod_chips` open item).

### Conversion map — client claim → authoritative intent
Every client→server action below is currently trusted. In the secure design,
the left becomes an *intent* and the server computes/validates the right.

| existing (client claims) | secure (client intent → server authority) |
|---|---|
| `playHand { score, handsLeft }` | `playHand { cardIndices }` → server runs §1 pipeline, computes score |
| `setAnte`, `setFurthestBlind`, `newRound` | server owns progression; derived, never sent |
| `failRound`, `failTimer` | server detects from authoritative state |
| `skip { skips }` | `skipBlind` intent → server applies tag, updates state |
| `spentLastShop { amount }`, `soldJoker` | `buy/sell { itemId }` → server validates money & ownership |
| `eatPizza`,`magnet`,`asteroid`,`sendPhantom`,`letsGoGamblingNemesis` | MP-joker intents → server validates & applies cross-player effect |
| `receiveEndGameJokers { keys }`, `receiveNemesisDeck { cards }` | server already holds both players' authoritative state; no transfer of trusted data |
| `moddedAction { [key]: unknown }` | no arbitrary trusted payloads; only typed, validated intents |

### Worth keeping from their server
- Lobby/matchmaking (5-letter codes, host/guest, ready states) — no security need.
- Reconnect/grace-period state restoration (`Lobby.ts`) — good pattern.
- TCP + newline-JSON transport shape, gamemode/blind schedule config (`GameMode.ts`).
- `bl_pvp` blind concept + lives/attrition/showdown/survival structure.

---

## 8. RNG / seed model (the hidden-information boundary)

Source: `misc_functions.lua:206-320`, `game.lua:2164-2168`.

### How vanilla works — three layers
1. **`pseudohash(str)`** — deterministic string→float in [0,1), pure IEEE-754
   double math:
   ```
   num = 1
   for i = #str downto 1:  num = ((1.1239285023/num)*byte(str,i)*π + π*i) % 1
   ```
2. **`pseudoseed(key)`** — **per-purpose keyed streams**. State lives in
   `G.GAME.pseudorandom[key]`:
   - lazily init: `state = pseudohash(key .. seed)`
   - advance each call: `state = abs(round13((2.134453429141 + state*1.72431234) % 1))`
   - return: `(state + hashed_seed) / 2`   where `hashed_seed = pseudohash(seed)` (set once, `game.lua:2168`)
   - `round13(x) = tonumber(string.format("%.13f", x))`  (locale-sensitive — '.' decimal)
3. **`pseudorandom(key,min,max)`** — `math.randomseed(pseudoseed(key)); math.random(...)`.
   `math.random` here is **LuaJIT's stock Tausworthe PRNG** (no LÖVE override of global `math`).

Seed itself: 8-char A–Z/1–9 string, user-entered or `generate_starting_seed()`.

### Reproducibility hazard
Bit-exact vanilla reproduction in Java requires porting **LuaJIT's Tausworthe
`math.random`/`math.randomseed`** AND every double rounding / `%.13f` round-trip.
Fragile; easy to get subtly wrong.

### DECISION: use our own PRNG, copy only the structure
In our design the **server is the sole RNG caller** (client computes nothing, §6),
so RNG need not match vanilla — only be **internally deterministic** (same seed →
same run on our server). That is all competitive play requires (both players get
the same run because the server uses one seed for both).

- ✅ Use a clean, fully-specified PRNG (xoshiro256**/PCG). Document it.
- ✅ Copy Balatro's **keyed-stream pattern**: one stream per purpose
  (`shuffle`, `shop`, `pack`, `glass`, `tag`, `boss`, …), lazy-init from
  `(seed, key)`, advance-on-use. Keeps purposes independent (consuming shop RNG
  never shifts the draw order) — good for fairness, replay, and debugging.
- ⛔ Do NOT replicate `pseudohash`/LuaJIT arithmetic unless **vanilla seed
  compatibility / community seed-sharing** becomes an explicit goal. That's an
  optional later feature, not a foundation.

### Hidden-information boundary (the security crux)
Server-only, NEVER sent to any client:
- the seed, `hashed_seed`, and every keyed-stream's current state
- unrealized future outcomes: deck order below the top, upcoming shop/pack
  contents, boss blind before reveal, tags not yet shown.

Sent to a client only at the moment it legitimately becomes visible:
- cards actually drawn into that player's hand
- shop contents once the shop is entered
- pack contents once opened
- the scoring replay log (§3) after a hand resolves
Opponent coupling (§7) exposes only the agreed-public summary (score, hands left,
lives) — never the opponent's deck/seed/hidden state.

---

## Open items to verify against source next
- [ ] `get_poker_hand_info` + `evaluate_poker_hand` (`misc_functions.lua:376`) — hand detection.
- [ ] `mod_mult`/`mod_chips` + big-number scheme (overflow at high scores).
- [ ] `pseudorandom` / seed derivation (engine; exact PRNG + per-key streams).
- [ ] Shop generation + `reroll` cost/odds.
- [ ] Blind/boss effect catalog (`blind.lua`) and `debuff_hand`/`modify_hand`.
- [ ] Full `calculate_joker` branch transcription (`card.lua`, ~4771 lines).
