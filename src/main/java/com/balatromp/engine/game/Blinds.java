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

    /** Faithful port of get_blind_amount(ante) for the default scaling tier. */
    public static long getBlindAmount(int ante, Ruleset ruleset) {
        int[] amounts = ruleset.blindBaseAmounts();
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

    public static long requirement(int ante, BlindType blind, Ruleset ruleset) {
        return Math.round(getBlindAmount(ante, ruleset) * blind.mult * ruleset.anteScaling());
    }
}
