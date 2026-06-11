package com.balatromp.engine.game;

/**
 * Booster pack definitions, per the mechanics doc. A pack has a {@link Kind} (what it
 * contains) and a {@link Size} (how many cards it shows and how many you pick), which
 * together fix its cost. The {@link #roll(double)} table is the vanilla appearance-rate
 * distribution used by the game-long "packs" queue (two pack slots per shop).
 */
public final class PackCatalog {

    private PackCatalog() {}

    public enum Kind { ARCANA, CELESTIAL, SPECTRAL, BUFFOON, STANDARD }

    public enum Size { NORMAL, JUMBO, MEGA }

    public record Pack(Kind kind, Size size) {
        public int cost() {
            return switch (size) {
                case NORMAL -> 4;
                case JUMBO -> 6;
                case MEGA -> 8;
            };
        }

        /** Cards shown when opened. Buffoon/Spectral show fewer (2/4/4) than the rest (3/5/5). */
        public int shown() {
            boolean small = kind == Kind.BUFFOON || kind == Kind.SPECTRAL;
            return switch (size) {
                case NORMAL -> small ? 2 : 3;
                case JUMBO, MEGA -> small ? 4 : 5;
            };
        }

        /** Cards you may pick (Mega = 2, otherwise 1). */
        public int choose() {
            return size == Size.MEGA ? 2 : 1;
        }

        public String displayName() {
            String sz = switch (size) {
                case NORMAL -> "";
                case JUMBO -> "Jumbo ";
                case MEGA -> "Mega ";
            };
            String kn = switch (kind) {
                case ARCANA -> "Arcana";
                case CELESTIAL -> "Celestial";
                case SPECTRAL -> "Spectral";
                case BUFFOON -> "Buffoon";
                case STANDARD -> "Standard";
            };
            return sz + kn + " Pack";
        }
    }

    // Vanilla baseline appearance rates (percent), aligned with PACKS below; sums to ~100.
    private static final double[] RATES = {
        17.84, 8.92, 2.23,   // Standard  N / J / M
        17.84, 8.92, 2.23,   // Arcana
        17.84, 8.92, 2.23,   // Celestial
        5.35, 2.68, 0.67,    // Buffoon
        2.68, 1.34, 0.31,    // Spectral
    };

    private static final Pack[] PACKS = {
        new Pack(Kind.STANDARD, Size.NORMAL), new Pack(Kind.STANDARD, Size.JUMBO), new Pack(Kind.STANDARD, Size.MEGA),
        new Pack(Kind.ARCANA, Size.NORMAL), new Pack(Kind.ARCANA, Size.JUMBO), new Pack(Kind.ARCANA, Size.MEGA),
        new Pack(Kind.CELESTIAL, Size.NORMAL), new Pack(Kind.CELESTIAL, Size.JUMBO), new Pack(Kind.CELESTIAL, Size.MEGA),
        new Pack(Kind.BUFFOON, Size.NORMAL), new Pack(Kind.BUFFOON, Size.JUMBO), new Pack(Kind.BUFFOON, Size.MEGA),
        new Pack(Kind.SPECTRAL, Size.NORMAL), new Pack(Kind.SPECTRAL, Size.JUMBO), new Pack(Kind.SPECTRAL, Size.MEGA),
    };

    /** Pick a pack from a uniform draw in [0,1) using the vanilla rate table. */
    public static Pack roll(double x) {
        double pct = x * 100.0;
        double acc = 0;
        for (int i = 0; i < RATES.length; i++) {
            acc += RATES[i];
            if (pct < acc) return PACKS[i];
        }
        return PACKS[PACKS.length - 1]; // rounding guard
    }
}
