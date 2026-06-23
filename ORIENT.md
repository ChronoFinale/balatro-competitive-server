# ORIENT — the 5-minute map

Read this when the codebase feels like too much. It is, structurally, small: a **server brain** + a few
**data pipelines** on top. Everything else is plumbing or scaffolding.

## The 30-second model

```
Author content/rules in a typed Java DSL
        │  compile (./gradlew generateContent)
        ▼
   JSON artifacts  (resources/content, resources/rulesets)
        │
   ┌────┼───────────────┐
   ▼    ▼               ▼
 engine   server serves    client (Electron PoC)
 boots    /content, /bootstrap   renders + auto-updates
 from it  (for thin clients)     from the JSON
```

The server is **authoritative** — it computes every outcome; clients only send intents and render what
`ClientView` sends back. That invariant is the whole point (anti-cheat). The eventual real client is a **Lua
mod in real Balatro** (thin shell + server config); the Electron app is a fast proof-of-concept.

## Where things live (`src/main/java/com/balatro/engine/`)

**The product — the server brain (~9k lines, this is the game):**
| package | what it is |
|---|---|
| `game/` | `Run` (the game loop), `Shop`, `Match` (PvP), and the content catalogs (`DeckCatalog`, `BossCatalog`, …) |
| `scoring/` | `ScoringEngine` — turns a played hand into a score |
| `rng/` | the deterministic PRNG (incl. bit-exact vanilla in `rng/vanilla/`) |
| `joker/`, `consumable/`, `card/`, `hand/` | the domain model + the data-driven joker definitions (`joker/def/`) |
| `intent/` | the actions a client can send (`PlayHand`, `Discard`, `BuyShopItem`, …) |
| `net/` | `GameServer` (routes intents, serves HTTP), `ClientView` (the info-hiding boundary) |
| `state/` | `Ruleset`, `RulesetBundle` (content + capabilities + mode), overlays, the stores |
| `auth/` | accounts + OAuth login |

**The data pipeline (how content becomes data clients can consume):**
| package | what it is |
|---|---|
| `joker/def/` | jokers as pure data (`JokerDef`) + `RulesetOverlay` (a ruleset = a diff) + `JokerOverlays` |
| `content/ContentStore` | loads the engine's content from the compiled JSON at startup |
| `i18n/Loc` | one localization layer; `${field}` templates fill from data (numbers single-sourced) |
| `codegen/` | `ClientCodegen` (generates the client's TS types) + `ContentManifest` (the delta-sync manifest) |
| `ui/` | server-driven-UI vocabulary (`UiScreen`/`UiComponent`) — the PoC for data-defined menus |

## The data pipeline in one breath

1. **Author** in the DSL (`BuiltinJokerDefs`, the catalogs, `Bundles`, `Screens`).
2. **`./gradlew generateContent`** compiles it all to JSON under `resources/` + the client's `content.ts`/
   `content-types.ts`. `RulesetArtifactsTest` *gates* these so they can never drift from the source.
3. The **engine** boots from the JSON (`ContentStore`); the **server** serves it (`/content`, `/bootstrap`);
   the **client** bundles it as an offline fallback and **delta-syncs** updates from the server.

## The client (`client/` — Electron PoC)

`renderer/src/`: `App` (shell), `session` (wire/store), `Game` (renders `ClientView` — the board),
`Almanac` (browses generated content), `ScreenView` (generic renderer for the data-defined menus),
`content`/`contentSync` (offline fallback + delta-sync). **`generated/content-types.ts` is committed (the
type contract); `generated/content.ts` is the ~10k-line data, gitignored and produced by `generateContent`.**

## The thin-client / Lua target (`tools/balatro-bridge/`)

The real goal: real Balatro as a thin renderer (`REBUILD-DESIGN.md`). The mod is a **stable shell** that, at
launch, calls **`GET /bootstrap`** (`modVersion`, `contentVersion`, offered `rulesets`, flags) and **delta-syncs
content** — so content/rulesets/config change server-side with no mod redistribution (only a mod-*code* change
needs a Lovely reboot). The board hooks `G.FUNCS` and renders natively with server data.

## Run / regenerate

```bash
./gradlew build              # compile + full test suite
./gradlew run                # start the server (HTTP 28788, TCP/WS 28789)
./gradlew generateContent    # regenerate all compiled artifacts + the client data
cd client && npm run dev     # the Electron client (auto-runs generate if content.ts is missing)
```

## Honest: product vs scaffolding

- **Product:** the server engine + the data pipeline + auto-update + bootstrap. Reused by any client.
- **Scaffolding (kept as PoC):** the Electron client, the Almanac, the SDUI screens. They proved the data
  spine fast; the Lua shell is the eventual real client. Don't mistake the PoC for the product.
