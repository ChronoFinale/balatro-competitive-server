package com.balatro.engine.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skip tags: claimed by skipping a Small/Big blind. Each has a {@link Timing} that says
 * WHEN its effect resolves — immediately on claim, when the next shop opens, after the
 * next boss is defeated, at the next blind, or just held. Some tags are locked out of
 * Ante 1. (Boss Reroll is banned in ranked MP.)
 */
public final class TagCatalog {

    private TagCatalog() {}

    public enum Timing {
        IMMEDIATE,        // resolves the moment it's claimed
        ON_SHOP,          // resolves when the next shop opens (voucher/pack/edition/free-joker/coupon/d6)
        ON_BOSS_DEFEAT,   // resolves after the next boss blind is beaten (Investment)
        NEXT_BLIND,       // resolves at the next blind (Juggle, Orbital sizing)
        HELD              // utility held in inventory (Double Tag)
    }

    public record Tag(String key, String name, String description, boolean ante1, Timing timing) {}

    private static final Map<String, Tag> BY_KEY = new LinkedHashMap<>();

    /** Banned in Standard Ranked multiplayer (PvP-blind interaction). */
    public static final Set<String> MP_BANNED = Set.of("tag_boss");

    static {
        put("tag_uncommon", "Uncommon Tag", "Shop has a free Uncommon Joker", true, Timing.ON_SHOP);
        put("tag_rare", "Rare Tag", "Shop has a free Rare Joker", true, Timing.ON_SHOP);
        put("tag_negative", "Negative Tag", "Next base shop Joker is free and Negative", false, Timing.ON_SHOP);
        put("tag_foil", "Foil Tag", "Next base shop Joker is free and Foil", true, Timing.ON_SHOP);
        put("tag_holo", "Holographic Tag", "Next base shop Joker is free and Holographic", true, Timing.ON_SHOP);
        put("tag_polychrome", "Polychrome Tag", "Next base shop Joker is free and Polychrome", true, Timing.ON_SHOP);
        put("tag_investment", "Investment Tag", "Gain $25 after defeating the next Boss Blind", true, Timing.ON_BOSS_DEFEAT);
        put("tag_voucher", "Voucher Tag", "Adds one Voucher to the next shop", true, Timing.ON_SHOP);
        put("tag_boss", "Boss Tag", "Rerolls the Boss Blind", true, Timing.HELD);
        put("tag_standard", "Standard Tag", "Free Mega Standard Pack", false, Timing.ON_SHOP);
        put("tag_charm", "Charm Tag", "Free Mega Arcana Pack", true, Timing.ON_SHOP);
        put("tag_meteor", "Meteor Tag", "Free Mega Celestial Pack", false, Timing.ON_SHOP);
        put("tag_buffoon", "Buffoon Tag", "Free Mega Buffoon Pack", false, Timing.ON_SHOP);
        put("tag_ethereal", "Ethereal Tag", "Free Spectral Pack", false, Timing.ON_SHOP);
        put("tag_coupon", "Coupon Tag", "Initial shop cards & packs are free", true, Timing.ON_SHOP);
        put("tag_double", "Double Tag", "Gives a copy of the next selected tag", true, Timing.HELD);
        put("tag_juggle", "Juggle Tag", "+3 hand size next round", true, Timing.NEXT_BLIND);
        put("tag_d6", "D6 Tag", "Rerolls in the next shop start at $0", true, Timing.ON_SHOP);
        put("tag_economy", "Economy Tag", "Doubles your money (max $40 gain)", true, Timing.IMMEDIATE);
        put("tag_speed", "Speed Tag", "$5 per blind skipped this run", true, Timing.IMMEDIATE);
        put("tag_orbital", "Orbital Tag", "Upgrade your most-played hand by 3 levels", false, Timing.IMMEDIATE);
        put("tag_handy", "Handy Tag", "$1 per hand played this run", false, Timing.IMMEDIATE);
        put("tag_garbage", "Garbage Tag", "$1 per unused discard this run", false, Timing.IMMEDIATE);
        put("tag_top_up", "Top-Up Tag", "Create up to 2 Common Jokers", false, Timing.IMMEDIATE);
    }

    private static void put(String key, String name, String desc, boolean ante1, Timing timing) {
        BY_KEY.put(key, new Tag(key, name, desc, ante1, timing));
    }

    public static Tag get(String key) {
        return BY_KEY.get(key);
    }

    /** Every tag key in the catalog — the surface the coverage net enumerates. */
    public static Set<String> keys() {
        return java.util.Collections.unmodifiableSet(BY_KEY.keySet());
    }

    public static Timing timing(String key) {
        Tag t = BY_KEY.get(key);
        return t != null ? t.timing() : Timing.HELD;
    }

    /** Tags that can be OFFERED on a skip at the given ante (excludes Ante-1-locked tags early,
     *  Double Tag — which comes from Diet Cola/Anaglyph — and MP-banned tags in multiplayer). */
    public static List<String> offerable(int ante, boolean multiplayer) {
        List<String> out = new ArrayList<>();
        for (Tag t : BY_KEY.values()) {
            if (t.key().equals("tag_double")) continue;          // not a skip offer
            if (ante < 2 && !t.ante1()) continue;                // Ante-1 lockouts
            if (multiplayer && MP_BANNED.contains(t.key())) continue;
            out.add(t.key());
        }
        return out;
    }
}
