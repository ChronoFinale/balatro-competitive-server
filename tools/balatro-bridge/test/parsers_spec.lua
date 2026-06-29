-- Standalone unit spec for BalatroBridge's PURE wire helpers (no Balatro, no server, no SMODS needed).
-- Run:  cd tools/balatro-bridge && luajit test/parsers_spec.lua
-- It loads the real rxi json decoder (vendored at test/json.lua — identical to the SMODS copy the mod uses
-- in-game via require("json")) and the pure BB.wire module (lib/wire.lua), then exercises view_of() against a
-- realistic full server response plus the pure key/edition/pack helpers. This is the gate that catches wire-
-- shape drift (e.g. the uid int->UUID change that silently rotted the old regex parsers).

package.path = "lib/?.lua;test/?.lua;" .. package.path
local json = require("json")
local W = require("wire")

-- ---- harness ---------------------------------------------------------------
local pass, fail = 0, 0
local function ok(cond, msg)
	if cond then pass = pass + 1 else fail = fail + 1; print("FAIL: " .. tostring(msg)) end
end
local function eq(a, b, msg) ok(a == b, (msg or "") .. " (got " .. tostring(a) .. ", want " .. tostring(b) .. ")") end

ok(W ~= nil, "wire module loaded")
ok(type(json.decode) == "function", "json decoder loaded")

-- ---- pack_center_key -------------------------------------------------------
eq(W.pack_center_key({ kind = "ARCANA", size = "NORMAL" }), "p_arcana_normal_1", "arcana normal key")
eq(W.pack_center_key({ kind = "BUFFOON", size = "MEGA" }), "p_buffoon_mega_1", "buffoon mega key")
eq(W.pack_center_key({ kind = "CELESTIAL", size = "JUMBO" }), "p_celestial_jumbo_1", "celestial jumbo key")
eq(W.pack_center_key({ kind = "ARCANA" }), nil, "missing size -> nil")
eq(W.pack_center_key(nil), nil, "nil -> nil")

-- ---- edition_table ---------------------------------------------------------
eq(W.edition_table("FOIL").foil, true, "foil")
eq(W.edition_table("HOLOGRAPHIC").holo, true, "holo")
eq(W.edition_table("POLYCHROME").polychrome, true, "poly")
eq(W.edition_table("NEGATIVE").negative, true, "negative")
eq(W.edition_table("NONE"), nil, "NONE -> nil")
eq(W.edition_table(nil), nil, "nil -> nil")

-- ---- card_key (server enum names -> Balatro center key) --------------------
eq(W.card_key({ rank = "KING", suit = "HEARTS" }), "H_K", "K of hearts")
eq(W.card_key({ rank = "TEN", suit = "CLUBS" }), "C_T", "10 of clubs")
eq(W.card_key({ rank = "ACE", suit = "SPADES" }), "S_A", "A of spades")
eq(W.card_key({ rank = "BOGUS", suit = "HEARTS" }), nil, "bad rank -> nil")
eq(W.card_key(nil), nil, "nil card -> nil")

-- ---- view_of: a full update response decoded by the real json decoder ------
-- Mirrors the real wire: a {type,seq,accepted,view,replay} envelope wrapping a serialized ClientView. uid is
-- a quoted UUID string (java.util.UUID); a face-DOWN card has null rank/suit (boss-forced) and must be filtered.
local UID1 = "11111111-2222-3333-4444-555555555555"
local UID2 = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
local resp = '{"type":"update","seq":7,"accepted":true,"rejection":null,"view":{' ..
	'"ante":1,"blind":"Boss Blind","requirement":600,"roundScore":120,"handsLeft":3,"discardsLeft":2,' ..
	'"money":14,"handSize":8,"phase":"BLIND_ACTIVE","bossKey":"bl_manacle","rerollCost":5,' ..
	'"hand":[' ..
		'{"uid":"' .. UID1 .. '","rank":"ACE","suit":"SPADES","enhancement":"NONE","edition":"NONE","seal":"NONE","faceDown":false},' ..
		'{"uid":"' .. UID2 .. '","rank":"KING","suit":"HEARTS","enhancement":"NONE","edition":"FOIL","seal":"NONE","faceDown":false},' ..
		'{"uid":"deadbeef-0000-1111-2222-333344445555","rank":null,"suit":null,"faceDown":true}],' ..
	'"jokers":[{"key":"j_joker","name":"Joker","cost":2,"def":{"key":"jd_inner","conditions":[]},"state":{"foo":1}}],' ..
	'"shop":[' ..
		'{"kind":"JOKER","key":"j_joker","name":"Joker","description":"+4 Mult","cost":4,"rarity":"Common","edition":"FOIL"},' ..
		'{"kind":"TAROT","key":"c_fool","name":"The Fool","description":"copy","cost":3,"rarity":null,"edition":"NONE"}],' ..
	'"rerollCost":5,"boss":"The Manacle","bossEffect":"-1 hand size",' ..
	'"consumables":[{"key":"c_fool","name":"The Fool"}],' ..
	'"handLevels":{},' ..
	'"deckStats":{"size":52,"remaining":40},' ..
	'"counters":{"OPP_HANDS_LEFT":4,"cashOutReward":5,"cashOutInterest":3},' ..
	'"shopVouchers":[{"key":"v_overstock_norm","name":"Overstock","cost":10}],' ..
	'"packs":[{"kind":"ARCANA","size":"NORMAL","name":"Arcana Pack","cost":4,"shown":3,"choose":1}],' ..
	'"openPack":{"picksLeft":2,"items":[' ..
		'{"type":"CONSUMABLE","key":"c_fool","name":"The Fool"},' ..
		'{"type":"JOKER","key":"j_joker","name":"Joker"},' ..
		'{"type":"CARD","name":"KING of HEARTS","rank":"KING","suit":"HEARTS","enhancement":"NONE"}]},' ..
	'"stake":"White Stake","deck":"Red Deck"}}'

local decoded = json.decode(resp)
eq(decoded.accepted, true, "envelope accepted (top-level, not view)")
eq(decoded.rejection, nil, "null rejection -> nil")

local v = W.view_of(decoded)
ok(v ~= nil, "view_of returned")
-- scalars
eq(v.phase, "BLIND_ACTIVE", "phase"); eq(v.money, 14, "money"); eq(v.requirement, 600, "requirement")
eq(v.roundScore, 120, "roundScore"); eq(v.handsLeft, 3, "handsLeft"); eq(v.discardsLeft, 2, "discardsLeft")
eq(v.rerollCost, 5, "rerollCost"); eq(v.bossKey, "bl_manacle", "bossKey")
-- derived (nested -> flat)
eq(v.remaining, 40, "remaining (from deckStats)")
eq(v.cashReward, 5, "cashReward (from counters)"); eq(v.cashInterest, 3, "cashInterest (from counters)")
-- hand: face-up only (the face-down boss card is filtered)
eq(#v.hand, 2, "hand size (face-down filtered)")
eq(v.hand[1].uid, UID1, "hand[1] uid is the UUID string"); eq(v.hand[1].rank, "ACE", "hand[1] rank")
eq(v.hand[2].suit, "HEARTS", "hand[2] suit")
eq(W.card_key(v.hand[2]), "H_K", "hand[2] -> center key")
-- full identity carried for the renderer (set_base + edition + enhancement + seal + faceDown)
eq(v.hand[1].enhancement, "NONE", "hand[1] enhancement carried")
eq(v.hand[1].seal, "NONE", "hand[1] seal carried")
eq(v.hand[1].faceDown, false, "hand[1] faceDown carried")
eq(v.hand[2].edition, "FOIL", "hand[2] edition carried (Hex/Wheel-style render)")
-- arrays pass through with the fields the renderer reads
eq(#v.shop, 2, "shop size"); eq(v.shop[1].kind, "JOKER", "shop[1] kind"); eq(v.shop[1].key, "j_joker", "shop[1] key")
eq(v.shop[1].cost, 4, "shop[1] cost (number)"); eq(v.shop[1].edition, "FOIL", "shop[1] edition")
eq(v.shop[2].edition, "NONE", "shop[2] edition")
eq(#v.jokers, 1, "jokers size"); eq(v.jokers[1].key, "j_joker", "joker key (top-level, not nested def)")
eq(#v.vouchers, 1, "vouchers size (from shopVouchers)"); eq(v.vouchers[1].key, "v_overstock_norm", "voucher key")
eq(v.vouchers[1].cost, 10, "voucher cost")
eq(#v.consumables, 1, "consumables size"); eq(v.consumables[1].key, "c_fool", "consumable key")
eq(#v.packs, 1, "packs size"); eq(W.pack_center_key(v.packs[1]), "p_arcana_normal_1", "pack -> center key")
-- openPack
ok(v.openPack ~= nil, "openPack present"); eq(v.openPack.picksLeft, 2, "openPack picksLeft")
eq(#v.openPack.items, 3, "openPack items"); eq(v.openPack.items[1].type, "CONSUMABLE", "item1 type")
eq(W.card_key(v.openPack.items[3]), "H_K", "CARD item -> set_base key")

-- ---- view_of robustness ----------------------------------------------------
-- A SHOP-phase view with no open pack: openPack is nil, arrays default to empty (never nil).
local shopResp = '{"type":"update","seq":9,"accepted":true,"view":{"phase":"SHOP","requirement":0,' ..
	'"roundScore":0,"handsLeft":4,"discardsLeft":3,"money":20,"rerollCost":5,"hand":[],' ..
	'"shop":[{"kind":"JOKER","key":"j_joker","name":"Joker","cost":4,"edition":"NONE"}],' ..
	'"deckStats":{"size":52,"remaining":52},"counters":{}}}'
local sv = W.view_of(json.decode(shopResp))
eq(sv.phase, "SHOP", "shop view phase"); eq(sv.openPack, nil, "no openPack -> nil")
eq(#sv.hand, 0, "empty hand -> {}"); eq(#sv.vouchers, 0, "absent shopVouchers -> {}")
eq(#sv.consumables, 0, "absent consumables -> {}"); eq(#sv.packs, 0, "absent packs -> {}")
eq(sv.cashReward, nil, "absent cashOutReward -> nil")
eq(W.view_of(nil), nil, "nil decoded -> nil")

-- ---- summary ---------------------------------------------------------------
print(string.format("\n%d passed, %d failed", pass, fail))
os.exit(fail == 0 and 0 or 1)
