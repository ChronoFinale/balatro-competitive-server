-- ShopDump: capture the REAL ante-1 shop the game generates for a fixed seed — true ground truth for
-- the seeded-shop diff. We don't navigate the GUI: after auto-starting a seeded run (so G.GAME + pools +
-- RNG are set), we call the game's own create_card_for_shop() directly. Sequential calls naturally do
-- within-shop dedup (each created card marks used_jokers), exactly like the real shop fill.
--
-- Crude frame-counter trigger (can't iterate fast, so be robust): start the run after the menu settles,
-- dump a few frames later once the run is initialized. pcall everywhere; a marker file aids diagnosis.

local SEED = "ABCD1234"
local OUT = "D:/NewServer/build/real-shop.json"
local MARK = "D:/NewServer/build/shopdump-mark.txt"

local function mark(s)
	pcall(function()
		local f = io.open(MARK, "a")
		if f then f:write(tostring(s) .. "\n"); f:close() end
	end)
end
mark("loaded")

local frames = 0
local started = false
local dumped = false

local function dumpShop()
	if not (G and G.GAME and G.jokers and create_card_for_shop) then
		mark("dumpShop not ready: G.GAME=" .. tostring(G and G.GAME ~= nil)
			.. " jokers=" .. tostring(G and G.jokers ~= nil)
			.. " ccfs=" .. tostring(create_card_for_shop ~= nil))
		return
	end
	-- Dump the CULLED pools FIRST — BEFORE generating the shop, so the shop's own jokers haven't been
	-- marked used_jokers yet (otherwise they'd show as UNAVAILABLE here). These are the exact pools the
	-- shop draw sees (lock/mod holes, no owned). _rarity poll: 0.5=common, 0.8=uncommon, 0.99=rare.
	local culled = {}
	pcall(function()
		if get_current_pool then
			for _, rp in ipairs({ { "common", 0.5 }, { "uncommon", 0.8 }, { "rare", 0.99 } }) do
				local pool = get_current_pool("Joker", rp[2], nil, "sho")
				local parts = {}
				for _, k in ipairs(pool) do parts[#parts + 1] = '"' .. tostring(k) .. '"' end
				culled[#culled + 1] = '"' .. rp[1] .. '":[' .. table.concat(parts, ",") .. "]"
			end
		end
	end)
	local items = {}
	for i = 1, 4 do
		local ok, card = pcall(create_card_for_shop, G.jokers)
		if ok and card and card.config and card.config.center then
			local ed = "none"
			if card.edition then
				ed = (card.edition.foil and "foil") or (card.edition.holo and "holo")
					or (card.edition.polychrome and "polychrome") or (card.edition.negative and "negative") or "none"
			end
			items[#items + 1] = '{"key":"' .. tostring(card.config.center.key) .. '","edition":"' .. ed .. '"}'
		else
			items[#items + 1] = '{"key":"ERR","edition":"none"}'
		end
	end
	local seed = (G.GAME.pseudorandom and G.GAME.pseudorandom.seed) or "?"
	local ante = (G.GAME.round_resets and G.GAME.round_resets.ante) or -1
	local order = (G.should_use_the_order and G.should_use_the_order()) or (MP and MP.should_use_the_order and MP.should_use_the_order())
	pcall(function()
		local f = io.open(OUT, "w")
		if f then
			f:write('{"seed":"' .. tostring(seed) .. '","ante":' .. tostring(ante)
				.. ',"the_order":' .. tostring(order and true or false)
				.. ',"culled":{' .. table.concat(culled, ",") .. "}"
				.. ',"shop":[' .. table.concat(items, ",") .. ']}')
			f:close()
		end
	end)
	mark("dumped seed=" .. tostring(seed) .. " ante=" .. tostring(ante) .. " order=" .. tostring(order))
end

pcall(function()
	local _upd = Game.update
	function Game:update(dt)
		_upd(self, dt)
		frames = frames + 1
		if frames == 240 and not started then
			started = true
			local ok, err = pcall(function() G.FUNCS.start_run(nil, { stake = 1, seed = SEED }) end)
			mark("start_run frame=" .. frames .. " ok=" .. tostring(ok) .. " err=" .. tostring(err))
		end
		if frames == 600 and not dumped then
			dumped = true
			pcall(dumpShop)
		end
	end
end)
