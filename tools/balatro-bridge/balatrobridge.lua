-- BalatroBridge (Stage 1 thin client): Balatro is the renderer; the SERVER owns the cards & scoring.
-- Instead of swapping cards on top of Balatro's animations (the old hack), we hook three clean seams in
-- Balatro's own flow so its state machine drives the timing and we just feed it server data:
--   * select_blind            -> open an authoritative run on the server (newRun + selectBlind).
--   * draw_from_deck_to_hand  -> fill G.hand from the server's hand (at the natural DRAW_TO_HAND moment,
--                                so there is NO swapping mid-animation). Each card carries its server idx.
--   * evaluate_play           -> send the played cards to the server, advance to the server's next hand,
--                                and show the server's authoritative score.
-- It auto-engages whenever you select a blind AND the server is reachable; if the server is UNREACHABLE it
-- shows a loud on-screen "NOT CONNECTED" warning (NEVER silently plays a fake-competitive vanilla blind).
-- Networking is blocking (the threaded variant is reverted — it dropped requests in-game; see git history).
-- Launch dump (proof) -> D:/NewServer/build/balatro-bridge.txt.

-- Load luasocket DEFENSIVELY: this is the only thing that runs unguarded at mod-load time, so if it ever
-- fails (load order / first cold boot) a raw `require` would crash Balatro on launch. On failure we just
-- run with socket=nil -> the mod degrades to vanilla (every networking call guards on socket below).
local ok_socket, socket = pcall(require, "socket")
if not ok_socket then socket = nil end
-- JSON: SMODS bundles rxi json (require"json"). Server lines are decoded through it instead of regex-scraped.
local ok_json, json = pcall(require, "json")
if not ok_json then json = nil end
-- Pure wire helpers (view_of + card/edition/pack key mappers), shared verbatim with the standalone parser
-- spec (test/parsers_spec.lua). The structured parse layer lives there now; the mod decodes + reads it.
local wire = assert(SMODS.load_file("lib/wire.lua", "balatrobridge"))()
local card_key, edition_table, pack_center_key = wire.card_key, wire.edition_table, wire.pack_center_key

local CONN = nil         -- persistent socket to the server's run
local SEQ = 2            -- last seq used; first play is seq 3 (auth0, newRun1, selectBlind2)
local SERVER_HAND = {}   -- the server's current authoritative hand (list of {uid,rank,suit})
local DRAW_QUEUE = {}    -- server cards waiting to be assigned to Balatro's next deck draws
local ENGAGED = false    -- true when a server run is driving this blind
local VIEW = nil         -- latest authoritative ClientView scalars (phase/requirement/roundScore/...)
local RUN_LIVE = false   -- a server run is mid-progression: continue it across blinds (don't newRun)
local SHOP_DONE = false  -- this shop visit has been reconciled to the server's items (once per visit)
local SCORING = false    -- a play is being scored: retarget ONLY its round-score ease, not Balatro's
                         -- end-of-round reset-to-0 ease (which we were hijacking -> score lingered)
local PENDING_PLAY = nil -- server result fetched at the Play button (BEFORE scoring animates), consumed by
                         -- evaluate_play -- so the blocking round-trip doesn't freeze mid-scoring

-- Leveled logging (append-based, so nothing is lost across a long session): INFO is a concise trail of
-- every game action; DEBUG is INFO plus the raw wire JSON + reconcile internals. Both truncated at mod load.
local LOG_INFO  = "D:/NewServer/build/competitive-balatro.info.log"
local LOG_DEBUG = "D:/NewServer/build/competitive-balatro.debug.log"
pcall(function() local f = io.open(LOG_INFO, "w");  if f then f:write("== Competitive Balatro -- INFO (action trail) ==\n");  f:close() end end)
pcall(function() local f = io.open(LOG_DEBUG, "w"); if f then f:write("== Competitive Balatro -- DEBUG (verbose + wire) ==\n"); f:close() end end)

local function append(path, s)
	pcall(function() local f = io.open(path, "a"); if f then f:write(s .. "\n"); f:close() end end)
end

-- INFO/WARN -> both files + console; DEBUG -> debug file only. Every server action calls log.info; the
-- raw wire JSON + reconcile detail call log.debug, so INFO stays a clean, complete action trail.
local log = {}
function log.info(s)  local l = "[INFO] " .. tostring(s); append(LOG_INFO, l); append(LOG_DEBUG, l); pcall(print, "[CompBalatro] " .. l) end
function log.warn(s)  local l = "[WARN] " .. tostring(s); append(LOG_INFO, l); append(LOG_DEBUG, l); pcall(print, "[CompBalatro] " .. l) end
function log.debug(s) append(LOG_DEBUG, "[DBG]  " .. tostring(s)) end

-- Existing logln(...) call sites map to INFO. (The old raw-wire `wire()` logger is removed -- it shadowed
-- the `wire` parse module [lib/wire.lua], silently breaking wire.view_of; its callers now log.debug directly.)
local function logln(s) log.info(s) end

-- Log any joker-trigger events the server surfaced with this response (e.g. "Hallucination triggered ->
-- created c_emperor"). The server DRAINS its buffer per response, so each view's events are new -> logged once.
local function log_events(v)
	if v and type(v.events) == "table" then
		for _, e in ipairs(v.events) do log.info("[JOKER] " .. tostring(e)) end
	end
end

-- DEV mode: emit "server resolution vs client resolution" comparisons at each action (what the server SENT
-- -- cards on a draw, shop items, a consumable's effect, the cash-out breakdown -- vs what the native game
-- actually rendered). ON -> these surface in INFO (prominent, next to the action); OFF -> DEBUG only. Toggle
-- by launching with the env var COMPBALATRO_DEV=0 (default ON while iterating). The server stays the source
-- of truth either way; this only changes how loudly the bridge narrates the reconciliation.
local DEV = (os.getenv("COMPBALATRO_DEV") ~= "0")
function log.dev(tag, s)
	local l = "[DEV " .. tostring(tag) .. "] " .. tostring(s)
	if DEV then log.info(l) else log.debug(l) end
end

-- Compact human label for a server card ({uid,rank,suit}) for the action trail: "AS" "KH" "10D".
-- Server sends ENUM names (SPADES/ACE); SUIT_CH below (capitalized) is for NATIVE base.suit ("Spades") in
-- the use path -- a different case, so fmt_card needs its own server-enum maps.
local SUIT_CH = { Spades = "S", Hearts = "H", Clubs = "C", Diamonds = "D" }
local FMT_SUIT = { SPADES = "S", HEARTS = "H", CLUBS = "C", DIAMONDS = "D" }
local FMT_RANK = { TWO = "2", THREE = "3", FOUR = "4", FIVE = "5", SIX = "6", SEVEN = "7", EIGHT = "8",
	NINE = "9", TEN = "10", JACK = "J", QUEEN = "Q", KING = "K", ACE = "A" }
local function fmt_card(sc)
	if not sc then return "?" end
	return (FMT_RANK[sc.rank] or tostring(sc.rank or "?")) .. (FMT_SUIT[sc.suit] or tostring(sc.suit or "?"))
end
local function fmt_cards(list)
	if not list or #list == 0 then return "(none)" end
	local t = {}
	for _, sc in ipairs(list) do t[#t + 1] = fmt_card(sc) end
	return table.concat(t, " ")
end

-- THE canonical lifecycle stage. Resolved from the NATIVE G.STATE first (it alone distinguishes the
-- post-blind cash-out screen from the real shop) and the server VIEW.phase as a fallback / run-outcome. The
-- server flips to SHOP the instant a blind is beaten, but Balatro stays in ROUND_EVAL until you click Cash
-- Out -- so anything that should wait for the actual shop must gate on stage()=="SHOP", not VIEW.phase=="SHOP".
-- Stages: SELECT | PLAYING | ROUND_EVAL (beat blind, pre-cashout) | SHOP | PACK | GAME_OVER | UNKNOWN.
local function stage()
	local S, st = G and G.STATES, G and G.STATE
	if S and st ~= nil then
		if st == S.GAME_OVER then return "GAME_OVER" end
		if st == S.ROUND_EVAL then return "ROUND_EVAL" end -- post-blind, BEFORE cash out (server is already SHOP)
		if st == S.SHOP then return "SHOP" end             -- actually in the shop (after Cash Out)
		if st == S.BLIND_SELECT then return "SELECT" end
		if st == S.SELECTING_HAND or st == S.HAND_PLAYED or st == S.DRAW_TO_HAND then return "PLAYING" end
		for _, k in ipairs({ "TAROT_PACK", "PLANET_PACK", "SPECTRAL_PACK", "STANDARD_PACK", "BUFFOON_PACK" }) do
			if S[k] and st == S[k] then return "PACK" end
		end
	end
	local p = VIEW and VIEW.phase -- fallback when native state isn't readable
	if p == "BLIND_ACTIVE" then return "PLAYING" end
	if p == "BLIND_SELECT" then return "SELECT" end
	if p == "RUN_LOST" or p == "RUN_WON" then return "GAME_OVER" end
	if p == "SHOP" then return "SHOP" end
	return "UNKNOWN"
end

local function http_login(username)
	if not socket then return nil end -- luasocket unavailable -> stay vanilla
	local s = socket.tcp(); s:settimeout(2)
	if not s:connect("127.0.0.1", 28788) then return nil end
	local body = '{"username":"' .. username .. '"}'
	s:send("POST /login HTTP/1.1\r\nHost: 127.0.0.1:28788\r\nContent-Type: application/json\r\nContent-Length: "
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

-- Decode one newline-delimited server line to a table (nil on failure/no json).
local function decode(resp)
	if not (json and resp) then return nil end
	local ok, d = pcall(json.decode, resp)
	if ok then return d end
	return nil
end

-- The structured parse: decode the line, then normalize via wire.view_of (flat scalars + hand + shop/
-- vouchers/consumables/jokers/packs/openPack). Replaces the old per-field regex scrapers (parse_objs/
-- parse_open_pack) and the fragile "uid":"..." hand regex that silently rotted on the int->UUID change.
local function parse_view(resp)
	local d = decode(resp)
	return d and wire.view_of(d) or nil
end

local function parse_hand(resp)
	local v = parse_view(resp)
	return v and v.hand or {}
end

local function recv_until(s, pred)
	for _ = 1, 24 do
		local l = s:receive("*l")
		if not l then log.debug("recv << (nil / timeout)"); return nil end
		log.debug("recv << " .. l)
		if pred(l) then return l end
	end
	return nil
end

-- Send a line and log it (debug).
local function wsend(s, json)
	log.debug("send >> " .. json)
	s:send(json .. "\n")
end

-- Match a response by seq (e.g. "seq":3,) so an auth re-attach push or other out-of-band message is
-- skipped instead of mistaken for our reply. [,}] avoids 3 matching 30.
local function by_seq(n) return function(l) return l:match('"seq":' .. n .. '[,}]') ~= nil end end

-- Log in + auth + newRun, forwarding the player's New Run deck/stake. Leaves the SERVER at BLIND_SELECT
-- (does NOT select the blind). Returns (socket, view) | (nil, err). This is the run-START engage: opening
-- the server run here (on the New Run "Play") -- not lazily at the first select_blind -- is what makes the
-- run properly server-driven from the deck screen and fixes the "fresh run at Small (300)"/skip class.
local function open_server_run()
	local token = http_login("balatro")
	if not token then return nil, "login failed (server :28788?)" end
	local s = socket.tcp(); s:settimeout(2)
	if not s:connect("127.0.0.1", 28789) then return nil, "tcp :28789 failed" end
	log.debug("send >> {\"type\":\"auth\",\"seq\":0,\"token\":\"<redacted " .. #token .. " chars>\"}")
	s:send('{"type":"auth","seq":0,"token":"' .. token .. '"}\n')
	if not recv_until(s, function(l) return l:match('"type":"authed"') ~= nil end) then s:close(); return nil, "no authed" end
	-- Forward the player's New Run choice: native deck key b_xxx -> our d_xxx; stake is an integer 1..8.
	-- G.GAME.selected_back_key is the CENTER OBJECT (get_deck_from_name returns the center, not a string --
	-- game.lua:2038/2044), so pull the string key off the center (.key) / the Back's center.
	local extra = ""
	pcall(function()
		if G and G.GAME then
			local sbk = G.GAME.selected_back_key
			local key = (type(sbk) == "string" and sbk)
				or (type(sbk) == "table" and sbk.key)
				or (G.GAME.selected_back and G.GAME.selected_back.effect
					and G.GAME.selected_back.effect.center and G.GAME.selected_back.effect.center.key)
			if type(key) == "string" then extra = extra .. ',"deck":"' .. key:gsub("^b_", "d_") .. '"' end
			if type(G.GAME.stake) == "number" then extra = extra .. ',"stake":"' .. G.GAME.stake .. '"' end
		end
	end)
	logln("newRun forwarding: " .. (extra == "" and "(none -> server default deck/stake)" or extra))
	-- No seed: the SERVER generates a fresh random seed each run (anti-cheat + so every run differs).
	wsend(s, '{"type":"newRun","seq":1' .. extra .. "}")
	local view = recv_until(s, by_seq(1))
	if not view then s:close(); return nil, "no newRun reply" end
	SEQ = 1
	return s, parse_view(view)
end

-- open_server_run + select the first blind (deal the opening hand). The FALLBACK for select_blind when no
-- run is already live (normally the run is opened at run start). Returns (socket, hand, view) | (nil, err).
local function open_run()
	local s, v = open_server_run()
	if not s then return nil, v end -- v is the error string
	wsend(s, '{"type":"selectBlind","seq":2}')
	local view = recv_until(s, by_seq(2))
	if not view then s:close(); return nil, "no selectBlind reply" end
	SEQ = 2
	return s, parse_hand(view), parse_view(view)
end

-- card_key/edition_table/pack_center_key are aliased to wire.* at the top (pure, spec-tested).

-- Drive a Balatro card to a server card's FULL identity: rank/suit (set_base), edition (Foil/Holo/Poly),
-- and tag the server uid + the stamped identity (so render_hand can detect a mid-blind mutation and avoid
-- needless re-stamps). enhancement/seal are added in the consumable phase (Tarot mutate). Idempotent: only
-- the fields that changed are re-applied by the caller (render_hand stamps on diff). Replaces set_card_identity.
local function hand_restamp(card, sc)
	if not (card and sc and card.set_base) then return end
	local key = card_key(sc)
	if key and G.P_CARDS and G.P_CARDS[key] then card:set_base(G.P_CARDS[key]) end
	-- Render a card edition (a Foil card dealt from the deck, or a mid-blind edition change). Apply only a
	-- real edition; a base card needs no clear (set_base already gave it no edition).
	if sc.edition and sc.edition ~= "NONE" and card.set_edition then
		pcall(function() card:set_edition(edition_table(sc.edition), true, true) end)
	end
	card.bbridge_uid = sc.uid
	card.bbridge_rank, card.bbridge_suit, card.bbridge_edition = sc.rank, sc.suit, sc.edition
end

-- Remove a native hand card the SERVER no longer has (destroyed by a consumable, e.g. Immolate). Animate
-- it out with the native dissolve, then drop it. Idempotent via bbridge_dissolving so render_hand never
-- double-dissolves a card already on its way out.
local function hand_remove(card)
	if not (card and not card.bbridge_dissolving) then return end
	card.bbridge_dissolving = true
	-- Prefer the native dissolve: start_dissolve animates the shatter/fade and removes the card from its area
	-- when the animation finishes (so the card LINGERS in G.hand.cards mid-dissolve -- bbridge_dissolving keeps
	-- a re-run from dissolving it twice). Only if there's no dissolve do we drop it outright.
	if card.start_dissolve then
		pcall(function() card:start_dissolve() end)
	else
		pcall(function() if G.hand and G.hand.remove_card then G.hand:remove_card(card) end end)
		pcall(function() if card.remove then card:remove() end end)
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
	local primed = {}
	for i = 1, n do
		local sc = table.remove(DRAW_QUEUE, 1)
		hand_restamp(G.deck.cards[#G.deck.cards - i + 1], sc) -- cards are drawn from the deck's end
		primed[#primed + 1] = sc
	end
	if n > 0 then log.dev("DRAW", "server dealing " .. n .. " card(s): " .. fmt_cards(primed) ..
		(#DRAW_QUEUE > 0 and ("  (" .. #DRAW_QUEUE .. " still queued)") or "")) end
end

-- THE uid-keyed hand reconciler (drive-it model): make the native hand match the server hand exactly,
-- whatever changed it (deal/draw, play/discard, OR a consumable that destroyed/mutated cards).
--   1. REMOVE (dissolve) native cards whose uid the server no longer has -> Immolate & friends.
--   2. RESTAMP cards whose mapped server identity changed (Tarot mutate / edition) -- on diff only.
--   3. ASSIGN each still-unmapped native card the next unclaimed server card (the escaped-card fix).
-- State-based, not prediction-based; never touches G.deck. Idempotent -> safe to call repeatedly/deferred.
-- `hand` defaults to SERVER_HAND (the cached authoritative hand). Replaces reconcile_hand_to_server.
local function render_hand(hand)
	hand = hand or SERVER_HAND
	-- Require a NATIVE hand to reconcile: outside a blind (shop/blind-select) G.hand is empty, and the deal
	-- -- not render_hand -- is what populates it. Reconciling an empty hand against a non-empty server hand
	-- would just report every server card "unplaced". So bail unless there are native cards present.
	if not (ENGAGED and G.hand and G.hand.cards and #G.hand.cards > 0 and hand and #hand > 0) then return end
	local server_by_uid = {}
	for _, sc in ipairs(hand) do server_by_uid[sc.uid] = sc end
	-- 1) Remove cards the server destroyed (uid gone). A played/discarded card has already left G.hand by
	--    the time this runs (deferred), so anything still here with a missing uid is a real server destroy.
	for i = #G.hand.cards, 1, -1 do
		local c = G.hand.cards[i]
		if c.bbridge_uid and not server_by_uid[c.bbridge_uid] then hand_remove(c) end
	end
	-- 2) Claim surviving cards; restamp ONLY when the mapped identity actually changed (no needless flicker).
	local claimed = {}
	for _, c in ipairs(G.hand.cards) do
		local sc = c.bbridge_uid and server_by_uid[c.bbridge_uid]
		if sc and not claimed[c.bbridge_uid] then
			claimed[c.bbridge_uid] = true
			if c.bbridge_rank ~= sc.rank or c.bbridge_suit ~= sc.suit or c.bbridge_edition ~= sc.edition then
				hand_restamp(c, sc) -- Tarot mutate / edition change in place
			end
		else
			c.bbridge_uid = nil
		end
	end
	-- 3) Assign each unmapped native card the next unclaimed server card (corrects an escaped/unstamped card).
	local pool = {}
	for _, sc in ipairs(hand) do if not claimed[sc.uid] then pool[#pool + 1] = sc end end
	local pi, fixed = 1, 0
	for _, c in ipairs(G.hand.cards) do
		if not c.bbridge_uid and pi <= #pool then hand_restamp(c, pool[pi]); pi = pi + 1; fixed = fixed + 1 end
	end
	if fixed > 0 then logln("hand reconcile: corrected " .. fixed .. " card(s)") end
	if pi <= #pool then log.warn("hand reconcile: " .. (#pool - pi + 1) .. " server card(s) unplaced (hand-size mismatch?)") end
	-- DEV: server hand identity vs what the native hand actually shows (should be equal).
	local native = {}
	for _, c in ipairs(G.hand.cards) do
		local sc = c.bbridge_uid and server_by_uid[c.bbridge_uid]
		native[#native + 1] = sc and fmt_card(sc) or "??"
	end
	log.dev("HAND", "server=[" .. fmt_cards(hand) .. "]  native=[" .. table.concat(native, " ") .. "]")
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

-- Make Balatro's blind-DECISION inputs equal the server's, so its NATIVE state machine renders the
-- server's outcome with zero round-end code ported. The whole win/lose hinge is two state updaters
-- reading three values (real source):
--   game.lua:3197  if G.GAME.chips - G.GAME.blind.chips >= 0 or hands_left < 1 then end the round
--   game.lua:3254  end_round(): chips - blind.chips >= 0 ? win (ROUND_EVAL) : lose (GAME_OVER)
-- We OWN blind.chips, hands_left, discards_left here. We do NOT yet force G.GAME.chips: for a vanilla
-- deck Balatro's native count-up already lands on the server's roundScore, so the native decision
-- matches the server. The deferred read-only check below proves that each hand and flags exactly where
-- Stage 4's replay-driven count-up (forcing G.GAME.chips) becomes necessary. It mutates nothing and
-- cannot soft-lock the game.
local function reconcile(view)
	if not (ENGAGED and view and G and G.GAME) then return end
	VIEW = view
	log_events(view) -- surface joker triggers (round-end/creation) that fired with this action
	pcall(function()
		if view.requirement and G.GAME.blind then G.GAME.blind.chips = view.requirement end
		if G.GAME.current_round then
			if view.handsLeft then G.GAME.current_round.hands_left = view.handsLeft end
			-- Do NOT set discards_left here: on a discard, native's own discard_cards_from_highlighted ALSO
			-- runs ease_discard(-1) (state_events.lua:435). Setting the server's already-decremented value here
			-- and THEN letting native ease -1 would burn TWO discards for one action. Native's ease drives the
			-- single visible decrement (it starts from the in-sync value); the deferred block below re-asserts
			-- the server's value as the authoritative backstop, after native's ease settles.
		end
		-- Render MONEY from the server's store (like the hand and score), but ONLY during active play.
		-- The blind reward + interest are added when the player clicks Cash Out, NOT at the win -- yet the
		-- server adds them in winBlind, so view.money already includes them the instant the blind is beaten.
		-- Setting dollars to that here would show the post-reward total on the round-eval screen before
		-- cash-out (premature). So skip the win/loss transition; the shop snap sets the post-cash-out total
		-- at the right time. Mid-blind, view.money is the pre-reward value (gold cards etc.) -> safe.
		if view.money and view.phase == "BLIND_ACTIVE" then G.GAME.dollars = view.money end
	end)
	pcall(function()
		if not (G.E_MANAGER and Event) then return end
		G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.6, blocking = false, func = function()
			-- Authoritative backstop AFTER native's eases settle: the server owns discards_left (and hands_left).
			-- This corrects any native/server drift without double-counting native's own ease_discard(-1)/hands.
			if G.GAME and G.GAME.current_round then
				if view.discardsLeft then G.GAME.current_round.discards_left = view.discardsLeft end
				if view.handsLeft then G.GAME.current_round.hands_left = view.handsLeft end
			end
			local native = G.GAME and G.GAME.chips
			log.debug(string.format("reconcile: native chips=%s | server roundScore=%s requirement=%s handsLeft=%s phase=%s",
				tostring(native), tostring(view.roundScore), tostring(view.requirement), tostring(view.handsLeft), tostring(view.phase)))
			if native ~= nil and view.roundScore ~= nil and math.abs(native - view.roundScore) > 0.5 then
				log.debug("note: native count-up diverged from server roundScore (server value enforced at round-end)")
			end
			-- The server has no separate "won" phase: beating a blind IS the SHOP transition (it generates the
			-- shop then). So phase=SHOP right after a win is EXPECTED -- native is still on the ROUND_EVAL
			-- cash-out screen (stage()=="ROUND_EVAL"); the real shop renders after you click Cash Out.
			if view.phase == "SHOP" then logln("blind beaten -> server now in SHOP (cash-out screen showing; shop after Cash Out)")
			elseif view.phase == "RUN_LOST" then logln("blind lost -> server RUN_LOST (native GAME_OVER renders it)") end
			return true
		end }))
	end)
end

-- Send a card-index intent (playHand/discard) and return the raw response line, matched by seq.
local function send_intent(typ, indices)
	if not CONN then return nil end
	SEQ = SEQ + 1
	local mySeq = SEQ
	wsend(CONN, '{"type":"' .. typ .. '","seq":' .. mySeq .. ',"cards":[' .. table.concat(indices, ",") .. ']}')
	return recv_until(CONN, by_seq(mySeq))
end

-- Send a no/simple-arg intent (selectBlind/proceed/reroll/buyShopItem). `extra` is an optional JSON
-- fragment like ',"index":2'. Returns {accepted, rejection, view, hand} | nil.
local function send_action(typ, extra)
	if not CONN then return nil end
	SEQ = SEQ + 1
	local mySeq = SEQ
	wsend(CONN, '{"type":"' .. typ .. '","seq":' .. mySeq .. (extra or "") .. "}")
	local resp = recv_until(CONN, by_seq(mySeq))
	if not resp then return nil end
	local v = parse_view(resp)
	log_events(v) -- joker triggers from this action (e.g. Hallucination on openPack)
	return {
		accepted = resp:match('"accepted":(%a+)') == "true", -- an error reply has no accepted field => false
		rejection = resp:match('"rejection":"([^"]+)"'),
		view = v,
		hand = parse_hand(resp),
	}
end

-- Give a Balatro shop card the identity of a server shop item: swap its center (joker/consumable) and
-- price, and tag it with the server shop index for the buy intent. set_ability is the joker/consumable
-- analog of set_base (verified card.lua). Returns true on success.
local function set_shop_card_identity(card, item, index)
	if not (card and item and item.key and G.P_CENTERS and G.P_CENTERS[item.key] and card.set_ability) then
		return false
	end
	pcall(function() card:set_ability(G.P_CENTERS[item.key], true) end)
	-- DRIVE native's buy routing by giving it correct INPUT (so there's nothing to reconcile after): native
	-- buy_from_shop routes PURELY on card.ability.consumeable (button_callbacks.lua:2429 -> consumeables else
	-- jokers). set_ability can leave a STALE consumeable flag when converting a consumable slot to a joker ->
	-- the joker-in-consumable-slot bug. Force the flag from the server's kind so native routes it right once.
	pcall(function()
		if card.ability then
			card.ability.consumeable = (item.kind == "TAROT" or item.kind == "PLANET" or item.kind == "SPECTRAL") or nil
		end
	end)
	card.bbridge_shop_index = index
	if item.cost then card.cost = item.cost end
	-- Render the server's rolled edition (Foil/Holo/Poly/Negative); the bought joker inherits this card,
	-- so its edition carries into G.jokers for free. NONE/nil clears to base.
	if card.set_edition then pcall(function() card:set_edition(edition_table(item.edition), true, true) end) end
	return true
end

-- OWN the shop (drive-it, no reconcile): build native's own "load this exact shop area" table from the
-- SERVER's items, so native LOADS our cards (game.lua:3099/3116/3135 `if G.load_shop_*`) instead of
-- RNG-generating them + us swapping identities afterward. The table is produced via the REAL Card:save()
-- round-trip, so it's byte-for-byte the format CardArea:load expects (Balatro's own save/restore path).
-- `center(item)` picks the P_CENTERS entry; `setup(card,item,i)` does per-area tweaks. Returns the load
-- table, or nil to bail (any gap -> native falls back to its generation + our reconcile: never worse).
local function build_shop_load(items, area, center, setup)
	if not (items and #items > 0 and Card and G.P_CARDS and area) then return nil end
	local cards = {}
	for i, item in ipairs(items) do
		local ctr = center(item)
		if not ctr then return nil end
		local ok, saved = pcall(function()
			local c = Card(0, 0, G.CARD_W, G.CARD_H, G.P_CARDS.empty, ctr,
				{ bypass_discovery_center = true, bypass_discovery_ui = true })
			if item.cost then c.cost = item.cost; c.sell_cost = math.max(1, math.floor((item.cost or 1) / 2)) end
			if setup then setup(c, item, i) end
			local t = c:save()
			pcall(function() if c.remove then c:remove() end end) -- discard temp card; only its save table is used
			return t
		end)
		if not (ok and saved) then return nil end
		cards[#cards + 1] = saved
	end
	return { config = area.config, cards = cards }
end

-- Per-area load-table builders (main jokers/consumables, vouchers, booster packs).
local function build_shop_load_jokers()
	return build_shop_load(VIEW and VIEW.shop, G.shop_jokers,
		function(item) return item.key and G.P_CENTERS and G.P_CENTERS[item.key] end,
		function(c, item)
			if c.set_edition and item.edition and item.edition ~= "NONE" then c:set_edition(edition_table(item.edition), true, true) end
			if c.ability then -- correct buy-routing flag (native routes on ability.consumeable)
				c.ability.consumeable = (item.kind == "TAROT" or item.kind == "PLANET" or item.kind == "SPECTRAL") or nil
			end
		end)
end
local function build_shop_load_vouchers()
	return build_shop_load(VIEW and VIEW.vouchers, G.shop_vouchers,
		function(item) return item.key and G.P_CENTERS and G.P_CENTERS[item.key] end,
		function(c) c.shop_voucher = true end)
end
local function build_shop_load_booster()
	return build_shop_load(VIEW and VIEW.packs, G.shop_booster,
		function(item) local k = pack_center_key(item); return k and G.P_CENTERS and G.P_CENTERS[k] end,
		function(c, item, i) if c.ability then c.ability.booster_pos = i end end)
end

-- Snap the HUD money to the server's truth (the native cash-out animated to its OWN figure). Set the value
-- DIRECTLY (the HUD DynaText is bound to G.GAME.dollars, so it refreshes next frame) rather than easing a
-- relative delta: ease_dollars(server-native) read mid-cash-out-animation could compound a second ease and
-- leave money off. Logs the breakdown so any server-vs-native economy gap is visible in the bridge log.
local function snap_money()
	pcall(function()
		if not (VIEW and VIEW.money and G and G.GAME) then return end
		local native = G.GAME.dollars or 0
		if native ~= VIEW.money then
			log.debug(string.format("money snap: native=$%s -> server=$%s (delta %s) [cashout reward=%s interest=%s]",
				tostring(native), tostring(VIEW.money), tostring(VIEW.money - native),
				tostring(VIEW.cashReward), tostring(VIEW.cashInterest)))
		end
		G.GAME.dollars = VIEW.money
	end)
end

-- Render the OWNED joker row (G.jokers) to match the server's VIEW.jokers: identity (center) + EDITION
-- (Foil/Holo/Poly/Negative -- e.g. a Hex/Wheel polychrome), creating missing ones and dropping extras
-- (Hex destroys others). Display only -- the server stays authoritative for scoring. Safe to call both in
-- the shop and mid-blind (after a consumable changes the jokers), which is why it's its own function now.
local function reconcile_jokers_to_server()
	if not (ENGAGED and VIEW and VIEW.jokers and G.jokers and G.jokers.cards and Card and G.P_CENTERS) then return end
	for i = 1, #VIEW.jokers do
		local jk, c = VIEW.jokers[i], G.jokers.cards[i]
		if jk and jk.key and G.P_CENTERS[jk.key] then
			if not c then
				pcall(function()
					local nc = Card(G.jokers.T.x + G.jokers.T.w / 2, G.jokers.T.y, G.CARD_W, G.CARD_H,
						G.P_CARDS.empty, G.P_CENTERS[jk.key],
						{ bypass_discovery_center = true, bypass_discovery_ui = true })
					nc.bbridge_owned = true
					nc.bbridge_jk_key, nc.bbridge_jk_edition = jk.key, jk.edition
					nc:start_materialize()
					G.jokers:emplace(nc)
					if nc.set_edition and jk.edition and jk.edition ~= "NONE" then
						pcall(function() nc:set_edition(edition_table(jk.edition), true, true) end)
					end
				end)
			else
				-- Only TOUCH what actually changed. Re-applying set_ability/set_edition every reconcile
				-- re-triggers the native edition shader + juice on EVERY joker -- the "Hex is crazy" flicker
				-- across the whole row. Track the last-rendered key/edition and act only on a diff.
				if (c.bbridge_jk_key or (c.config and c.config.center_key)) ~= jk.key and c.set_ability then
					pcall(function() c:set_ability(G.P_CENTERS[jk.key], true) end)
					c.bbridge_jk_key = jk.key
				end
				if c.bbridge_jk_edition ~= jk.edition and c.set_edition then
					pcall(function() c:set_edition(edition_table(jk.edition), true, true) end)
					c.bbridge_jk_edition = jk.edition
				end
			end
		end
	end
	for i = #G.jokers.cards, #VIEW.jokers + 1, -1 do -- native row longer than the server -> drop extras
		local c = G.jokers.cards[i]
		pcall(function() G.jokers:remove_card(c); if c.remove then c:remove() end end)
	end
	log.debug("joker row rendered: " .. #G.jokers.cards .. " card(s) (server " .. #VIEW.jokers .. ")")
end

-- Render the held CONSUMABLE row (G.consumeables) to match the server's VIEW.consumables: fix identities
-- positionally and DROP extras. The bug this fixes: native buy can mis-route a bought JOKER into the
-- consumable slot (a server joker mapped onto a native consumable shop card whose stale `consumeable` flag
-- makes native route it there), leaving a phantom consumable while the joker correctly enters G.jokers via
-- reconcile_jokers_to_server. The server owns the truth, so drop any held consumable it doesn't list.
local function reconcile_consumables_to_server()
	if not (ENGAGED and VIEW and G.consumeables and G.consumeables.cards and G.P_CENTERS) then return end
	local items = VIEW.consumables or {} -- nil/absent -> the server holds NONE -> drop every native phantom
	for i = 1, math.min(#G.consumeables.cards, #items) do
		local it, c = items[i], G.consumeables.cards[i]
		if it and it.key and G.P_CENTERS[it.key] and c and (c.config and c.config.center_key) ~= it.key and c.set_ability then
			pcall(function() c:set_ability(G.P_CENTERS[it.key], true) end) -- fix a wrong identity in place
		end
	end
	for i = #G.consumeables.cards, #items + 1, -1 do -- native holds more than the server -> drop the phantom(s)
		local c = G.consumeables.cards[i]
		pcall(function() G.consumeables:remove_card(c); if c.remove then c:remove() end end)
	end
	log.debug("consumable row reconciled: " .. #G.consumeables.cards .. " of server " .. #items)
end

-- Reconcile Balatro's native shop to the server's shop (same move as the deal): let the native shop
-- build, then swap each main-slot card to the server's item (center + price + index), drop any extras,
-- and snap money. Re-run after a buy to re-index the remaining (shifted) slots.
local function reconcile_shop_to_server()
	-- Gate on the REAL shop (native G.STATE==SHOP via stage()), NOT the server phase: the server flips to
	-- SHOP the instant the blind is beaten, while Balatro is still on the ROUND_EVAL cash-out screen. Reading
	-- the server phase here is what made the bridge "assume shop before cash out". stage()=="SHOP" is true
	-- only AFTER Cash Out.
	if not (ENGAGED and stage() == "SHOP" and VIEW and VIEW.shop and G.shop_jokers and G.shop_jokers.cards) then return end
	local items, cards = VIEW.shop, G.shop_jokers.cards
	local n = math.min(#cards, #items)
	for i = 1, n do
		set_shop_card_identity(cards[i], items[i], i - 1) -- server index is 0-based
		-- Disable native Buy&Use (we don't drive the immediate-use path yet): buy then use separately.
		pcall(function()
			local c = cards[i]
			if c.children and c.children.buy_and_use_button then
				c.children.buy_and_use_button:remove(); c.children.buy_and_use_button = nil
			end
		end)
	end
	for i = #cards, #items + 1, -1 do -- native offered more slots than the server: remove the extras
		local c = cards[i]
		pcall(function() G.shop_jokers:remove_card(c); if c.remove then c:remove() end end)
	end
	log.debug("shop reconcile: showing " .. n .. " server item(s) of " .. #items .. " offered")

	-- Vouchers: swap each native voucher card to the server's offered voucher (key + cost + index), and
	-- remove any native voucher the server doesn't offer (untagged -> would redeem natively = desync).
	if G.shop_vouchers and G.shop_vouchers.cards then
		local vc, vitems = G.shop_vouchers.cards, (VIEW.vouchers or {})
		local vn = math.min(#vc, #vitems)
		for i = 1, vn do
			local it = vitems[i]
			if it and it.key and G.P_CENTERS and G.P_CENTERS[it.key] and vc[i].set_ability then
				pcall(function() vc[i]:set_ability(G.P_CENTERS[it.key], true) end)
				vc[i].bbridge_voucher_index = i - 1
				if it.cost then vc[i].cost = it.cost end
			end
		end
		for i = #vc, #vitems + 1, -1 do
			local c = vc[i]
			pcall(function() G.shop_vouchers:remove_card(c); if c.remove then c:remove() end end)
		end
	end

	-- Booster packs: swap each native booster to the server's pack (center + cost + index); remove extras.
	if G.shop_booster and G.shop_booster.cards then
		local bc, bitems = G.shop_booster.cards, (VIEW.packs or {})
		local bn = math.min(#bc, #bitems)
		for i = 1, bn do
			local ckey = pack_center_key(bitems[i])
			if ckey and G.P_CENTERS and G.P_CENTERS[ckey] and bc[i].set_ability then
				local pos = bc[i].ability and bc[i].ability.booster_pos -- preserve native bookkeeping field
				pcall(function() bc[i]:set_ability(G.P_CENTERS[ckey], true) end)
				if pos and bc[i].ability then bc[i].ability.booster_pos = pos end
				bc[i].bbridge_pack_index = i - 1
				if bitems[i].cost then bc[i].cost = bitems[i].cost end
			end
		end
		for i = #bc, #bitems + 1, -1 do
			local c = bc[i]
			pcall(function() G.shop_booster:remove_card(c); if c.remove then c:remove() end end)
		end
	end

	-- Render the joker row from the server (identity + edition). Native's buy_from_shop does NOT reliably
	-- emplace bought jokers here (observed native row=0 while server=1 -> you pay but see no joker), so the
	-- server view is authoritative for the row. Shared with the mid-blind consumable path.
	reconcile_jokers_to_server()
	-- And the held consumable row (drops a phantom from a mis-routed joker buy; keeps it server-authoritative).
	reconcile_consumables_to_server()
	-- Reroll-button cost = the server's (native increments its own on reroll, which can drift).
	pcall(function()
		if VIEW.rerollCost and G.GAME and G.GAME.current_round then
			G.GAME.current_round.reroll_cost = VIEW.rerollCost
		end
	end)
	-- DEV: the full shop the server resolved (cards / vouchers / packs / joker row), vs the native slot count.
	if DEV then
		local function lbls(list, kf) local t = {}; for _, it in ipairs(list or {}) do t[#t + 1] = kf(it) end; return table.concat(t, ", ") end
		local kc = function(it) return tostring(it.key or "?") .. (it.cost and ("$" .. it.cost) or "") end
		log.dev("SHOP", "cards=[" .. lbls(VIEW.shop, kc) .. "]  vouchers=[" .. lbls(VIEW.vouchers, kc) ..
			"]  packs=[" .. lbls(VIEW.packs, function(p) return pack_center_key(p) or "?" end) ..
			"]  jokers=[" .. lbls(VIEW.jokers, function(j) return tostring(j.key or "?") end) .. "]")
	end
	snap_money()
end

-- Reconcile only AFTER the native shop has actually settled to the server's slot count, since native
-- populates inside a slide-in-gated deferred chain and reroll clears-then-repopulates at the SAME count.
-- A fixed delay races both. Poll until #shop_jokers == #VIEW.shop with NO card still flagged stale
-- (reroll marks the outgoing cards), then swap. Times out after ~4s and reconciles whatever is there.
local function schedule_shop_reconcile()
	if not (ENGAGED and VIEW and VIEW.shop and G and G.E_MANAGER and Event) then return end
	local target = #VIEW.shop
	local tries = 0
	G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.1, blocking = false, func = function()
		tries = tries + 1
		if not (G.STATES and G.STATE == G.STATES.SHOP) then return true end -- left the shop -> abort
		local cards = G.shop_jokers and G.shop_jokers.cards
		local have = cards and #cards or 0
		local stale = false
		if cards then for _, c in ipairs(cards) do if c.bbridge_stale then stale = true break end end end
		if (have == target and not stale) or tries > 240 then
			pcall(reconcile_shop_to_server)
			return true
		end
		return false -- keep polling each frame until the shop settles
	end }))
end

-- Swap the native pack choices (G.pack_cards) to the server's revealed items: set_base for playing cards,
-- set_ability for jokers/consumables, and tag each with its server pack-item index for the pick.
local function reconcile_pack_contents()
	if not (ENGAGED and VIEW and VIEW.openPack and G.pack_cards and G.pack_cards.cards) then return end
	local items, cards = VIEW.openPack.items, G.pack_cards.cards
	local n = math.min(#cards, #items)
	for i = 1, n do
		local it, card = items[i], cards[i]
		if it.type == "CARD" then
			local key = card_key({ rank = it.rank, suit = it.suit })
			if key and G.P_CARDS and G.P_CARDS[key] and card.set_base then
				pcall(function() card:set_base(G.P_CARDS[key]) end)
			end
		elseif it.key and G.P_CENTERS and G.P_CENTERS[it.key] and card.set_ability then
			pcall(function() card:set_ability(G.P_CENTERS[it.key], true) end)
			-- Force the routing flag from the server type (same class as the shop-buy mis-route): a picked
			-- JOKER must land in G.jokers, a CONSUMABLE in G.consumeables. (CARD items are set_base'd above.)
			pcall(function() if card.ability then card.ability.consumeable = (it.type == "CONSUMABLE") or nil end end)
		end
		card.bbridge_pack_item_index = i - 1
	end
	logln("pack reconcile: " .. n .. " of " .. #items .. " revealed item(s) shown")
end

-- The pack choices materialize ~2-3s after open (and one is removed per pick). Poll until G.pack_cards
-- matches the server's revealed count, then reconcile identities + re-index. Aborts if the pack closed.
local function schedule_pack_reconcile()
	if not (ENGAGED and VIEW and VIEW.openPack and G.E_MANAGER and Event) then return end
	local target = #VIEW.openPack.items
	local tries = 0
	G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.2, blocking = false, func = function()
		tries = tries + 1
		if not (VIEW and VIEW.openPack) then return true end -- pack closed -> nothing to do
		local cards = G.pack_cards and G.pack_cards.cards
		local have = cards and #cards or 0
		if (have == target and target > 0) or tries > 400 then
			pcall(reconcile_pack_contents)
			return true
		end
		return false
	end }))
end

-- Send the played indices to the server; return {score, chips, mult, accepted, rejection, handsLeft, hand}.
-- The final count-up totals are the LAST replay entry's running totals (read structurally now, not a
-- "take the last runningChips the regex sees" scrape).
local function server_play(indices)
	local resp = send_intent("playHand", indices)
	local d = decode(resp)
	if not d then return nil end
	local v = wire.view_of(d)
	local chips, mult
	if type(d.replay) == "table" and #d.replay > 0 then
		local last = d.replay[#d.replay]
		chips, mult = last.runningChips, last.runningMult
	end
	return {
		score = v and v.roundScore,
		chips = chips, mult = mult,
		accepted = d.accepted == true,
		rejection = d.rejection,
		handsLeft = v and v.handsLeft,
		hand = (v and v.hand) or {},
		view = v,
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

-- THE unified authoritative renderer (drive-it): after ANY server response, mirror the server VIEW to native
-- so nothing is ever "forgotten to reconcile". Caches VIEW, surfaces joker-trigger events, and drives the
-- always-available rows -- owned JOKERS and held CONSUMABLES -- both idempotent (touch only what changed) and
-- self-guarded (no-op outside their context). The HAND and the SHOP-main cards have their own TIMED paths
-- (deferred draw / poll-gated materialize) and MONEY has cash-out timing, so those are driven by their own
-- hooks; apply_view is the single sync point every action funnels through for the rest.
-- Deferred, READ-ONLY divergence detector: after native settles, assert the native rows match the server
-- VIEW and log LOUD on a mismatch. Turns a silent desync (the "everything got fucked" class) into a visible
-- signal at the seam, instead of a mystery three actions later. Never mutates -> cannot soft-lock.
local _last_divergence = "" -- dedupe: only log when the mismatch set CHANGES (the frame-drain runs often)
local function divergence_check(view)
	pcall(function()
		if not (ENGAGED and view) then return end
		local msgs = {}
		if view.jokers and G.jokers and G.jokers.cards and #G.jokers.cards ~= #view.jokers then
			msgs[#msgs + 1] = "jokers native=" .. #G.jokers.cards .. " server=" .. #view.jokers
		end
		if G.consumeables and G.consumeables.cards and #G.consumeables.cards ~= #(view.consumables or {}) then
			msgs[#msgs + 1] = "consumables native=" .. #G.consumeables.cards .. " server=" .. #(view.consumables or {})
		end
		-- Hand: only while actively PLAYING, counting only cards NOT mid-dissolve (a destroyed card lingers
		-- during its animation). Skips DRAW/scoring states where the hand is legitimately in flux.
		if stage() == "PLAYING" and view.hand and G.hand and G.hand.cards then
			local n = 0
			for _, c in ipairs(G.hand.cards) do if not c.bbridge_dissolving then n = n + 1 end end
			if n ~= #view.hand then msgs[#msgs + 1] = "hand native=" .. n .. " server=" .. #view.hand end
		end
		local joined = table.concat(msgs, "; ")
		if joined ~= "" and joined ~= _last_divergence then log.warn("DIVERGENCE " .. joined) end
		_last_divergence = joined -- clearing (joined=="") silently resets, so the next occurrence logs again
	end)
end

local function apply_view(view)
	if not (ENGAGED and view) then return end
	VIEW = view
	-- (events are logged by send_action/reconcile, which fetch the view -> not re-logged here to avoid dupes)
	pcall(reconcile_jokers_to_server)
	pcall(reconcile_consumables_to_server)
	-- Verify the sync landed (deferred so native's own emplace/dissolve settles first).
	if G.E_MANAGER and Event then
		G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.5, blocking = false,
			func = function() divergence_check(view); return true end }))
	end
end

-- Install the three hooks (called once at launch).
local function install_hooks()
	if not (G and G.FUNCS) then return false end

	-- 0) start_run (the New Run "Play", deck/stake chosen): OPEN the server run NOW, so it's live at
	-- BLIND_SELECT before the first blind. This is the correct engage point -- lazily engaging at the first
	-- select_blind left the server behind (fresh run at Small=300, first-blind skip did nothing). Native
	-- Game:start_run initializes G.GAME (deck/stake/seed + first blind select); we then newRun with them.
	if Game and Game.start_run then
		local _start = Game.start_run
		function Game:start_run(args)
			local ret = _start(self, args) -- native builds the run (deck/stake/seed, first blind select)
			pcall(function()
				if CONN then pcall(function() CONN:close() end) end -- drop any prior run
				CONN, SERVER_HAND, DRAW_QUEUE, ENGAGED, VIEW, RUN_LIVE = nil, {}, {}, false, nil, false
				local s, v = open_server_run()
				if s then
					CONN, VIEW, RUN_LIVE = s, v, true
					logln("RUN STARTED: server run open at blind-select (deck/stake forwarded).")
					log.dev("BLIND", "run start: server phase=" .. tostring(v and v.phase) ..
						" requirement=" .. tostring(v and v.requirement))
				else
					logln("run start: server UNREACHABLE (" .. tostring(v) .. ") -> NOT server-driven.")
					popup("NOT CONNECTED — competitive server unreachable", G.C.RED)
				end
			end)
			return ret
		end
	end

	-- 1) select_blind: drive the SAME server run across blinds. If a run is live and we just proceed()ed
	-- out of the shop (server phase BLIND_SELECT), CONTINUE it (selectBlind) so jokers/economy persist.
	-- Otherwise (no run / run over / fresh Balatro run) open a new authoritative run. Either way, queue
	-- the dealt hand for the deal.
	local _sel = G.FUNCS.select_blind
	G.FUNCS.select_blind = function(e)
		pcall(function()
			local handled = false
			-- Continue the SAME run across blinds; NEVER nuke a live run. Re-opening restarts the server at the
			-- Small blind (300) -- which, when select_blind re-fires mid-blind, is exactly the "450 then back to
			-- 300" bug. A fresh run is opened ONLY when there is no live run, or one the server has dropped.
			if RUN_LIVE and CONN and VIEW then
				log.dev("BLIND", "select_blind: live run, server phase=" .. tostring(VIEW.phase))
				-- Recover a MISSED "Next Round": if the server is still in the shop, proceed to the next blind.
				if VIEW.phase == "SHOP" then
					local p = send_action("proceed")
					if p and p.view then
						VIEW = p.view
						logln("recover: server was in SHOP at blind-select -> proceed -> phase " .. tostring(p.view.phase))
					end
				end
				if VIEW and VIEW.phase == "BLIND_SELECT" then
					local r = send_action("selectBlind")
					if r and r.accepted and r.hand then
						SERVER_HAND, VIEW, DRAW_QUEUE, ENGAGED = r.hand, r.view, {}, true
						for _, sc in ipairs(r.hand) do DRAW_QUEUE[#DRAW_QUEUE + 1] = sc end
						-- Boss blind: face the SERVER's boss. select_blind faces e.config.ref_table (the UI's
						-- baked blind), so swap it to the server's boss center here (key is already bl_xxx). On a
						-- non-boss blind / missing key this is a no-op -> native's own blind (current behavior).
						if r.view and r.view.bossKey and G.P_BLINDS and G.P_BLINDS[r.view.bossKey]
							and e and e.config then
							e.config.ref_table = G.P_BLINDS[r.view.bossKey]
							logln("boss blind -> facing server boss " .. r.view.bossKey)
						end
						logln("CONTINUE: next blind, " .. #r.hand .. "-card hand queued (same run).")
						log.dev("BLIND", "server requirement=" .. tostring(r.view and r.view.requirement) ..
							" phase=" .. tostring(r.view and r.view.phase) ..
							(r.view and r.view.bossKey and (" boss=" .. r.view.bossKey) or "") ..
							(r.view and r.view.offeredTag and (" skip-tag=" .. tostring(r.view.offeredTag)) or ""))
						handled = true
					else
						-- Stale RUN_LIVE (quit-to-menu, server dropped the run): fall through to a fresh run.
						logln("selectBlind continue failed -> " .. tostring(r and r.rejection) .. " — opening a fresh run")
					end
				else
					-- A live run NOT at a select point: native re-fired select_blind while the blind was already
					-- active (or a transient phase). KEEP it -- re-opening here is exactly the 450->300 bug.
					log.dev("BLIND", "live run already active (phase=" .. tostring(VIEW and VIEW.phase) .. ") -> keeping it")
					handled = true
				end
			end
			if not handled then
				if CONN then pcall(function() CONN:close() end) end
				CONN, SERVER_HAND, DRAW_QUEUE, ENGAGED, VIEW, RUN_LIVE = nil, {}, {}, false, nil, false
				local s, hand, iview = open_run()
				if s then
					CONN, SERVER_HAND, ENGAGED, VIEW, RUN_LIVE = s, hand, true, iview, true
					for _, sc in ipairs(hand) do DRAW_QUEUE[#DRAW_QUEUE + 1] = sc end -- whole hand for the initial deal
					logln("ENGAGED: server run open, " .. #hand .. "-card hand queued.")
					log.dev("BLIND", "server requirement=" .. tostring(iview and iview.requirement) ..
						" phase=" .. tostring(iview and iview.phase))
				else
					-- NEVER silently degrade to vanilla: a competitive run that isn't server-driven is a
					-- trap. Make it impossible to miss that this blind is NOT connected to the server.
					logln("select_blind: server UNREACHABLE (" .. tostring(hand) .. ") -> NOT server-driven.")
					popup("NOT CONNECTED — competitive server unreachable", G.C.RED)
				end
			end
		end)
		return _sel(e)
	end

	-- 2) draw_from_deck_to_hand: let Balatro draw from its OWN deck (perfect count, native animation, no
	-- deck-fighting), but (a) PRIME the about-to-be-drawn deck cards to the server's faces so they fly in
	-- correct (smooth), and (b) after the draw settles, RECONCILE the hand to the server to guarantee
	-- correctness — catching anything the prime missed. We never manipulate the deck pile, so its count is
	-- always Balatro's own and stays correct during and after the blind.
	local _draw = G.FUNCS.draw_from_deck_to_hand
	G.FUNCS.draw_from_deck_to_hand = function(e)
		if ENGAGED then pcall(prime_deck_for_draw) end
		local r = _draw(e) -- Balatro draws its own deck cards: count decrements natively, animation native
		if ENGAGED and G.E_MANAGER and Event then
			G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.5, blocking = false,
				func = function() pcall(render_hand); return true end }))
		end
		return r
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
					logln("discard -> server applied (" .. #idx .. " cards), " .. #DRAW_QUEUE .. " new queued"); reconcile(parse_view(resp))
				elseif resp then
					logln("discard REJECTED: " .. tostring(resp:match('"rejection":"([^"]+)"')))
					popup("DISCARD REJECTED: " .. tostring(resp:match('"rejection":"([^"]+)"')), G.C.RED)
				end
			end)
		end
		return _disc(e, hook)
	end

	-- 4) update_hand_played: drive the win/lose decision from the SERVER by giving NATIVE the server's
	-- numbers, then delegating. Native decides NEW_ROUND (chips>=blind.chips OR hands_left<1) vs
	-- DRAW_TO_HAND from exactly those three values (game.lua:3196), then runs end_round -> ROUND_EVAL ->
	-- cash_out -> SHOP (win) or GAME_OVER (loss) -- so we get the native cash-out + shop FOR FREE instead
	-- of porting them. We force G.GAME.chips because the local count-up diverges once jokers are server-
	-- owned. Pure value-forcing + native delegate => no soft-lock surface of our own.
	local _uhp = Game.update_hand_played
	function Game:update_hand_played(dt)
		if not (ENGAGED and VIEW and G and G.GAME) then return _uhp(self, dt) end
		-- Force the server's numbers ONLY at the decision frame, not every frame. During the score count-up
		-- STATE_COMPLETE is true (set in play_cards_from_highlighted, cleared only AFTER evaluate_play); the
		-- native count-up EASES G.GAME.chips up over ~0.5s (state_events.lua:1044). Forcing chips every frame
		-- overwrote that ease's target, snapping the round score to final before the hand animated (the
		-- early-jump bug). Gating on `not STATE_COMPLETE` lets the native ease play, then corrects any
		-- divergence + sets the blind/hands so native's win/lose decision matches the server.
		if not G.STATE_COMPLETE then
			local v = VIEW
			pcall(function()
				-- Do NOT force G.GAME.chips here. The ease-retarget (hook 7) already drives the NATIVE
				-- count-up animation to the server's roundScore, and native reads the eased chips for its
				-- win/lose decision. Forcing it here fired BEFORE the count-up ran (Balatro queues the
				-- score eases after this state-complete event), snapping the round score to final the instant
				-- the hand was played -- the jump the user saw. Just set the blind/hands for the decision.
				if v.requirement and G.GAME.blind then G.GAME.blind.chips = v.requirement end
				if G.GAME.current_round and v.handsLeft then G.GAME.current_round.hands_left = v.handsLeft end
			end)
			if v.phase == "SHOP" then SHOP_DONE = false end -- a shop visit is coming -> reconcile it once
			if v.phase == "RUN_LOST" or v.phase == "RUN_WON" then RUN_LIVE = false end -- run over -> next fresh
			log.debug("round decision (server): phase=" .. tostring(v.phase) .. " chips=" .. tostring(v.roundScore) ..
				" req=" .. tostring(v.requirement) .. " handsLeft=" .. tostring(v.handsLeft))
		end
		return _uhp(self, dt)
	end

	-- 5) update_shop: native builds + populates the shop; we then reconcile it to the server's items.
	-- Set joker_max first so native emplaces the right number of slots, then defer the swap until the
	-- native cards have materialized.
	local _ushop = Game.update_shop
	function Game:update_shop(dt)
		if ENGAGED and VIEW and VIEW.shop and #VIEW.shop > 0 and G.GAME and G.GAME.shop and not SHOP_DONE then
			-- Match native's main-slot count to the server's exactly, so the poll settles fast.
			pcall(function() G.GAME.shop.joker_max = #VIEW.shop end)
			-- OWN the whole shop: if the native shop isn't built yet this visit (not G.shop), hand native our
			-- exact cards to LOAD for every area (main slots, vouchers, packs) instead of RNG-generating them.
			-- Native consumes each G.load_shop_* on build. Bail-safe per area: nil -> native generates that area
			-- + our reconcile handles it, so this can never make the shop worse.
			if not G.shop then
				if not G.load_shop_jokers then G.load_shop_jokers = build_shop_load_jokers() end
				if not G.load_shop_vouchers then G.load_shop_vouchers = build_shop_load_vouchers() end
				if not G.load_shop_booster then G.load_shop_booster = build_shop_load_booster() end
				if G.load_shop_jokers then logln("shop: owning main slots via native load (" .. #VIEW.shop .. " items)") end
			end
		end
		local r = _ushop(self, dt)
		if ENGAGED and stage() == "SHOP" and VIEW and #VIEW.shop > 0 and not SHOP_DONE then
			SHOP_DONE = true -- reconcile exactly once per visit (the poll waits for the cards to settle)
			schedule_shop_reconcile()
		end
		return r
	end

	-- 5b) buy_from_shop: the bought card (e.config.ref_table) carries its server shop index. Tell the
	-- server first; on accept let native fly it into G.jokers/G.consumeables, then re-reconcile (re-index
	-- the shifted slots) and snap money. On reject, popup and block the native buy.
	local _buy = G.FUNCS.buy_from_shop
	G.FUNCS.buy_from_shop = function(e)
		if ENGAGED and CONN and e and e.config and e.config.ref_table and e.config.ref_table.bbridge_shop_index ~= nil then
			local idx = e.config.ref_table.bbridge_shop_index
			local r = send_action("buyShopItem", ',"index":' .. idx)
			if not (r and r.accepted) then
				logln("buy REJECTED slot " .. idx .. ": " .. tostring(r and r.rejection))
				popup("CAN'T BUY: " .. tostring((r and r.rejection) or "server error"), G.C.RED)
				return -- block native buy: the server said no
			end
			apply_view(r.view) -- sync jokers + consumables so the bought card lands where the SERVER has it
			logln("buy -> server bought slot " .. idx .. " (money=" .. tostring(r.view and r.view.money) .. ")")
			local res = _buy(e) -- native: card flies to jokers/consumeables, money eases
			schedule_shop_reconcile() -- waits for native to remove the bought card, then re-indexes the rest
			return res
		end
		return _buy(e)
	end

	-- 5c) reroll_shop: tell the server, let native clear+repopulate, then re-reconcile to the new items.
	local _reroll = G.FUNCS.reroll_shop
	G.FUNCS.reroll_shop = function(e)
		if ENGAGED and CONN then
			local r = send_action("reroll")
			if not (r and r.accepted) then
				logln("reroll REJECTED: " .. tostring(r and r.rejection))
				popup("CAN'T REROLL: " .. tostring((r and r.rejection) or "server error"), G.C.RED)
				return
			end
			apply_view(r.view) -- sync jokers/consumables + surface any reroll-triggered events
			-- Mark the outgoing cards stale so the poll waits for native to clear+repopulate before swapping
			-- (reroll keeps the same slot count, so a count-only check would fire on the stale cards).
			pcall(function()
				if G.shop_jokers and G.shop_jokers.cards then
					for _, c in ipairs(G.shop_jokers.cards) do c.bbridge_stale = true end
				end
			end)
			local res = _reroll(e)
			schedule_shop_reconcile()
			return res
		end
		return _reroll(e)
	end

	-- 5d) toggle_shop (the "Next Round" button): leave the shop on the server (proceed -> next blind),
	-- so the run advances server-side; native then goes to BLIND_SELECT and select_blind continues it.
	local _toggle = G.FUNCS.toggle_shop
	G.FUNCS.toggle_shop = function(e)
		if ENGAGED and CONN then
			local r = send_action("proceed")
			if r then
				VIEW = r.view or VIEW
				logln("next round -> server proceed (phase=" .. tostring(r.view and r.view.phase) .. ")")
			end
		end
		return _toggle(e)
	end

	-- 5e) Card:redeem (voucher): the shop voucher carries bbridge_voucher_index. Tell the server, then let
	-- native apply the voucher (rendering) + snap money. Hook the METHOD (the FUNCS button is engine-injected).
	local _redeem = Card.redeem
	function Card:redeem()
		if ENGAGED and CONN and self.bbridge_voucher_index ~= nil then
			local r = send_action("buyVoucher", ',"index":' .. self.bbridge_voucher_index)
			if not (r and r.accepted) then
				logln("voucher REJECTED: " .. tostring(r and r.rejection))
				popup("CAN'T BUY VOUCHER: " .. tostring((r and r.rejection) or "?"), G.C.RED)
				return -- block native redeem: the server said no
			end
			apply_view(r.view) -- a voucher can change jokers/consumables/slots -> resync
			self.bbridge_voucher_index = nil
			logln("voucher -> server redeemed (money=" .. tostring(r.view and r.view.money) .. ")")
			local res = _redeem(self)
			if G.E_MANAGER and Event then
				G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.3, blocking = false,
					func = function() snap_money(); return true end }))
			end
			return res
		end
		return _redeem(self)
	end

	-- 5f) sell_card: map the sold JOKER to its server row index (orders stay aligned: jokers appended on
	-- buy, both sides remove the same index on sell). Consumable sells aren't routed yet -> blocked.
	local _sell = G.FUNCS.sell_card
	G.FUNCS.sell_card = function(e)
		if ENGAGED and CONN and e and e.config and e.config.ref_table then
			local card = e.config.ref_table
			if card.area == G.consumeables then
				popup("SELL CONSUMABLES: not supported yet", G.C.RED)
				return -- block (would desync: server has no sell-consumable intent)
			end
			if card.area == G.jokers and card.ability and card.ability.set == "Joker" then
				local idx
				for i, c in ipairs(G.jokers.cards) do if c == card then idx = i - 1; break end end
				if idx ~= nil then
					local r = send_action("sellJoker", ',"index":' .. idx)
					if not (r and r.accepted) then
						logln("sell REJECTED idx " .. idx .. ": " .. tostring(r and r.rejection))
						popup("CAN'T SELL: " .. tostring((r and r.rejection) or "?"), G.C.RED)
						return -- block native sell (e.g. eternal)
					end
					apply_view(r.view) -- resync the joker/consumable rows to the post-sell server state
					logln("sell -> server sold joker " .. idx)
					local res = _sell(e)
					if G.E_MANAGER and Event then
						G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.3, blocking = false,
							func = function() snap_money(); return true end }))
					end
					return res
				end
			end
		end
		return _sell(e)
	end

	-- 5g) skip_blind: keep the server in lockstep (skipBlind advances the blind + deals next hand, leaving
	-- phase BLIND_SELECT). Native then shows the next blind; select_blind continues it.
	local _skip = G.FUNCS.skip_blind
	G.FUNCS.skip_blind = function(e)
		pcall(function()
			-- The run is opened at run start (start_run hook), so normally it's already live here. Fallback:
			-- if somehow no run is live, open one (newRun -> BLIND_SELECT, exactly where we're skipping from)
			-- before skipping -- otherwise skipBlind never reaches the server and the next select opens a fresh
			-- Small run (the "Big blind shows 300 after skipping" bug).
			if not (RUN_LIVE and CONN) then
				local s, v = open_server_run()
				if s then
					CONN, VIEW, RUN_LIVE = s, v, true
					logln("skip: opened server run (fallback) -> phase " .. tostring(v and v.phase))
				else
					logln("skip: server UNREACHABLE -> cannot engage")
					popup("NOT CONNECTED — competitive server unreachable", G.C.RED)
					return
				end
			end
			if RUN_LIVE and CONN then
				local r = send_action("skipBlind")
				if r and r.accepted then
					VIEW = r.view or VIEW
					-- The skip advanced the blind + dealt the next hand server-side; refresh the queue so a
					-- deal uses the NEW blind's cards (select_blind continue re-queues too, but be safe).
					if r.hand then
						SERVER_HAND, DRAW_QUEUE = r.hand, {}
						for _, sc in ipairs(r.hand) do DRAW_QUEUE[#DRAW_QUEUE + 1] = sc end
					end
					logln("skip -> server skipBlind (phase=" .. tostring(r.view and r.view.phase) ..
						" req=" .. tostring(r.view and r.view.requirement) .. ")")
				else
					logln("skip REJECTED: " .. tostring(r and r.rejection) .. " (boss/pvp can't skip)")
				end
			end
		end)
		return _skip(e)
	end

	-- 5h) use_card (consumable): a held consumable (G.consumeables) -> useConsumable(index, targets). Index
	-- by center key against VIEW.consumables; targets = highlighted hand cards' server uids (Tarots; planets
	-- ignore them). Server applies the effect; the next draw reconcile re-stamps any card identities it changed.
	local _use = G.FUNCS.use_card
	G.FUNCS.use_card = function(e, mute, nosave)
		if ENGAGED and CONN and e and e.config and e.config.ref_table then
			local card = e.config.ref_table
			-- 5i) pack pick: a card chosen from an open booster (use_card is the universal pick path for
			-- jokers/playing cards/consumables). Server pickPackItem STORES the item; native USES consumables
			-- immediately, so for a consumable we chain useConsumable to match. Targets = highlighted hand uids.
			if card.area == G.pack_cards and card.bbridge_pack_item_index ~= nil then
				local idx = card.bbridge_pack_item_index
				local consumable = card.ability and card.ability.consumeable
				local targets = {}
				if G.hand and G.hand.highlighted then
					for _, hc in ipairs(G.hand.highlighted) do
						if hc.bbridge_uid then targets[#targets + 1] = hc.bbridge_uid end
					end
				end
				local r = send_action("pickPackItem", ',"index":' .. idx)
				if not (r and r.accepted) then
					logln("pickPackItem REJECTED idx " .. idx .. ": " .. tostring(r and r.rejection))
					popup("CAN'T PICK: " .. tostring((r and r.rejection) or "?"), G.C.RED)
					return -- block native pick
				end
				apply_view(r.view) -- a picked joker/card lands in the owned row per the server
				if consumable and VIEW.consumables and #VIEW.consumables > 0 then
					local cidx = #VIEW.consumables - 1 -- the just-stored consumable (appended last)
					local r2 = send_action("useConsumable",
						',"index":' .. cidx .. ',"targets":[' .. table.concat(targets, ",") .. "]")
					apply_view(r2 and r2.view) -- the chained use may change jokers/consumables too
					logln("pack pick(consumable) -> picked+used idx " .. idx .. " targets=" .. #targets)
				else
					logln("pack pick -> picked idx " .. idx)
				end
				schedule_pack_reconcile() -- re-index remaining choices (multi-pick) once native removes one
				return _use(e, mute, nosave)
			end
			if card.area == G.consumeables and card.ability and card.ability.consumeable then
				local key = card.config and card.config.center_key
				local idx
				if key and VIEW and VIEW.consumables then
					for i, it in ipairs(VIEW.consumables) do if it.key == key then idx = i - 1; break end end
				end
				if idx ~= nil then
					local targets = {}
					if G.hand and G.hand.highlighted then
						for _, hc in ipairs(G.hand.highlighted) do
							if hc.bbridge_uid then targets[#targets + 1] = hc.bbridge_uid end
						end
					end
					local r = send_action("useConsumable",
						',"index":' .. idx .. ',"targets":[' .. table.concat(targets, ",") .. "]")
					if not (r and r.accepted) then
						logln("useConsumable REJECTED idx " .. idx .. ": " .. tostring(r and r.rejection))
						popup("CAN'T USE: " .. tostring((r and r.rejection) or "?"), G.C.RED)
						return -- block native use
					end
					VIEW = r.view or VIEW
					logln("use -> server consumable " .. idx .. " targets=" .. #targets)
					-- DEV: which consumable, the cards you selected (native identity), and that the server applied
					-- it (the next draw reconcile re-stamps any identities the effect changed).
					if DEV then
						local sel = {}
						if G.hand and G.hand.highlighted then
							for _, hc in ipairs(G.hand.highlighted) do
								local b = hc.base or {}
								sel[#sel + 1] = tostring(b.value or "?") .. (SUIT_CH[b.suit] or tostring(b.suit or ""))
							end
						end
						log.dev("USE", "consumable '" .. tostring(key) .. "' (idx " .. idx .. ") on [" ..
							table.concat(sel, " ") .. "]; server applied -> " .. ((stage() == "PLAYING")
								and "reconciling hand (destroyed dissolve, mutated restamp) + jokers"
								or "in shop: reconciling jokers + consumables (no hand to touch)"))
					end
					-- A consumable can change the JOKERS (Hex polychrome + destroy others, Ankh copy, Ectoplasm)
					-- AND the HAND (Immolate destroys cards, a Tarot mutates rank/suit). Re-render all of them now;
					-- the HAND is DEFERRED so any native use animation settles before destroyed cards dissolve.
					apply_view(VIEW) -- resync jokers + consumables (Hex polychrome/destroy, Ankh, Ectoplasm)
					-- Hand effects only matter while actually PLAYING a blind (no hand in the shop / cash-out).
					-- Updating SERVER_HAND or reconciling the hand outside a blind would stamp a stale/empty hand
					-- and corrupt the next deal (the "deck all wrong next draw" + the spurious '8 unplaced' warning).
					if stage() == "PLAYING" then
						SERVER_HAND = (r.view and r.view.hand) or r.hand or SERVER_HAND
						if G.E_MANAGER and Event then
							G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.25, blocking = false,
								func = function() pcall(render_hand); return true end }))
						else
							pcall(render_hand)
						end
					end
				elseif key then
					-- A card sits in the consumable slot that the SERVER does not list as a consumable -- e.g. a
					-- JOKER native mis-routed here. Do NOT let native "use" it: there's no server consumable to
					-- apply, and letting native resolve it locally desyncs (the "used a joker to level a planet"
					-- bug). Block it and reconcile both rows so the stray card returns to where the server has it.
					log.warn("use blocked: '" .. tostring(key) .. "' is in the consumable slot but the server lists no such consumable (mis-routed card?) -- reconciling rows")
					popup("MIS-PLACED CARD — reconciling", G.C.RED)
					pcall(reconcile_consumables_to_server)
					pcall(reconcile_jokers_to_server)
					return -- block native use of an unrecognized consumable-slot card
				end
			end
		end
		return _use(e, mute, nosave)
	end

	-- 5j) Card:open (booster): the shop pack carries bbridge_pack_index. Tell the server, let native open
	-- (creates the pack UI + choices), then reconcile the choices to the server's revealed items. Hook the
	-- METHOD (the open_booster FUNCS button is engine-injected).
	local _open = Card.open
	function Card:open()
		if ENGAGED and CONN and self.bbridge_pack_index ~= nil then
			local r = send_action("openPack", ',"index":' .. self.bbridge_pack_index)
			if not (r and r.accepted) then
				logln("openPack REJECTED: " .. tostring(r and r.rejection))
				popup("CAN'T OPEN: " .. tostring((r and r.rejection) or "?"), G.C.RED)
				return -- block native open
			end
			apply_view(r.view) -- opening a pack keeps VIEW authoritative (rows unchanged, but stay in sync)
			self.bbridge_pack_index = nil
			logln("openPack -> server (items=" .. tostring(VIEW.openPack and #VIEW.openPack.items) .. ")")
			local res = _open(self)
			schedule_pack_reconcile()
			if G.E_MANAGER and Event then
				G.E_MANAGER:add_event(Event({ trigger = "after", delay = 0.3, blocking = false,
					func = function() snap_money(); return true end }))
			end
			return res
		end
		return _open(self)
	end

	-- 5k) skip_booster (skip the rest of an open pack): close it on the server too.
	local _skipb = G.FUNCS.skip_booster
	G.FUNCS.skip_booster = function(e)
		if ENGAGED and CONN and VIEW and VIEW.openPack then
			local r = send_action("skipPack")
			if r then apply_view(r.view); logln("skip pack -> server skipPack") end
		end
		return _skipb(e)
	end

	-- 5l) cash_out: drive the round cash-out amount from the SERVER (reward + interest), since money is
	-- added when the player clicks Cash Out. Set G.GAME.current_round.dollars before native eases it; with
	-- mid-blind money already rendering the server's pre-reward value, the ease lands on view.money. The
	-- shop snap is the exact backstop for any native end_round joker-$ remainder (none on a base run).
	if G.FUNCS.cash_out then
		local _cashout = G.FUNCS.cash_out
		G.FUNCS.cash_out = function(e)
			if ENGAGED and VIEW and G.GAME and G.GAME.current_round and (VIEW.cashReward or VIEW.cashInterest or VIEW.cashHands) then
				local native = G.GAME.current_round.dollars
				-- Total the cash-out from its ITEMIZED parts: blind reward + per-hand/discard money + interest.
				G.GAME.current_round.dollars = (VIEW.cashReward or 0) + (VIEW.cashHands or 0) + (VIEW.cashInterest or 0)
				logln("cash_out -> server total $" .. tostring(G.GAME.current_round.dollars))
				log.dev("CASHOUT", "server reward=$" .. tostring(VIEW.cashReward or 0) .. " + hands=$" ..
					tostring(VIEW.cashHands or 0) .. " + interest=$" .. tostring(VIEW.cashInterest or 0) ..
					" = $" .. tostring(G.GAME.current_round.dollars) ..
					"  (native had $" .. tostring(native) .. ", new bankroll $" .. tostring(VIEW.money) .. ")")
			end
			return _cashout(e)
		end
	end

	-- 3) evaluate_play: map played cards (by uid) to server indices, the server scores them, queue new draws.
	-- 2c) play_cards_from_highlighted: fetch the server's score for this hand NOW, at the Play-button moment,
	-- BEFORE the cards fly up and score. The networking is blocking; doing it here (like discard already
	-- does) keeps the freeze OUT of the scoring animation. If the block happened mid-scoring (as it did in
	-- evaluate_play), the frozen frame made Balatro fast-forward its event queue and skip every per-card
	-- "+chips" animation. evaluate_play then consumes the pre-fetched result without blocking.
	local _play = G.FUNCS.play_cards_from_highlighted
	G.FUNCS.play_cards_from_highlighted = function(e)
		PENDING_PLAY = nil
		if ENGAGED and CONN and G.hand and G.hand.highlighted and G.hand.highlighted[1] then
			pcall(function()
				local idx = {}
				for _, c in ipairs(G.hand.highlighted) do
					local ix = c.bbridge_uid and uid_to_index(c.bbridge_uid)
					if ix then idx[#idx + 1] = ix end
				end
				if #idx == 0 then return end
				if #idx ~= #G.hand.highlighted then
					logln("!! IDENTITY GAP: played " .. #G.hand.highlighted .. " cards, only " .. #idx ..
						" mapped to server (unmapped cards keep Balatro's own face -> score divergence)")
				end
				PENDING_PLAY = server_play(idx) -- blocking, but BEFORE the animation
			end)
		end
		return _play(e)
	end

	-- 3) evaluate_play: consume the pre-fetched server result (no blocking here), apply it, then let native
	-- animate the score uninterrupted.
	local _eval = G.FUNCS.evaluate_play
	G.FUNCS.evaluate_play = function(e)
		if ENGAGED and CONN and G.play and G.play.cards then
			pcall(function()
				local res = PENDING_PLAY
				PENDING_PLAY = nil
				if not res then -- safety net only: the Play hook normally pre-fetched (this re-blocks if not)
					local idx = {}
					for _, c in ipairs(G.play.cards) do
						local ix = c.bbridge_uid and uid_to_index(c.bbridge_uid)
						if ix then idx[#idx + 1] = ix end
					end
					if #idx == 0 then return end
					res = server_play(idx)
				end
				if not res then logln("play: no server response"); return end
				if res.accepted then
					SCORING = true -- arm the ONE round-score ease this play creates (consumed in hook 7)
					SERVER_HAND = (res.handsLeft and res.handsLeft > 0) and res.hand or {}
					recompute_draw_queue(nil) -- played cards already left G.hand -> kept cards are "held"
					local hs = (res.chips or 0) * (res.mult or 0)
					logln(string.format("play -> SERVER %s x %s = %g [round %s] handsLeft=%s new=%d",
						tostring(res.chips), tostring(res.mult), hs, tostring(res.score), tostring(res.handsLeft), #DRAW_QUEUE))
					reconcile(res.view); if res.handsLeft == 0 then logln("server: blind hands exhausted -> server decides win/lose, native renders it") end
				else
					logln("play REJECTED: " .. tostring(res.rejection))
					popup("REJECTED: " .. tostring(res.rejection), G.C.RED)
				end
			end)
		end
		return _eval(e) -- native animates the count-up uninterrupted now (no mid-scoring block)
	end

	-- 6) Tag:apply_to_run -- NEUTER native tag EFFECTS while engaged (the #1 systemic divergence). Native
	-- runs its OWN tag system in parallel: Top-up spawns jokers, Skip/Garbage/Handy ease dollars, Boss/
	-- Orbital reroll the boss -- all autonomous grants the SERVER owns authoritatively (tag.lua:115+).
	-- No-op makes native tags inert (icons still show); every caller reads the return only as
	-- `if apply_to_run() then break end`, so a nil return simply doesn't break and no flow depends on it.
	-- HIGHEST-RISK hook -> kill-switch: set `BBRIDGE_NEUTER_TAGS=false` in the console to restore native
	-- tags if blind-select/shop ever misbehaves. (Server-granted tag effects still apply server-side.)
	if Tag and Tag.apply_to_run then
		local _apply = Tag.apply_to_run
		function Tag:apply_to_run(ctx)
			if ENGAGED and rawget(_G, "BBRIDGE_NEUTER_TAGS") ~= false then return nil end
			return _apply(self, ctx)
		end
	end

	-- 6b) VIEW DECK -- render the SERVER's deck. Native G.UIDEF.view_deck iterates G.playing_cards, which our
	-- identity-override restamps (native's deck != the server's) -> a corrupted, non-standard grid. Instead,
	-- build the server's authoritative deck COMPOSITION (VIEW.deckCards -- sorted, NO draw order, so the
	-- info-hiding invariant holds), temporarily swap it in for G.playing_cards, let native draw the grid +
	-- tallies, then restore the real deck (draw mechanics untouched). Raw Card(...) does NOT auto-register to
	-- G.playing_cards (verified card.lua), so the swap is clean. Degrades to native on any failure.
	if G.UIDEF and G.UIDEF.view_deck then
		local _view_deck = G.UIDEF.view_deck
		local ENH_CENTER = { BONUS = "m_bonus", MULT = "m_mult", WILD = "m_wild", GLASS = "m_glass",
			STEEL = "m_steel", STONE = "m_stone", GOLD = "m_gold", LUCKY = "m_lucky" }
		local SEAL_NAME = { RED = "Red", BLUE = "Blue", GOLD = "Gold", PURPLE = "Purple" }
		G.UIDEF.view_deck = function(unplayed_only)
			if not (ENGAGED and VIEW and VIEW.deckCards and #VIEW.deckCards > 0 and Card and G.P_CARDS and G.deck) then
				return _view_deck(unplayed_only)
			end
			local real, built = G.playing_cards, {}
			local ok_build = pcall(function()
				for _, dc in ipairs(VIEW.deckCards) do
					local key = card_key(dc)
					if key and G.P_CARDS[key] then
						local center = (dc.enhancement and ENH_CENTER[dc.enhancement] and G.P_CENTERS[ENH_CENTER[dc.enhancement]])
							or G.P_CENTERS.c_base
						local c = Card(G.deck.T.x, G.deck.T.y, G.CARD_W, G.CARD_H, G.P_CARDS[key], center,
							{ bypass_discovery_center = true, bypass_discovery_ui = true })
						if dc.edition and dc.edition ~= "NONE" and c.set_edition then
							pcall(function() c:set_edition(edition_table(dc.edition), true, true) end)
						end
						if dc.seal and SEAL_NAME[dc.seal] and c.set_seal then
							pcall(function() c:set_seal(SEAL_NAME[dc.seal], true) end)
						end
						-- Set the area so the "remaining/unplayed" view greys drawn/played cards: native greys any
						-- card whose area ~= G.deck. inDeck (from the server) = still in the draw pile this round.
						c.area = (dc.inDeck ~= false) and G.deck or nil
						built[#built + 1] = c
					end
				end
			end)
			if not (ok_build and #built > 0) then
				for _, c in ipairs(built) do pcall(function() if c.remove then c:remove() end end) end
				return _view_deck(unplayed_only)
			end
			G.playing_cards = built
			local ok_view, res = pcall(_view_deck, unplayed_only)
			G.playing_cards = real -- restore the real deck for the draw mechanics
			for _, c in ipairs(built) do pcall(function() if c.remove then c:remove() end end) end
			if ok_view then return res end
			return _view_deck(unplayed_only)
		end
		logln("deck-view override installed (renders the server's deck composition, not native's)")
	end

	-- 7) RETARGET the round-score count-up to the SERVER's value. Native eases G.GAME.chips toward a
	-- target it computed LOCALLY (state_events.lua:1044); when local scoring diverges (jokers, or the
	-- first hand before identities settle) that target is wrong, and our after-the-fact snap then made
	-- it jump. Instead, intercept the ease as it's queued and rewrite its end value to the server's
	-- roundScore (already stored in VIEW by the play hook). The native animation then COUNTS UP to the
	-- server's number at native pace — no jump, correct for every hand. (User's idea: compute early,
	-- store it, let the game render it later.)
	if EventManager and EventManager.add_event then
		local _add = EventManager.add_event
		function EventManager:add_event(event, queue, front)
			if SCORING and ENGAGED and VIEW and VIEW.roundScore and event and event.trigger == "ease"
				and event.ease and event.ease.ref_table == G.GAME and event.ease.ref_value == "chips" then
				log.debug("chips ease retargeted: native end_val=" .. tostring(event.ease.end_val) ..
					" -> server roundScore=" .. tostring(VIEW.roundScore))
				event.ease.end_val = VIEW.roundScore
				SCORING = false -- one-shot: do NOT touch Balatro's end-of-round reset-to-0 ease
			end
			return _add(self, event, queue, front)
		end
	end

	return true
end

-- launch-time connectivity check. Deliberately a LOGIN-ONLY probe: the old version opened a throwaway
-- run (newRun) just to dump the card mapping, which orphaned a server-side Run every launch and did a
-- full handshake at startup. The real engage + card mapping happen on select_blind, so a light probe is
-- enough here.
local function prove_translation()
	logln("== BalatroBridge ==")
	local token = http_login("balatro")
	if token then
		logln("server reachable (login OK, " .. #token .. "-char token). Select a blind to engage.")
	else
		logln("server check FAILED: login (server :28788 up?) -> vanilla Balatro until it is reachable.")
	end
end

-- Test seam: expose the uid-keyed hand model to the stubbed load_spec harness so the drive-it logic
-- (restamp on diff, dissolve a server-removed card, escaped-card assignment) is gated HERE, not only
-- in-game. Never set in real play; the standalone parser spec still loads lib/wire.lua directly.
if rawget(_G, "BBRIDGE_TEST") then
	_G.__bbridge_test = {
		hand_restamp = hand_restamp,
		hand_remove = hand_remove,
		render_hand = render_hand,
		uid_to_index = uid_to_index,
		set_engaged = function(v) ENGAGED = v end,
		set_server_hand = function(h) SERVER_HAND = h end,
	}
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
		-- Frame-drain, every ~1.5s while engaged: in the SHOP (no scoring animation to fight) actively SELF-HEAL
		-- the owned rows so a native mis-route -- e.g. a joker bought into the consumable slot -- can't persist;
		-- everywhere else, only observe. Then run the (deduped) divergence check either way.
		if done and ENGAGED and VIEW and frames % 90 == 0 then
			pcall(function()
				if stage() == "SHOP" then
					pcall(reconcile_jokers_to_server)
					pcall(reconcile_consumables_to_server)
				end
				divergence_check(VIEW)
			end)
		end
	end
end)
