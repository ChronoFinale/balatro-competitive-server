package com.balatro.engine.rank;

/**
 * The visible ladder tier derived purely from MMR (display only — MMR is the source of truth; see {@link Elo}).
 * Each tier below Master spans a fixed MMR band split into four divisions (IV at the bottom … I at the top,
 * League-style); Master is an open-ended capstone with no divisions. A pure function of MMR — no stored state.
 *
 * <p>Bands are centred on the {@code 1000} starting MMR ({@link com.balatro.engine.auth.Account#DEFAULT_MMR}),
 * so a fresh account sits in Gold and climbs or falls from there.
 */
public enum RankTier {
    BRONZE("Bronze", 0),
    SILVER("Silver", 800),
    GOLD("Gold", 1000),
    PLATINUM("Platinum", 1200),
    DIAMOND("Diamond", 1400),
    MASTER("Master", 1600);

    private final String label;
    private final int floor; // inclusive MMR floor for this tier

    RankTier(String label, int floor) {
        this.label = label;
        this.floor = floor;
    }

    public int floor() {
        return floor;
    }

    /** The tier an MMR falls in. */
    public static RankTier of(double mmr) {
        RankTier[] tiers = values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            if (mmr >= tiers[i].floor) return tiers[i];
        }
        return BRONZE;
    }

    /** Full ladder label, e.g. {@code "Gold II"} or {@code "Master"}. */
    public static String display(double mmr) {
        RankTier t = of(mmr);
        if (t == MASTER) return t.label;
        int ceil = values()[t.ordinal() + 1].floor;
        double frac = (mmr - t.floor) / (double) (ceil - t.floor); // 0 (bottom) .. 1 (top)
        int division = 4 - (int) Math.min(3, Math.max(0, frac * 4)); // IV (4) at the bottom, I (1) at the top
        return t.label + " " + roman(division);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }
}
