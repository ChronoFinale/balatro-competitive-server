# 36 — Content Authoring Pipeline (JokerDef + Ruleset builders, validation, sprites, curation, versioning, cheat-proofing)

Target parity: **Balatro Multiplayer 0.4.0** (`Multiplayer.json` → `"version": "0.4.0"`,
on disk at `C:/Users/micha/AppData/Roaming/Balatro/Mods/multiplayer-0.4.0/`). Where the
0.3.3 ranked-changes spreadsheet (`C:/Users/micha/AppData/Local/Temp/xlsx_out2/*.txt`)
disagrees with the 0.4.0 source, the 0.4.0 source wins; deltas are flagged inline.

This doc covers how content gets *authored, validated, governed for ranked, versioned, and
made cheat-proof* — the pipeline, not the per-joker effects (those are docs 10–21).

---

## 1. How the REAL game / BMP 0.4.0 does it

### 1.1 The big 0.4.0 restructure: rulesets are now layered compositions, not flat tables

In 0.3.x a ruleset was a single flat table of `banned_*` / `reworked_*` arrays. **0.4.0
splits this into two object types — `MP.Layer` (reusable fragment) and `MP.Ruleset`
(named composition of layers + own fields).** This is the single largest structural change
since 0.3.3 and the spreadsheet does not describe it at all (the spreadsheet is a flat
0.3.3 ranked content list — treat as effect data, not structure).

**Ruleset base object** — `rulesets/_rulesets.lua:4-52`. A ruleset is an
`SMODS.GameObject:extend` with `class_prefix = "ruleset"`, injected into
`G.P_CENTER_POOLS.Ruleset` and `MP.Rulesets[key]`. Required params
(`_rulesets.lua:7-22`):

```
key, multiplayer_content,
banned_jokers, banned_consumables, banned_vouchers,
banned_enhancements, banned_tags, banned_blinds,
reworked_jokers, reworked_consumables, reworked_vouchers,
reworked_enhancements, reworked_tags, reworked_blinds
```

**Layers** — `layers/_layers.lua`. `MP.Layer(name, definition)` registers a fragment with
the same field vocabulary plus hooks. Real layers on disk: `standard.lua`, `classic.lua`,
`ranked.lua`, `smallworld.lua`, `glass_cannon.lua`, `glass_variants.lua`,
`speedlatro_timer.lua`, `pvp_timer.lua`, `pressure_timer.lua`, `no_anim_timer.lua`,
`experimental.lua`, `sandbox.lua`, and mutator stubs (`ban_mutators.lua`,
`economy_mutators.lua`, `esoteric_mutators.lua`, `shop_mutators.lua`,
`mutator_stubs.lua`).

**Composition.** A ruleset names its layers and lets `resolve_layers` merge them
*before* SMODS validates `required_params` (`_rulesets.lua:54-77`, `_layers.lua:143-190`):

- `rulesets/ranked.lua` (the real Standard Ranked): `layers = { "standard", "ranked" }`,
  `forced_gamemode = "gamemode_mp_attrition"`.
- `rulesets/legacy_ranked.lua`: `layers = { "classic", "ranked" }`.
- `rulesets/badlatro.lua`: a *flat* ruleset (no layers) — a big explicit
  `banned_jokers`/`banned_consumables`/… list. Both styles coexist.

**Merge semantics** (`_layers.lua:143-190`): array fields (the 14 in
`MP._LAYER_ARRAY_FIELDS`, `_layers.lua:122-138`) are **concatenated** across layers + the
ruleset's own entries; scalar fields are **last-layer-wins, but the ruleset's own value
always beats any layer** (`ruleset_owned[k]` guard, `_layers.lua:170`). Resolved layer
names are stashed as `_layers` (set) + `_layer_order` (ordered) for later hook dispatch.

**Modifiers** (`_layers.lua:192-246`) are layers chosen at *runtime* (host overlay or
practice mode), held in the ordered `MP.MODIFIERS` list, **not** materialized onto the
ruleset — they are queried at read sites alongside the ruleset's own layers and reset on
lobby-leave. `default_modifiers` on a ruleset pre-checks some in the overlay
(`apply_default_modifiers`, `_layers.lua:238-246`).

**Active-context resolution** (`_rulesets.lua:110-196`): `MP.current_ruleset()` returns a
metatable proxy `_resolver` whose `__index` merges (ruleset + active modifiers) per field —
arrays concatenated, scalars modifier-last-wins then ruleset. `active_layer_chain`
(`_rulesets.lua:168-196`) produces a **deduped ordered** list (ruleset's `_layer_order`,
then its self-name, then modifiers) — dedup matters because not all hooks are idempotent
(comment: smallworld's 75 % cull would re-cull survivors).

### 1.2 Bans and reworks — two distinct mechanisms

**Bans** (`MP.ApplyBans`, `_rulesets.lua:198-230`). Iterates the six banned tables off the
resolved ruleset proxy plus the active gamemode plus the deck's `BANNED_*`, writing
`G.GAME.banned_keys[v] = true`. Plus a `banned_silent` table (`badlatro` and `standard`
layer use it) for keys hidden from the player. Dynamic bans run as layer hooks:
`smallworld.lua:on_apply_bans` (`smallworld.lua:2-49`) seed-shuffles each pool and bans
`floor(0.5 + 0.75*n)` of every category via `pseudoshuffle(v, pseudoseed(k.."_mp_smallworld"))`
— a **content cull that must be deterministic and seed-driven** so both clients agree.

**Reworks** (`MP.ReworkCenter` / `SMODS.injectItems` graft / `MP.LoadReworks`,
`_rulesets.lua:232-377`). A rework does not replace a center; it **stamps layer-prefixed
shadow keys** onto the vanilla center: for layer `L`, every property `k` is written to
`center["mp_"..L.."_"..k]`, and the untouched original to `center["mp_vanilla_"..k]`
(falling back to the sentinel string `"NULL"`). `MP.LoadReworks(ruleset)` then, per the
`active_layer_chain`, resets to vanilla and re-applies layer shadows in order
(`_rulesets.lua:321-377`). This is how the *same* `j_to_do_list` is "vanilla" in one
ruleset and "reworked" in another **without a second center** — the rework is a deterministic
overlay keyed by active layer. Rework injection also auto-sets `config.mp_balanced = true`
(`_rulesets.lua:281-284`) which drives the **Balanced sticker** tooltip.

### 1.3 New-content gating: default-deny on `*_mp_*` keys

`_layers.lua:39-46` `MP.should_exclude_from_pool`: any center whose key matches
`^%a+_mp_` is **excluded from every pool by default** unless it has an `mp_include`
function that returns true. `_layers.lua:72-119` grafts `SMODS.Joker/Consumable/Tag:register`
to **auto-attach** an `mp_include` for any object listed in some layer's `reworked_*` array
(via the reverse indices `MP._JOKER_LAYERS` etc., `_layers.lua:6-30`), and warns
(`warn_if_ungated`, `_layers.lua:50-65`) if an `_mp_` object has neither `mp_include` nor a
reworked-list membership. So MP content is **opt-in per layer/ruleset**, fail-closed.

Per-object `mp_include` patterns seen in `objects/jokers/*` (e.g.
`defensive_joker.lua:23-25`, `penny_pincher.lua:25-28`):
`return MP.LOBBY.code and MP.LOBBY.config.multiplayer_jokers` — content only enters the pool
when the lobby toggled it on.

### 1.4 JokerDef authoring — what a joker definition actually is

A joker is `SMODS.Joker({...})` plus a sibling `SMODS.Atlas({...})`
(`objects/jokers/defensive_joker.lua:1-54`). Key authoring fields observed:

- Identity/shop: `key`, `atlas`, `rarity` (1=Common…4=Legendary), `cost`,
  `unlocked`, `discovered`.
- Compat flags: `blueprint_compat`, `eternal_compat`, `perishable_compat`
  (`penny_pincher.lua` sets `blueprint_compat = false`).
- State: `config = { extra = {...}, t_chips = 0 }` (per-instance ability state).
- Effect: `calculate(self, card, context)` returning `{ chip_mod=, mult_mod=, Xmult=,
  dollars=, message=, repetitions= }` keyed off `context` flags
  (`context.cardarea == G.jokers`, `context.joker_main`, etc.). This `context` vocabulary
  is the SMODS calculate-context API (`smods/src/`), the canonical effect grammar.
- Display: `loc_vars(self, info_queue, card)` returns `{ vars = {...} }` for the
  description templating; `add_nemesis_info` etc. push info-queue tooltips.
- Lifecycle: `update(self, card, dt)` for per-frame recompute (Defensive Joker recomputes
  `t_chips` from `MP.GAME.enemy.lives - MP.GAME.lives` each frame, `defensive_joker.lua:26-36`).
- Pool gating: `in_pool(self)` (Penny Pincher gates on `MP.GAME.pincher_unlock`),
  `mp_include(self)`.
- **Governance metadata**: `mp_credits = { idea=, art=, code= }` — every MP joker carries
  an attribution block (`defensive_joker.lua:49-53`, `penny_pincher.lua:35-39`).

Display defs are *separate*, in JokerDisplay
(`JokerDisplay/definitions/display_definitions.lua`) — the on-card live readout is a
different artifact from the joker effect.

### 1.5 Sprites

`SMODS.Atlas({ key, path = "j_<name>.png", px = 71, py = 95 })` — one PNG atlas per joker,
71×95 cell, referenced by the joker's `atlas = "<key>"`. Art ships *inside the mod*
(`assets/`). The 0.3.3 spreadsheet's `11_Images.txt` enumerates art assets. Stickers are
also atlases (`objects/stickers/balanced.lua:1-10`).

### 1.6 Load order (`core.lua:301-320`)

Strict, deterministic load order matters for the reverse-index gating to work:
`layers` → `rulesets` → `objects/editions,enhancements,seals,stickers,blinds,decks` →
`objects/jokers` (+ `sandbox`, `standard`, `experimental`) → `stakes` → `tags` →
`consumables` (+ `sandbox`) → `boosters` → `challenges`. Layers/rulesets load **first** so
that by the time jokers `:register()`, the `MP._*_LAYERS` reverse indices are populated and
auto-gating can fire.

### 1.7 Versioning & compatibility

`Multiplayer.json`: `version: "0.4.0"`, hard `dependencies`
(`Steamodded >=1.0.0~BETA-1221a`, `Lovely >=0.8`, `Balatro >=1.0.1o`) and `conflicts`
(`Cryptid <<0.5.4`, `Talisman <=2.0.2`, `JokerDisplay <<1.9.6`, …). The `ranked` layer
itself self-disables if SMODS/Lovely are too old (`layers/ranked.lua:3-5` →
`MP.UTILS.check_smods_version() / check_lovely_version()`). **There is no per-ruleset
semantic version** — ruleset identity *is* its content; balance changes ship as a whole
mod version bump documented in `CHANGELOG.md`.

### 1.8 Cheat-proofing model in BMP

BMP is **peer-to-peer-ish with a thin relay server** (`D:/BalatroMultiplayerAPI-Server-main/src/`:
`Lobby.ts`, `GameMode.ts`, `actionHandlers.ts`). The server relays scores/lives and enforces
lobby config, but each client runs its own authoritative Balatro instance. Determinism, not
server re-simulation, is the integrity mechanism: both clients must compute the same pools
and the same RNG. Hence the obsessive `pseudoseed` use in smallworld, the
"Mac/Windows To Do List queue parity" fix (`05_Jokers.txt:46-47`), and dedup in
`active_layer_chain`. **This is the gap our server closes: we re-simulate authoritatively.**

### 1.9 0.3.3 → 0.4.0 deltas (from `CHANGELOG.md`)

Standard Ranked content (the spreadsheet baseline is 0.3.3; these are the 0.4.0 overrides):

- **To Do List** — reworked: pays **$5** (was $4); target hand chosen from *all* hands, not
  just discovered. (Spreadsheet `05_Jokers.txt:46` still describes the 0.3.3 "discovered
  only" behavior — 0.4.0 wins.)
- **Golden Ticket** — payout nerf **reverted**, back to **$4** (0.3.0 had cut it to $3 and
  made it Uncommon, no longer requiring Gold cards — 0.4.0 reverts the payout).
- **Speedrun** — out of rotation.
- **Ouija / Ectoplasm** — now cost **$4** (bug fix).
- **Justice** — back in rotation (0.3.x had it banned; `standard` layer still bans
  `c_justice` at `standard.lua:13` while the ruleset re-enables — note this tension).
- **Gold Card** enhancement payout **$3 → $4**.
- **Comeback Gold** — awarded on *any* life loss again, not just PvP-boss losses ($4 / $2
  high-stake unchanged).
- **Balanced sticker** — now shows a tooltip of what was changed (driven by
  `config.mp_balanced`, §1.2).
- **Legacy Ranked / Hanging Chad** — reworked: retriggers first **2** cards instead of
  first card twice.
- New 0.4.0 systems: **Match Replays / ghost practice** (`replays/`), menu hotkeys, timer &
  score-preview rework (CHANGELOG marks "TODO"). The `gamemodes/` split
  (`_gamemodes`, `attrition`, `showdown`, `survival`) is the 0.4.0 gamemode-as-object model
  that `forced_gamemode` references.

---

## 2. How OUR engine does it today

Our server already has a **data-driven authoring pipeline** that is in many ways cleaner
than BMP's Lua-mod approach, because we are authoritative.

### 2.1 JokerDef builder (already exists)

- `joker/def/JokerDef.java` — the data record: `key, name, description, rarity, cost,
  atlasX, atlasY, spriteUrl, spriteUrl2x, blueprintCompatible, List<Mutation>, List<Rule>`.
  `info()` projects the client-visible `JokerInfo`.
- `joker/def/Rule.java` — `(Trigger when, Condition condition, EffectTemplate effect)`,
  first-match-wins.
- `joker/def/EffectTemplate.java` — `Op ∈ {CHIPS, MULT, XMULT, DOLLARS, REPETITIONS,
  HELD_MULT}` × `Value`, with identity-skip (returns null at +0 / x1.0 / 0 reps).
- `joker/def/Value.java` — sealed `Const | State | Count | RunVar`, all in the unified
  `base + scale*n` shape; `Count` reuses the per-card `Condition` vocabulary.
- `joker/def/Mutation.java`, `Condition.java` — state changes and predicates.
- `joker/def/DataJoker.java` — interprets a `JokerDef` as a live `Joker`; **server-side
  only**, so the client only ever sees `JokerInfo` — "a custom joker is exactly as
  cheat-proof as a hand-coded one" (class doc).
- `joker/def/BuilderSchema.java` — emits the dropdown vocabulary (triggers, condition
  types, effect ops, value types, mutation ops, enum value lists) **derived from the engine
  enums**, so the UI can never offer a building block the interpreter can't evaluate.

### 2.2 Persistence + validation

- `joker/def/CustomJokerStore.java` — one JSON file per joker under
  `web-assets/custom-jokers` (git-ignored). `loadAll()` registers each with
  `JokerLibrary.registerDef`. Validation (`validate`, lines 125-146): key regex
  `^j_[a-z0-9_]{3,48}$`, no shadowing built-ins, `MAX_RULES=24`, `MAX_MUTATIONS=16`, each
  rule needs trigger+condition+effect. Sprite upload (`saveSprite`, 85-105): scale ∈ {1,2},
  PNG magic-byte check, `MAX_SPRITE_BYTES = 2 MiB`, traversal-safe `resolveAsset` (118).
  Explicitly: "validation is about safety, not balance."
- `state/RulesetStore.java` — one JSON per custom ruleset under `web-assets/custom-rulesets`.
  Validation (93-115): name regex `^[A-Za-z0-9][A-Za-z0-9 _-]{1,31}$`, no curated-name
  collision, `hands>=1/discards>=0/handSize>=1`, `winAnte>=0`, `blindBaseAmounts` length
  ≥ 8, **every `jokerPool` key must be a known joker** (built-in or loaded custom def).

### 2.3 Ruleset as data

- `state/Ruleset.java` — record: `name, startingMoney, hands, discards, handSize,
  anteScaling, winAnte, int[] blindBaseAmounts, List<String> jokerPool`. Empty pool →
  `JokerLibrary.builtinKeys()`.
- `state/RulesetCatalog.java` — curated set: `Standard`, `Blitz`, `Marathon` (hard-coded).
- `JokerLibrary.java` — hand-coded starter set (11 jokers) + `registerDef` for data jokers.
  Critically: `BUILTIN_KEYS` is snapshotted at class-init (line 45) so later
  `registerDef` calls "never leak into the standard shop" — custom jokers only appear when a
  ruleset's `jokerPool` opts them in.

### 2.4 Cheat-proofing today

The whole engine is **server-authoritative re-simulation** (intent in, scoring computed
server-side, only display data out). Custom jokers run through `DataJoker` server-side, so
authoring content cannot change what the client can fake. This already structurally beats
BMP's determinism-trust model.

---

## 3. The GAP

| Concern | BMP 0.4.0 | Our engine today | Gap |
|---|---|---|---|
| **Ruleset structure** | Layered composition (`Layer` + `Ruleset`, merge rules, modifiers) | Flat single record (`Ruleset.java`) | No layers, no modifiers, no composition — can't express "standard + ranked" reuse |
| **Bans/reworks** | Six `banned_*` + six `reworked_*` arrays + `banned_silent` + dynamic ban hooks (smallworld cull) | Only `jokerPool` allowlist | No bans for consumables/vouchers/tags/blinds/enhancements; no rework overlay; no deterministic dynamic-ban hook |
| **Rework overlay** | Same center, layer-prefixed shadow props, deterministic re-apply | None — a changed joker = a new key | Can't have "vanilla joker X here, reworked X there" without forking the def |
| **Default-deny on new content** | `*_mp_*` excluded unless `mp_include`/reworked-listed | `jokerPool` allowlist already default-deny for jokers | OK for jokers; missing for other content types |
| **Forced gamemode / lobby options** | `forced_gamemode`, `forced_lobby_options`, `force_lobby_options()` | None | Ruleset can't pin a gamemode or lock lobby settings (needed for ranked) |
| **Governance metadata** | `mp_credits`, `mp_balanced`/Balanced sticker tooltip | None | No attribution, no "this card was changed" surfacing |
| **Curation tiers for ranked** | Curated rulesets (`ranked`, `majorleague`) vs experimental dir; conflicts/version gates | `RulesetCatalog` (curated) vs `RulesetStore` (custom) — two-tier exists | No explicit *ranked-eligible* flag; custom content can't be promoted into ranked |
| **Versioning** | Whole-mod `version` + `CHANGELOG`; ruleset identity = content | No version on `Ruleset`/`JokerDef`; no content hash | No reproducibility guarantee for "the ruleset we agreed to play" |
| **Validation** | SMODS `required_params`; safety implicit | Safety validation present (regex, sizes, pool membership) | Missing: bounded numeric ranges on effects (a custom joker can return `XMULT 1e9`); cross-ref validation for non-joker bans |
| **Determinism of dynamic content** | seed-driven culls (`pseudoseed`) | n/a | If we add a smallworld-style cull, it must use our `RandomStreams` seed, not `java.util.Random` |
| **Replays/ghost** | 0.4.0 `replays/` ghost practice | none | Out of scope here but content pipeline must record the exact ruleset+content version per match for replay fidelity |

---

## 4. Proposed target design — authoritative, queue-shaped, ruleset-driven

Goal: match BMP 0.4.0's *expressiveness* (layers, bans, reworks, forced gamemode, curation)
while keeping our *authoritative re-simulation* (the cheat-proofing win) and our *data-only*
authoring (no Lua, no shipped code from authors).

### 4.1 Adopt the Layer/Ruleset composition model, as data

Introduce `ContentLayer` and recast `Ruleset` as a composition:

```
record ContentLayer(
    String name,
    boolean multiplayerContent,
    BanSet bans,              // jokers, consumables, vouchers, enhancements, tags, blinds, silent
    List<Rework> reworks,     // see 4.3
    String forcedGamemode,    // nullable
    LobbyLock lobbyLock,      // forced lobby options (timer, the_order, preview_disabled)
    List<String> dynamicBanHooks  // named, deterministic — e.g. "smallworld_cull"
)

record Ruleset(
    String name,
    int formatVersion,            // NEW: bump on any balance change
    List<String> layers,          // ordered; resolved like _layers.lua
    List<String> defaultModifiers,// pre-checked modifiers
    // resolved/own fields (own beats layer, arrays concatenate):
    StartParams start,            // startingMoney/hands/discards/handSize
    Scaling scaling,              // anteScaling, winAnte, blindBaseAmounts
    BanSet bans,
    List<Rework> reworks,
    List<String> jokerPool,       // additive allowlist for custom content
    String forcedGamemode,
    LobbyLock lobbyLock,
    RankedTier rankedTier         // NEW: CURATED_RANKED | CURATED_CASUAL | EXPERIMENTAL | CUSTOM
)
```

Resolution mirrors `_layers.lua:143-190`: a `RulesetResolver` merges layers in order then
the ruleset's own fields — **arrays concatenate, scalars are last-layer-wins but ruleset-own
beats any layer**. Keep our `BUILTIN_KEYS` snapshot trick (`JokerLibrary:45`) so custom
content is opt-in. Modifiers are a runtime list (`List<String> activeModifiers` on the match
config) merged at read time exactly like BMP's `MP.MODIFIERS` — never baked into the
persisted ruleset.

### 4.2 Generalize bans to all six content categories + silent + dynamic

`BanSet` carries six explicit key sets (jokers/consumables/vouchers/enhancements/tags/blinds)
plus `silent` (banned but hidden). At match setup, `applyBans` writes them into the run's
`bannedKeys` set, consulted by **every pool draw** (shop, packs, tags, vouchers) — the
authoritative analog of `G.GAME.banned_keys`. `dynamicBanHooks` are *named, registered*
Java functions (default-deny: only hooks in a server registry are runnable), seeded from our
`RandomStreams` so a smallworld-style 75 % cull is reproducible and identical on re-sim. No
author-supplied code ever runs — a custom ruleset can only *name* a vetted hook.

### 4.3 Reworks as a deterministic data overlay (not a new key)

Match BMP's "same center, layer-prefixed shadows" model in data:

```
record Rework(
    String targetKey,                 // e.g. "j_to_do_list"
    Map<String,Object> overrides,     // cost, rarity, config.extra.*, blueprintCompatible…
    List<RuleDelta> ruleDeltas,       // add/replace/remove Rules on a DataJoker
    boolean balanced                  // -> Balanced sticker tooltip
)
```

A `ReworkResolver` takes the base joker (built-in `JokerInfo`+logic, or a `JokerDef`) and the
active layer chain (deduped, ordered — port `active_layer_chain`, `_rulesets.lua:168-196`)
and produces the *effective* joker for this match. Built-in hand-coded jokers expose a small
set of override-able numeric params (a `config`-like map) so reworks can retune them without
forking Java — this is the one piece we must add to hand-coded jokers to reach parity with
BMP's "retune To Do List to $5" capability. Reworks set `balanced=true` → the client shows a
"changed in this ruleset" tooltip (parity with the 0.4.0 Balanced sticker, CHANGELOG §QoL).

### 4.4 Forced gamemode + lobby locks

Add `forcedGamemode` and `LobbyLock` (record of pinned lobby fields: `timerBaseSeconds`,
`timerForgiveness`, `theOrder`, `previewDisabled`) to layer/ruleset, applied at lobby-config
time and **re-validated server-side** so a client can't unlock them. Mirrors
`majorleague.lua:21-27` (timer 180, the_order off, preview disabled) and
`layers/ranked.lua:6-9` (the_order on). The lobby/queue model (docs `queue-model.md`) reads
the resolved lock when seating a match.

### 4.5 Curation / governance for ranked

Two-axis governance:

1. **Tier flag** `RankedTier` on every ruleset. Only `CURATED_RANKED` rulesets are
   selectable in the ranked queue; `CUSTOM`/`EXPERIMENTAL` are casual-only. Mirrors BMP's
   `rulesets/experimental/` directory + `ranked` layer self-disable gate, but enforced
   server-side instead of by-convention.
2. **Content provenance.** Every `JokerDef` and `Ruleset` carries `author`, `credits`
   (idea/art/code, parity with `mp_credits`), `createdAt`, and a **content hash** (4.6). A
   custom joker can only enter a *ranked* pool by being **promoted**: an admin moves its def
   from `custom-jokers/` into a curated, version-pinned `ranked-content/` set, at which point
   its hash is frozen and it gains a `RankedTier`. Promotion is the governance gate; the
   builder remains open for casual play.

A `CurationService` exposes: list curated/ranked rulesets, propose-custom-for-review,
promote (admin), and a **diff view** (what a ruleset bans/reworks vs vanilla) so reviewers
see the balance surface — the data equivalent of reading `badlatro.lua`.

### 4.6 Versioning & reproducibility

- `formatVersion` (int) on `Ruleset`; bump on any balance change, log to a `CHANGELOG`-style
  `RulesetHistory` table.
- **Content hash**: SHA-256 over the *resolved* ruleset (layers merged, reworks applied,
  pool sorted, joker defs canonicalized). Stored on the match record and echoed to both
  clients at match start. Two players are guaranteed the same content surface iff hashes
  match — the authoritative, verifiable version of BMP's "both clients must agree."
- A match persists `{ rulesetName, formatVersion, contentHash, seed, activeModifiers }` —
  enough to fully reproduce/replay (feeds the future replay/ghost system, CHANGELOG 0.4.0).

### 4.7 Validation hardening (safety + bounded balance for ranked)

Keep current safety checks; add:

- **Numeric bounds** on `EffectTemplate`/`Value` resolved magnitudes per tier: e.g. ranked
  caps `XMULT` base/scale, `CHIPS`, `DOLLARS` to sane ranges so a promoted custom joker can't
  carry a 1e9 multiplier. Casual is looser.
- **Cross-reference validation** for *all* ban categories (not just jokers): every banned/
  reworked key must resolve to a known center, paralleling `RulesetStore`'s joker-pool check
  (`RulesetStore:109-114`). Unknown key → reject (BMP only warns; we reject pre-match).
- **Effect-budget / static analysis**: bound total rules×mutations (already
  `MAX_RULES`/`MAX_MUTATIONS`) and reject rules that can't fire (dead `Condition`) at save
  time, so the builder gives authoring feedback.
- **Determinism lint**: a `dynamicBanHook` must read only from `RandomStreams`+resolved
  state (enforced by the registry — authors can't supply hooks, only select vetted ones).

### 4.8 Sprites — authoritative metadata, client-side art

Keep our model: server stores `spriteUrl`/`spriteUrl2x` (uploaded, PNG-validated, 2 MiB cap,
traversal-safe) **as metadata only**; art is served to the client, never participates in
scoring. Fall back to Balatro atlas cell (`atlasX/atlasY`) when no custom art. This already
matches BMP's "atlas per joker, 71×95 cell" model (`defensive_joker.lua:1-6`) while shipping
no copyrighted art from the server. Add: dimension validation (must be a multiple of the
71×95 cell, 1x and 2x consistent) and store the sprite hash in the content hash so a sprite
swap is a content change.

### 4.9 Load/registration order

Port BMP's strict order (`core.lua:301-320`): register layers → resolve rulesets → load
content defs → snapshot `BUILTIN_KEYS` → register custom defs. This guarantees the
default-deny gating and reverse-index auto-attach work, and that custom content never
perturbs the curated shop's determinism.

---

## Open questions

1. **Built-in joker retuning.** Reaching parity with "To Do List now pays $5" requires
   hand-coded jokers to expose an override-able param map. Do we (a) give every hand-coded
   joker a `config`-style map (more BMP-like, more work), or (b) re-author the
   ranked-relevant ones as `JokerDef`s so reworks are pure data? Likely (b) for the
   ~30 ranked-pool jokers, (a) for the rest.
2. **Modifier model scope.** BMP modifiers are runtime layers (timers, glass-cannon, etc.).
   Do we expose modifiers to the *ranked* queue at all, or only casual/practice? Ranked
   probably pins modifiers via the curated ruleset and disallows player-chosen ones.
3. **Dynamic-ban hook registry.** Which culls do we vet and ship (smallworld 75 %,
   small-world voucher offset `smallworld.lua:33`)? Each needs a deterministic Java
   re-implementation seeded from `RandomStreams`. Is the offset/`legacy_smallworld` toggle
   (`smallworld.lua:153-155`) worth carrying?
4. **Content-hash canonicalization.** Exact byte form to hash (field order, float
   formatting, sprite inclusion) must be specified and frozen, or hashes won't match across
   server versions. Need a `CanonicalJson` spec.
5. **Promotion workflow / authority.** Who promotes custom content to ranked, and is there
   an audit log? Does promotion require a second curated version of the def, or an immutable
   freeze of the existing file?
6. **Balanced-sticker surfacing.** We have no client sticker system; how do we surface
   "this card was reworked in this ruleset" (CHANGELOG 0.4.0 QoL)? Probably a `reworked:true`
   flag on the per-match `JokerInfo` the client renders.
7. **Spreadsheet vs 0.4.0 drift.** The `standard` layer still bans `c_justice`
   (`standard.lua:13`) yet CHANGELOG says Justice is back in rotation — is the re-enable at
   the ruleset level or did the layer not get updated? Need to confirm against the live
   `standard_ranked` resolved pool before copying ban lists.

## New building blocks needed

- `ContentLayer` (record) + `RulesetResolver` — port `_layers.lua:143-190` merge semantics
  (arrays concat, scalars last-layer-wins, ruleset-own beats layer) and
  `active_layer_chain` dedup/order (`_rulesets.lua:168-196`).
- Recast `state/Ruleset.java` to a layered composition with `formatVersion`,
  `defaultModifiers`, `rankedTier`, `forcedGamemode`, `LobbyLock`.
- `BanSet` (six categories + `silent`) and authoritative `applyBans` writing a per-run
  `bannedKeys` set consulted by every pool draw (analog of `G.GAME.banned_keys`,
  `_rulesets.lua:198-230`).
- `Rework` + `RuleDelta` + `ReworkResolver` — deterministic overlay onto a base joker
  (data parity with the `mp_<layer>_*` shadow-prop system, `_rulesets.lua:232-377`),
  including `balanced` flag.
- Override-able `config` map on hand-coded jokers (or re-author ranked-pool jokers as
  `JokerDef`s) so reworks can retune them.
- `DynamicBanHookRegistry` — vetted, seed-driven culls (smallworld) selectable by name only;
  no author code execution.
- `Modifier` runtime list on match config + merge at read sites (parity with `MP.MODIFIERS`).
- `RankedTier` enum + `CurationService` (list/propose/promote/diff) and a
  version-pinned `ranked-content/` store distinct from open `custom-jokers/`.
- `ContentHasher` (canonical-JSON SHA-256 over resolved ruleset+defs+sprite hashes) and a
  per-match `{ruleset, formatVersion, contentHash, seed, modifiers}` record.
- Validation additions: per-tier numeric bounds on `EffectTemplate`/`Value`; cross-ref
  validation for all ban categories; dead-rule lint; sprite dimension validation.
- `RulesetHistory` / changelog store for `formatVersion` bumps (governance audit).
- `credits`/`author`/`createdAt` provenance fields on `JokerDef` and `Ruleset`
  (parity with `mp_credits`).
