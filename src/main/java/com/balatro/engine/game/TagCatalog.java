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

    /** {@code effects} are the tag's behaviour as DATA — applied through the same {@link
     *  com.balatro.engine.joker.def.Effect} vocabulary jokers/bosses/consumables use, instead of a hard-coded
     *  {@code switch} in {@code Run}. Empty = a tag still resolved by Run's remaining bespoke handling. */
    public record Tag(String key, String name, String description, boolean ante1, Timing timing,
                      java.util.List<com.balatro.engine.joker.def.Effect> effects) {
        public Tag {
            effects = effects == null ? java.util.List.of() : java.util.List.copyOf(effects);
        }
        public Tag(String key, String name, String description, boolean ante1, Timing timing) {
            this(key, name, description, ante1, timing, java.util.List.of());
        }
    }

    private static final Map<String, Tag> BY_KEY = new LinkedHashMap<>();

    /** Banned in Standard Ranked multiplayer (PvP-blind interaction). */
    public static final Set<String> MP_BANNED = Set.of("tag_boss");

    static {
        // Runtime loads from /content/tags.json; the content is authored in com.balatro.content.TagDefs.
        List<Tag> tags;
        try {
            tags = com.balatro.engine.content.ContentStore.tags();
        } catch (RuntimeException e) {
            tags = com.balatro.content.TagDefs.authored();
        }
        for (Tag t : tags) BY_KEY.put(t.key(), t);
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
