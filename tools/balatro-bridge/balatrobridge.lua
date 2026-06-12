-- BalatroBridge (Stage 1 thin client): Balatro is the renderer; the SERVER owns the cards & scoring.
-- Instead of swapping cards on top of Balatro's animations (the old hack), we hook three clean seams in
-- Balatro's own flow so its state machine drives the timing and we just feed it server data:
--   * select_blind            -> open an authoritative run on the server (newRun + selectBlind).
--   * draw_from_deck_to_hand  -> fill G.hand from the server's hand (at the natural DRAW_TO_HAND moment,
--                                so there is NO swapping mid-animation). Each card carries its server idx.
--   * evaluate_play           -> send the played cards to the server, advance to the server's next hand,
--                                and show the server's authoritative score.
-- It auto-engages whenever you select a blind AND the server is reachable; if the server is down it falls
-- back to vanilla Balatro silently. Networking is blocking (a real client must thread it).
-- Launch dump (proof) -> D:/NewServer/build/balatro-bridge.txt.

local OUT = "D:/NewServer/build/balatro-bridge.txt"
local WIRE = "D:/NewServer/build/balatro-bridge-wire.txt"
local socket = require("socket")

local RANK = { TWO = "2", THREE = "3", FOUR = "4", FIVE = "5", SIX = "6", SEVEN = "7", EIGHT = "8",
	NINE = "9", TEN = "T", JACK = "J", QUEEN = "Q", KING = "K", ACE = "A" }
local SUIT = { SPADES = "S", HEARTS = "H", CLUBS = "C", DIAMONDS = "D" }

local CONN = nil         -- persistent socket to the server's run
local SEQ = 2            -- last seq used; first play is seq 3 (auth0, newRun1, selectBlind2)
local SERVER_HAND = {}   -- the server's current authoritative hand (list of {uid,rank,suit})
local DRAW_QUEUE = {}    -- server cards waiting to be assigned to Balatro's next deck draws
local ENGAGED = false    -- true when a server run is driving this blind

local lines = {}
local function logln(s)
	lines[#lines + 1] = tostring(s)
	pcall(function()
		local f = io.open(OUT, "w")
		if f then f:write(table.concat(lines, "\n") .. "\n"); f:close() end
	end)
end

-- Raw wire log: ">>" = sent to server, "<<" = received. Goes to the lovely console (print) and to a file
-- we can read back. Truncated once at load.
pcall(function()
	local f = io.open(WIRE, "w")
	if f then f:write("== bbridge wire log ==\n"); f:close() end
end)
local function wire(tag, s)
	local line = "[bbridge] " .. tag .. " " .. tostring(s)
	pcall(function() print(line) end)
	pcall(function()
		local f = io.open(WIRE, "a")
		if f then f:write(line .. "\n"); f:close() end
	end)
end

local function http_login(username)
	local s = socket.tcp(); s:settimeout(2)
	if not s:connect("127.0.0.1", 8788) then return nil end
	local body = '{"username":"' .. username .. '"}'
	s:send("POST /login HTTP/1.1\r\nHost: 127.0.0.1:8788\r\nContent-Type: application/json\r\nContent-Length: "
		.. #body .. "\r\nConnection: close\r\n\r\n" .. body)
	local clen
	while true do
		local l = s:receive("*l")
		if not l or l == "" then break end
		local n = l:match("[Cc]ontent%-[Ll]ength:%s*(%d+)")
		if n then clen = tonumber(n) end
	end
	local resp = clen and s:receive(clen) or s:receive("*a")
	s:close()
	return resp and resp:match('"token":"([^"]+)"') or nil
end

local function parse_hand(view)
	local hand = {}
	for uid, rank, suit in view:gmatch('"uid":(%d+),"rank":"(%u+)","suit":"(%u+)"') do
		hand[#hand + 1] = { uid = tonumber(uid), rank = rank, suit = suit }
	end
	return hand
end

local function recv_until(s, pred)
	for _ = 1, 24 do
		local l = s:receive("*l")
		if not l then wire("<<", "(nil / timeout)"); return nil end
		wire("<<", l)
		if pred(l) then return l end
	end
	return nil
end

-- Send a line and log it.
local function wsend(s, json)
	wire(">>", json)
	s:send(json .. "\n")
end

-- Match a response by seq (e.g. "seq":3,) so an auth re-attach push or other out-of-band message is
-- skipped instead of mistaken for our reply. [,}] avoids 3 matching 30.
local function by_seq(n) return function(l) return l:match('"seq":' .. n .. '[,}]') ~= nil end end

-- Open a fresh run + select the blind, keeping the socket. Returns (socket, hand) | (nil, err).
local function open_run()
	local token = http_login("balatro")
	if not token then return nil, "login failed (server :8788?)" end
	local s = socket.tcp(); s:settimeout(2)
	if not s:connect("127.0.0.1", 8789) then return nil, "tcp :8789 failed" end
	wire(">>", '{"type":"auth","seq":0,"token":"<redacted ' .. #token .. ' chars>"}')
	s:send('{"type":"auth","seq":0,"token":"' .. token .. '"}\n')
	if not recv_until(s, function(l) return l:match('"type":"authed"') ~= nil end) then s:close(); return nil, "no authed" end
	wsend(s, '{"type":"newRun","seq":1,"seed":"BRIDGE001"}')
	if not recv_until(s, by_seq(1)) then s:close(); return nil, "no newRun reply" end
	wsend(s, '{"type":"selectBlind","seq":2}')
	local view = recv_until(s, by_seq(2))
	if not view then s:close(); return nil, "no selectBlind reply" end
	SEQ = 2
	return s, parse_hand(view)
end

local function card_key(c)
	local r, su = RANK[c.rank], SUIT[c.suit]
	return (su and r) and (su .. "_" .. r) or nil
end

-- Give a Balatro card the identity of a server card (rank/suit + sprite via set_base) and tag it with the
-- server's stable uid, so we can map it back to a server hand index regardless of position/reordering.
local function set_card_identity(card, sc)
	local key = card_key(sc)
	if card and key and G.P_CARDS and G.P_CARDS[key] and card.set_base then
		card:set_base(G.P_CARDS[key])
		card.bbridge_uid = sc.uid
	end
end

-- Just before Balatro draws, set the identities of the cards it is ABOUT to draw (the deck's END, which
-- is what draw_card pops) to the queued server cards. Balatro then deals THOSE cards with its own
-- animation + deck bookkeeping fully intact — no face flicker, no deck manipulation.
local function prime_deck_for_draw()
	if not (ENGAGED and G.deck and G.deck.cards and #DRAW_QUEUE > 0) then return end
	local limit = (G.hand and G.hand.config and G.hand.config.card_limit) or 8
	local space = math.max(0, limit - ((G.hand and #G.hand.cards) or 0))
	local n = math.min(space, #G.deck.cards, #DRAW_QUEUE)
	for i = 1, n do
		local sc = table.remove(DRAW_QUEUE, 1)
		set_card_identity(G.deck.cards[#G.deck.cards - i + 1], sc) -- cards are drawn from the deck's end
	end
end

-- 0-based index of a server uid in the current server hand (nil if gone).
local function uid_to_index(uid)
	for i, sc in ipairs(SERVER_HAND) do if sc.uid == uid then return i - 1 end end
	return nil
end

-- After a play/discard, queue the server cards that are NEW (not held by a current hand card) so the next
-- draw assigns them. `removing` = set of uids leaving the hand this action (so they don't count as held).
local function recompute_draw_queue(removing)
	local held = {}
	if G.hand and G.hand.cards then
		for _, c in ipairs(G.hand.cards) do
			if c.bbridge_uid and not (removing and removing[c.bbridge_uid]) then held[c.bbridge_uid] = true end
		end
	end
	DRAW_QUEUE = {}
	for _, sc in ipairs(SERVER_HAND) do
		if not held[sc.uid] then DRAW_QUEUE[#DRAW_QUEUE + 1] = sc end
	end
end

-- Send a card-index intent (playHand/discard) and return the raw response line, matched by seq.
local function send_intent(typ, indices)
	if not CONN then return nil end
	SEQ = SEQ + 1
	local mySeq = SEQ
	wsend(CONN, '{"type":"' .. typ .. '","seq":' .. mySeq .. ',"cards":[' .. table.concat(indices, ",") .. ']}')
	return recv_until(CONN, by_seq(mySeq))
end

-- Send the played indices to the server; return {score, chips, mult, accepted, rejection, handsLeft, hand}.
local function server_play(indices)
	local resp = send_intent("playHand", indices)
	if not resp then return nil end
	local chips, mult
	for v in resp:gmatch('"runningChips":(%-?%d+)') do chips = tonumber(v) end
	for v in resp:gmatch('"runningMult":(%-?[%d%.]+)') do mult = tonumber(v) end
	return {
		score = tonumber(resp:match('"roundScore":(%-?%d+)')),
		chips = chips, mult = mult,
		accepted = resp:match('"accepted":(%a+)') == "true",
		rejection = resp:match('"rejection":"([^"]+)"'),
		handsLeft = tonumber(resp:match('"handsLeft":(%d+)')),
		hand = parse_hand(resp),
	}
end

local function popup(text, colour)
	pcall(function()
		attention_text({
			text = text, scale = 1.05, hold = 1.8, colour = colour or G.C.GREEN, align = "cm",
			pos = { x = (G.ROOM and G.ROOM.T.w or 20) / 2, y = (G.ROOM and G.ROOM.T.h or 11) / 3 },
		})
	end)
end

-- Install the three hooks (called once at launch).
local function install_hooks()
	if not (G and G.FUNCS) then return false end

	-- 1) select_blind: open the authoritative run, and queue the initial hand for the first deal.
	local _sel = G.FUNCS.select_blind
	G.FUNCS.select_blind = function(e)
		pcall(function()
			if CONN then pcall(function() CONN:close() end) end
			CONN, SERVER_HAND, DRAW_QUEUE, ENGAGED = nil, {}, {}, false
			local s, hand = open_run()
			if s then
				CONN, SERVER_HAND, ENGAGED = s, hand, true
				for _, sc in ipairs(hand) do DRAW_QUEUE[#DRAW_QUEUE + 1] = sc end -- whole hand for the initial deal
				logln("ENGAGED: server run open, " .. #hand .. "-card hand queued.")
			else
				logln("select_blind: server offline (" .. tostring(hand) .. ") -> vanilla Balatro.")
			end
		end)
		return _sel(e)
	end

	-- 2) draw_from_deck_to_hand: let Balatro draw its OWN cards (full animation + deck bookkeeping), but
	-- first set the identities of the cards it's about to draw to the queued server cards.
	local _draw = G.FUNCS.draw_from_deck_to_hand
	G.FUNCS.draw_from_deck_to_hand = function(e)
		if ENGAGED then pcall(prime_deck_for_draw) end
		return _draw(e)
	end

	-- 2b) discard: map the highlighted cards (by uid) to server indices, tell the server, queue the new draws.
	local _disc = G.FUNCS.discard_cards_from_highlighted
	G.FUNCS.discard_cards_from_highlighted = function(e, hook)
		if ENGAGED and CONN and G.hand and G.hand.highlighted and G.hand.highlighted[1] then
			pcall(function()
				local removing, idx = {}, {}
				for _, c in ipairs(G.hand.highlighted) do -- read BEFORE the original moves them
					if c.bbridge_uid then
						removing[c.bbridge_uid] = true
						local ix = uid_to_index(c.bbridge_uid)
						if ix then idx[#idx + 1] = ix end
					end
				end
				if #idx == 0 then return end
				local resp = send_intent("discard", idx)
				if resp and resp:match('"accepted":(%a+)') == "true" then
					SERVER_HAND = parse_hand(resp)
					recompute_draw_queue(removing) -- the new server cards fill the discarded slots
					logln("discard -> server applied (" .. #idx .. " cards), " .. #DRAW_QUEUE .. " new queued")
				elseif resp then
					logln("discard REJECTED: " .. tostring(resp:match('"rejection":"([^"]+)"')))
					popup("DISCARD REJECTED: " .. tostring(resp:match('"rejection":"([^"]+)"')), G.C.RED)
				end
			end)
		end
		return _disc(e, hook)
	end

	-- 3) evaluate_play: map played cards (by uid) to server indices, the server scores them, queue new draws.
	local _eval = G.FUNCS.evaluate_play
	G.FUNCS.evaluate_play = function(e)
		if ENGAGED and CONN and G.play and G.play.cards then
			pcall(function()
				local idx = {}
				for _, c in ipairs(G.play.cards) do
					local ix = c.bbridge_uid and uid_to_index(c.bbridge_uid)
					if ix then idx[#idx + 1] = ix end
				end
				if #idx == 0 then return end
				local res = server_play(idx)
				if not res then logln("play: no server response"); return end
				if res.accepted then
					SERVER_HAND = (res.handsLeft and res.handsLeft > 0) and res.hand or {}
					recompute_draw_queue(nil) -- played cards already left G.hand -> kept cards are "held"
					local hs = (res.chips or 0) * (res.mult or 0)
					-- No bolted-on popup or fixed-delay forced set (those desync on slow machines / game
					-- speed). Balatro renders the score with its OWN add animation at its own pace; for a
					-- base run that IS the server's number. The log records server vs the authoritative total
					-- so we can confirm parity and catch any divergence (where Stage 4 will drive the native
					-- chip-ease to the server value instead).
					logln(string.format("play -> SERVER %s x %s = %g [round %s] handsLeft=%s new=%d",
						tostring(res.chips), tostring(res.mult), hs, tostring(res.score), tostring(res.handsLeft), #DRAW_QUEUE))
					if res.handsLeft == 0 then logln("server: blind hands exhausted (round-end is Stage 2)") end
				else
					logln("play REJECTED: " .. tostring(res.rejection))
					popup("REJECTED: " .. tostring(res.rejection), G.C.RED)
				end
			end)
		end
		return _eval(e) -- Balatro animates locally (matches the server for the base game); Stage 1b drives it from the replay
	end

	return true
end

-- launch-time proof: dump the ClientView->Balatro card mapping (throwaway run), then install hooks.
local function prove_translation()
	logln("== BalatroBridge Stage 1 thin client ==")
	local s, hand = open_run()
	if not s then logln("server check FAILED: " .. tostring(hand)); return end
	pcall(function() s:close() end)
	logln(#hand .. "-card server hand maps to Balatro identities:")
	local all_valid = true
	for i, c in ipairs(hand) do
		local key = card_key(c)
		local valid = key and G.P_CARDS and G.P_CARDS[key] ~= nil
		if not valid then all_valid = false end
		logln(string.format("  %d  %-6s %-9s -> %s  valid=%s", i, c.rank, c.suit, tostring(key), tostring(valid)))
	end
	logln(all_valid and "ALL valid." or "SOME invalid.")
	logln("Select a blind to engage: the server deals the hand and scores your plays.")
end

local frames, done = 0, false
pcall(function()
	local _upd = Game.update
	function Game:update(dt)
		_upd(self, dt)
		frames = frames + 1
		if frames == 200 and not done then
			done = true
			pcall(prove_translation)
			pcall(function() logln("hooks installed=" .. tostring(install_hooks())) end)
		end
	end
end)
