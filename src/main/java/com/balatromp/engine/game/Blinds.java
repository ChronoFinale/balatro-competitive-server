package com.balatromp.engine.game;

import com.balatromp.engine.state.Ruleset;

/**
 * Blind types and the score-to-beat curve, ported faithfully from Balatro's
 * {@code get_blind_amount} (misc_functions.lua:919) and the blind definitions
 * (game.lua: Small ×1/$3, Big ×1.5/$4, Boss ×2/$5).
 *
 * requirement(ante, blind) = get_blind_amount(ante) * blind.mult * ruleset.anteScaling
 */
public final class Blinds {

    private Blinds() {}

    public enum BlindType {
        SMALL("Small Blind", 1.0, 3),
        BIG("Big Blind", 1.5, 4),
        BOSS("Boss Blind", 2.0, 5);

        public final String display;
        public final double mult;
        public final int reward;

        BlindType(String display, double mult, int reward) {
            this.display = display;
            this.mult = mult;
            this.reward = reward;
        }
    }

    // Stake scaling tiers select which ante 1..8 base-requirement table get_blind_amount reads — a TABLE
    // SWAP, not a multiplier. This engine uses the Balatro-Multiplayer "release" competitive curve
    // (multiplayer-0.4.2/rulesets/release.lua:748-754): tier 1 keeps the vanilla base (White/Red fall
    // back to vanilla get_blind_amount), tier 2 = BMP "green", tier 3 = BMP "purple". BMP defines no
    // curve past purple, so higher tiers (Spectral/Spectral+) clamp to it — matching the real mod.
    private static final int[] BMP_GREEN  = {300, 1000, 3200, 9000, 18000, 32000, 56000, 90000};
    private static final int[] BMP_PURPLE = {300, 1200, 3600, 10000, 25000, 50000, 90000, 180000};

    /**
     * Faithful port of get_blind_amount(ante) for the given stake scaling tier.
     * Tier 1 honours the ruleset's (vanilla) base curve; tiers 2-3 are BMP's green/purple. Tiers 4-5
     * (BMP Spectral/Spectral+) clamp to tier 3 — faithful to the real mod, whose release override
     * defines no curve past purple and ignores the higher numeric scaling increments.
     */
    public static long getBlindAmount(int ante, Ruleset ruleset, int scaling) {
        int[] amounts = switch (Math.min(scaling, 3)) {
            case 2 -> BMP_GREEN;
            case 3 -> BMP_PURPLE; // tiers >3 (BMP Spectral/Spectral+) clamp here — see Javadoc
            default -> ruleset.blindBaseAmounts(); // tier 1: honour the ruleset's (possibly custom) curve
        };
        if (ante < 1) return 100;
        if (ante <= 8) return amounts[ante - 1];

        double k = 0.75;
        double a = amounts[7];
        double b = 1.6;
        double c = ante - 8;
        double d = 1 + 0.2 * (ante - 8);
        double amount = Math.floor(a * Math.pow(b + Math.pow(k * c, d), c));
        // Round down to the second-most-significant digit (Balatro's truncation).
        double mag = Math.pow(10, Math.floor(Math.log10(amount) - 1));
        amount = amount - (amount % mag);
        return (long) amount;
    }

    /** Backward-compatible: the base (White-stake) scaling tier. */
    public static long getBlindAmount(int ante, Ruleset ruleset) {
        return getBlindAmount(ante, ruleset, 1);
    }

    public static long requirement(int ante, BlindType blind, Ruleset ruleset, int scaling) {
        return Math.round(getBlindAmount(ante, ruleset, scaling) * blind.mult * ruleset.anteScaling());
    }

    public static long requirement(int ante, BlindType blind, Ruleset ruleset) {
        return requirement(ante, blind, ruleset, 1);
    }
}
