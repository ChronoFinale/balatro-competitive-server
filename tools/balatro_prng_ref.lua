-- Balatro PRNG reference-vector generator (Stage 3, Layer A — bit-exact milestone).
--
-- Copies Balatro's pseudohash / pseudoseed / pseudorandom VERBATIM from the decompiled source
-- (functions/misc_functions.lua + game.lua:2164-2168) and dumps golden vectors for the Java port to
-- diff against. Run with the SAME runtime family Balatro uses (LuaJIT 2.1 — Balatro does not override
-- the global math.random, so LuaJIT's TW223 math.random is the oracle):
--
--   luajit tools/balatro_prng_ref.lua > build/prng-vectors.json
--
-- Output is JSON; every double is printed with %.17g so it round-trips to the exact IEEE-754 value
-- when parsed by Java's Double.parseDouble.

-- ---- verbatim from functions/misc_functions.lua:279 ----
local function pseudohash(str)
  local num = 1
  for i = #str, 1, -1 do
    num = ((1.1239285023 / num) * string.byte(str, i) * math.pi + math.pi * i) % 1
  end
  return num
end

-- the %.13f round-trip used inside pseudoseed (misc_functions.lua:311)
local function round13(x)
  return tonumber(string.format("%.13f", x))
end

-- Standalone stateful pseudoseed, mirroring game.lua:2164-2168 + misc_functions.lua:298-313.
-- hashed_seed = pseudohash(seed); per-key state initialised to pseudohash(key..seed).
local function make_run(seedstr)
  local state = {}
  local hashed_seed = pseudohash(seedstr)
  local function pseudoseed(key)
    if not state[key] then state[key] = pseudohash(key .. seedstr) end
    state[key] = math.abs(round13((2.134453429141 + state[key] * 1.72431234) % 1))
    return (state[key] + hashed_seed) / 2
  end
  return pseudoseed, hashed_seed
end

-- pseudorandom(seed) = math.randomseed(pseudoseed); math.random()  (misc_functions.lua:315-320)
local function pseudorandom(seedval)
  math.randomseed(seedval)
  return math.random()
end

-- ---- JSON helpers (minimal; values are numbers/strings only) ----
local out = {}
local function g(x) return string.format("%.17g", x) end -- exact double round-trip
local function esc(s) return (s:gsub('\\', '\\\\'):gsub('"', '\\"')) end

-- 1. pseudohash of representative strings (and the byte values, so Java can sanity-check encoding).
local hash_strs = { "", "A", "shuffle", "Joker1", "Joker3sho", "front", "lucky_mult",
                    "Voucher0", "soul_Tarot", "*TESTSEED", "TESTSEED", "ABCD1234", "deal:1:SMALL" }
local hash_lines = {}
for _, s in ipairs(hash_strs) do
  hash_lines[#hash_lines + 1] = string.format('    {"s":"%s","h":"%s"}', esc(s), g(pseudohash(s)))
end

-- 2. raw LuaJIT math.random after randomseed(x) — isolates the TW223 port from everything else.
--    For each seed double we dump the first 5 consecutive math.random() draws (no reseed between).
local raw_seeds = { 0.0, 0.5, 0.123456789, 0.999999, 0.0000001, 0.7320508075688772 }
local raw_lines = {}
for _, sd in ipairs(raw_seeds) do
  math.randomseed(sd)
  local draws = {}
  for _ = 1, 5 do draws[#draws + 1] = g(math.random()) end
  raw_lines[#raw_lines + 1] = string.format('    {"seed":"%s","draws":["%s"]}',
    g(sd), table.concat(draws, '","'))
end

-- 3. full stack on a fixed seed: hashed_seed, the per-key pseudoseed advance (first 5 calls),
--    and pseudorandom(key) (first 5 calls) for several keys.
-- TESTSEED/ABCD1234 are valid 8-char Balatro seeds; 7MABKPQZ/C3D9FH2J are extra proper seeds (every char
-- in {1-9,A-N,P-Z}); *TESTSEED is the deliberately ranked-marked (invalid-vanilla) seed.
local runs = { "TESTSEED", "*TESTSEED", "ABCD1234", "7MABKPQZ", "C3D9FH2J" }
local keys = { "shuffle", "Joker1", "Joker3sho", "Voucher0", "lucky_mult", "soul_Tarot" }
local run_lines = {}
for _, seedstr in ipairs(runs) do
  local pseudoseed, hashed = make_run(seedstr)
  local key_lines = {}
  for _, k in ipairs(keys) do
    local seeds, rands = {}, {}
    for _ = 1, 5 do
      local ps = pseudoseed(k)
      seeds[#seeds + 1] = g(ps)
      rands[#rands + 1] = g(pseudorandom(ps))
    end
    key_lines[#key_lines + 1] = string.format(
      '      {"key":"%s","pseudoseed":["%s"],"pseudorandom":["%s"]}',
      esc(k), table.concat(seeds, '","'), table.concat(rands, '","'))
  end
  run_lines[#run_lines + 1] = string.format(
    '    {"seed":"%s","hashed_seed":"%s","keys":[\n%s\n    ]}',
    esc(seedstr), g(hashed), table.concat(key_lines, ',\n'))
end

-- 4. pool selection under The Order: create_card draws pseudorandom_element(pool, pseudoseed(pool_key)),
--    which for an array pool is pool[math.random(#pool)]; UNAVAILABLE entries are skipped by re-drawing,
--    and under The Order the resample advances the SAME pool_key stream (not a new _resample seed).
--    The pool is a fixed-length array with holes, exactly as get_current_pool returns it.
local sel_pool = { "j_a", "j_b", "UNAVAILABLE", "j_d", "j_e", "UNAVAILABLE", "j_g", "j_h", "j_i", "UNAVAILABLE" }
local sel_lines = {}
for _, seedstr in ipairs({ "TESTSEED", "*TESTSEED" }) do
  for _, pk in ipairs({ "Joker3sho", "Voucher0" }) do
    local pseudoseed = make_run(seedstr)
    local picks = {}
    for _ = 1, 8 do
      local key, it = "UNAVAILABLE", 0
      repeat
        math.randomseed(pseudoseed(pk))           -- advance pool_key stream
        key = sel_pool[math.random(#sel_pool)]    -- index into the fixed-length pool
        it = it + 1
      until key ~= "UNAVAILABLE" or it > 1000
      picks[#picks + 1] = key
    end
    sel_lines[#sel_lines + 1] = string.format(
      '    {"seed":"%s","pool_key":"%s","picks":["%s"]}', esc(seedstr), esc(pk), table.concat(picks, '","'))
  end
end

-- 5. rate polls: poll_edition (misc_functions.lua:2055, edition_rate=1 game.lua:1900, mod=1, no_neg=false)
--    and the joker rarity tier (get_current_pool:1969-1970). Both = a pseudorandom(pseudoseed(key))
--    value thresholded. We dump the raw poll value too so the Java threshold logic can be checked exactly.
local function poll_edition_one(pseudoseed, key)
  local p = pseudorandom(pseudoseed(key))
  local e
  if p > 1 - 0.003 then e = "negative"
  elseif p > 1 - 0.006 then e = "polychrome"
  elseif p > 1 - 0.02 then e = "holo"
  elseif p > 1 - 0.04 then e = "foil"
  else e = "none" end
  return e
end
local function rarity_tier(pseudoseed, append)
  local r = pseudorandom(pseudoseed("rarity" .. append))
  return (r > 0.95 and 3) or (r > 0.7 and 2) or 1
end
local rate_lines = {}
for _, seedstr in ipairs({ "TESTSEED", "*TESTSEED" }) do
  local pse = make_run(seedstr)
  local eds = {}
  for _ = 1, 12 do eds[#eds + 1] = poll_edition_one(pse, "edi_test") end
  local pse2 = make_run(seedstr)
  local rars = {}
  for _ = 1, 12 do rars[#rars + 1] = tostring(rarity_tier(pse2, "sho")) end
  rate_lines[#rate_lines + 1] = string.format(
    '    {"seed":"%s","editions":["%s"],"rarities":[%s]}',
    esc(seedstr), table.concat(eds, '","'), table.concat(rars, ","))
end

-- 6. slot type ('cdt', UI_definitions.lua:765-777): pseudorandom(pseudoseed('cdt'..append))*total_rate,
--    cumulative over default rates joker20/tarot4/planet4/base0/spectral0 (game.lua:1901-1905).
local function slot_type(pseudoseed, append)
  local types = { { "Joker", 20 }, { "Tarot", 4 }, { "Planet", 4 }, { "Base", 0 }, { "Spectral", 0 } }
  local total = 28
  local polled = pseudorandom(pseudoseed("cdt" .. append)) * total
  local check = 0
  for _, v in ipairs(types) do
    if polled > check and polled <= check + v[2] then return v[1] end
    check = check + v[2]
  end
  return "Joker"
end
-- 7. standard-card seal (card.lua:1763-1771): seal_rate=10 -> 20% seal, then equal Red/Blue/Gold/Purple.
local function std_seal(pseudoseed, append)
  local seal_poll = pseudorandom(pseudoseed("stdseal" .. append))
  if seal_poll > 1 - 0.02 * 10 then
    local st = pseudorandom(pseudoseed("stdsealtype" .. append))
    if st > 0.75 then return "Red"
    elseif st > 0.5 then return "Blue"
    elseif st > 0.25 then return "Gold"
    else return "Purple" end
  end
  return "none"
end
-- 8. standard-card edition (card.lua:1760-1761): poll_edition('standard_edition'..append, mod=2, no_neg=true).
local function std_edition(pseudoseed, append)
  local p = pseudorandom(pseudoseed("standard_edition" .. append))
  if p > 1 - 0.006 * 2 then return "polychrome"
  elseif p > 1 - 0.02 * 2 then return "holo"
  elseif p > 1 - 0.04 * 2 then return "foil"
  else return "none" end
end
local card_lines = {}
for _, seedstr in ipairs({ "TESTSEED", "*TESTSEED" }) do
  local a, b, c = make_run(seedstr), make_run(seedstr), make_run(seedstr)
  local slots, seals, sed = {}, {}, {}
  for _ = 1, 12 do slots[#slots + 1] = slot_type(a, "0") end
  for _ = 1, 24 do seals[#seals + 1] = std_seal(b, "0") end
  for _ = 1, 12 do sed[#sed + 1] = std_edition(c, "0") end
  card_lines[#card_lines + 1] = string.format(
    '    {"seed":"%s","slot_types":["%s"],"seals":["%s"],"std_editions":["%s"]}',
    esc(seedstr), table.concat(slots, '","'), table.concat(seals, '","'), table.concat(sed, '","'))
end

-- Write the file directly (raw ASCII bytes) rather than via stdout, so a UTF-16-redirecting shell
-- can't corrupt the encoding. Path is arg[1] or the default build location.
local fh = assert(io.open(arg[1] or "build/prng-vectors.json", "w"))
fh:write("{\n")
fh:write('  "pseudohash": [\n', table.concat(hash_lines, ",\n"), "\n  ],\n")
fh:write('  "raw_random": [\n', table.concat(raw_lines, ",\n"), "\n  ],\n")
fh:write('  "runs": [\n', table.concat(run_lines, ",\n"), "\n  ],\n")
fh:write('  "pool_select": {\n')
fh:write('    "pool": ["', table.concat(sel_pool, '","'), '"],\n')
fh:write('    "cases": [\n', table.concat(sel_lines, ",\n"), "\n    ]\n")
fh:write("  },\n")
fh:write('  "rate_polls": [\n', table.concat(rate_lines, ",\n"), "\n  ],\n")
fh:write('  "card_polls": [\n', table.concat(card_lines, ",\n"), "\n  ]\n")
fh:write("}\n")
fh:close()
io.write("wrote " .. (arg[1] or "build/prng-vectors.json") .. "\n")
