# Rebuild: Balatro as a server-driven mod (own the logic, render with its assets)

The strategic shift from the thin-client *bridge*: stop **shadowing** Balatro's logic (intercept + sticker, which needs fragile prediction) and start **owning** it — replace Balatro's decision systems (deck/draw/shop/scoring/RNG/state) with server-driven mod code, and call Balatro's *rendering* (sprites, fly animations, juice, sounds, UI) with the data we choose. Balatro becomes the **body**; the server is the **brain**. Respects LocalThunk: players own Balatro, assets stay theirs, we add a competitive layer.

Supersedes the "Stage 2/3/4" framing in `STAGE2-DESIGN.md` for the *client*. The Java engine, `ClientView`, and intents are unchanged — the mod is just a much richer client of the same server.

---

## 1. Why owning is *cleaner* than shadowing (the prediction problem dissolves)

The bridge let Balatro draw its **own** cards and stamped server faces onto them just-in-time → we had to predict Balatro's every draw, and a missed card "escaped" (the `188 vs 148` drift).

The rebuild inverts it: **we create the server's exact cards and animate them in.** There is nothing to predict and nothing to stamp — Balatro's deck contents become irrelevant. We don't follow Balatro's draw; we *are* the draw.

## 2. The info-hiding superpower

`ClientView` deliberately carries **no deck order and no future cards** — only the current hand and `deckStats.remaining`. For the bridge that was incidental; for the rebuild it's load-bearing and *good*:

- We **cannot** pre-stage the full deck because the server never tells us — and that's exactly right. The client literally never holds the future, so it can't leak or exploit it.
- We only ever create the cards the server has *revealed* (the current hand; the replacement cards after a play/discard). The rebuild is **cheat-proof by construction** — the renderer has nothing to cheat with.

The deck on screen is a **face-down prop**: a stack of `deckStats.remaining` card-backs whose identities don't exist until the server reveals them on draw.

## 3. Owning the deal (the first system) — grounded recipe

Real machinery (verified, `D:\BalatroMod\Balatro`):

- **Draw loop** `G.FUNCS.draw_from_deck_to_hand` (`functions/state_events.lua:355`): draws
  `hand_space = min(#G.deck.cards, hand.config.card_limit - #G.hand.cards)` cards, calling
  `draw_card(G.deck, G.hand, …)` that many times. The stop condition is **ironclad** — staging the
  right deck count makes it draw the right number. (The old "draws ~50" came from corrupting
  `G.deck.cards`/`card_limit`, not from the loop.)
- **Single draw** `draw_card(from, to, percent, dir, sort, card, …)` (`functions/common_events.lua:386`):
  if a `card` is passed, it does `from:remove_card(card)` then `to:emplace(card)`. A card created at the
  **deck position** (`G.deck.T.x/y`) that is emplaced into `G.hand` **flies** there via the Moveable lerp
  + `align_cards` — that *is* the native animation. (`emplace` alone with no starting position = the
  pop-in dead-end.)
- **Create a specific card** `card_from_control({s='C', r='8'})` (`misc_functions.lua:1625`): builds a real
  `Card` from `G.P_CARDS['C_8']` at the deck position and appends to `G.playing_cards`.
- **Draw order**: `remove_card` pops the **END** of `G.deck.cards` for a deck-type area
  (`cardarea.lua`). So append (don't prepend) the card we want drawn next.

**Recipe (REVISED — the create-and-inject approach below was a dead end; see the correction).**

> **CORRECTION (2026-06-13):** the first cut *created* server cards and injected them into `G.deck`, then
> drew them back out. It produced correct identities but **fought Balatro's deck count** — we were
> manipulating the same `G.deck.cards` Balatro owns, so the pile count oscillated and was left corrupt
> after a blind. **Don't create cards. Let Balatro draw its own deck.** The deck pile is then natively
> correct (Balatro decrements it), the animation is native, and nothing is left messed up.
>
> The deal is now **prime + native draw + reconcile**:
> 1. **Prime** (before the draw): set the about-to-be-drawn deck-end cards' faces to the queued server
>    cards (`prime_deck_for_draw`) so they fly in showing the right face — *smooth*.
> 2. **Native draw**: call the original `draw_from_deck_to_hand`. Balatro draws its own deck → perfect
>    count, native animation, no deck-fighting.
> 3. **Reconcile** (after the draw settles, deferred event): `reconcile_hand_to_server()` forces every
>    `G.hand` card to carry a server `uid` and each server card to appear once — *state-based*, so it
>    catches anything the prime missed. This is the no-escaped-card guarantee, without ever touching the
>    deck pile.
>
> We never manipulate `G.deck`, so the count is always Balatro's own and stays correct during and after
> the blind. `sync_deck_pile`/`make_card`/`deal_server_cards` are superseded (left as dead code, to remove).

The reconcile makes the hand match the server by construction, so index mapping on play/discard
(`bbridge_uid` → server index) can
never have an unmapped card.

**Deck pile visual** (`sync_deck_pile`): keep `G.deck.cards` populated with `remaining` face-down dummy
backs purely for the count/stack look; they're never revealed (we always pass an explicit real `card`
to `draw_card`, so the dummies are just visual filler we add/remove to match `deckStats.remaining`).

## 4. Owning the count-up (next system)

Same move for score: don't let `evaluate_play` compute the number (it drifts). Drive the count-up from
the server's `replay` stream — step `G.GAME.current_round.current_hand.chips/mult` via the native
`update_hand_text(...)` + `juice` per `ReplayEntry` (`source, kind, text, runningChips, runningMult`),
and snap the final `G.GAME.chips = roundScore`. The `update_hand_played` hook (already in the mod) keeps
the **win/lose** decision server-driven. Result: the count-up *looks* native but every number is the
server's.

## 5. System-by-system rebuild order

1. **Deal / draw** — `dealServerHand` (this doc). *PoC that proves the model.*
2. **Play / discard** — already routed to the server; swap the count-up to replay-driven (§4).
3. **Blind / round win-lose** — `update_hand_played` hook already drives it from `phase`. ✔ in progress.
4. **Shop / economy** — replace shop generation; render `ClientView.shop/vouchers/packs` as native shop
   cards; buys/sells/rerolls are intents.
5. **Jokers / consumables / vouchers / tags** — render from `ClientView`; effects already server-side.
6. **Boss blinds, deck/stake select, run start** — drive from the server (the engine now has all 15
   decks + 11 stakes).

Each step is the same pattern: replace the *decision*, call the *renderer* with server data.

## 6. Dead-ends, explained and avoided

- *Pop-in* (`create_playing_card` straight into `G.hand`): the card had no start position, so no fly.
  **Fix:** create at `G.deck.T` and `draw_card(... , card)` so the Moveable lerps it in.
- *"Draws ~50 / kills the deck"* (stage onto `G.deck` + `draw_card`): we corrupted `G.deck.cards` /
  `card_limit` while Balatro still thought it owned the count. **Fix:** we now own the draw loop *and*
  the deck pile, so the count is always ours and consistent.

## 7. PoC acceptance

Owning the deal is proven when, in the real game: a blind deals the **server's exact hand**, in a stable
order, with the **native fly-from-deck animation and sound**, **zero stickering / zero `IDENTITY GAP`**,
and the deck-pile count matches `deckStats.remaining`. At that point the fragile prediction is gone and
the same pattern rolls out to scoring, shop, and the rest.

---

## 8. Owning the shop + the run lifecycle (the next system — grounded)

This is what makes **jokers work**. Today every `select_blind` opens a *fresh* server run (`newRun`),
so there is no progression, no shop, and any joker you buy in Balatro's native shop is invisible to the
server → the server keeps scoring a jokerless run. To fix it we must (a) make one server run **persist
across blinds**, and (b) render the **server's** shop so a buy actually enters the server run.

### 8.1 The two phase machines that must line up

**Server** (`Run.java`, verified): `newRun → BLIND_SELECT`; `selectBlind → BLIND_ACTIVE` (deals);
`playHand`/`discard` until win → `winBlind()` sets `phase = SHOP` and **generates the shop + packs +
per-ante voucher**; in `SHOP` the intents `buyShopItem` / `reroll` / `buyVoucher` / `openPack` /
`pickPackItem` / `sellJoker` / `useConsumable` apply; **`proceed`** exits the shop, advances
blind/ante, calls `startBlind()` → back to `BLIND_SELECT`. So one run is a *loop*:
`BLIND_SELECT → BLIND_ACTIVE → SHOP → (proceed) → BLIND_SELECT → …`.

**Balatro** (real seams, verified in `D:\BalatroMod\Balatro`):
`SELECTING_HAND → (win) end_round → ROUND_EVAL=8` (cash-out screen, reads `G.GAME.dollars`)
`→ G.FUNCS.cash_out` (`button_callbacks.lua:2912`, sets `G.STATE = SHOP=5`, `ease_dollars(round.dollars)`)
`→ Game:update_shop` (`game.lua:3072`, builds `G.shop` UIBox + populates `G.shop_jokers/_vouchers/_booster`)
`→ G.FUNCS.toggle_shop` (`button_callbacks.lua:2481`, the **Next Round** button, sets `G.STATE =
BLIND_SELECT=7`, removes `G.shop`) `→ G.FUNCS.select_blind`.

**The lining-up:** map Balatro's `toggle_shop` (leave shop) → server `proceed`; map Balatro's
`select_blind` → server `selectBlind` **on the existing connection** (NOT a new run) whenever a run is
already live. Only open `newRun` when there is no live run (first blind of a fresh Balatro run).

### 8.2 Lifecycle restructure (the foundational change)

- `select_blind` hook: if `CONN` is alive and the run is mid-progression (server is in `BLIND_SELECT`
  because we just `proceed`ed), send **`selectBlind`** and queue the returned hand for the deal. Else
  (no run / run over / stale socket) `open_run()` as today. A single `RUN_LIVE` bool tracks this.
- **Win path change** (`update_hand_played`): stop forcing `NEW_ROUND` (which *skips* the shop). Drive
  the native outcome from the server phase: `SHOP → G.STATES.ROUND_EVAL` (cash-out → native shop),
  `RUN_LOST → G.STATES.GAME_OVER`, otherwise `DRAW_TO_HAND`. Snap `G.GAME.dollars = view.money` and
  `G.GAME.chips = view.roundScore` so the cash-out shows the server's economy. (The no-soft-lock
  guarantee — advance state first, then `pcall` the snaps — is preserved.)
- `toggle_shop` hook: send **`proceed`**; keep `CONN`. Native then goes to `BLIND_SELECT`.

### 8.3 Rendering the server shop (reconcile, like the deal)

Same move as the deal: **let Balatro build its native shop, then reconcile its cards to the server's.**
Don't replicate `create_card_for_shop`'s RNG.

- Before `update_shop` populates, set `G.GAME.shop.joker_max = #view.shop` so the native loop
  (`game.lua:3111`) emplaces the right number of main-slot cards (with real price tags + buy buttons via
  `create_shop_card_ui`).
- After the shop materializes (deferred ~0.8s, or poll until `#G.shop_jokers.cards > 0`), **reconcile**:
  for each server shop item `i`, take `G.shop_jokers.cards[i]`, `card:set_ability(G.P_CENTERS[key])`
  (the joker/consumable analog of `set_base` — verified `card.lua`), set `card.cost = item.cost`, and
  tag `card.bbridge_shop_index = i`. Add/remove trailing cards to match the count. Vouchers →
  `G.shop_vouchers` from `view.shopVouchers`; booster packs → `G.shop_booster` from `view.packs`
  (follow-ups; v1 does the main joker/consumable slots).
- `Card:set_ability` between Joker and Tarot/Planet is fine for v1 (the plain **Buy** button works for
  all; the consumable-only *Buy & Use* button is a follow-up).

### 8.4 Buy / reroll / sell as intents

- `G.FUNCS.buy_from_shop(e)` hook (`button_callbacks.lua:2404`): `e.config.ref_table` is the card →
  read `card.bbridge_shop_index` → send **`buyShopItem`** with that index. On `accepted`, let native run
  for the visual (card flies to `G.jokers`/`G.consumeables`), then snap `G.GAME.dollars = view.money`.
  On reject, popup + swallow (don't let native buy). The bought joker now lives in the **server** run, so
  the next blind's `roundScore` includes it — *jokers work.*
- `G.FUNCS.reroll_shop(e)` hook (`button_callbacks.lua:2855`): send **`reroll`**; on success let native
  clear+repopulate, then re-run the §8.3 reconcile against the new `view.shop`; snap dollars.
- `G.FUNCS.sell_card` (joker sell) → **`sellJoker`** by the joker's row index. Follow-up.

### 8.5 Rendering owned jokers

The bought joker must *show* in the joker row. Render `G.jokers` from `view.jokers` (each
`{key,name,…}` → `Card(...,G.P_CENTERS[key])` emplaced into `G.jokers`), reconciled the same way as the
hand (by a stable id) so it stays in sync after sells. Their **effect** is already server-side (the
`roundScore` snap), so rendering is purely visual — exactly the info-hiding contract.

### 8.6 v1 scope vs follow-ups

- **v1 (the spine):** persist the run across blinds; native cash-out shows server money; native shop
  populated with the server's main-slot items; **buy a joker → it enters the server run**; Next Round →
  `proceed`; owned jokers rendered. This delivers the loop where *jokers work*.
- **Follow-ups:** reroll re-reconcile, vouchers, booster packs (open/pick/skip), sell joker, consumable
  use (Tarot targets), editions/stickers on shop cards, the *Buy & Use* button. Each is the same
  hook-an-intent-then-render pattern.

### 8.7 Acceptance

Win a blind in the real game → the **cash-out shows the server's $$$** → the shop shows the **server's
jokers at the server's prices** → buying one deducts the server's money and adds it to your row → the
**next blind's score reflects that joker** (server `roundScore`, snapped) → Next Round advances the
server's ante. No newRun between blinds; one persistent server run drives the whole sequence.

---

## 9. Shop economy — full coverage (landed) + packs (deferred)

Built on §8, the rest of the between-blinds economy is now server-driven via the same
hook-an-intent-then-render pattern. Seams verified in `D:\BalatroMod\Balatro`; intents verified to route
with the `accepted`/`view` shape over the wire.

**Landed (v1.1):**
- **Vouchers** — `reconcile_shop_to_server` swaps `G.shop_vouchers` cards to `view.shopVouchers`
  (`set_ability` + cost + `bbridge_voucher_index`); hook **`Card:redeem`** (the method — the
  `redeem_from_shop` FUNCS button is engine-injected and not in the base files) → `buyVoucher` → native
  redeem renders the voucher + `snap_money`.
- **Sell joker** — hook `G.FUNCS.sell_card`; the sold card's row position in `G.jokers.cards` is the
  server index (orders stay aligned: jokers append on buy, both sides remove the same index on sell) →
  `sellJoker`. Eternal/consumable sells are blocked (no desync). 
- **Skip blind** — hook `G.FUNCS.skip_blind` → `skipBlind` (server advances the blind + deals the next
  hand, leaving phase `BLIND_SELECT`); native then shows the next blind and `select_blind` continues it.
  Fixes the v1 "don't skip" caveat.
- **Consumable use** — hook `G.FUNCS.use_card`; a held consumable (`card.area == G.consumeables`) maps to
  its server index by `center_key` against `view.consumables`; targets = highlighted hand cards'
  `bbridge_uid`s (Tarots; planets ignore them) → `useConsumable(index, targets)`. Any card identities the
  Tarot changes self-heal on the next draw via `reconcile_hand_to_server`. Buy&Use is disabled (buy then
  use separately) to avoid the immediate-use desync.
- **Deck/stake** — `open_run` forwards the player's New Run choice: native `G.GAME.selected_back_key`
  `b_xxx → d_xxx`, and `G.GAME.stake` (1..8) as the stake. Verified: `d_blue`→"Blue Deck", `3`→"Green
  Stake".
- **Cleanup** — the dead create-and-inject deal code (`make_card`/`deal_server_cards`/`sync_deck_pile`/
  `REBUILD`) is removed.

**Landed — booster packs.** Same own-it pattern as the shop, now implemented (seams verified):
- Reconcile `G.shop_booster` from `view.packs` (`{kind,size,cost}`) — `set_ability` to the pack center
  **`p_<kind>_<size>_1`** (the trailing variant only changes the sprite; game.lua:665+), set cost, tag
  `bbridge_pack_index`; preserve `ability.booster_pos`. Extras removed.
- **Open**: hook **`Card:open`** (`card.lua:1681` — sets `G.STATE` to the pack state `TAROT_PACK=9 /
  PLANET_PACK=10 / SPECTRAL_PACK=15 / STANDARD_PACK=17 / BUFFOON_PACK=18`, reads `extra`/`choose` from
  the center) → `openPack(index)`; then **poll** `G.pack_cards` (materializes ~2-3s) and reconcile to
  `view.openPack.items` (CARD→`set_base`, JOKER/CONSUMABLE→`set_ability`, tag `bbridge_pack_item_index`).
- **Pick**: `G.FUNCS.use_card` is the *universal* pick path (consumable→use, Joker→`G.jokers`, playing
  card→`G.deck`). Hook branch `card.area == G.pack_cards` → `pickPackItem(index)`. **Model bridge:** the
  server's `pickPackItem` *stores* the item, but native *uses* consumables immediately — so for a
  consumable we chain `useConsumable(lastIndex, targets)` (targets = highlighted hand uids) to match.
  Multi-pick (Mega, `choose=2`) re-indexes the remaining choices via the same poll.
- **Skip**: hook `G.FUNCS.skip_booster` (`button_callbacks.lua:2558`) → `skipPack` (guarded on
  `view.openPack`). Pack auto-closes server-side when `picksLeft` hits 0 (`view.openPack`→null), matching
  native `end_consumeable` → `PACK_INTERRUPT` (shop). Phase stays `SHOP` throughout.

**Highest in-game-iteration risk** of all systems (pack materialize timing, the pick+use chaining index,
targets in packs, consumable-slot-full edge). All paths are pcall'd / poll-gated / block-on-reject, so a
glitch degrades rather than soft-locks — but this is the system most likely to need a real-game pass.

---

## 10. Editions, reroll cost, boss blind (landed)

- **Editions** — `parse_objs` reads shop item `edition`; `set_shop_card_identity` applies
  `Card:set_edition` (`FOIL→{foil}`, `HOLOGRAPHIC→{holo}`, `POLYCHROME→{polychrome}`, `NEGATIVE→{negative}`,
  `NONE→nil`). Bought jokers **inherit** the edition (native buy keeps the card), and rendering Negative
  also makes native's `check_for_buy_space` grant the extra slot — matching the server.
- **Reroll cost** — shop reconcile sets `G.GAME.current_round.reroll_cost = view.rerollCost` (native
  increments its own, which drifts after server-driven rerolls).
- **Boss blind** — `select_blind` faces `e.config.ref_table` (the UI's baked blind, not `blind_choices` at
  select-time — verified `button_callbacks.lua:2513`). The continue branch has the server view (with the
  new `bossKey`) *before* `_sel(e)`, so it overrides `e.config.ref_table = G.P_BLINDS[bossKey]` →the player
  faces the **server's** boss with the real native effect. `BossBlind.key` is already the native `bl_xxx`
  format; non-boss / missing key = no-op (native's own blind). Server change: `bossKey` added to
  `ClientView` (build green). This *reduces* divergence (native already applied its own boss).

**Not done — and intentionally so without an in-game pass:** replay-driven count-up (cosmetic; the
end-of-blind snap already makes the score correct, and native per-hand scoring ≈ server now) and
server-*granted* joker rendering (native runs its own tag system in parallel, so additively creating
granted jokers risks a double-render that won't self-remove). Both want ground truth from a real session.

**Owned-joker rendering.** Bought jokers render for free (native buy emplaces the swapped card into
`G.jokers`). A divergence tripwire logs when `#G.jokers.cards != #view.jokers` — the case that needs full
server→native joker creation is **server-granted** jokers (tag rewards, pack jokers), deferred with packs.
