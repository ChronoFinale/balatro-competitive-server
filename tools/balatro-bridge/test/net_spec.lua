-- Unit spec for BB.net (lib/net.lua) — the main-thread threaded-networking core. Fakes the love channels +
-- clock and uses the real rxi json, so the bug-prone seq-correlation / stash / drain logic is verified with
-- no love.thread or socket. (The thread SOURCE in networking/socket.lua is compile-checked here too; its
-- runtime behavior still needs a real in-game session.)
-- Run:  cd tools/balatro-bridge && luajit test/net_spec.lua

package.path = "lib/?.lua;test/?.lua;" .. package.path
local json = require("json")
local net = require("net")

local pass, fail = 0, 0
local function ok(c, m) if c then pass = pass + 1 else fail = fail + 1; print("FAIL: " .. tostring(m)) end end
local function eq(a, b, m) ok(a == b, (m or "") .. " (got " .. tostring(a) .. ", want " .. tostring(b) .. ")") end

-- ---- fakes -----------------------------------------------------------------
-- inbound channel: demand()/pop() return queued lines in order, then nil forever.
local function in_chan(lines)
	local i = 0
	local function take() i = i + 1; return lines[i] end
	return { demand = function(_, _) return take() end, pop = function(_) return take() end }
end
-- outbound channel: captures pushed lines.
local function out_chan() return { pushed = {}, push = function(self, v) self.pushed[#self.pushed + 1] = v end } end
-- clock: advances 0.02s per call (deadline 0.5 -> ~25 await iterations before timeout).
local function clock() local t = 0; return function() local v = t; t = t + 0.02; return v end end

local function line(tbl) return json.encode(tbl) end

-- ---- request: pushes the encoded intent, returns the seq-matched reply, stashes the rest --------------
do
	local out = out_chan()
	-- seq starts at 2 -> request's nextSeq is 3. Queue: a status, an OTHER-seq reply, then OUR seq-3 reply.
	local inq = in_chan({
		line({ action = "status", state = "authed" }),
		line({ type = "update", seq = 99, accepted = true, view = { phase = "SHOP" } }),
		line({ type = "update", seq = 3, accepted = true, view = { phase = "BLIND_ACTIVE", roundScore = 120 } }),
	})
	local n = net.new(out, inq, json, clock())
	local reply = n:request("playHand", { cards = { 0, 1, 2 } })
	ok(reply ~= nil, "request got a reply")
	eq(reply and reply.seq, 3, "reply is the seq-matched one (3, not 99)")
	eq(reply and reply.view and reply.view.phase, "BLIND_ACTIVE", "reply carries the view")
	-- the outgoing intent was encoded with type+seq+args
	local sent = json.decode(out.pushed[1])
	eq(sent.type, "playHand", "sent type"); eq(sent.seq, 3, "sent seq"); eq(#sent.cards, 3, "sent cards")
	-- the status was applied (connected), the other-seq reply was stashed for drain
	eq(n.connected, true, "status authed -> connected")
	eq(#n.stash, 1, "non-matching reply stashed"); eq(n.stash[1].seq, 99, "stashed the seq-99 reply")
end

-- ---- timeout: no matching reply -> nil (the caller's degrade-to-vanilla path) ------------------------
do
	local n = net.new(out_chan(), in_chan({}), json, clock())
	eq(n:request("reroll"), nil, "no reply within timeout -> nil")
end

-- ---- send: fire-and-forget pushes the encoded line, awaits nothing -----------------------------------
do
	local out = out_chan()
	local n = net.new(out, in_chan({}), json, clock())
	n:send("proceed")
	local sent = json.decode(out.pushed[1])
	eq(sent.type, "proceed", "send pushed the intent"); eq(sent.seq, 3, "send used the next seq")
end

-- ---- drain: returns app pushes (from stash + channel), applies status side effects --------------------
do
	local inq = in_chan({
		line({ type = "opponentUpdate", playerId = "x" }),  -- app push
		line({ action = "status", state = "disconnected" }), -- control
	})
	local n = net.new(out_chan(), inq, json, clock())
	n.connected = true
	n.stash = { json.decode(line({ action = "status", state = "authed" })), json.decode(line({ type = "matchResult", winner = "y" })) }
	local msgs = n:drain()
	eq(#msgs, 2, "drain returns the 2 app pushes (matchResult from stash + opponentUpdate from channel)")
	eq(n.connected, false, "the disconnected status was applied")
	eq(#n.stash, 0, "stash cleared")
end

-- ---- the thread source compiles -----------------------------------------------------------------------
do
	local src = dofile("networking/socket.lua")
	ok(type(src) == "string" and #src > 0, "socket.lua returns a non-empty thread source string")
	local chunk, err = loadstring(src)
	ok(chunk ~= nil, "thread source compiles" .. (err and (" (" .. err .. ")") or ""))
end

print(string.format("\n%d passed, %d failed", pass, fail))
os.exit(fail == 0 and 0 or 1)
