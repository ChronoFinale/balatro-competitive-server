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
        put("tag_uncommon", "Uncommon Tag", true, Timing.ON_SHOP);
        put("tag_rare", "Rare Tag", true, Timing.ON_SHOP);
        put("tag_negative", "Negative Tag", false, Timing.ON_SHOP);
        put("tag_foil", "Foil Tag", true, Timing.ON_SHOP);
        put("tag_holo", "Holographic Tag", true, Timing.ON_SHOP);
        put("tag_polychrome", "Polychrome Tag", true, Timing.ON_SHOP);
        put("tag_investment", "Investment Tag", true, Timing.ON_BOSS_DEFEAT);
        put("tag_voucher", "Voucher Tag", true, Timing.ON_SHOP);
        put("tag_boss", "Boss Tag", true, Timing.HELD);
        put("tag_standard", "Standard Tag", false, Timing.ON_SHOP);
        put("tag_charm", "Charm Tag", true, Timing.ON_SHOP);
        put("tag_meteor", "Meteor Tag", false, Timing.ON_SHOP);
        put("tag_buffoon", "Buffoon Tag", false, Timing.ON_SHOP);
        put("tag_ethereal", "Ethereal Tag", false, Timing.ON_SHOP);
        put("tag_coupon", "Coupon Tag", true, Timing.ON_SHOP);
        put("tag_double", "Double Tag", true, Timing.HELD);
        put("tag_juggle", "Juggle Tag", true, Timing.NEXT_BLIND);
        put("tag_d_six", "D6 Tag", true, Timing.ON_SHOP);
        put("tag_economy", "Economy Tag", true, Timing.IMMEDIATE);
        put("tag_skip", "Speed Tag", true, Timing.IMMEDIATE);
        put("tag_orbital", "Orbital Tag", false, Timing.IMMEDIATE);
        put("tag_handy", "Handy Tag", false, Timing.IMMEDIATE);
        put("tag_garbage", "Garbage Tag", false, Timing.IMMEDIATE);
        put("tag_top_up", "Top-Up Tag", false, Timing.IMMEDIATE);
    }

    private static void put(String key, String name, boolean ante1, Timing timing) {
        BY_KEY.put(key, new Tag(key, name, com.balatro.engine.i18n.Loc.text(key), ante1, timing));
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
