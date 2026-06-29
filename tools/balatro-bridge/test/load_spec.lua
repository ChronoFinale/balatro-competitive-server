-- Stubbed-environment load harness: proves balatrobridge.lua ASSEMBLES and install_hooks runs in a
-- faithful-enough fake of the SMODS/Balatro runtime (no real game). This catches load-path, reference and
-- wiring bugs that a plain syntax check (loadfile) misses -- e.g. a bad SMODS.load_file path, a nil method
-- call at install time, or a hook that fails to wrap. It is the structural gate for the SMODS-native rebuild;
-- behavior still needs a real in-game session, but "does it even load + install" is verified here.
-- Run:  cd tools/balatro-bridge && luajit test/load_spec.lua

package.path = "lib/?.lua;test/?.lua;" .. package.path

local pass, fail = 0, 0
local function ok(c, m) if c then pass = pass + 1 else fail = fail + 1; print("FAIL: " .. tostring(m)) end end

-- ---- stub the runtime the mod loads against -------------------------------
local _require, _open = require, io.open
require = function(m)
	if m == "socket" then
		-- a socket whose connect always fails -> open_run/http_login degrade to "server offline" (no real I/O)
		return { tcp = function() return {
			settimeout = function() end, connect = function() return false end,
			send = function() end, receive = function() return nil end, close = function() end,
		} end }
	end
	if m == "json" then return _require("json") end -- the vendored rxi json (test/json.lua)
	return _require(m)
end
io.open = function() return nil end -- swallow the mod's log-file writes (it guards on a nil handle)

-- SMODS.load_file resolves a mod-relative path to a chunk, exactly like in-game (so "lib/wire.lua" is exercised).
SMODS = { current_mod = {}, load_file = function(path) return assert(loadfile(path)) end }

-- The Balatro globals install_hooks touches. Pre-populate G.FUNCS + the methods with stub originals so the
-- save-ref-and-wrap path (the realistic in-game case) is exercised, and we can prove each got wrapped.
local SEAMS = { "select_blind", "draw_from_deck_to_hand", "discard_cards_from_highlighted",
	"play_cards_from_highlighted", "evaluate_play", "buy_from_shop", "reroll_shop", "toggle_shop",
	"sell_card", "skip_blind", "use_card", "skip_booster", "cash_out" }
G = { FUNCS = {}, C = {} }
for _, k in ipairs(SEAMS) do G.FUNCS[k] = function() end end
local orig = {}
for _, k in ipairs(SEAMS) do orig[k] = G.FUNCS[k] end

Game = { update = function() end, update_hand_played = function() end, update_shop = function() end }
Card = { redeem = function() end, open = function() end }
Tag = { apply_to_run = function() end }
EventManager = { add_event = function() end }
Event = function() return {} end

-- love.thread / love.timer: the mod starts its networking thread + channels at load. Stub them so that path
-- (getChannel x2, SMODS.load_file networking/socket.lua, newThread:start, net.new) is exercised here.
local thread_started = false
local function fake_channel() return { push = function() end, pop = function() return nil end, demand = function() return nil end } end
love = {
	thread = {
		getChannel = function(_) return fake_channel() end,
		newThread = function(_) thread_started = true; return { start = function() end } end,
	},
	timer = { getTime = function() return 0 end },
}

-- ---- load the mod ---------------------------------------------------------
local chunk, lerr = loadfile("balatrobridge.lua")
ok(chunk ~= nil, "balatrobridge.lua compiles" .. (lerr and (" (" .. lerr .. ")") or ""))
local loaded, rerr = pcall(chunk)
ok(loaded, "balatrobridge.lua runs to completion (require json + load lib/wire.lua + define everything)" ..
	(rerr and (" -- " .. tostring(rerr)) or ""))

require, io.open = _require, _open -- restore

-- ---- drive the frame-200 boot (prove_translation + install_hooks) ---------
ok(type(Game.update) == "function", "Game.update was wrapped at load")
local boot_ok, berr = true, nil
for _ = 1, 205 do
	local s, e = pcall(Game.update, Game, 0.016)
	if not s then boot_ok = false; berr = e; break end
end
ok(boot_ok, "200-frame boot runs install_hooks without error" .. (berr and (" -- " .. tostring(berr)) or ""))
ok(thread_started, "net thread was started at load (threaded networking wired)")

-- ---- assert every seam got wrapped ----------------------------------------
for _, k in ipairs(SEAMS) do
	ok(type(G.FUNCS[k]) == "function" and G.FUNCS[k] ~= orig[k], "G.FUNCS." .. k .. " wrapped")
end
ok(type(Game.update_hand_played) == "function", "Game.update_hand_played wrapped")
ok(type(Game.update_shop) == "function", "Game.update_shop wrapped")
ok(type(Card.redeem) == "function", "Card.redeem wrapped")
ok(type(Card.open) == "function", "Card.open wrapped")
ok(type(Tag.apply_to_run) == "function", "Tag.apply_to_run wrapped")
ok(type(EventManager.add_event) == "function", "EventManager.add_event wrapped")

print(string.format("\n%d passed, %d failed", pass, fail))
os.exit(fail == 0 and 0 or 1)
