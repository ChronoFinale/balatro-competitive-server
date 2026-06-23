package com.balatro.engine.game;

import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vouchers: permanent, once-per-run shop upgrades. There are 16 base vouchers, each
 * with a Tier-2 upgrade (32 total). The shop's per-ante voucher is chosen from a
 * game-long queue over the 16 bases, resolving by tier: show Tier 1 until bought,
 * then Tier 2, then skip the position once both are owned (see {@code Run}). Effects
 * live in {@code Run} (which owns the state they touch); this catalog is metadata +
 * the base/upgrade links.
 */
public final class VoucherCatalog {

    private VoucherCatalog() {}

    /**
     * {@code upgradeKey} is the Tier-2 key for a base voucher, or null for a Tier-2 voucher.
     * {@code mods} are the voucher's resource effects as data — a Grabber is {@code add(HANDS_LEFT, 1)},
     * not a key-string check in {@code Run}.
     */
    public record Voucher(String key, String name, String description, int cost, String upgradeKey,
                          List<Modify> mods) {
        public Voucher(String key, String name, String description, int cost, String upgradeKey) {
            this(key, name, description, cost, upgradeKey, List.of());
        }

        Voucher withMods(Modify... mods) {
            return new Voucher(key, name, description, cost, upgradeKey, List.of(mods));
        }
    }

    private static final Map<String, Voucher> BY_KEY = new LinkedHashMap<>();
    private static final List<String> BASES = new ArrayList<>();

    /** Banned in Standard Ranked multiplayer (affect ante/boss in ways that break PvP pacing). */
    public static final Set<String> MP_BANNED =
            Set.of("v_hieroglyph", "v_petroglyph", "v_directors_cut", "v_retcon");

    static {
        // Runtime loads from /content/vouchers.json; content authored in com.balatro.content.VoucherDefs.
        List<Voucher> vouchers;
        try {
            vouchers = com.balatro.engine.content.ContentStore.vouchers();
        } catch (RuntimeException e) {
            vouchers = com.balatro.content.VoucherDefs.authored();
        }
        for (Voucher v : vouchers) {
            BY_KEY.put(v.key(), v);
            if (v.upgradeKey() != null) BASES.add(v.key()); // a base voucher is one that has an upgrade
        }
    }

    public static Voucher get(String key) {
        return BY_KEY.get(key);
    }

    /** The Tier-2 key for a base voucher (null if {@code baseKey} is itself a Tier-2 or unknown). */
    public static String upgradeKey(String baseKey) {
        Voucher v = BY_KEY.get(baseKey);
        return v != null ? v.upgradeKey() : null;
    }

    /** The 16 base voucher keys the queue draws from; in multiplayer the banned ones are excluded. */
    public static List<String> baseKeys(boolean multiplayer) {
        if (!multiplayer) return new ArrayList<>(BASES);
        return BASES.stream().filter(k -> !MP_BANNED.contains(k)).toList();
    }

    /** All voucher keys (base + upgrade). */
    public static List<String> keys() {
        return new ArrayList<>(BY_KEY.keySet());
    }
}
