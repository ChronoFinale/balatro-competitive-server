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

-- Enable the in-mod test seam (exposes the uid-keyed hand model via _G.__bbridge_test) BEFORE load.
_G.BBRIDGE_TEST = true

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

-- ---- uid-keyed hand model (drive-it) --------------------------------------
-- Exercise render_hand/hand_restamp/hand_remove against stub cardareas, so the drive-it logic is gated
-- HERE (restamp-on-diff, dissolve a server-removed card = Immolate, escaped-card assignment), not only in-game.
local T = _G.__bbridge_test
ok(T ~= nil, "hand-model test seam exposed")
if T then
	T.set_engaged(true)
	G.P_CARDS = { S_A = {center="S_A"}, H_K = {center="H_K"}, C_5 = {center="C_5"}, D_9 = {center="D_9"} }
	local function mkcard()
		return {
			set_base = function(self, c) self.based = c end,
			set_edition = function() end,
			-- the stub dissolve removes the card from its area (simulating the native animation completing)
			start_dissolve = function(self) self.dissolved = (self.dissolved or 0) + 1
				if G.hand and G.hand.remove_card then G.hand:remove_card(self) end end,
			remove = function(self) self.removed = true end,
		}
	end
	local function mkhand(cards)
		return { cards = cards, remove_card = function(self, card)
			for i = #self.cards, 1, -1 do if self.cards[i] == card then table.remove(self.cards, i) end end
		end }
	end

	-- A: restamp ONLY on diff (uid kept, identity changed ACE->KING)
	local c1 = mkcard(); c1.bbridge_uid="u1"; c1.bbridge_rank="ACE"; c1.bbridge_suit="SPADES"
	G.hand = mkhand({ c1 })
	T.render_hand({ {uid="u1", rank="KING", suit="HEARTS", edition="NONE"} })
	ok(c1.based and c1.based.center == "H_K", "render_hand restamps a mutated card (ACE->KING)")
	ok(c1.bbridge_rank == "KING", "restamp updates the tracked identity")

	-- A2: NO restamp when unchanged (no needless flicker)
	local c2 = mkcard(); c2.bbridge_uid="u1"; c2.bbridge_rank="ACE"; c2.bbridge_suit="SPADES"; c2.bbridge_edition="NONE"
	G.hand = mkhand({ c2 })
	T.render_hand({ {uid="u1", rank="ACE", suit="SPADES", edition="NONE"} })
	ok(c2.based == nil, "render_hand does NOT re-stamp an unchanged card")

	-- B: remove server-destroyed cards (Immolate destroys u2,u3; u1 survives)
	local a = mkcard(); a.bbridge_uid="u1"; a.bbridge_rank="ACE"; a.bbridge_suit="SPADES"; a.bbridge_edition="NONE"
	local b = mkcard(); b.bbridge_uid="u2"
	local d = mkcard(); d.bbridge_uid="u3"
	G.hand = mkhand({ a, b, d })
	T.render_hand({ {uid="u1", rank="ACE", suit="SPADES", edition="NONE"} })
	ok(#G.hand.cards == 1, "render_hand removes server-destroyed cards (Immolate)")
	ok(b.dissolved == 1 and d.dissolved == 1, "each destroyed card dissolved once")

	-- C: assign an unmapped native card the unclaimed server card (escaped-card fix)
	local e = mkcard() -- no uid
	G.hand = mkhand({ e })
	T.render_hand({ {uid="u9", rank="FIVE", suit="CLUBS", edition="NONE"} })
	ok(e.bbridge_uid == "u9", "render_hand assigns an unmapped native card")
	ok(e.based and e.based.center == "C_5", "assigned card stamped to the server identity")

	-- D: hand_remove idempotent (double-call dissolves once)
	local f = mkcard(); f.bbridge_uid="u1"
	G.hand = mkhand({ f })
	T.hand_remove(f); T.hand_remove(f)
	ok(f.dissolved == 1, "hand_remove dissolves exactly once (idempotent)")
end

print(string.format("\n%d passed, %d failed", pass, fail))
os.exit(fail == 0 and 0 or 1)
