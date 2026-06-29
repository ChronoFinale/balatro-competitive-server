-- BB.wire — PURE wire helpers (no Balatro G, no socket, no SMODS). This is the one module the standalone
-- luajit spec loads directly. The mod's networking decodes a server line with json.decode, then calls
-- view_of(decoded) to get the flat VIEW table the rest of the mod consumes — so the renderer/hooks never
-- touch raw JSON and there is no regex scraping to silently rot (the uid int->UUID class of bug).

local wire = {}

-- Server enum names -> Balatro center-key fragments.
local RANK = { TWO = "2", THREE = "3", FOUR = "4", FIVE = "5", SIX = "6", SEVEN = "7", EIGHT = "8",
	NINE = "9", TEN = "T", JACK = "J", QUEEN = "Q", KING = "K", ACE = "A" }
local SUIT = { SPADES = "S", HEARTS = "H", CLUBS = "C", DIAMONDS = "D" }

-- {rank,suit} (server enum names) -> Balatro playing-card center key, e.g. {KING,HEARTS} -> "H_K". nil if unmappable.
function wire.card_key(c)
	if not c then return nil end
	local r, su = RANK[c.rank], SUIT[c.suit]
	return (su and r) and (su .. "_" .. r) or nil
end

-- Server edition name -> Balatro Card:set_edition table (nil = base, no edition).
function wire.edition_table(ed)
	if ed == "FOIL" then return { foil = true }
	elseif ed == "HOLOGRAPHIC" then return { holo = true }
	elseif ed == "POLYCHROME" then return { polychrome = true }
	elseif ed == "NEGATIVE" then return { negative = true } end
	return nil
end

-- Server pack (kind+size) -> Balatro booster center key p_<kind>_<size>_1 (trailing variant only changes sprite).
function wire.pack_center_key(it)
	if not (it and it.kind and it.size) then return nil end
	return "p_" .. it.kind:lower() .. "_" .. it.size:lower() .. "_1"
end

-- Normalize a decoded server response (the {type,seq,accepted,view,...} envelope, or a bare ClientView)
-- into the flat VIEW shape the renderer/hooks consume. Pure: takes an already-decoded table, returns a table.
-- Field names match what the mod already reads (vouchers<-shopVouchers, remaining<-deckStats.remaining,
-- cashReward/cashInterest<-counters.*), so swapping the data source from regex to this needs no consumer change.
function wire.view_of(decoded)
	if type(decoded) ~= "table" then return nil end
	local v = decoded.view or decoded
	if type(v) ~= "table" then return nil end
	local counters = v.counters or {}
	local deckStats = v.deckStats or {}
	-- Hand: only face-up cards. Face-down (boss-forced) cards carry a nil rank/suit on the wire; the renderer
	-- skips them today (a backlog item), so view_of preserves that by filtering them out here.
	local hand = {}
	if type(v.hand) == "table" then
		for _, c in ipairs(v.hand) do
			if c and c.rank and c.suit then
				hand[#hand + 1] = { uid = c.uid, rank = c.rank, suit = c.suit }
			end
		end
	end
	return {
		phase        = v.phase,
		bossKey      = v.bossKey,
		requirement  = v.requirement,
		roundScore   = v.roundScore,
		handsLeft    = v.handsLeft,
		discardsLeft = v.discardsLeft,
		money        = v.money,
		cashReward   = counters.cashOutReward,
		cashInterest = counters.cashOutInterest,
		remaining    = deckStats.remaining,
		rerollCost   = v.rerollCost,
		hand         = hand,
		shop         = v.shop or {},          -- main slots (kind/key/name/cost/edition); empty unless phase==SHOP
		vouchers     = v.shopVouchers or {},  -- offered vouchers (key/name/cost)
		consumables  = v.consumables or {},   -- held consumables (key/name) -> index for use
		jokers       = v.jokers or {},        -- owned jokers (key/name) -> divergence check / row render
		packs        = v.packs or {},         -- shop booster packs (kind/size/cost)
		openPack     = v.openPack,            -- the currently-open pack (nil when none)
	}
end

return wire
