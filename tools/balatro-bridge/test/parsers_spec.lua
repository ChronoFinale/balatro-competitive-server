-- Standalone unit spec for BalatroBridge's PURE parsers (no Balatro, no server needed).
-- Run:  cd tools/balatro-bridge && luajit test/parsers_spec.lua
-- It stubs require("socket") + io.open, sets the BBRIDGE_EXPOSE seam, loads the mod (whose only
-- top-level effects are guarded), then exercises the JSON parsers the mod scrapes wire responses with.

-- ---- harness ---------------------------------------------------------------
local pass, fail = 0, 0
local function ok(cond, msg)
	if cond then pass = pass + 1 else fail = fail + 1; print("FAIL: " .. tostring(msg)) end
end
local function eq(a, b, msg) ok(a == b, (msg or "") .. " (got " .. tostring(a) .. ", want " .. tostring(b) .. ")") end

-- ---- stubs: load the mod without Balatro/luasocket -------------------------
local _real_require, _real_open = require, io.open
require = function(m) if m == "socket" then return { tcp = function() end } end; return _real_require(m) end
io.open = function() return nil end -- no log-file side effects (mod guards on nil handle)
_G.BBRIDGE_EXPOSE = true
dofile("balatrobridge.lua")          -- Game is nil -> the frame-200 hook is pcall'd to a no-op
require, io.open = _real_require, _real_open
local B = _G.BBRIDGE
ok(B ~= nil, "BBRIDGE exposed")

-- ---- pack_center_key -------------------------------------------------------
eq(B.pack_center_key({ kind = "ARCANA", size = "NORMAL" }), "p_arcana_normal_1", "arcana normal key")
eq(B.pack_center_key({ kind = "BUFFOON", size = "MEGA" }), "p_buffoon_mega_1", "buffoon mega key")
eq(B.pack_center_key({ kind = "CELESTIAL", size = "JUMBO" }), "p_celestial_jumbo_1", "celestial jumbo key")
eq(B.pack_center_key({ kind = "ARCANA" }), nil, "missing size -> nil")
eq(B.pack_center_key(nil), nil, "nil -> nil")

-- ---- edition_table ---------------------------------------------------------
eq(B.edition_table("FOIL").foil, true, "foil")
eq(B.edition_table("HOLOGRAPHIC").holo, true, "holo")
eq(B.edition_table("POLYCHROME").polychrome, true, "poly")
eq(B.edition_table("NEGATIVE").negative, true, "negative")
eq(B.edition_table("NONE"), nil, "NONE -> nil")
eq(B.edition_table(nil), nil, "nil -> nil")

-- ---- card_key (server enum names -> Balatro center key) --------------------
eq(B.card_key({ rank = "KING", suit = "HEARTS" }), "H_K", "K of hearts")
eq(B.card_key({ rank = "TEN", suit = "CLUBS" }), "C_T", "10 of clubs")
eq(B.card_key({ rank = "ACE", suit = "SPADES" }), "S_A", "A of spades")
eq(B.card_key({ rank = "BOGUS", suit = "HEARTS" }), nil, "bad rank -> nil")

-- ---- parse_hand ------------------------------------------------------------
-- uid is a java.util.UUID -> a QUOTED hex-and-hyphen string on the wire (CardView.uid). The regex requires
-- the quotes; the old integer fixture predated the UUID migration and silently rotted this spec.
local UID1, UID2 = "11111111-2222-3333-4444-555555555555", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
local handResp = '"hand":[{"uid":"' .. UID1 .. '","rank":"KING","suit":"HEARTS"},' ..
	'{"uid":"' .. UID2 .. '","rank":"TWO","suit":"CLUBS"}]'
local hand = B.parse_hand(handResp)
eq(#hand, 2, "hand size")
eq(hand[1].uid, UID1, "hand[1] uid"); eq(hand[1].rank, "KING", "hand[1] rank"); eq(hand[1].suit, "HEARTS", "hand[1] suit")
eq(hand[2].uid, UID2, "hand[2] uid")

-- ---- parse_objs: shop (flat items, incl. edition) --------------------------
local shopResp = '"shop":[' ..
	'{"kind":"JOKER","key":"j_joker","name":"Joker","description":"+4 Mult","cost":4,"rarity":"Common","edition":"FOIL"},' ..
	'{"kind":"TAROT","key":"c_fool","name":"The Fool","description":"copy","cost":3,"rarity":null,"edition":"NONE"}]'
local shop = B.parse_objs(shopResp, "shop")
eq(#shop, 2, "shop size")
eq(shop[1].kind, "JOKER", "shop[1] kind"); eq(shop[1].key, "j_joker", "shop[1] key")
eq(shop[1].cost, 4, "shop[1] cost"); eq(shop[1].edition, "FOIL", "shop[1] edition")
eq(shop[2].kind, "TAROT", "shop[2] kind"); eq(shop[2].edition, "NONE", "shop[2] edition")

-- ---- parse_objs: jokers with NESTED def/state (first key is the joker key) --
local jokersResp = '"jokers":[' ..
	'{"key":"j_joker","name":"Joker","cost":2,"def":{"key":"jd_inner","conditions":[]},"state":{"foo":1}},' ..
	'{"key":"j_greedy_joker","name":"Greedy","cost":5,"def":{"key":"jd_g","conditions":[{"op":"EQ"}]},"state":{}}]'
local jk = B.parse_objs(jokersResp, "jokers")
eq(#jk, 2, "jokers size (nested braces balanced)")
eq(jk[1].key, "j_joker", "joker[1] top-level key, not nested def key")
eq(jk[2].key, "j_greedy_joker", "joker[2] key")

-- ---- parse_objs: packs (kind + size) ---------------------------------------
local packsResp = '"packs":[{"kind":"ARCANA","size":"NORMAL","name":"Arcana Pack","cost":4,"shown":3,"choose":1},' ..
	'{"kind":"BUFFOON","size":"MEGA","name":"Mega Buffoon Pack","cost":8,"shown":4,"choose":2}]'
local packs = B.parse_objs(packsResp, "packs")
eq(#packs, 2, "packs size")
eq(B.pack_center_key(packs[1]), "p_arcana_normal_1", "pack[1] -> center key")
eq(B.pack_center_key(packs[2]), "p_buffoon_mega_1", "pack[2] -> center key")

-- ---- parse_objs: absent field -> empty -------------------------------------
eq(#B.parse_objs('"foo":123', "shop"), 0, "absent shop -> {}")
eq(#B.parse_objs(nil, "shop"), 0, "nil resp -> {}")

-- ---- parse_open_pack -------------------------------------------------------
local openResp = '"openPack":{"picksLeft":2,"items":[' ..
	'{"type":"CONSUMABLE","key":"c_fool","name":"The Fool"},' ..
	'{"type":"JOKER","key":"j_joker","name":"Joker"},' ..
	'{"type":"CARD","name":"KING of HEARTS","rank":"KING","suit":"HEARTS","enhancement":"NONE"}]}'
local op = B.parse_open_pack(openResp)
ok(op ~= nil, "openPack parsed")
eq(op.picksLeft, 2, "picksLeft")
eq(#op.items, 3, "openPack items")
eq(op.items[1].type, "CONSUMABLE", "item1 type"); eq(op.items[1].key, "c_fool", "item1 key")
eq(op.items[3].type, "CARD", "item3 type")
eq(B.card_key(op.items[3]), "H_K", "CARD item -> set_base key")
eq(B.parse_open_pack('"foo":1'), nil, "no openPack -> nil")

-- ---- parse_view: a full update response ------------------------------------
local resp = '{"type":"update","seq":7,"accepted":true,"rejection":null,"view":{' ..
	'"ante":1,"blind":"Boss Blind","requirement":600,"roundScore":120,"handsLeft":3,"discardsLeft":2,' ..
	'"money":14,"handSize":8,"phase":"BLIND_ACTIVE","bossKey":"bl_manacle","rerollCost":5,' ..
	'"hand":[{"uid":"deadbeef-0000-1111-2222-333344445555","rank":"ACE","suit":"SPADES"}],' ..
	'"shop":[{"kind":"JOKER","key":"j_joker","name":"Joker","cost":4,"edition":"NONE"}],' ..
	'"consumables":[{"key":"c_fool","name":"The Fool"}],' ..
	'"deckStats":{"size":52,"remaining":40}}}'
local v = B.parse_view(resp)
eq(v.phase, "BLIND_ACTIVE", "view phase")
eq(v.money, 14, "view money"); eq(v.requirement, 600, "view requirement")
eq(v.roundScore, 120, "view roundScore"); eq(v.handsLeft, 3, "view handsLeft")
eq(v.rerollCost, 5, "view rerollCost"); eq(v.bossKey, "bl_manacle", "view bossKey")
eq(v.remaining, 40, "view remaining (deckStats)")
eq(#v.hand, 1, "view hand"); eq(#v.shop, 1, "view shop"); eq(#v.consumables, 1, "view consumables")

-- ---- summary ---------------------------------------------------------------
print(string.format("\n%d passed, %d failed", pass, fail))
os.exit(fail == 0 and 0 or 1)
