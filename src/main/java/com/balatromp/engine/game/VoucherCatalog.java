package com.balatromp.engine.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vouchers: permanent, once-per-run shop upgrades. Each is bought at most once and
 * then applies for the rest of the run — some immediately (a consumable slot, the
 * interest cap), some each blind (extra hands/discards), some to the shop itself
 * (extra slot, discount). Effects live in {@code Run} (which owns the state they
 * touch); this catalog is just the metadata + the shop's draw pool.
 */
public final class VoucherCatalog {

    private VoucherCatalog() {}

    public record Voucher(String key, String name, String description, int cost) {}

    private static final Map<String, Voucher> BY_KEY = new LinkedHashMap<>();

    static {
        put(new Voucher("v_grabber", "Grabber", "Permanently gain +1 hand per round", 10));
        put(new Voucher("v_wasteful", "Wasteful", "Permanently gain +1 discard per round", 10));
        put(new Voucher("v_crystal_ball", "Crystal Ball", "+1 consumable slot", 10));
        put(new Voucher("v_seed_money", "Seed Money", "Raise the interest cap to $10 per round", 10));
        put(new Voucher("v_overstock", "Overstock", "+1 card slot in the shop", 10));
        put(new Voucher("v_clearance_sale", "Clearance Sale", "All shop cards are 25% cheaper", 10));
    }

    private static void put(Voucher v) {
        BY_KEY.put(v.key(), v);
    }

    public static Voucher get(String key) {
        return BY_KEY.get(key);
    }

    public static List<String> keys() {
        return new ArrayList<>(BY_KEY.keySet());
    }
}
