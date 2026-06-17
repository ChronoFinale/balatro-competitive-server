package com.balatromp.engine.state;

/**
 * Difficulty stakes as <em>explicitly-resolved</em> cumulative modifier bundles.
 *
 * <p>The vanilla 8 (White..Gold, game.lua:2050-2059) form a clean ladder where each
 * tier inherits all lower ones. The Balatro-Multiplayer (BMP) stakes that sit above
 * Gold — Planet/Spectral/Spectral+ (multiplayer-0.4.2/objects/stakes/) — are NOT a
 * linear continuation: they drop Blue's discard penalty and push the score scaling
 * past tier 3. So rather than compute effects from {@code level >= N}, each stake
 * declares its fully-resolved modifiers directly (validated against the real sources).
 *
 * <pre>
 *   1  White     base
 *   2  Red       Small Blind reward $0
 *   3  Green     + score scaling tier 2
 *   4  Black     + Eternal jokers in shop
 *   5  Blue      + -1 discard
 *   6  Purple    + score scaling tier 3
 *   7  Orange    + Perishable jokers in shop
 *   8  Gold      + Rental jokers in shop
 *   9  Planet    (BMP) Red+Green(tier2)+Black + Perishable + scaling tier 3; NO -1 discard
 *   10 Spectral  (BMP) Planet + Rental + scaling tier 4
 *   11 Spectral+ (BMP) Spectral + scaling tier 5
 * </pre>
 *
 * BMP's experimental alt-stakes (Plastic..Antimatter — persistent/unreliable/draining
 * jokers, custom interest/reroll) are gated off by default upstream and are not modeled.
 *
 * Sticker effects (eternal/perishable/rental) are exposed as flags here; their mechanics
 * land with the joker-sticker subsystem.
 */
public enum Stake {
    //            lvl display              noSmallReward scaling discardDelta eternal perish rental
    WHITE        (1,  "White Stake",        false, 1,  0, false, false, false),
    RED          (2,  "Red Stake",          true,  1,  0, false, false, false),
    GREEN        (3,  "Green Stake",        true,  2,  0, false, false, false),
    BLACK        (4,  "Black Stake",        true,  2,  0, true,  false, false),
    BLUE         (5,  "Blue Stake",         true,  2, -1, true,  false, false),
    PURPLE       (6,  "Purple Stake",       true,  3, -1, true,  false, false),
    ORANGE       (7,  "Orange Stake",       true,  3, -1, true,  true,  false),
    GOLD         (8,  "Gold Stake",         true,  3, -1, true,  true,  true),
    // --- Balatro-Multiplayer (BMP) default-active stakes (multiplayer-0.4.2) ---
    PLANET       (9,  "Planet Stake",       true,  3,  0, true,  true,  false),
    SPECTRAL     (10, "Spectral Stake",     true,  4,  0, true,  true,  true),
    SPECTRAL_PLUS(11, "Spectral+ Stake",    true,  5,  0, true,  true,  true);

    /** Sticker tuning constants (game.lua:1914-1915, mechanics doc). */
    public static final int PERISHABLE_ROUNDS = 5;
    public static final int RENTAL_RATE = 3;
    public static final double STICKER_CHANCE = 0.30;

    /** stake_level (1..11) — selection order, and a stable id for the wire. */
    public final int level;
    public final String display;
    private final boolean smallBlindNoReward;
    private final int scaling;
    private final int discardDelta;
    private final boolean eternalsInShop;
    private final boolean perishablesInShop;
    private final boolean rentalsInShop;

    Stake(int level, String display, boolean smallBlindNoReward, int scaling, int discardDelta,
          boolean eternalsInShop, boolean perishablesInShop, boolean rentalsInShop) {
        this.level = level;
        this.display = display;
        this.smallBlindNoReward = smallBlindNoReward;
        this.scaling = scaling;
        this.discardDelta = discardDelta;
        this.eternalsInShop = eternalsInShop;
        this.perishablesInShop = perishablesInShop;
        this.rentalsInShop = rentalsInShop;
    }

    /** The Small Blind pays no reward money (Red+). */
    public boolean smallBlindNoReward() {
        return smallBlindNoReward;
    }

    /** Required-score scaling tier (selects the blind requirement curve). 1..5; >3 are BMP MP tiers. */
    public int scaling() {
        return scaling;
    }

    /** Per-round discard delta (Blue stake = -1; the BMP stakes deliberately do NOT apply it). */
    public int discardDelta() {
        return discardDelta;
    }

    /** Shop jokers may roll the Eternal sticker (can't be sold/destroyed). */
    public boolean eternalsInShop() {
        return eternalsInShop;
    }

    /** Shop jokers may roll the Perishable sticker (debuffed after {@link #PERISHABLE_ROUNDS} rounds). */
    public boolean perishablesInShop() {
        return perishablesInShop;
    }

    /** Shop jokers may roll the Rental sticker ($3/round, buyable for $1). */
    public boolean rentalsInShop() {
        return rentalsInShop;
    }

    public static Stake fromLevel(int level) {
        for (Stake s : values()) {
            if (s.level == level) return s;
        }
        return WHITE;
    }

    /**
     * Lenient parse for the wire. Accepts a level number ("11"), an enum name
     * ("SPECTRAL_PLUS"), the internal key ("spectralplus"), or a display name
     * ("Spectral+ Stake"). Unknown input -> White.
     */
    public static Stake parse(String s) {
        if (s == null || s.isBlank()) return WHITE;
        String t = s.trim();
        try {
            return fromLevel(Integer.parseInt(t));
        } catch (NumberFormatException ignore) {
            // not a level number; fall through to name/key matching
        }
        // Normalise: drop "Stake", expand "+" to PLUS, keep only letters → compare ignoring underscores.
        String norm = t.toUpperCase().replace("STAKE", "").replace("+", "PLUS").replaceAll("[^A-Z]", "");
        for (Stake st : values()) {
            if (st.name().replace("_", "").equals(norm)) return st;
        }
        return WHITE;
    }
}
