# PoolDump — real-Balatro pool-order reference

The bit-exact PRNG + card-creation pipeline are verified against LuaJIT (`tools/balatro_prng_ref.lua` →
`BalatroPrngTest`). The one thing that can't be derived without the real game is the **pool registration
order** — which joker/consumable/voucher/tag sits at which index in `P_JOKER_RARITY_POOLS` /
`P_CENTER_POOLS`. `pseudorandom_element` indexes into those fixed-length arrays, so the order *is* the
data that makes shop contents bit-exact.

This mod dumps those pools straight from a running Balatro (the authoritative source — they're built by
`Game:init_item_prototypes`, which this hooks). It's far lighter than full BalatroBot: no action-driving,
no socket — just launch the game once and it writes a file.

## Run it (your machine — Balatro is a GUI app, can't be driven from CI)

1. Copy this folder into Balatro's Mods dir:
   `C:\Users\micha\AppData\Roaming\Balatro\Mods\PoolDump\`  (needs lovely + Steamodded, already installed)
2. Launch Balatro and reach the main menu (the hook fires on init).
3. It writes `D:\NewServer\build\balatro-prototypes.json` — ordered key-lists per pool.

**Which mods are loaded matters:**
- For **BMP-ranked** parity, run with the multiplayer mod active (its bans/MP content are what we match).
- For **vanilla** parity, disable mods (rename Mods, or a clean profile).
Record which set you used — the committed reference should note it.

## Next (after the file exists)
- Commit the ordered pools as the master pool data feeding `BalatroPool.cull`.
- v2: a seeded-shop dump (force a seed, log the generated shop) → a Java diff test that reproduces the
  shop from the seed via the oracle and asserts it matches real Balatro byte-for-byte.
