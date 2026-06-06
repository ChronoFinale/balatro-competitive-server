-- Feasibility spike: can Balatro's REAL scoring Lua run headless (no LÖVE)?
-- We stub the engine/UI/sound/event layer, load the real card.lua, and call the
-- real Card:calculate_joker without ever running the UI-heavy Card:init.

local BAL = "D:/SteamLibrary/steamapps/common/Balatro/Balatro/"

local function nop() return nil end

-- UI / animation / sound / localization stubs (pure no-ops)
localize = function() return "" end
Event = function(t) return t or {} end
card_eval_status_text = nop
juice_card = nop
check_for_unlock = nop
inc_career_stat = nop
play_sound = nop
delay = nop
ease_dollars = nop
mod_chips = function(x) return x end
mod_mult = function(x) return x end
pseudorandom = function() return 0 end
pseudoseed = function() return 0 end
pseudorandom_element = function(t) return next(t) end
find_joker = function() return {} end

-- The global game object, with just enough structure for the simple paths.
G = {
  GAME = {
    used_vouchers = {},
    probabilities = { normal = 1 },
    round_resets = { ante = 1 },
    current_round = {},
    pseudorandom = {},
    dollars = 0,
  },
  jokers = { cards = {} },
  consumeables = { cards = {} },
  hand = { cards = {} },
  play = { cards = {} },
  deck = { cards = {} },
  C = setmetatable({}, { __index = function() return { 1, 1, 1, 1 } end }),
  P_CENTERS = setmetatable({}, { __index = function() return { config = {} } end }),
  E_MANAGER = { add_event = nop },
  FUNCS = {},
}

-- Real engine class lib (tiny, standalone).
dofile(BAL .. "engine/object.lua")

-- Minimal Moveable so `Card = Moveable:extend()` works; init bypassed entirely.
Moveable = Object:extend()
function Moveable:init() end

-- Load the REAL card.lua (defines Card:calculate_joker + ~100 methods).
local ok, err = pcall(function() dofile(BAL .. "card.lua") end)
if not ok then
  print("LOAD card.lua FAILED:")
  print(err)
  os.exit(1)
end
print("card.lua loaded OK (real Card:calculate_joker is now available)")

-- Extra state stubs the simple jokers read.
G.GAME.hands = setmetatable({}, { __index = function() return { played = 1, mult = 2, chips = 10, level = 1 } end })
local poker_hands = setmetatable({}, { __index = function() return {} end })

-- Build a joker WITHOUT Card:init — just attach the Card metatable + ability.
local function makeJoker(ability)
  return setmetatable({ ability = ability, debuff = false, edition = nil,
                        seal = nil, config = { center = {} } }, Card)
end

-- Ability table as the real game builds it (config defaults from G.P_CENTERS).
local function ability(name, fields)
  local a = { set = "Joker", name = name, mult = 0, x_mult = 1, t_mult = 0,
              h_mult = 0, h_x_mult = 0, type = "", extra = {} }
  for k, v in pairs(fields or {}) do a[k] = v end
  return a
end

local ctx = { cardarea = G.jokers, joker_main = true, full_hand = {}, scoring_hand = {},
              poker_hands = poker_hands, scoring_name = "Pair" }

-- Run the REAL calculate_joker for several vanilla jokers.
local function run(name, fields, ctxOverrides)
  local j = makeJoker(ability(name, fields))
  local c = {}
  for k, v in pairs(ctx) do c[k] = v end
  for k, v in pairs(ctxOverrides or {}) do c[k] = v end
  local ok, ret = pcall(function() return j:calculate_joker(c) end)
  if not ok then return "ERROR " .. tostring(ret) end
  if not ret then return "nil" end
  local parts = {}
  for _, k in ipairs({ "mult_mod", "chip_mod", "Xmult_mod", "dollars" }) do
    if ret[k] then parts[#parts + 1] = k .. "=" .. tostring(ret[k]) end
  end
  return #parts > 0 and table.concat(parts, " ") or "(effect, no scoring field)"
end

print("[Joker      ] " .. run("Joker", { mult = 4 }))
print("[Half Joker ] " .. run("Half Joker", { mult = 20, extra = { size = 3 } }))
print("[Joker Stencil] " .. run("Joker Stencil", { x_mult = 4 }))
