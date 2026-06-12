-- PoolDump: dump the real, fully-built item pools from Balatro so our engine can match their exact
-- registration ORDER. Writes JSON via raw `io` (works under lovely, like the verified LÖVE probe).
--
-- Robust to load ordering: dumps immediately if the pools already exist (mod loaded after the game's
-- init_item_prototypes), AND hooks init_item_prototypes for the case we loaded before it. A load-marker
-- file is written first so we can confirm the mod loaded at all.

local OUT = "D:/NewServer/build/balatro-prototypes.json"
local MARK = "D:/NewServer/build/pooldump-loaded.txt"

-- load marker (diagnostic): proves the mod's main file ran, and what state exists at load time.
pcall(function()
	local f = io.open(MARK, "w")
	if f then
		f:write("loaded; G=" .. tostring(G ~= nil)
			.. " Game=" .. tostring(Game ~= nil)
			.. " pools=" .. tostring(G ~= nil and G.P_JOKER_RARITY_POOLS ~= nil))
		f:close()
	end
end)

local function keys_of(pool)
	local keys = {}
	if type(pool) == "table" then
		for _, v in ipairs(pool) do
			if type(v) == "table" and v.key then keys[#keys + 1] = v.key end
		end
	end
	return keys
end

local function json_list(t)
	local parts = {}
	for _, k in ipairs(t) do parts[#parts + 1] = '"' .. tostring(k) .. '"' end
	return "[" .. table.concat(parts, ",") .. "]"
end

local function dump()
	if not (G and G.P_JOKER_RARITY_POOLS and G.P_CENTER_POOLS) then return end
	local lines = {}
	for rarity = 1, 4 do
		lines[#lines + 1] = '"joker_' .. rarity .. '":' .. json_list(keys_of(G.P_JOKER_RARITY_POOLS[rarity]))
	end
	for _, set in ipairs({ "Tarot", "Planet", "Spectral", "Voucher", "Tag" }) do
		lines[#lines + 1] = '"' .. set .. '":' .. json_list(keys_of(G.P_CENTER_POOLS[set]))
	end
	local f = io.open(OUT, "w")
	if f then
		f:write("{" .. table.concat(lines, ",") .. "}")
		f:close()
	end
end

-- Case A: pools already built when we loaded -> dump now.
pcall(dump)

-- Case B: we loaded before init -> catch it (and any re-init).
pcall(function()
	if Game and Game.init_item_prototypes then
		local _orig = Game.init_item_prototypes
		function Game:init_item_prototypes()
			_orig(self)
			pcall(dump)
		end
	end
end)

return { dump = dump }
